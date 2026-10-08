#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PANEL="$ROOT/panel/panel.py"
PID_FILE="$ROOT/panel/panel.pid"
LOG="$ROOT/logs/panel.log"
mkdir -p "$ROOT/panel" "$ROOT/logs"
if [ -s "$PID_FILE" ]; then
  pid=$(cat "$PID_FILE" 2>/dev/null || true)
  if [ -n "$pid" ] && [ -d "/proc/$pid" ] && tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -Fq "$PANEL"; then
    echo "Panelul rulează deja cu PID $pid."
    exit 0
  fi
  rm -f "$PID_FILE"
fi
python3 "$PANEL" --root "$ROOT" --init-auth
if [ -s "$LOG" ] && [ "$(stat -c %s "$LOG")" -gt 5242880 ]; then
  tail -n 2000 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
fi
env -u RUNNER_TRACKING_ID nohup python3 "$PANEL" --root "$ROOT" --host 0.0.0.0 --port 8081 \
  >> "$LOG" 2>&1 </dev/null &
pid=$!
echo "$pid" > "$PID_FILE"
chmod 600 "$PID_FILE"
for _ in $(seq 1 30); do
  if curl -fsS --connect-timeout 1 http://127.0.0.1:8081/healthz >/dev/null; then
    echo "Panelul a pornit pe portul 8081."
    exit 0
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    tail -40 "$LOG" >&2 || true
    echo "Procesul panelului s-a oprit în timpul pornirii." >&2
    exit 1
  fi
  sleep 1
done
echo "Panelul nu a răspuns în 30 de secunde." >&2
exit 1
