#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT/panel/panel.pid"
[ -s "$PID_FILE" ] || { echo "Panelul este deja oprit."; exit 0; }
pid=$(cat "$PID_FILE" 2>/dev/null || true)
if [ -n "$pid" ] && [ -d "/proc/$pid" ]; then
  kill -TERM "$pid" 2>/dev/null || true
  for _ in $(seq 1 15); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  kill -KILL "$pid" 2>/dev/null || true
fi
rm -f "$PID_FILE"
echo "Panelul a fost oprit."
