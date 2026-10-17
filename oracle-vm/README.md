# Server pe Oracle VM (Micro 1GB / 1 OCPU, Ubuntu 22.04)

Setup complet: Paper 1.21.4 + MrCrayfishVehiclePlugin + panou web + deploy automat din GitHub. Totul reglat pentru 1 GB RAM.

## Budget de RAM (de ce arată așa)

| Componentă | RAM |
|---|---|
| OS + ssh + systemd (după purge snapd) | ~150 MB |
| Java/Paper (`-Xmx640M` + native) | ~750 MB |
| Panou web (python stdlib) | ~25 MB |
| **Swap (obligatoriu)** | 4 GB absorbind vârfurile |

Configurile din `config/` (view-distance 4, simulation-distance 3, fără spawn-chunks, firește de chunking 1 fir) sunt aplicate la prima instalare.

## 1. Deschide porturile în Oracle (OBLIGATORIU)

VM-ul are firewall și la nivel de rețea (Security List), nu doar pe el:

**OCI Console → Compute → Instances → instanța ta → Subnet (anime-subnet) → Default Security List → Add Ingress Rules**, de două ori:

| Source CIDR | Protocol | Port |
|---|---|---|
| `0.0.0.0/0` | TCP | `25565` |
| `0.0.0.0/0` | TCP | `8081` |

(SSH/22 e deja deschis implicit.)

## 2. Rulează setup-ul (o singură dată, ~10 min)

De pe PC-ul tău:

```bash
ssh ubuntu@<IP-PUBLIC-VM>
```

Apoi pe VM:

```bash
curl -fsSL https://raw.githubusercontent.com/ExMarius/MrCrayfishVehicleMod/arena/2f9f7b9e-mrcrayfishvehiclemod/oracle-vm/setup.sh -o setup.sh
sudo bash setup.sh
sudo bash setup.sh   # a doua oară chiar instalează
```

La final afișează adresele: **Minecraft** `IP:25565`, **Panou** `http://IP:8081` (admin / parola din `/etc/mcserver.env`, implicit `12345`).

## 3. Upload instant al pluginului la fiecare push (CI deploy)

Repo-ul are workflow-ul **Oracle VM deploy (SSH)**: orice modificare în `paper-plugin/**` → build pe GitHub → JAR-ul ajunge pe VM prin SSH → server repornit. Necesită 4 secrete (**GitHub → Settings → Secrets and variables → Actions**):

| Secret | Valoare |
|---|---|
| `ORACLE_VM_HOST` | IP-ul public al VM-ului |
| `ORACLE_VM_USER` | `ubuntu` |
| `ORACLE_VM_SSH_KEY` | conținutul cheii **private** (perechea celei publice puse în VM) |
| `ORACLE_VM_KNOWN_HOSTS` | output-ul de la `ssh-keyscan -t ed25519,rsa <IP-PUBLIC-VM>` (rulat pe PC) |

## Operațiuni zilnice

| Ce vrei | Cum |
|---|---|
| Pornire/oprire/consolă/loguri | panoul web `http://IP:8081` |
| Status servicii | `systemctl status minecraft mcvehicles-panel` |
| Log server live | `sudo tail -f /opt/mcserver/logs/server-console.log` |
| Restart server | `sudo systemctl restart minecraft` (sau din panou) |
| OP un jucător | consola panoului: `op NumeJucator` |
| Re-tunezi (RAM, parolă, versiune) | editezi `/etc/mcserver.env`, re-rulezi `sudo bash setup.sh`, `sudo systemctl restart minecraft mcvehicles-panel` |

## Note

- **Fără FRP**: VM-ul are IP public, tunelul nu mai e necesar.
- **Parola panoului** e slabă în mod deliberat („12345"); schimb-o din panou după login dacă expui adresa cuiva.
- **Reboot VM**: totul revine singur (systemd `enable`, swap în fstab, „Restart after maintenance" activat).
- Un **crash Java** nu repornește singur serverul (intenționat, ca să nu mascheze problemele); îl vezi oprit în panou și pornești cu un click.
