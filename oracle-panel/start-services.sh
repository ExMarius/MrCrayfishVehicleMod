#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$ROOT/bin/start-runner.sh"
"$ROOT/bin/start-panel.sh"
"$ROOT/bin/start-frp.sh"
echo
"$ROOT/bin/status.sh"
echo
echo "Serviciile de control sunt pornite. Minecraft rămâne oprit până primește Start din panel sau GitHub."
