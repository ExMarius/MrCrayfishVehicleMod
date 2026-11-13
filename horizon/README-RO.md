# Horizon Server — ghid scurt (română)

> **Ce e, de fapt:** `horizon-rust/horizon-server` **nu conține cod** — e doar locul de unde se distribuie
> serverul. Codul e în [horizon-rust/horizon](https://github.com/horizon-rust/horizon): un server de
> **Minecraft Java 26.3 scris de la zero în Rust** (fără Java). Build-urile gata făcute sunt în
> **Releases → `nightly`**.
>
> ⚠️ Nu e NeoForge/Paper. **Modurile Java (inclusiv modul nostru AIBridge) nu merg aici.**
> Modurile pentru Horizon sunt „native mods", alt limbaj, alt ecosistem.

## Ce-ți trebuie înainte de orice

1. **Minecraft Java Edition 26.3** cumpărat și **jucat o dată** din launcherul oficial pe același PC
   (serverul nu descarcă nimic de la Mojang; își ia datele din copia ta).
2. **Java 25** instalat.
3. Fișierul **`server.jar` de 26.3** — îl găsești ca `downloads.server.url` în
   `versions/26.3/26.3.json` din folderul launcherului.
4. Un loc unde rulează: **PC-ul tău** (cel mai simplu) sau un server **Linux x86-64**.

> Dacă nu ai Minecraft 26.3, proiectul nu poate porni deloc — nu e ceva ce se rezolvă din setări.

## Varianta 1 — pe PC (Windows)

În PowerShell (fără administrator):

```powershell
irm https://raw.githubusercontent.com/horizon-rust/horizon/main/packaging/server/install.ps1 | iex
```

Instalatorul ia ultimul Nightly, verifică SHA-256, pune `horizon-server` și `horizon-prepare` în PATH
și creează folderul `game-files`.

Apoi, pregătești datele jocului (o singură dată):

```powershell
horizon-prepare --prepare --server-jar "C:\cale\catre\26.3\server.jar"
```

Și pornești serverul:

```powershell
mkdir C:\horizon-server
cd C:\horizon-server
horizon-server init
horizon-server run
```

`init` te întreabă 5 lucruri (EULA, nume lume + seed, port, whitelist, primul operator).
Varianta fără întrebări:

```powershell
horizon-server init --eula --world survival --port 25565 --whitelist --player NumeleTau --op NumeleTau --yes
```

Te conectezi în joc la `localhost:25565` (sau `IP-ul-PC-ului:25565` de pe alt PC din rețea).
**Clientul vanilla 26.3 se poate conecta** — nu trebuie launcherul Horizon.

## Varianta 2 — pe panel (Pterodactyl/HidenCloud)

Există egg oficial: `packaging/server/pterodactyl-egg.json` din repo (Docker `ghcr.io/ptero-eggs/yolks:debian`).

Pași: imporți egg-ul în panel → creezi serverul → pui `EULA=true` → urci conținutul folderului `26.3`
pregătit pe PC în `resources/` → pornești.

**Atenție la arhitectură:** build-urile Linux publicate sunt **x86-64**. Serverul nostru HidenCloud rulează pe
**ARM64** (`aarch64`), deci varianta asta **nu va merge acolo** — ar trebui construit din sursă pe ARM
(`cargo build --release -p horizon-client --bin horizon-client -p horizon-server`), ceea ce înseamnă Rust +
multă memorie pe server.

## Varianta 3 — VPS Linux x86-64 sau Docker

```sh
curl -fsSL https://raw.githubusercontent.com/horizon-rust/horizon/main/packaging/server/install.sh | sh
horizon-prepare --prepare --server-jar /path/to/server.jar   # pe PC-ul tău, apoi copiază folderul 26.3
horizon-server import-resources 26.3                          # pe server
horizon-server init && horizon-server run
```

## Ce primești

- consolă în terminal, `server.toml` (un singur fișier de setări), backup-uri automate,
  restart la crash, panel web local pe `127.0.0.1:8130`, API de control local;
- whitelist, op-uri, comenzi vanilla (`/tp`, `/gamemode`, `execute in ... run tp`), Nether/End per jucător;
- TPS/MSPT în `horizon-server status`.

## Când ceva nu merge

| Mesaj | Cauza |
|---|---|
| `no prepared game files` | nu ai rulat `horizon-prepare` sau `[engine] resources` e greșit |
| `registries.json not found next to the resources` | folderul pregătit e din release vechi — pregătește din nou |
| nimeni nu poate intra | whitelist activ (`horizon-server whitelist add NUME`) sau port închis în firewall |
| repornește în buclă | `horizon-server status` + `horizon-server logs -n 200`, crash-uri în `crash-reports/` |
