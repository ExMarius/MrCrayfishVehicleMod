"""Executa comenzi RCON pe serverul HidenCloud. Folosit de hc-rcon.yml.

Usage: python3 scripts/rcon_exec.py "comanda" ["alta comanda" ...]
Env: RCON_HOST, RCON_PORT, RPASS (obligatoriu)
"""
import os
import sys

from mcrcon import MCRcon

host = os.environ.get("RCON_HOST", "nile.hidencloud.com")
port = int(os.environ.get("RCON_PORT", "24637"))
passwd = os.environ.get("RPASS", "")

if not passwd:
    print("::error title=HC-RCON::nu am parola RCON")
    sys.exit(0)

cmds = [c for c in sys.argv[1:] if c]
if not cmds:
    cmds = ["list", "plugins"]

r = MCRcon(host, passwd, port=port, timeout=20)
try:
    r.connect()
    print("RCON CONECTAT")
    for c in cmds:
        try:
            out = r.command(c)
            print(f"== {c} ==")
            print((out or "(fara raspuns)")[:900])
        except Exception as e:
            print(f"eroare la '{c}': {e}")
    print("::notice title=HC-RCON-OK::comanda rulata direct pe server")
except Exception as e:
    print(f"::error title=HC-RCON-FAIL::conectare esuata: {str(e)[:200]}")
    sys.exit(1)
finally:
    try:
        r.disconnect()
    except Exception:
        pass
