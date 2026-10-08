#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER="$ROOT/mc"
if screen -list 2>/dev/null | grep -q '[.]mcv-minecraft[[:space:]]'; then
  echo "Minecraft: running"
else
  echo "Minecraft: stopped"
fi
java_pid=""
while IFS= read -r pid; do
  [ -n "$pid" ] || continue
  if [ "$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)" = "$SERVER" ]; then
    java_pid="$pid"
    break
  fi
done < <(pgrep -x java 2>/dev/null || true)
echo "Java: ${java_pid:-stopped}"
echo "Panel: $(pgrep -f "$ROOT/panel/panel.py" >/dev/null 2>&1 && echo running || echo stopped)"
echo "FRP: $(pgrep -x frpc >/dev/null 2>&1 && echo connected || echo reconnecting)"
echo "FRP supervisor: $(pgrep -f "$ROOT/bin/frp-supervisor.sh" >/dev/null 2>&1 && echo running || echo stopped)"
echo "Runner: $(pgrep -f 'Runner.Listener' >/dev/null 2>&1 && echo running || echo stopped)"
