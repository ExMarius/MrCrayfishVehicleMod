#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# The user's preserved configuration is YAML. FRPC runs in Cloud Shell and
# connects outbound to a separate, publicly reachable FRPS endpoint.
CONFIG="$ROOT/frp/frpc.yaml"
[ -s "$CONFIG" ] || CONFIG="$ROOT/frp/frpc.toml"
test -x "$ROOT/frp/frpc"
test -s "$CONFIG"
LOG="$ROOT/logs/frpc.log"
mkdir -p "$ROOT/logs"
child=""
cleanup() {
  [ -z "$child" ] || kill -TERM "$child" 2>/dev/null || true
  wait "$child" 2>/dev/null || true
  exit 0
}
trap cleanup TERM INT
while true; do
  if [ -s "$LOG" ] && [ "$(stat -c %s "$LOG")" -gt 5242880 ]; then
    tail -n 2000 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
  fi
  "$ROOT/frp/frpc" -c "$CONFIG" >> "$LOG" 2>&1 &
  child=$!
  wait "$child" || true
  child=""
  sleep 15 &
  wait $! || true
done
