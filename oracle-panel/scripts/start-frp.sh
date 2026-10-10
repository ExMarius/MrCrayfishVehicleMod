#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT/frp/supervisor.pid"
SUPERVISOR="$ROOT/bin/frp-supervisor.sh"
mkdir -p "$ROOT/logs"
if [ -s "$PID_FILE" ]; then
  pid=$(cat "$PID_FILE" 2>/dev/null || true)
  if [ -n "$pid" ] && [ -d "/proc/$pid" ] && tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -Fq "$SUPERVISOR"; then
    echo "Supervisorul FRP rulează deja cu PID $pid."
    exit 0
  fi
  rm -f "$PID_FILE"
fi
env -u RUNNER_TRACKING_ID nohup "$SUPERVISOR" >> "$ROOT/logs/frp-supervisor.log" 2>&1 </dev/null &
echo $! > "$PID_FILE"
chmod 600 "$PID_FILE"
echo "Supervisorul FRP a pornit și va reconecta automat tunelul."
