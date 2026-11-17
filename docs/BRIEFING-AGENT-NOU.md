# Briefing pentru agentul nou — proiectul de mașini (Paper)

Citește tot o dată, apoi lucrează. Aici e tot ce s-a decis și s-a verificat până acum, ca să nu
repetăm discuțiile.

---

## 1. Ce se lucrează acum

Sunt **două proiecte separate**. Unul e închis, celălalt e activ.

| Proiect | Stare | Unde |
|---|---|---|
| **Port Horizon** (Rust, mod nativ) | **închis** — nu se mai dezvoltă | `horizon/` în acest repo |
| **Plugin Paper** (mașini pentru server Bukkit/Paper) | **activ** | `paper-plugin/` în acest repo |

Codul pluginului e al nostru (81 fișiere Java, din care 65 principale + 16 teste). Modelele și
sunetele vin din modul MrCrayfish (GPLv3) — vezi secțiunea 6.

---

## 2. Reguli de lucru (cerute explicit de utilizator)

1. **Română** cu utilizatorul; cod, comentarii și documente tehnice pot fi în română.
2. **Nimic manual pe PC-ul lui dacă se poate evita.** Orice pas care rămâne la el trebuie să fie
   numerotat, cu link direct și cu o singură acțiune clară („copiază fișierul", „apasă Build").
   A respins instrucțiuni PowerShell și pași în mai multe locuri.
3. **Autonomie**: „fă ce crezi că trebuie" — nu cere confirmări pentru pași tehnici.
4. **Atenție la buget**: rulările lungi (build-uri) se fac o dată, nu în buclă; nu porni build-uri
   de 25 de minute ca să testezi o virgulă.
5. **Verifică înainte să afirmi.** Nu spune „e gata" fără un log, un hash sau un fișier care o
   dovedește. Dacă nu ai putut verifica, spune exact ce n-ai putut.
6. **Proiectele rămân separate**: mașini ≠ personaje AI ≠ Horizon. Nu amesteca fișierele.
7. **Secrete**: decizia actuală a utilizatorului e să **nu folosim GitHub Secrets**; cheia SSH e
   în repo (`hidencloud/keys/arena_hc_ed25519`) și workflow-urile o folosesc direct. Consecința,
   spusă o dată: repo-ul e public, deci cheia e publică; dacă cineva intră pe server, prima
   măsură e rotirea cheii din panel. Nu pune tokenuri în chat.
8. **Nu promite ce nu e făcut.** Pluginul de față e funcțional pe serverul lui, dar orice
   modificare trebuie testată.

---

## 3. Cum e făcut pluginul (arhitectura reală)

`paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/`

| Pachet | Fișiere | Ce face |
|---|---|---|
| `vehicle` | 10 | `LandVehicle` (ticks, condus, combustibil, zbor, apă), `LandVehicleSpec` (**specificațiile celor 23 de vehicule**), `VehicleManager`, `VehicleInput`, `DriveTuning`, `FuelGauge`, `HelicopterPhysics`, `WaterVehiclePhysics`, `LawnMowerBehavior`, `TrailerSpec` |
| `render` | 3 | `LandVehicleRig` / `TrailerRig` — fiecare vehicul e un grup de **`ItemDisplay`** (body, engine, steering, towBar, chest); `SourceTransforms` ține transformările din mod |
| `physics` | 2 | `SurfaceProfile` (frecare pe tip de bloc), `VehicleCollisionMover` |
| `runtime` | 4 | `EngineSoundController`, `TrailerManager`, `PaperTrailer`, `PerfTracker` |
| `persistence` | 5 | salvarea vehiculelor, remorcilor, benzinăriilor, mecanicilor |
| `garage` | 1 | `GarageStationManager` |
| `gaspump` | 6 | benzinărie: pompă, duză, furtun, curbe |
| `jerrycan` | 1 | canistra |
| `mechanic` | 14 | mecanicul (reparații, pneuri, motor) |
| `shop` | 5 | magazin + podium dealer |
| `listener` | 8 | evenimente Bukkit (click, chunk unload, XP speedometer…) |
| `command` | 2 | `/vehicle`, `/garaj` |
| `world`, `restart` | 2 | lume și repornire controlată |

### Cum funcționează un vehicul, cap-coadă

1. **Definiție**: `LandVehicleSpec` — nume, model de corp, dimensiuni, offset-uri, putere motor,
   viteză, frână, roți, scaune, tow bar, sunet de motor. 23 de vehicule + 5 remorci.
2. **Apariție**: `LandVehicleRig` creează `ItemDisplay`-uri cu `item_model` care trimite la
   modelele din resource pack; transformările vin din `SourceTransforms` (corecția de 180° e
   documentată în `LandVehicleRig`: un `ItemDisplay` desenează +Z invers față de entitate).
3. **Condus**: `LandVehicle.tick()` — input de la șofer (`VehicleInput`), fizică terestră în
   `tick` + `DriveTuning`, apă în `tickWater`, elicopter în `tickHelicopter`, avion în `tickPlane`.
   Mișcarea e aplicată pe entitatea care ține locul vehiculului, iar piesele o urmează.
4. **Consum**: `FuelGauge` + motorul din portbagaj (itemele `*_engine`); fără motor nu merge.
5. **Persistență**: `persistence` salvează poziția, combustibilul, culoarea, piesele.
6. **Resource pack**: `ResourcePackSender` trimite pack-ul construit de `tools/build_resource_pack.py`.

