#!/usr/bin/env bash
# Clean-room integration test for a built MrCrayfishVehiclePlugin JAR.
# Downloads the latest stable Paper 1.21.4 build, starts it twice in a brand-new
# directory, exercises console commands, and audits first-install persistence.
set -euo pipefail

if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
  echo "Usage: $0 <MrCrayfishVehiclePlugin.jar>" >&2
  exit 2
fi

PLUGIN_JAR=$(realpath "$1")
SERVER_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mcv-fresh-paper.XXXXXX")
SERVER_LOG="$SERVER_DIR/server.log"
PAPER_API="https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds"
USER_AGENT="ExMarius-MrCrayfishVehicleMod/clean-room-test (https://github.com/ExMarius/MrCrayfishVehicleMod)"
CURRENT_PID=""
CONSOLE_OPEN=false

cleanup() {
  if [ -n "$CURRENT_PID" ] && kill -0 "$CURRENT_PID" 2>/dev/null; then
    kill "$CURRENT_PID" 2>/dev/null || true
    wait "$CURRENT_PID" 2>/dev/null || true
  fi
  if $CONSOLE_OPEN; then
    exec 3>&- || true
  fi
  rm -rf "$SERVER_DIR"
}
trap cleanup EXIT

fail() {
  echo "Fresh Paper integration test failed: $*" >&2
  if [ -f "$SERVER_LOG" ]; then
    echo "----- server log -----" >&2
    tail -n 300 "$SERVER_LOG" >&2
    echo "----------------------" >&2
  fi
  exit 1
}

mkdir -p "$SERVER_DIR/plugins"
test ! -e "$SERVER_DIR/plugins/MrCrayfishVehiclePlugin" \
  || fail "temporary server unexpectedly contains plugin data"
cp "$PLUGIN_JAR" "$SERVER_DIR/plugins/MrCrayfishVehiclePlugin.jar"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<'EOF'
online-mode=false
server-ip=127.0.0.1
server-port=25568
spawn-protection=0
view-distance=2
simulation-distance=2
sync-chunk-writes=true
EOF

BUILDS_JSON=$(curl --fail --silent --show-error --location \
  --header "User-Agent: $USER_AGENT" "$PAPER_API")
readarray -t PAPER_DOWNLOAD < <(python3 -c '
import json, sys
builds = json.load(sys.stdin)
stable = next((build for build in builds if build.get("channel") == "STABLE"), None)
if stable is None:
    raise SystemExit("Paper API returned no stable 1.21.4 build")
download = stable["downloads"]["server:default"]
print(download["url"])
print(download["checksums"]["sha256"])
print(stable["id"])
' <<< "$BUILDS_JSON")
PAPER_URL=${PAPER_DOWNLOAD[0]:-}
PAPER_SHA256=${PAPER_DOWNLOAD[1]:-}
PAPER_BUILD=${PAPER_DOWNLOAD[2]:-}
[ -n "$PAPER_URL" ] && [ -n "$PAPER_SHA256" ] && [ -n "$PAPER_BUILD" ] \
  || fail "could not resolve the latest stable Paper 1.21.4 download"

curl --fail --silent --show-error --location \
  --header "User-Agent: $USER_AGENT" --output "$SERVER_DIR/paper.jar" "$PAPER_URL"
echo "$PAPER_SHA256  $SERVER_DIR/paper.jar" | sha256sum --check --status \
  || fail "Paper build $PAPER_BUILD checksum mismatch"

echo "Using Paper 1.21.4 build $PAPER_BUILD in clean directory $SERVER_DIR"

run_server_cycle() {
  local cycle=$1
  local ready=false
  local fifo="$SERVER_DIR/console-$cycle.fifo"

  mkfifo "$fifo"
  exec 3<>"$fifo"
  CONSOLE_OPEN=true
  (
    cd "$SERVER_DIR"
    exec java -Xms512M -Xmx1G -jar paper.jar --nogui < "$fifo" >> "$SERVER_LOG" 2>&1
  ) &
  CURRENT_PID=$!

  for _ in $(seq 1 240); do
    if ! kill -0 "$CURRENT_PID" 2>/dev/null; then
      break
    fi
    if grep -Fq 'Done (' "$SERVER_LOG" \
        && [ "$(grep -Fc 'Vehicle plugin enabled. Seventeen land vehicles, three water vehicles, three aircraft, and five trailers are ready.' "$SERVER_LOG")" -ge "$cycle" ]; then
      ready=true
      break
    fi
    sleep 1
  done
  $ready || fail "cycle $cycle did not become ready within 240 seconds"

  printf 'vehicle list\nvehicle save\nvehicle pack\nplugins\n' >&3
  sleep 3
  printf 'stop\n' >&3

  local stopped=false
  for _ in $(seq 1 90); do
    if ! kill -0 "$CURRENT_PID" 2>/dev/null; then
      stopped=true
      break
    fi
    sleep 1
  done
  $stopped || fail "cycle $cycle did not stop cleanly within 90 seconds"
  wait "$CURRENT_PID" || fail "cycle $cycle exited unsuccessfully"
  CURRENT_PID=""
  exec 3>&-
  CONSOLE_OPEN=false
  rm -f "$fifo"
}

# First boot proves installation without prior config, resource-pack state, data,
# worlds, or vehicle YAML. The second boot proves generated state reloads.
run_server_cycle 1

DATA_DIR="$SERVER_DIR/plugins/MrCrayfishVehiclePlugin"
test -s "$DATA_DIR/config.yml" || fail "default config.yml was not generated"
test -f "$DATA_DIR/vehicles.yml" || fail "vehicles.yml was not created on shutdown"
test -f "$DATA_DIR/trailers.yml" || fail "trailers.yml was not created on shutdown"
grep -Fq 'MrCrayfishVehiclePlugin-resource-pack-1.21.4-r35.zip' "$DATA_DIR/config.yml" \
  || fail "generated config does not select resource pack r35"
grep -Fq 'e0a532b50344e2d6020e335c7baa18bd466280ec' "$DATA_DIR/config.yml" \
  || fail "generated config does not contain the verified r35 SHA-1"

run_server_cycle 2

[ "$(grep -Fc 'Vehicle plugin enabled. Seventeen land vehicles, three water vehicles, three aircraft, and five trailers are ready.' "$SERVER_LOG")" -eq 2 ] \
  || fail "plugin did not enable exactly once per startup"
[ "$(grep -Fc 'Loaded 0 vehicle(s); 0 deferred for unavailable worlds/types.' "$SERVER_LOG")" -eq 2 ] \
  || fail "empty vehicle persistence did not load on both starts"
[ "$(grep -Fc 'Loaded 0 trailer(s).' "$SERVER_LOG")" -eq 2 ] \
  || fail "empty trailer persistence did not load on both starts"
[ "$(grep -Fc 'Stopping server' "$SERVER_LOG")" -eq 2 ] \
  || fail "server did not receive both clean stop commands"

if grep -Eiq '(\[(ERROR|SEVERE)\]|Exception|Caused by:|Could not load|Could not enable|Failed to load)' "$SERVER_LOG"; then
  fail "server log contains an error or exception"
fi

echo "Fresh Paper 1.21.4 integration test passed: clean install, commands, persistence, shutdown, and restart."
