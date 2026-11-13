# Horizon Server pe panelul HidenCloud — cum e făcut și cum îl administrezi

Server de **Minecraft 26.3 scris în Rust** (proiectul `horizon-rust/horizon`), rulat pe un panel
Pterodactyl **ARM64** unde oficial nu există build. Aici e tot ce am făcut ca să meargă.

## Pe scurt

- Panelul rulează `java -jar neoforge-21.1.172-installer.jar` (comandă fixă, needitabilă).
- Acel jar **nu mai e NeoForge**: e `horizon-launch.jar`, un lansator de 4 KB scris de noi.
- Lansatorul: despachetează binarul → importă datele jocului → scrie `server.toml` cu portul
  panelului → pornește `horizon-server` → copiază tot output-ul în `horizon/console.log` și
  trimite comenzile din consola panelului în server.

## Fișiere pe server

| Cale | Ce e |
|---|---|
| `neoforge-21.1.172-installer.jar` | lansatorul Java (4 KB) |
| `horizon/horizon-server` | supervisorul (Rust, aarch64) |
| `horizon/horizon-engine` | motorul de joc (fără sunet/controller) |
| `horizon/server.toml` → | de fapt `server.toml` în rădăcină |
| `resources/` | datele jocului 26.3 (226 MB, importate) |
| `horizon/console.log` | tot ce scoate serverul |
| `world/`, `.horizon/`, `logs/` | lumea și starea internă |

## Pipeline-uri (GitHub Actions)

| Workflow | Rol |
|---|---|
| `horizon-arm64.yml` | compilează motorul + supervisorul pentru aarch64 și le publică în release |
| `horizon-launcher.yml` | compilează `horizon-launch.jar` |
| `horizon-resources.yml` | descarcă server.jar oficial 26.3, reconstruiește o instalare minimă, rulează `horizon-prepare`, urcă `26.3.tar.gz` |
| `horizon-deploy.yml` | urcă binarul + lansatorul + `eula.txt` + `ops.json` pe panel prin SFTP |
| `ai-bridge-diag.yml` | aduce în repo consola Horizon, `server.toml`, liste de fișiere |
| `panel-verify.yml` / `panel-probe.yml` / `panel-power.yml` | API-ul panelului (necesită token temporar, șters imediat după) |

Toate se declanșează prin push pe fișierul lor `.github/*.trigger` (workflow-urile nu sunt pe
branch-ul default, deci `workflow_dispatch` nu e disponibil).

## Setări

Se editează `server.toml` (panel → File Manager). `server.properties` e generat din el la fiecare
start — nu-l edita. Restart din panel după modificări.

```toml
[server]
port = 24601
online_mode = true
whitelist = false
view_distance = 10
[world]
game_mode = "survival"
difficulty = "easy"
```

## Dacă vrei înapoi NeoForge

Nu mai există backup pe server (a fost șters la wipe). Se face reinstall din panel, apoi se
re-urcă `ai-characters/bridge-mod` compilat.

## Note

- Consola panelului funcționează doar după un restart (lansatorul nou redirectează stdin/stdout).
- `ExMarius` e trecut în `ops.json` (operator nivel 4).
- Build-urile sunt în release-ul `horizon-arm64` din acest repo.
