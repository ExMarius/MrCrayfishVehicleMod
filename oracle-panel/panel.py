#!/usr/bin/env python3
"""Small authenticated Minecraft control panel for the Oracle Cloud Shell host.

The panel intentionally uses only Python's standard library. It exposes a narrow
lifecycle/console API and delegates server state changes to the canonical scripts
under ~/minecraft-server/bin; it never executes arbitrary shell input.
"""

from __future__ import print_function

import argparse
import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import shutil
import subprocess
import threading
import time
from collections import defaultdict, deque
from http import cookies
from http.server import BaseHTTPRequestHandler, HTTPServer
try:
    from http.server import ThreadingHTTPServer
except ImportError:  # Python 3.6 compatibility on older Cloud Shell images.
    from socketserver import ThreadingMixIn

    class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
        daemon_threads = True
from urllib.parse import urlparse


PANEL_VERSION = "1.0.0"
SESSION_SECONDS = 12 * 60 * 60
PBKDF2_ITERATIONS = 600000
MAX_REQUEST_BYTES = 8192
MAX_CONSOLE_COMMAND = 256
ANSI_RE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
MC_COLOR_RE = re.compile(r"\u00a7[0-9A-FK-ORa-fk-or]")
PLAYER_RE = re.compile(r"There are (\d+) of a max of (\d+) players online")


def atomic_json(path, value):
    directory = os.path.dirname(path)
    if not os.path.isdir(directory):
        os.makedirs(directory, mode=0o700)
    temporary = path + ".tmp-" + secrets.token_hex(6)
    with open(temporary, "w") as handle:
        json.dump(value, handle, sort_keys=True)
        handle.write("\n")
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)


def password_hash(password, salt=None, iterations=PBKDF2_ITERATIONS):
    if salt is None:
        salt = secrets.token_bytes(32)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, iterations)
    return {
        "algorithm": "pbkdf2-sha256",
        "iterations": iterations,
        "salt": base64.b64encode(salt).decode("ascii"),
        "hash": base64.b64encode(digest).decode("ascii"),
    }


def verify_password(password, definition):
    try:
        iterations = int(definition["iterations"])
        salt = base64.b64decode(definition["salt"].encode("ascii"))
        expected = base64.b64decode(definition["hash"].encode("ascii"))
    except (KeyError, TypeError, ValueError):
        return False
    actual = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, iterations)
    return hmac.compare_digest(actual, expected)


def initialize_auth(root):
    panel_dir = os.path.join(root, "panel")
    auth_path = os.path.join(panel_dir, "auth.json")
    initial_path = os.path.join(panel_dir, "initial-password.txt")
    if not os.path.isdir(panel_dir):
        os.makedirs(panel_dir, mode=0o700)
    os.chmod(panel_dir, 0o700)
    if os.path.isfile(auth_path):
        print("Panel authentication already exists; it was preserved.")
        return False
    password = secrets.token_urlsafe(24)
    atomic_json(auth_path, {"username": "admin", "password": password_hash(password)})
    with open(initial_path, "w") as handle:
        handle.write("username: admin\npassword: " + password + "\n")
    os.chmod(initial_path, 0o600)
    print("Panel authentication created. Read the initial credential only from the Oracle host.")
    return True


def read_tail(path, maximum_bytes=65536, maximum_lines=350):
    try:
        size = os.path.getsize(path)
        with open(path, "rb") as handle:
            if size > maximum_bytes:
                handle.seek(size - maximum_bytes)
            data = handle.read(maximum_bytes)
    except OSError:
        return ""
    text = data.decode("utf-8", "replace")
    text = ANSI_RE.sub("", MC_COLOR_RE.sub("", text))
    return "\n".join(text.splitlines()[-maximum_lines:])


def process_exists(pid):
    return pid is not None and os.path.isdir("/proc/" + str(pid))


def process_cwd(pid):
    try:
        return os.path.realpath(os.readlink("/proc/{}/cwd".format(pid)))
    except OSError:
        return ""


def find_process(name, cwd=None):
    try:
        entries = os.listdir("/proc")
    except OSError:
        return None
    for entry in entries:
        if not entry.isdigit():
            continue
        try:
            with open("/proc/{}/comm".format(entry), "r") as handle:
                process_name = handle.read().strip()
        except OSError:
            continue
        if process_name != name:
            continue
        pid = int(entry)
        if cwd is None or process_cwd(pid) == os.path.realpath(cwd):
            return pid
    return None


