# Deploy pipeline

- 2026-10-17 — VM 140.238.221.52 provisioned automat din Actions (run 39060859421):
  Instalare Java 21 + swap 4G + Paper 1.21.4 + plugin + pack + panou (admin/12345),
  servicii active, lume generată. Acest commit verifică bucla completă:
  push → build JAR pe GitHub → SCP pe VM → `systemctl restart minecraft`.
