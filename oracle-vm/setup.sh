#!/usr/bin/env bash
# =============================================================================
# Oracle VM (VM.Standard.E2.1.Micro — 1 OCPU / 1 GB RAM, Ubuntu 22.04 LTS)
# Setup complet: Paper 1.21.4 + MrCrayfishVehiclePlugin + panou web,
# optimizat pentru consum mic. FĂRĂ FRP — VM-ul are IP public propriu.
# IDEMPOTENT: poți re-rula oricând (nu strică nimic existent).
#
# Pe VM (ca root):
#   curl -fsSL https://raw.githubusercontent.com/ExMarius/MrCrayfishVehicleMod/arena/2f9f7b9e-mrcrayfishvehiclemod/oracle-vm/setup.sh -o setup.sh
#   sudo bash setup.sh          # 1) creează /etc/mcserver.env
#   sudo nano /etc/mcserver.env # 2) opțional: schimbi parola panoului
#   sudo bash setup.sh          # 3) instalează și pornește totul
# =============================================================================
set -euo pipefail

APP=mcserver
ROOT=/opt/mcserver
ENV_FILE=/etc/mcserver.env
REPO=ExMarius/MrCrayfishVehicleMod

[ "$(id -u)" = 0 ] || { echo "Rulează cu sudo: sudo bash setup.sh"; exit 1; }

# --- Config din afara git-ului ------------------------------------------------
if [ ! -f "$ENV_FILE" ]; then
  cat > "$ENV_FILE" <<'EOF'
# Config server — nu merge în git. Editează după gust, apoi re-rulează setup.sh.
PANEL_PASSWORD=12345        # parola panoului web (utilizator: admin)
GAME_PORT=25565             # portul Minecraft (de deschis și în Security List OCI)
PANEL_PORT=8081             # portul panoului web (la fel)

MC_VERSION=1.21.4
REPO_BRANCH=arena/2f9f7b9e-mrcrayfishvehiclemod
RELEASE_TAG=vehicle-plugin-prototype-v0.1.0

# Reglat pentru 1 GB RAM + swap. NU mări fără măsurători.
JAVA_XMS=256M
JAVA_XMX=640M
SWAP_GB=4
EOF
  chmod 600 "$ENV_FILE"
  echo "✅ Am creat $ENV_FILE (parola implicită a panoului: 12345 — schimb-o dacă vrei)"
  echo "→ Re-rulează acum: sudo bash setup.sh"
  exit 0
fi
# shellcheck disable=SC1090
source "$ENV_FILE"

echo "==> [1/9] Pachete + curățare snapd (eliberăm ~100 MB RAM)…"
export DEBIAN_FRONTEND=noninteractive
systemctl stop snapd snapd.socket 2>/dev/null || true
apt-get update -qq
apt-get purge -y -qq snapd >/dev/null 2>&1 || true
apt-get autoremove -y -qq --purge >/dev/null 2>&1 || true
apt-get install -y -qq openjdk-21-jre-headless screen curl git python3 logrotate ca-certificates ufw acl >/dev/null
update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-21-openjdk-amd64/bin/java 2100 >/dev/null || true
update-alternatives --set java /usr/lib/jvm/java-21-openjdk-amd64/bin/java >/dev/null

echo "==> [2/9] Swap ${SWAP_GB:-4}G (obligatoriu pe 1 GB RAM)…"
if ! swapon --show=NAME | grep -qx /swapfile; then
  if [ ! -f /swapfile ]; then
    fallocate -l "${SWAP_GB:-4}G" /swapfile
    chmod 600 /swapfile
    mkswap /swapfile >/dev/null
  fi
  swapon /swapfile
fi
grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
cat > /etc/sysctl.d/99-mcserver.conf <<'EOF'
# Aplicațiile stau în RAM, restul se swap-uiește.
vm.swappiness=60
vm.vfs_cache_pressure=50
EOF
sysctl --system >/dev/null 2>&1 || sysctl -p /etc/sysctl.d/99-mcserver.conf

echo "==> [3/9] Utilizator de sistem '$APP' + directoare…"
id -u "$APP" >/dev/null 2>&1 || useradd --system --home "$ROOT" --shell /usr/sbin/nologin "$APP"
mkdir -p "$ROOT"/{bin,panel,logs,mc,frp}

