#!/usr/bin/env bash
set -euo pipefail
RUNNER="$HOME/actions-runner"
LOG="$HOME/minecraft-server/logs/actions-runner.log"
if pgrep -f "$RUNNER/bin/Runner.Listener" >/dev/null 2>&1; then
  echo "Runner-ul GitHub rulează deja."
  exit 0
fi
test -x "$RUNNER/run.sh"
mkdir -p "$(dirname "$LOG")"
if [ -s "$LOG" ] && [ "$(stat -c %s "$LOG")" -gt 5242880 ]; then
  tail -n 2000 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
fi
(
  cd "$RUNNER"
  env -u RUNNER_TRACKING_ID nohup ./run.sh >> "$LOG" 2>&1 </dev/null &
)
echo "Runner-ul GitHub a fost pornit."
