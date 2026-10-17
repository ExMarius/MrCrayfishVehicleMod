#!/usr/bin/env bash
# Repornește minecraft DOAR dacă:
#  - unitul systemd e complet activ (nu boot/în oprire în curs),
#  - au trecut ≥5 minute de la activare (boot-ul durează ~2:30),
#  - NU s-a oprit intenționat din panou (markerul .manual-stop nu există),
#  - listenerul 25565 a dispărut (java a murit în screen, ex. crash/OOM).
set -u
systemctl is-active --quiet minecraft.service || FAILED_STATE=$(systemctl show -p ActiveState --value minecraft.service)
STATE=$(systemctl show -p ActiveState --value minecraft.service)
# Acoperim și starea "failed" (restart eșuat repetat) — start scriptul curăță
# și javaughele orfane, deci batem la sigur.
case "$STATE" in active|failed) ;; *) exit 0;; esac
[ ! -e /opt/mcserver/logs/.manual-stop ] || exit 0
A=$(systemctl show minecraft.service -p ActiveEnterTimestampMonotonic --value)
B=$(cut -d. -f1 /proc/uptime)
AGE=$(( B - A/1000000 ))
[ "$AGE" -ge 300 ] || exit 0
ss -tlnH "sport = :25565" 2>/dev/null | grep -q . && exit 0
systemctl restart minecraft.service
echo "mc-watchdog: listenerul 25565 lipsea; minecraft repornit (age=${AGE}s)"
