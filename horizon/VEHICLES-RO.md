# Mașinile noastre pe Horizon — planul portului

Horizon nu are JVM, deci `paper-plugin/` (22.000 de linii Java) nu poate rula acolo.
Nu îl aruncăm: îl **portăm** în interiorul engine-ului, în Rust, folosind punctele de
extensie pe care Horizon le expune oficial.

## De ce e posibil

Horizon expune ~537 de „hook-uri" native, documentate public, iar cele de care avem nevoie
există deja:

| Ce ne trebuie | Hook Horizon | Observație |
|---|---|---|
| bucla de fizică | `native_server_tick` | la fiecare tick de server |
| tastele șoferului | `native_player_input` | pachetul `PlayerInput` (W/A/S/D/salt) |
| spawn mașină | `native_summon` | orice entitate, inclusiv `item_display` |
| mișcare | `native_move_entity`, `native_teleport_vehicle` | teleport cu pasageri |
| scaun | `native_mount`, `native_dismount` | API 472 |
| cine stă pe ce | `native_vehicle_of`, `native_passengers_of` | API 642 |
| modele pentru clienți vanilla | resource pack de la server | funcționează pe Horizon |

## Arhitectura mașinii (aceeași ca pe Paper)

1. **Schelet** — o entitate invizibilă care se mișcă; șoferul stă pe ea.
2. **Caroserie** — `item_display`-uri atașate, fiecare cu un model din resource pack
   (aceeași conversie OBJ → model de item ca la pluginul Paper).
3. **Fizică** — viteză, accelerație, frână, viraj din yaw, gravitație și contact cu solul,
   în `native_server_tick`. Modelul e cel din `paper-plugin/.../physics/`.
4. **Input** — în `native_player_input`: înainte/înapoi = accelerație/frână,
   stânga/dreapta = viraj, salt = frână de mână.
5. **Combustibil, garaj, mecanic, benzinării** — etapele următoare, preluate din plugin.

## Etape

| Etapă | Ce livrează | Stare |
|---|---|---|
| **M0** | engine propriu compilat din sursă, cu modul lipit; log de tick + input în consola panelului | în lucru |
| M1 | o mașină: `/car` o pune jos, te sui, mergi cu W/S/A/D, cobori cu sneak | |
| M2 | caroserie vizibilă (display-uri + resource pack cu modelele modului) | |
| M3 | combustibil, vitezometru, sunet de motor | |
| M4 | garaj, benzinării, mecanic, economie (restul din plugin) | |

## Cum se construiește

- Modul: `horizon/engine-mod/vehicle.rs`
- Lipirea în sursă: `horizon/engine-mod/apply.py` (eșuează zgomotos dacă Horizon își schimbă hook-urile)
- Build + upload: `.github/workflows/horizon-engine-mod.yml` (ARM64, reviziune Horizon fixă)
- Lansatorul folosește engine-ul propriu **doar** dacă binarul răspunde la `--version`;
  altfel rămâne pe engine-ul normal, deci serverul nu poate fi lăsat jos de un build prost.

## Siguranță

- Revenire la engine-ul normal: se șterge `horizon/horizon-engine-mod` (lansatorul curăță
  `engine.path` singur).
- Engine-ul nostru e compilat din sursa Horizon + modul nostru; nimic din Mojang nu e
  redistribuit.