### Ce e deja funcțional (nu rescrie)

condus terestru/apă/zbor, roți care se învârtesc, volan, benzinărie cu furtun, canistră, garaj,
magazin, mecanic, remorci, sunet de motor, speedometru XP.

---

## 4. Build și livrare

- **Build**: GitHub Actions, nu local (mediul de lucru nu are Java). Workflow
  `.github/workflows/paper-plugin.yml`; `api-version: '1.21.4'` în `plugin.yml`, Java 21.
- **Server**: panel HidenCloud, server id `d496911d`, nod `jacob`, adresă `jacob.hidencloud.com:24601`,
  SFTP `jacob.hidencloud.com:2022`, user `mariusuke-184939.d496911d`, cheie în
  `hidencloud/keys/arena_hc_ed25519`.
- **Detalii de acces**: `docs/ACCES-SERVER-RO.md`.
- **Urcare fișiere**: prin SFTP din Actions. Atenție: `put` **nu suprascrie** fișierul existent —
  întâi `-rm`, apoi `put`, apoi verifică mărimea (a costat o zi întreagă în proiectul Horizon).
- **Loguri**: nu există consolă live; se descarcă `console.log` prin SFTP
  (workflow `panel-log.yml` → `horizon/_console.md`).
- **Serverul actual rulează Horizon** (Rust). Pentru Paper trebuie ales PaperMC 1.21.4 în
  instalatorul din panel — ceea ce **înlocuiește jar-ul** și termină Horizon definitiv.
  Alternativa curată: server nou.

---

## 5. Decizii luate (nu redeschide)

1. **Plugin Paper, nu mod** — Horizon a fost abandonat după ce s-a dovedit că modul nativ cere
   client Horizon la fiecare jucător.
2. **Varianta A**: plugin nou, cu modelele modului, **declarat GPLv3**, cu sursă, licență și credit.
   (Varianta B = vehicule din blocuri vanilla; C = modele desenate de la zero. S-au respins.)
3. **Licența**: modul e **GPLv3** (`MOD-LICENSE.txt`). Reutilizarea e permisă cu condiția ca
   derivatul să fie tot GPLv3, cu sursă publică, licență inclusă și MrCrayfish creditat.
4. **„All Rights Reserved"**: 59 de fișiere de model conțin nota în metadate, deși repo-ul e GPLv3.
   Nu e o licență, dar contradicția e reală → **prima versiune merge cu cele 14 vehicule fără notă**
   (jet_ski, lawn_mower, golf_cart, off_roader, tractor, trailer + dune_buggy, atv, bumper_car,
   shopping_cart, mini_bike, smart_car, speed_boat, aluminum_boat). Cele 8 cu nota ARR
   (go_kart, sports_car, quad_bike, sports_plane, helicopter, moped, mini_bus, dirt_bike) se
   adaugă după clarificare.
5. **Autobuzul FoundationGames (MIT)** nu există în acest repo; dacă se folosește, nota MIT se
   păstrează separat în `THIRD-PARTY.md`.
6. **Lista de fișiere** pentru varianta A: `docs/LISTA-FISIERE-VARIANTE-A.md`.

---

## 6. Ce se copiază în pluginul nou și ce nu

- **Se copiază**: `paper-plugin/` (cod + teste + build), `tools/*.py`, `tools/source_assets/`,
  modelele/texturile/sunetele/textele din `src/main/resources/assets/vehicle/`, plus
  `MOD-LICENSE.txt` (ca `LICENSE`), creditele Forge și licențele Paulscode.
- **Nu se copiază**: `horizon/` (proiect închis), `client-mod/`, `ai-characters/`, `demo/`.
- **Obligatoriu în pluginul nou**: `LICENSE` = GPLv3, `THIRD-PARTY.md` cu modul (GPLv3),
  Furniture Mod (GPL), eventual FoundationGames (MIT) și lista modelelor cu nota ARR.

---

## 7. Capcane deja plătite (nu le repeta)

1. `put` prin SFTP nu suprascrie → `-rm` + `put` + verificare de mărime.
2. `ItemDisplay` desenează +Z invers față de entitate → corecție de 180° (vezi `LandVehicleRig`).
3. Resource pack: formatul nou cere `min_format`/`max_format` împreună; `supported_formats` e
   respins pentru formate noi.
4. GitHub Actions: `if: hashFiles(...)` nu vede fișierele din runner → folosește `always()`.
5. În Actions, `cargo`/`javac` lipsesc din sandbox → build doar pe runner.
6. Modelele modului vin în două formate: `elements` (Blockbench) și `components`
   (`framework:open_model`). Ambele au `from/to/faces/rotation` și se pot trata la fel.
7. Un build „success" în Actions nu înseamnă binar produs: verifică pașii și artefactul.

---

## 8. Primii pași pentru agentul nou

1. Citește `docs/LISTA-FISIERE-VARIANTE-A.md` și copiază fișierele în `paper-vehicles/`.
2. Pune la build workflow-ul Java 21 + Paper API, rulează, obține primul JAR.
3. Pornește serverul Paper 1.21.4 (decide: server nou sau convertirea celui existent, cu
   confirmarea utilizatorului pentru că asta închide Horizon).
4. Test: un vehicul fără nota ARR (ex. `lawn_mower`), apoi două.
5. Abia apoi: celelalte vehicule, economia, garajul.
