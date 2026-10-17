#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER="$ROOT/mc"
LOGS="$ROOT/logs"
SESSION=mcv-minecraft
mkdir -p "$LOGS"
rm -f "$LOGS/.manual-stop"
screen -wipe >/dev/null 2>&1 || true
if screen -list 2>/dev/null | grep -q "[.]$SESSION[[:space:]]"; then
  echo "Minecraft rulează deja în sesiunea $SESSION."
  exit 0
fi
# Fără sesiune de control, dar dacă rămân procese Java orfane (ex. după crash:
# kill pe screen lasă java în spate; blochează următorul start) → le curățăm.
stale=$(pgrep -x java 2>/dev/null || true)
for pid in $stale; do
  if [ "$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)" = "$SERVER" ]; then
    echo "Găsit Java orfan $pid în $SERVER (cliopero: fără sesiune de control) → îl opresc."
    kill -TERM "$pid" 2>/dev/null || true
  fi
done
if [ -n "$stale" ]; then
  for _ in $(seq 1 15); do
    alive=0
    for pid in $stale; do kill -0 "$pid" 2>/dev/null && alive=1 && [ "$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)" = "$SERVER" ] && kill -KILL "$pid" 2>/dev/null || true; done
    [ "$alive" = 0 ] && break
    sleep 1
  done
fi
test -s "$SERVER/server.jar"
env -u RUNNER_TRACKING_ID screen -dmS "$SESSION" bash -lc \
  "cd '$SERVER' && exec java -Xms512M -Xmx2G -XX:+UseG1GC -jar server.jar --nogui >> '$LOGS/server-console.log' 2>&1"
echo "Minecraft a fost pornit în sesiunea $SESSION."
