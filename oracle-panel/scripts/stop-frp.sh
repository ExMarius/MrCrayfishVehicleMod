#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT/frp/supervisor.pid"
if [ -s "$PID_FILE" ]; then
  pid=$(cat "$PID_FILE" 2>/dev/null || true)
  [ -z "$pid" ] || kill -TERM "$pid" 2>/dev/null || true
fi
for _ in $(seq 1 15); do
  pgrep -x frpc >/dev/null 2>&1 || break
  sleep 1
done
pkill -TERM -x frpc 2>/dev/null || true
rm -f "$PID_FILE"
echo "Clientul FRP a fost oprit."
