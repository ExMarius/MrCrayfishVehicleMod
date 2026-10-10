#!/usr/bin/env bash
set -euo pipefail
SESSION=mcv-minecraft
sessions() {
  screen -list 2>/dev/null | awk '$1 ~ /^[0-9]+[.]mcv-minecraft$/ {print $1}'
}
session=$(sessions | head -1 || true)
if [ -z "$session" ]; then
  screen -wipe >/dev/null 2>&1 || true
  echo "Minecraft este deja oprit."
  exit 0
fi
# Ask Paper to save and exit cleanly first.
screen -S "$session" -p 0 -X stuff $'save-all flush\r' || true
sleep 3
screen -S "$session" -p 0 -X stuff $'stop\r' || true
for _ in $(seq 1 60); do
  [ -z "$(sessions)" ] && { echo "Minecraft s-a oprit curat."; exit 0; }
  sleep 2
done
# Only the named screen process is terminated. Never kill arbitrary Java processes.
echo "Oprirea normală a expirat; închid sesiunea dedicată $session." >&2
screen -S "$session" -X quit || true
screen_pid=${session%%.*}
kill -TERM "$screen_pid" 2>/dev/null || true
for _ in $(seq 1 10); do
  [ -z "$(sessions)" ] && break
  sleep 1
done
if [ -n "$(sessions)" ]; then
  kill -KILL "$screen_pid" 2>/dev/null || true
fi
screen -wipe >/dev/null 2>&1 || true
if [ -n "$(sessions)" ]; then
  echo "Sesiunea Minecraft nu a putut fi închisă." >&2
  exit 1
fi
echo "Minecraft a fost oprit prin sesiunea dedicată."