def process_memory(pid):
    try:
        with open("/proc/{}/status".format(pid), "r") as handle:
            for line in handle:
                if line.startswith("VmRSS:"):
                    return int(line.split()[1]) * 1024
    except (OSError, ValueError, IndexError):
        pass
    return 0


def screen_session():
    try:
        output = subprocess.check_output(["screen", "-list"], stderr=subprocess.STDOUT,
                                         universal_newlines=True, timeout=3)
    except (OSError, subprocess.SubprocessError):
        return None
    match = re.search(r"\b(\d+\.mcv-minecraft)\s+\(", output)
    return match.group(1) if match else None


def human_bytes(value):
    amount = float(max(0, value))
    for suffix in ("B", "KiB", "MiB", "GiB", "TiB"):
        if amount < 1024.0 or suffix == "TiB":
            return "{:.1f} {}".format(amount, suffix)
        amount /= 1024.0
    return "0 B"


class PanelState(object):
    def __init__(self, root):
        self.root = os.path.realpath(root)
        self.server = os.path.join(self.root, "mc")
        self.logs = os.path.join(self.root, "logs")
        self.auth_path = os.path.join(self.root, "panel", "auth.json")
        self.initial_password_path = os.path.join(self.root, "panel", "initial-password.txt")
        self.latest_log = os.path.join(self.server, "logs", "latest.log")
        self.sessions = {}
        self.session_lock = threading.Lock()
        self.login_attempts = defaultdict(deque)
        self.login_lock = threading.Lock()
        self.operation_lock = threading.Lock()
        self.operation = {"action": None, "state": "idle", "message": "", "updated": 0}
        self.cpu_lock = threading.Lock()
        self.last_cpu = None
        self.started_at = time.time()

    def auth(self):
        with open(self.auth_path, "r") as handle:
            return json.load(handle)

    def create_session(self, username):
        token = secrets.token_urlsafe(32)
        csrf = secrets.token_urlsafe(24)
        now = time.time()
        with self.session_lock:
            self.sessions[token] = {"username": username, "csrf": csrf,
                                    "expires": now + SESSION_SECONDS}
        return token, csrf

    def session(self, token):
        if not token:
            return None
        now = time.time()
        with self.session_lock:
            expired = [key for key, value in self.sessions.items()
                       if value["expires"] <= now]
            for key in expired:
                self.sessions.pop(key, None)
            value = self.sessions.get(token)
            if value is not None:
                value["expires"] = now + SESSION_SECONDS
            return dict(value) if value is not None else None

    def destroy_session(self, token):
        with self.session_lock:
            self.sessions.pop(token, None)

    def login_allowed(self, address):
        now = time.time()
        with self.login_lock:
            attempts = self.login_attempts[address]
            while attempts and attempts[0] < now - 60:
                attempts.popleft()
            return len(attempts) < 8

    def login_failed(self, address):
        with self.login_lock:
            self.login_attempts[address].append(time.time())

    def login_succeeded(self, address):
        with self.login_lock:
            self.login_attempts.pop(address, None)

    def audit(self, username, action, address):
        if not os.path.isdir(self.logs):
            os.makedirs(self.logs, mode=0o700)
        line = "{} user={} action={} address={}\n".format(
            time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            re.sub(r"[^A-Za-z0-9_.-]", "_", username),
            re.sub(r"[^A-Za-z0-9_.:-]", "_", action),
            re.sub(r"[^A-Za-z0-9_.:-]", "_", address))
        with open(os.path.join(self.logs, "panel-audit.log"), "a") as handle:
            handle.write(line)

    def cpu_percent(self, pid):
        if pid is None:
            with self.cpu_lock:
                self.last_cpu = None
            return 0.0
        try:
            with open("/proc/{}/stat".format(pid), "r") as handle:
                fields = handle.read().split()
            process_ticks = int(fields[13]) + int(fields[14])
            with open("/proc/stat", "r") as handle:
                total_ticks = sum(int(value) for value in handle.readline().split()[1:])
        except (OSError, ValueError, IndexError):
            return 0.0
        with self.cpu_lock:
            previous = self.last_cpu
            self.last_cpu = (pid, process_ticks, total_ticks)
        if previous is None or previous[0] != pid or total_ticks <= previous[2]:
            return 0.0
        processors = max(1, os.cpu_count() or 1)
        usage = (process_ticks - previous[1]) / float(total_ticks - previous[2])
        return round(max(0.0, min(usage * processors * 100.0, processors * 100.0)), 1)

    def status(self):
        pid = find_process("java", self.server)
        session = screen_session()
        running = pid is not None and session is not None
        disk = shutil.disk_usage(self.root)
        log = read_tail(self.latest_log, maximum_bytes=32768, maximum_lines=180)
        player_match = None
        for match in PLAYER_RE.finditer(log):
            player_match = match
        operation = dict(self.operation)
        return {
            "version": PANEL_VERSION,
            "server": "running" if running else "stopped",
            "pid": pid if running else None,
            "uptimeSeconds": self.process_uptime(pid) if running else 0,
            "cpuPercent": self.cpu_percent(pid if running else None),
            "memoryBytes": process_memory(pid) if running else 0,
            "memoryDisplay": human_bytes(process_memory(pid) if running else 0),
            "diskUsedBytes": disk.used,
            "diskTotalBytes": disk.total,
            "diskUsedPercent": round(disk.used * 100.0 / max(1, disk.total), 1),
            "players": int(player_match.group(1)) if player_match else None,
            "maxPlayers": int(player_match.group(2)) if player_match else None,
            "frp": "running" if find_process("frpc") is not None else "reconnecting",
            "operation": operation,
            "panelUptimeSeconds": int(time.time() - self.started_at),
        }

    @staticmethod
    def process_uptime(pid):
        if pid is None:
            return 0
        try:
            with open("/proc/{}/stat".format(pid), "r") as handle:
                start_ticks = int(handle.read().split()[21])
            with open("/proc/uptime", "r") as handle:
                host_uptime = float(handle.read().split()[0])
            ticks = float(os.sysconf(os.sysconf_names["SC_CLK_TCK"]))
            return max(0, int(host_uptime - start_ticks / ticks))
        except (OSError, ValueError, IndexError, KeyError):
            return 0

    def begin_operation(self, action, username, address):
        if action not in ("start", "stop", "restart"):
            return False, "Acțiune necunoscută."
        if not self.operation_lock.acquire(False):
            return False, "O altă operație este deja în curs."
        self.operation = {"action": action, "state": "running",
                          "message": "Operația a început.", "updated": int(time.time())}
        self.audit(username, "control-" + action, address)
        thread = threading.Thread(target=self._operation_worker, args=(action,))
        thread.daemon = True
        thread.start()
        return True, "Operația a fost acceptată."

    def _run_script(self, name, timeout):
        path = os.path.join(self.root, "bin", name)
        result = subprocess.run([path], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                universal_newlines=True, timeout=timeout, check=False)
        if result.returncode != 0:
            raise RuntimeError((result.stdout or "script failed").strip()[-500:])
        return (result.stdout or "").strip()[-500:]

    def _operation_worker(self, action):
        try:
            messages = []
            if action in ("stop", "restart"):
                messages.append(self._run_script("stop-minecraft.sh", 150))
            if action in ("start", "restart"):
                messages.append(self._run_script("start-minecraft.sh", 30))
            self.operation = {"action": action, "state": "success",
                              "message": " ".join(value for value in messages if value),
                              "updated": int(time.time())}
        except Exception as exception:  # Report a bounded message; no command input is involved.
            self.operation = {"action": action, "state": "failure",
                              "message": str(exception)[-500:], "updated": int(time.time())}
        finally:
            self.operation_lock.release()

    def send_console(self, command, username, address):
        command = command.strip()
        if not command or len(command) > MAX_CONSOLE_COMMAND:
            return False, "Comanda trebuie să aibă între 1 și 256 de caractere."
        if any(ord(character) < 32 for character in command):
            return False, "Comanda conține caractere nepermise."
        session = screen_session()
        if session is None:
            return False, "Serverul este oprit."
        try:
            result = subprocess.run(["screen", "-S", session, "-p", "0", "-X", "stuff",
                                     command + "\r"], stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, timeout=5, check=False)
        except (OSError, subprocess.SubprocessError):
            return False, "Comanda nu a putut fi trimisă."
        if result.returncode != 0:
            return False, "Sesiunea consolei nu a acceptat comanda."
        self.audit(username, "console-command", address)
        return True, "Comanda a fost trimisă."

    def change_password(self, username, current_password, new_password, address):
        if len(new_password) < 12 or len(new_password) > 128:
            return False, "Parola nouă trebuie să aibă între 12 și 128 de caractere."
        auth = self.auth()
        if username != auth.get("username") or not verify_password(
                current_password, auth.get("password", {})):
            return False, "Parola curentă este incorectă."
        atomic_json(self.auth_path, {"username": username,
                                    "password": password_hash(new_password)})
        try:
            os.remove(self.initial_password_path)
        except OSError:
            pass
        with self.session_lock:
            self.sessions.clear()
        self.audit(username, "password-changed", address)
        return True, "Parola a fost schimbată. Autentifică-te din nou."


