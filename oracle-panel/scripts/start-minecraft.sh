#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER="$ROOT/mc"
LOGS="$ROOT/logs"
SESSION=mcv-minecraft
mkdir -p "$LOGS"
screen -wipe >/dev/null 2>&1 || true
if screen -list 2>/dev/null | grep -q "[.]$SESSION[[:space:]]"; then
  echo "Minecraft rulează deja în sesiunea $SESSION."
  exit 0
fi
while IFS= read -r pid; do
  [ -n "$pid" ] || continue
  if [ "$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)" = "$SERVER" ]; then
    echo "Refuz pornirea unei a doua instanțe: Java $pid rulează deja în $SERVER." >&2
    exit 1
  fi
done < <(pgrep -x java 2>/dev/null || true)
test -s "$SERVER/server.jar"
env -u RUNNER_TRACKING_ID screen -dmS "$SESSION" bash -lc \
  "cd '$SERVER' && exec java -Xms512M -Xmx2G -XX:+UseG1GC -jar server.jar --nogui >> '$LOGS/server-console.log' 2>&1"
echo "Minecraft a fost pornit în sesiunea $SESSION."
