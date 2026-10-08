#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_CONFIG="$ROOT/frp/frpc.toml"
[ -s "$CLIENT_CONFIG" ] || CLIENT_CONFIG="$ROOT/frp/frpc.yaml"
SERVER_CONFIG="$ROOT/frp/frps.toml"
[ -s "$SERVER_CONFIG" ] || SERVER_CONFIG="$ROOT/frp/frps.yaml"
test -x "$ROOT/frp/frpc"
test -s "$CLIENT_CONFIG"
CLIENT_LOG="$ROOT/logs/frpc.log"
SERVER_LOG="$ROOT/logs/frps.log"
mkdir -p "$ROOT/logs"
client_child=""
server_child=""

cleanup() {
  [ -z "$client_child" ] || kill -TERM "$client_child" 2>/dev/null || true
  [ -z "$server_child" ] || kill -TERM "$server_child" 2>/dev/null || true
  [ -z "$client_child" ] || wait "$client_child" 2>/dev/null || true
  [ -z "$server_child" ] || wait "$server_child" 2>/dev/null || true
  exit 0
}
trap cleanup TERM INT

rotate_log() {
  local log="$1"
  if [ -s "$log" ] && [ "$(stat -c %s "$log")" -gt 5242880 ]; then
    tail -n 2000 "$log" > "$log.tmp" && mv "$log.tmp" "$log"
  fi
}

start_local_server() {
  # The original Cloud Shell setup includes its own FRPS endpoint. It must run
  # before FRPC, otherwise every public proxy (panel, SSH and Minecraft) is refused.
  if [ ! -x "$ROOT/frp/frps" ] || [ ! -s "$SERVER_CONFIG" ]; then
    return 0
  fi
  if pgrep -x frps >/dev/null 2>&1; then
    return 0
  fi
  rotate_log "$SERVER_LOG"
  "$ROOT/frp/frps" -c "$SERVER_CONFIG" >> "$SERVER_LOG" 2>&1 &
  server_child=$!
  # Give the listener a moment to bind before FRPC connects to it.
  sleep 2
  if ! kill -0 "$server_child" 2>/dev/null; then
    wait "$server_child" 2>/dev/null || true
    server_child=""
  fi
}

while true; do
  start_local_server
  rotate_log "$CLIENT_LOG"
  "$ROOT/frp/frpc" -c "$CLIENT_CONFIG" >> "$CLIENT_LOG" 2>&1 &
  client_child=$!
  wait "$client_child" || true
  client_child=""
  if [ -n "$server_child" ] && ! kill -0 "$server_child" 2>/dev/null; then
    wait "$server_child" 2>/dev/null || true
    server_child=""
  fi
  sleep 15 &
  wait $! || true
done