echo "==> [4/9] Fișiere din repo (scripturi + panel.py + systemd + config reglat)…"
TMPDIR_CLONE=$(mktemp -d)
git clone --depth 1 --branch "${REPO_BRANCH}" --quiet "https://github.com/$REPO.git" "$TMPDIR_CLONE/repo"
cp "$TMPDIR_CLONE"/repo/oracle-panel/scripts/*.sh "$ROOT/bin/"
cp "$TMPDIR_CLONE"/repo/oracle-panel/panel.py "$ROOT/panel/"
chmod +x "$ROOT"/bin/*.sh
sed -i "s/-Xms512M -Xmx2G/-Xms${JAVA_XMS:-256M} -Xmx${JAVA_XMX:-640M}/" "$ROOT/bin/start-minecraft.sh"
install -m 644 "$TMPDIR_CLONE"/repo/oracle-vm/etc/minecraft.service /etc/systemd/system/
install -m 644 "$TMPDIR_CLONE"/repo/oracle-vm/etc/mcvehicles-panel.service /etc/systemd/system/
install -m 644 "$TMPDIR_CLONE"/repo/oracle-vm/etc/mc-ports.service /etc/systemd/system/
[ -f "$ROOT/mc/server.properties" ] || install -m 644 "$TMPDIR_CLONE"/repo/oracle-vm/config/server.properties "$ROOT/mc/server.properties"
mkdir -p "$ROOT/mc/config"
[ -f "$ROOT/mc/config/paper-world-defaults.yml" ] || install -m 644 "$TMPDIR_CLONE"/repo/oracle-vm/config/paper-world-defaults.yml "$ROOT/mc/config/paper-world-defaults.yml"
rm -rf "$TMPDIR_CLONE"

echo "==> [5/9] Paper ${MC_VERSION} (API v3, verificat SHA-256)…"
cd "$ROOT/mc"
if [ ! -s server.jar ]; then
  J=$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds/latest")
  INFO=$(echo "$J" | python3 -c '
import sys, json
d = json.load(sys.stdin)
dl = d.get("downloads", {}).get("server:default", {})
print(dl.get("name", ""))
print(dl.get("url", ""))
')
  NAME=$(echo "$INFO" | sed -n 1p)
  URL=$(echo "$INFO" | sed -n 2p)
  [ -n "$URL" ] || { echo "❌ Nu am găsit Paper ${MC_VERSION} în API v3"; exit 1; }
  SHA=$(echo "$URL" | awk -F/ '{for(i=1;i<=NF;i++) if ($i=="objects") print $(i+1)}')
  curl -fsSL -o server.jar "$URL"
  echo "$SHA  server.jar" | sha256sum -c - >/dev/null
  echo "   $NAME descărcat și verificat."
else
  echo "   server.jar există deja (neschimbat)."
fi

echo "==> [6/9] Pluginul + resource pack din release ${RELEASE_TAG}…"
mkdir -p plugins
BASE="https://github.com/$REPO/releases/download/$RELEASE_TAG"
A=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/tags/$RELEASE_TAG")
JA=$(echo "$A" | python3 -c 'import sys,json; print([a["name"] for a in json.load(sys.stdin)["assets"] if a["name"].endswith(".jar")][0])')
[ -s plugins/MrCrayfishVehiclePlugin.jar ] || curl -fsSL -o plugins/MrCrayfishVehiclePlugin.jar "$BASE/$JA"
PACK=$(echo "$A" | python3 -c '
import re, sys, json
c = [n for n in (a["name"] for a in json.load(sys.stdin)["assets"]) if re.search(r"-r\d+\.zip$", n)]
print(max(c, key=lambda n: int(re.search(r"-r(\d+)\.zip$", n).group(1))))')
curl -fsSL -o "/tmp/$PACK" "$BASE/$PACK"
PACK_SHA=$(sha1sum "/tmp/$PACK" | awk '{print $1}')
mkdir -p plugins/MrCrayfishVehiclePlugin
if [ ! -s plugins/MrCrayfishVehiclePlugin/config.yml ]; then
  cat > plugins/MrCrayfishVehiclePlugin/config.yml <<EOF
resource-pack:
  url: "$BASE/$PACK"
  sha1: "$PACK_SHA"
  required: true
  prompt: "Acest resource pack este necesar pentru modelele și sunetele vehiculelor."

physics:
  global-speed-limit: 100.0
  fuel-consumption-factor: 1.0

performance:
  activation-distance: 64.0

gas-pump:
  max-hose-distance: 10.0
  fill-seconds: 15.0

persistence:
  autosave-seconds: 300
EOF
  echo "   $PACK (sha1: ${PACK_SHA:0:12}…) — config.yml scris."
else
  echo "   config.yml există deja (neschimbat); pack: $PACK."
fi
echo "eula=true" > eula.txt

echo "==> [7/9] Autentificare panou (utilizator: admin)…"
python3 - "$ROOT" "$PANEL_PASSWORD" <<'PYEOF'
import base64, hashlib, json, os, secrets, sys
root, password = sys.argv[1], sys.argv[2]
panel_dir = os.path.join(root, "panel")
os.makedirs(panel_dir, exist_ok=True)
salt = secrets.token_bytes(32)
iterations = 600000
digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, iterations)
auth = {"username": "admin", "password": {
    "algorithm": "pbkdf2-sha256", "iterations": iterations,
    "salt": base64.b64encode(salt).decode("ascii"),
    "hash": base64.b64encode(digest).decode("ascii"),
}}
path = os.path.join(panel_dir, "auth.json")
tmp = path + ".tmp-" + secrets.token_hex(6)
with open(tmp, "w") as f:
    json.dump(auth, f, sort_keys=True); f.write("\n")
os.chmod(tmp, 0o600)
os.replace(tmp, path)
old = os.path.join(panel_dir, "initial-password.txt")
if os.path.exists(old):
    os.remove(old)
PYEOF

echo "==> [8/9] Firewall: ufw + gaură în REJECT-ul implicit al imaginii Oracle…"
ufw allow 22/tcp >/dev/null
ufw allow "${GAME_PORT:-25565}/tcp" >/dev/null
ufw allow "${PANEL_PORT:-8081}/tcp" >/dev/null
echo "y" | ufw enable >/dev/null
# Imaginea Ubuntu OCI are INPUT: ACCEPT tcp/22 apoi `REJECT all` ÎNAINTEA
# lanțurilor ufw — ufw nu prinde șansa să accepte. Inserăm ACCEPT-uri deasupra:
for p in 22 "${GAME_PORT:-25565}" "${PANEL_PORT:-8081}"; do
  iptables -C INPUT -p tcp --dport "$p" -m conntrack --ctstate NEW -j ACCEPT 2>/dev/null \
    || iptables -I INPUT -p tcp --dport "$p" -m conntrack --ctstate NEW -j ACCEPT
done
echo "   ufw: $(ufw status | head -1); iptables: ACCEPT 22/${GAME_PORT:-25565}/${PANEL_PORT:-8081} insertate"
echo "   ⚠️  Nu uita: aceleași 2 porturi trebuie deschise și în OCI (Security List) — vezi oracle-vm/README.md"

echo "==> [9/9] Permisiuni, logrotate, servicii…"
rmdir "$ROOT/frp" 2>/dev/null || true
cat > /etc/logrotate.d/mcserver <<EOF
$ROOT/logs/*.log {
    daily
    rotate 7
    compress
    missingok
    notifempty
    copytruncate
    su $APP $APP
}
EOF
chown -R "$APP:$APP" "$ROOT"
# SFTP-ul userului ubuntu (cheia din wizard) trebuie să poată edita fișierele
# serverului fără sudo: grup + ACL default care dă serverului write chiar și
# pe fișierele nou-urcate ca ubuntu. Setup fără sudo pe remoter:
if id -u ubuntu >/dev/null 2>&1; then usermod -aG "$APP" ubuntu; fi
setfacl -R -m "g:$APP:rwX" "$ROOT"
setfacl -R -d -m "g:$APP:rwX" "$ROOT"
systemctl daemon-reload
systemctl enable --now mc-ports.service
systemctl enable --now mcvehicles-panel.service
systemctl enable --now minecraft.service
sleep 8

PUBLIC_IP=$(curl -fsS -m 5 https://ifconfig.me 2>/dev/null || echo "<IP-public>")
echo
echo "══════════════════════════════════════════════════════════════"
echo "  Servicii: panel=$(systemctl is-active mcvehicles-panel) minecraft=$(systemctl is-active minecraft)"
echo "  Panou local: $(curl -fsS -m 3 http://127.0.0.1:${PANEL_PORT:-8081}/healthz >/dev/null 2>&1 && echo OK || echo 'pornește încă…')"
echo
echo "  🟢 Minecraft: $PUBLIC_IP:${GAME_PORT:-25565}"
echo "  🟢 Panou web: http://$PUBLIC_IP:${PANEL_PORT:-8081}  (utilizator: admin)"
echo
echo "  Prima pornire a lumii: 2–5 minute. Loguri:"
echo "  sudo tail -f $ROOT/logs/server-console.log"
echo "══════════════════════════════════════════════════════════════"