STATE = None


class PanelHandler(BaseHTTPRequestHandler):
    server_version = "OracleMinecraftPanel/" + PANEL_VERSION

    def log_message(self, _format, *_args):
        return

    def security_headers(self, content_type, nonce=None):
        self.send_header("Content-Type", content_type)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        if nonce is not None:
            self.send_header("Content-Security-Policy",
                             "default-src 'none'; connect-src 'self'; img-src 'self'; "
                             "style-src 'nonce-{}'; script-src 'nonce-{}'; "
                             "base-uri 'none'; form-action 'self'; frame-ancestors 'none'".format(
                                 nonce, nonce))

    def json_response(self, status, value, extra_headers=None):
        payload = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.security_headers("application/json; charset=utf-8")
        if extra_headers:
            for key, content in extra_headers:
                self.send_header(key, content)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def html_response(self):
        nonce = secrets.token_urlsafe(18)
        payload = dashboard_html(nonce).encode("utf-8")
        self.send_response(200)
        self.security_headers("text/html; charset=utf-8", nonce)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def body_json(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            raise ValueError("Dimensiune invalidă.")
        if length <= 0 or length > MAX_REQUEST_BYTES:
            raise ValueError("Cerere prea mare sau goală.")
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            raise ValueError("JSON invalid.")

    def cookie_token(self):
        raw = self.headers.get("Cookie", "")
        parsed = cookies.SimpleCookie()
        try:
            parsed.load(raw)
        except cookies.CookieError:
            return None
        value = parsed.get("mcv_session")
        return value.value if value is not None else None

    def authenticated(self):
        token = self.cookie_token()
        return token, STATE.session(token)

    def require_session(self, csrf=False):
        token, session = self.authenticated()
        if session is None:
            self.json_response(401, {"ok": False, "error": "Autentificare necesară."})
            return None, None
        if csrf and not hmac.compare_digest(
                self.headers.get("X-CSRF-Token", ""), session["csrf"]):
            self.json_response(403, {"ok": False, "error": "Token CSRF invalid."})
            return None, None
        return token, session

    def valid_origin(self):
        origin = self.headers.get("Origin")
        if not origin:
            return True
        try:
            return urlparse(origin).netloc.lower() == self.headers.get("Host", "").lower()
        except ValueError:
            return False

    @property
    def address(self):
        # The panel is forwarded as raw TCP by FRP, so proxy headers are not trusted.
        return self.client_address[0]

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/":
            self.html_response()
            return
        if path == "/healthz":
            self.json_response(200, {"ok": True, "version": PANEL_VERSION})
            return
        if path == "/api/session":
            _token, session = self.authenticated()
            self.json_response(200, {"ok": True, "authenticated": session is not None,
                                     "username": session["username"] if session else None,
                                     "csrf": session["csrf"] if session else None})
            return
        if path == "/api/status":
            _token, session = self.require_session()
            if session is not None:
                self.json_response(200, {"ok": True, "status": STATE.status()})
            return
        if path == "/api/logs":
            _token, session = self.require_session()
            if session is not None:
                self.json_response(200, {"ok": True, "logs": read_tail(STATE.latest_log)})
            return
        self.json_response(404, {"ok": False, "error": "Ruta nu există."})

    def do_POST(self):
        path = urlparse(self.path).path
        if not self.valid_origin():
            self.json_response(403, {"ok": False, "error": "Origine invalidă."})
            return
        try:
            body = self.body_json()
        except ValueError as exception:
            self.json_response(400, {"ok": False, "error": str(exception)})
            return

        if path == "/api/login":
            if not STATE.login_allowed(self.address):
                self.json_response(429, {"ok": False, "error": "Prea multe încercări. Așteaptă un minut."})
                return
            auth = STATE.auth()
            username = str(body.get("username", ""))
            password = str(body.get("password", ""))
            valid = username == auth.get("username") and verify_password(
                password, auth.get("password", {}))
            if not valid:
                STATE.login_failed(self.address)
                time.sleep(0.35)
                self.json_response(401, {"ok": False, "error": "Utilizator sau parolă incorectă."})
                return
            STATE.login_succeeded(self.address)
            token, csrf = STATE.create_session(username)
            secure = self.headers.get("X-Forwarded-Proto", "").lower() == "https"
            cookie = "mcv_session={}; Path=/; HttpOnly; SameSite=Strict; Max-Age={}".format(
                token, SESSION_SECONDS)
            if secure:
                cookie += "; Secure"
            STATE.audit(username, "login", self.address)
            self.json_response(200, {"ok": True, "username": username, "csrf": csrf},
                               [("Set-Cookie", cookie)])
            return

        token, session = self.require_session(csrf=True)
        if session is None:
            return
        if path == "/api/logout":
            STATE.destroy_session(token)
            self.json_response(200, {"ok": True},
                               [("Set-Cookie", "mcv_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0")])
        elif path == "/api/control":
            accepted, message = STATE.begin_operation(str(body.get("action", "")),
                                                      session["username"], self.address)
            self.json_response(202 if accepted else 409,
                               {"ok": accepted, "message": message,
                                "error": None if accepted else message})
        elif path == "/api/command":
            accepted, message = STATE.send_console(str(body.get("command", "")),
                                                   session["username"], self.address)
            self.json_response(200 if accepted else 409,
                               {"ok": accepted, "message": message,
                                "error": None if accepted else message})
        elif path == "/api/password":
            changed, message = STATE.change_password(
                session["username"], str(body.get("currentPassword", "")),
                str(body.get("newPassword", "")), self.address)
            self.json_response(200 if changed else 400,
                               {"ok": changed, "message": message,
                                "error": None if changed else message},
                               [("Set-Cookie", "mcv_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0")]
                               if changed else None)
        else:
            self.json_response(404, {"ok": False, "error": "Ruta nu există."})


HTML_TEMPLATE = r'''<!doctype html>
<html lang="ro"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Oracle Minecraft Panel</title>
<style nonce="__NONCE__">
:root{color-scheme:dark;--bg:#0b1020;--panel:#151d31;--panel2:#1b2740;--line:#2b3a5b;--text:#edf3ff;--muted:#91a1bd;--green:#45d483;--red:#ff6b78;--amber:#ffc857;--blue:#62a8ff}*{box-sizing:border-box}body{margin:0;background:radial-gradient(circle at 20% 0,#152544 0,var(--bg) 42%);font:15px system-ui,sans-serif;color:var(--text);min-height:100vh}.wrap{max-width:1200px;margin:auto;padding:24px}.top{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.brand{font-size:21px;font-weight:750}.sub{color:var(--muted);font-size:13px}.card{background:linear-gradient(145deg,var(--panel),#11192b);border:1px solid var(--line);border-radius:14px;padding:17px;box-shadow:0 10px 30px #0004}.grid{display:grid;grid-template-columns:repeat(6,1fr);gap:12px}.metric b{display:block;font-size:22px;margin-top:6px}.metric span{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.08em}.main{display:grid;grid-template-columns:2fr 1fr;gap:14px;margin-top:14px}.console{height:470px;overflow:auto;background:#070b13;border:1px solid #25314c;border-radius:10px;padding:13px;color:#c9d7ef;font:12px/1.48 ui-monospace,monospace;white-space:pre-wrap}.row{display:flex;gap:9px;flex-wrap:wrap}.stack{display:grid;gap:12px}button,input{border-radius:9px;border:1px solid var(--line);padding:10px 13px;background:var(--panel2);color:var(--text)}input{width:100%}button{cursor:pointer;font-weight:650}button:hover{filter:brightness(1.16)}button:disabled{opacity:.45;cursor:wait}.start{background:#174c34;border-color:#287a52}.stop{background:#56232b;border-color:#8b3542}.restart{background:#594918;border-color:#8d7428}.wide{flex:1}.status{display:inline-flex;align-items:center;gap:7px}.dot{width:9px;height:9px;border-radius:50%;background:var(--red)}.dot.on{background:var(--green);box-shadow:0 0 10px #45d48388}.muted{color:var(--muted)}.notice{min-height:22px;color:var(--amber);font-size:13px}.login{max-width:390px;margin:12vh auto}.hidden{display:none!important}h2,h3{margin:0 0 13px}.sep{height:1px;background:var(--line);margin:6px 0}.footer{color:var(--muted);font-size:12px;text-align:center;margin-top:18px}@media(max-width:900px){.grid{grid-template-columns:repeat(3,1fr)}.main{grid-template-columns:1fr}}@media(max-width:520px){.grid{grid-template-columns:repeat(2,1fr)}.wrap{padding:13px}.console{height:360px}}
</style></head><body>
<div id="login" class="wrap login hidden"><div class="card"><h2>Oracle Minecraft Panel</h2><p class="muted">Autentificare securizată</p><form id="loginForm" class="stack"><input id="username" autocomplete="username" value="admin" required><input id="password" type="password" autocomplete="current-password" placeholder="Parolă" required><button class="start">Autentificare</button><div id="loginMessage" class="notice"></div></form></div></div>
<div id="dashboard" class="wrap hidden"><div class="top"><div><div class="brand">Oracle Minecraft Panel</div><div class="sub">Control unificat · Paper 1.21.4</div></div><div class="row"><span id="serverBadge" class="status"><i class="dot"></i><b>Necunoscut</b></span><button id="logout">Ieșire</button></div></div>
<div class="grid"><div class="card metric"><span>Jucători</span><b id="players">—</b></div><div class="card metric"><span>CPU</span><b id="cpu">—</b></div><div class="card metric"><span>Memorie</span><b id="memory">—</b></div><div class="card metric"><span>Disc</span><b id="disk">—</b></div><div class="card metric"><span>Uptime</span><b id="uptime">—</b></div><div class="card metric"><span>Tunel FRP</span><b id="frp">—</b></div></div>
<div class="main"><div class="card"><div class="row" style="justify-content:space-between"><h3>Consolă</h3><div class="row"><button class="start control" data-action="start">Start</button><button class="restart control" data-action="restart">Restart</button><button class="stop control" data-action="stop">Stop</button></div></div><pre id="console" class="console">Se încarcă…</pre><form id="commandForm" class="row" style="margin-top:10px"><input id="command" class="wide" autocomplete="off" placeholder="Comandă Minecraft fără /" maxlength="256"><button>Trimite</button></form><div id="operation" class="notice"></div></div>
<div class="stack"><div class="card"><h3>Stare servicii</h3><p>Server: <b id="serverText">—</b></p><p>FRP: <b id="frpText">—</b></p><p>PID: <b id="pid">—</b></p><div class="sep"></div><p class="muted">Runner-ul, panelul și FRP pornesc dintr-o singură comandă. Minecraft pornește numai din panel sau prin controlul GitHub.</p></div><div class="card"><h3>Schimbă parola</h3><form id="passwordForm" class="stack"><input id="currentPassword" type="password" autocomplete="current-password" placeholder="Parola curentă"><input id="newPassword" type="password" autocomplete="new-password" placeholder="Parolă nouă (minim 12 caractere)"><button>Actualizează parola</button><div id="passwordMessage" class="notice"></div></form></div></div></div><div class="footer">Panel minimal, fără daemon greu · v__VERSION__</div></div>
<script nonce="__NONCE__">
let csrf=null, timer=null;const $=id=>document.getElementById(id);async function api(path,opt={}){opt.headers=Object.assign({'Content-Type':'application/json'},opt.headers||{});if(csrf&&opt.method==='POST')opt.headers['X-CSRF-Token']=csrf;const r=await fetch(path,opt);let d={};try{d=await r.json()}catch(e){}if(!r.ok)throw new Error(d.error||'Cererea a eșuat');return d}function show(login){$('login').classList.toggle('hidden',!login);$('dashboard').classList.toggle('hidden',login)}function duration(s){s=Math.max(0,s|0);const h=Math.floor(s/3600),m=Math.floor(s%3600/60);return h?h+'h '+m+'m':m+'m'}async function session(){const d=await api('/api/session');if(!d.authenticated){show(true);return}csrf=d.csrf;show(false);refresh();timer=setInterval(refresh,2500)}async function refresh(){try{const d=await api('/api/status'),s=d.status,on=s.server==='running';$('serverBadge').innerHTML='<i class="dot '+(on?'on':'')+'"></i><b>'+(on?'Online':'Oprit')+'</b>';$('players').textContent=s.players===null?'—':s.players+' / '+s.maxPlayers;$('cpu').textContent=s.cpuPercent.toFixed(1)+'%';$('memory').textContent=s.memoryDisplay;$('disk').textContent=s.diskUsedPercent.toFixed(1)+'%';$('uptime').textContent=on?duration(s.uptimeSeconds):'—';$('frp').textContent=s.frp==='running'?'Conectat':'Reconectare';$('serverText').textContent=on?'rulează':'oprit';$('frpText').textContent=s.frp;$('pid').textContent=s.pid||'—';$('operation').textContent=s.operation.state==='idle'?'':(s.operation.action+': '+s.operation.message);document.querySelectorAll('.control').forEach(b=>b.disabled=s.operation.state==='running');const l=await api('/api/logs');const c=$('console'),bottom=c.scrollHeight-c.scrollTop-c.clientHeight<45;c.textContent=l.logs||'Nu există încă loguri.';if(bottom)c.scrollTop=c.scrollHeight}catch(e){if(String(e).includes('Autentificare'))location.reload()}}$('loginForm').addEventListener('submit',async e=>{e.preventDefault();try{const d=await api('/api/login',{method:'POST',body:JSON.stringify({username:$('username').value,password:$('password').value})});csrf=d.csrf;$('password').value='';show(false);refresh();timer=setInterval(refresh,2500)}catch(x){$('loginMessage').textContent=x.message}});document.querySelectorAll('.control').forEach(b=>b.addEventListener('click',async()=>{try{const d=await api('/api/control',{method:'POST',body:JSON.stringify({action:b.dataset.action})});$('operation').textContent=d.message;refresh()}catch(x){$('operation').textContent=x.message}}));$('commandForm').addEventListener('submit',async e=>{e.preventDefault();const v=$('command').value.trim();if(!v)return;try{await api('/api/command',{method:'POST',body:JSON.stringify({command:v})});$('command').value='';setTimeout(refresh,600)}catch(x){$('operation').textContent=x.message}});$('passwordForm').addEventListener('submit',async e=>{e.preventDefault();try{const d=await api('/api/password',{method:'POST',body:JSON.stringify({currentPassword:$('currentPassword').value,newPassword:$('newPassword').value})});$('passwordMessage').textContent=d.message;setTimeout(()=>location.reload(),1200)}catch(x){$('passwordMessage').textContent=x.message}});$('logout').addEventListener('click',async()=>{try{await api('/api/logout',{method:'POST',body:'{}'})}catch(e){}location.reload()});session();
</script></body></html>'''


def dashboard_html(nonce):
    return HTML_TEMPLATE.replace("__NONCE__", nonce).replace("__VERSION__", PANEL_VERSION)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=os.path.expanduser("~/minecraft-server"))
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8081)
    parser.add_argument("--init-auth", action="store_true")
    args = parser.parse_args()
    root = os.path.realpath(os.path.expanduser(args.root))
    if args.init_auth:
        initialize_auth(root)
        return
    initialize_auth(root)
    global STATE
    STATE = PanelState(root)
    server = ThreadingHTTPServer((args.host, args.port), PanelHandler)
    server.daemon_threads = True
    print("Oracle Minecraft Panel {} listening on {}:{}".format(PANEL_VERSION, args.host, args.port))
    server.serve_forever()


if __name__ == "__main__":
    main()
