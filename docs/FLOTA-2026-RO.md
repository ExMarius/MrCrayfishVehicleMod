# Flota noastră (r40) — nume, culori din fabrică, insignă, scaun

Document scurt despre ce s-a schimbat în r40, ca să nu mai arătăm ca o copie a
modului original și ca scaunul să fie stabil.

## 1. Nume proprii pentru toate cele 24 de vehicule

ID-urile rămân aceleași (comenzile, garajul și salvagele vechi funcționează în
continuare); se schimbă doar numele afișat.

| ID | Nume nou |
|---|---|
| `go_kart` | Street Kart 125 |
| `lawn_mower` | Turf Cutter |
| `quad_bike` | Ridge Runner 400 |
| `dune_buggy` | Sand Viper |
| `tractor` | Fieldmaster 75 |
| `dirt_bike` | Trail Raptor 250 |
| `moped` | City Scoot 50 |
| `off_roader` | Trailbreaker 4x4 |
| `sports_car` | Apex GT |
| `mini_bus` | Metro Shuttle |
| `police_car` | Patrol 4x4 |
| `golf_cart` | Fairway Cruiser |
| `jet_ski` | Wave Dash |
| `compact_helicopter` | Skyhopper |
| `sofacopter` | Lazy Lift |
| `sports_plane` | Skyline 200 |
| `atv` | Mud Brute |
| `mini_bike` | Pocket Rocket |
| `smart_car` | Tiny Two |
| `speed_boat` | Riptide 210 |
| `aluminum_boat` | Tin Skiff |
| `couch` | Sofa Glider |
| `bumper_car` | Bumper Brawl |
| `shopping_cart` | Trolley Turbo |

## 2. Vopsea „din fabrică" (fiecare vehicul are altă culoare)

`LandVehicleSpec.FACTORY_PAINTS` dă fiecărui model o culoare proprie, aplicată
ca tint `dye` peste texturile albe ale caroseriei. Consecințe:

* la spawn, vehiculul are deja culoarea lui (nu mai e o flotă albă identică);
* spray can-ul revopsește corect — tintul înmulțește o textură **albă**, deci
  culoarea jucătorului nu se amestecă cu o vopsea existentă;
* `paintColor = -1` înseamnă „vopseaua din fabrică", nu „alb forțat".

Culori: Street Kart portocaliu-roșu, Turf Cutter verde, Ridge Runner roșu,
Sand Viper chihlimbar, Fieldmaster verde închis, Trail Raptor albastru, City
Scoot galben, Trailbreaker verde oliv, Apex GT roșu, Metro Shuttle turcoaz,
Patrol 4x4 negru, Fairway Cruiser alb, Wave Dash roz, Skyhopper violet,
Skyline 200 crem, Mud Brute oliv, Pocket Rocket cupru, Tiny Two bleu, Riptide
albastru, Tin Skiff argintiu, Sofa Glider maro, Bumper Brawl roz, Trolley Turbo
oțel.

## 3. Insigna noastră, nu a modului

Toate sprite-urile `*logo*.png` din resource pack erau marca „Cray Industries"
a modului. Acum sunt desenate procedural de `tools/restyle.py`: un chevron
portocaliu într-un inel închis, generat pixel cu pixel, fără nicio artă
împrumutată. Fișierele sunt deterministe (același PNG la fiecare build), deci
SHA-1-ul pack-ului rămâne reproductibil.

Modelele în sine rămân GPLv3 (credit + `LICENSE.txt` în pack), dar aspectul de
flotă — nume, culori, insignă — este al nostru.

## 4. Geometria: caroseriile nu mai sunt cele originale (r41)

Fiecare caroserie trece printr-un pass declarat de restilizare
(`tools/restyle_geometry.py`), aplicat după conversia modelului sursă:

* **proporții proprii** — lățime/înălțime/lungime diferite de original
  (ex. off-roader +8% lățime, tuns 8% pe înălțime la kart, +5% lungime la
  bărci), aplicate în jurul centrului modelului;
* **plafon** — raport de cabină pe verticală, aplicat doar deasupra liniei
  brâului, deci stâlpii și geamurile rămân lipite (off-roader/poliție +12%,
  tractor +8%);
* **kit propriu** — piese adăugate, generate din cutia modelului: roof rack,
  light bar, roll hoop, footboards, side skirt, coș de evacuare, antenă, roof
  box. Sunt pur aditive, deci nu pot strica geometria sursă;
* totul rămâne în limitele vanilla (elemente −16..32, UV 0..16, fără unghiuri
  noi) — testat automat.

Modelele cu piese separate (uși/capotă/plafon la sports car și mini bus,
ghidoane la motociclete) primesc doar kitul: piesele sunt poziționate de
specificație, deci o scalare doar a corpului le-ar deplasa.

Comparație randată offline (stânga = original, dreapta = r41):
`docs/flota-r41-înainte-după.png`. Randările se fac cu
`python3 tools/model_preview.py out.png <model.json>`.

### 4b. De la zero: ce se poate și ce nu (r42, în lucru)

Clientul vanilla **nu încarcă OBJ/glTF**. Un pack Java poate conține doar
modele din cuboizi (`elements` cu `from`/`to` în −16..32, UV 0..16, rotații
multiplu de 22,5°). Deci „model nou” = geometrie scrisă cub cu cub, nu un mesh
adus din alt program.

`tools/scratch_models.py` conține primul astfel de model (un pickup 4x4, în
două variante: civil și poliție), cu **set propriu de texturi** (`mat_*`,
generate pixel cu pixel) și cu punctele de ancorare păstrate: roți la
x −2/18, z 22,5/−6,5, bancheta șoferului la y 8, față = +Z. Gardurile
automate (`tools/test_scratch_models.py`) verifică limitele vanilla, faptul că
nicio piesă nu intră în volumul roților, că doar vopseaua e vopsibilă și că
stopurile/farurile sunt la capetele corecte.

Modelul **nu este încă activat** în pack (`SCRATCH_BODIES` e gol): randarea
lui e mai săracă decât silueta r41, deci nu se urcă până nu e mai bun.
Comparație: `docs/flota-r42-caroserii-noi.png`.

### 4c. Roți proprii (r42)

Trei roți desenate de la zero (`tools/scratch_models.py`, funcția
`wheel_body`) și livrate ca piese normale de mecanică:

| Cheie | Nume în joc | Model | Asfalt / pământ / zăpadă | Prindere |
|---|---|---|---|---|
| `ALLOY` | Roti aliaj | `wheel_alloy` | 0,85 / 1,55 / 2,20 | 0,92 (alunecare 0,13) |
| `TRAIL` | Roti noroi | `wheel_trail` | 1,30 / 0,80 / 0,95 | 0,84 (alunecare 0,26) |
| `HEAVY` | Roti grele | `wheel_heavy` | 1,15 / 1,05 / 1,20 | 0,86 (alunecare 0,20) |

Fiecare roată păstrează anvelopa pe care o așteaptă rig-ul (16 unități
diametru, 4 unități lățime, centru în 8/8/8), deci se montează pe orice
vehicul fără corecție de scală. Sunt desenate ca măști de 16×16 pixeli
(anvelopă, spițe, jantă, butuc, șuruburi), fără elemente rotite — arată la fel
din ambele părți și nu pot produce fețe care pâlpâie.

Unde apar:

* **`/vehicle creative` → tab „Roti”** — toate cele 6 tipuri (3 vechi + 3 noi),
  câte un set de 4 în inventar la click;
* **magazinul de piese** (`/vehicle magazin`) — cu prețurile din `config.yml`
  (`wheel_alloy: 7500`, `wheel_trail: 5200`, `wheel_heavy: 6800`);
* **stația de mecanic** — se montează cu click dreapta pe vehicul, ca înainte;
* fizica pe suprafețe intră automat prin `WheelType.surfaceModifier()`.

Comanda de test: `/vehicle spawn sports_car`, apoi `/vehicle creative`, tab
„Roti”, click pe „Set roti 4x - Roti aliaj”, click drept pe mașină.

Randare: `docs/roti-r42.png` (rândul de jos = roțile noastre; rândul de sus =
cele trei convertite din mod, pe care randătorul offline le arată deformate
pentru că folosesc elemente rotite).

## 5. Scaunul: călărețul stă lipit de mașină

`seat.glue-every-tick: true` (implicit) înseamnă că scaunul primește poziția și
rotația în **fiecare** tick, cu exact întârzierea de un tick pe care o are
desenul caroseriei (`interpolationDuration = 1`). Astfel:

* la viteză mare scaunul nu mai rămâne în urmă (înainte se muta doar la
  abateri mari, deci „aluneca" până la 10 cm);
* la viraje, scaunul și călărețul se rotesc o dată cu mașina, fără tremurat;
* dacă tot apare o deriva, se poate opri/reporni live:
  `/vehicle seatglue on|off` (salvat în `config.yml`).

Reglaj fin pe înălțime: `/vehicle seatheight -0.85` (valoarea 26.3).

## 6. Comenzi utile pentru test

```
/vehicle spawn police_car      # Patrol 4x4 (off-roader + girofare)
/vehicle spawn sports_car      # Apex GT roșu din fabrică
/vehicle paint                 # spray can (revopsire corectă)
/vehicle seatglue on           # sudează călărețul pe scaun
/vehicle seatheight -0.85      # înălțimea călărețului
```

## 7. Import 1:1 dintr-un pack extern (r42)

`tools/import_external_pack.py` mută un set de modele străine în pack-ul nostru
fără să redeseneze nimic: cuboizii, UV-urile și rotațiile rămân identice, doar
referințele de textură sunt rescrise în namespace-ul `vehicle`.

```
python3 tools/import_external_pack.py horizon.zip --prefix hz_ --dry-run   # raport
python3 tools/import_external_pack.py horizon.zip --prefix hz_            # scrie
python3 tools/build_resource_pack.py --output /tmp/pack.zip               # le bagă în pack
```

* intră în pack: `tools/external_models/models/*.json` + `textures/*.png`;
* **nu** intră: `.obj/.gltf/.glb/.fbx` (clientul vanilla nu le poate randa),
  modelele care își iau geometria din `parent` și modelele fără `elements` —
  toate apar în raport, nu sunt aruncate în tăcere;
* ștergerea unui model vechi se face din `tools/deleted_models.txt` (un stem pe
  linie) — sistemul (comenzi, fizică, garaj, mecanic, magazin) rămâne intact.

## 8. Off-Roader Horizon importat 1:1 (r43)

Sursa: `tools/horizon/horizon-off-roader-transfer.zip` (pachetul de transfer din
HorizonMods, commit `cd950cb`). Conversia e în `tools/horizon_import.py`.

* **caroseria păstrează toate cele 163 de cuboizi** ai modelului Horizon;
* cele **17 panouri cu rotații pe care vanilla nu le poate exprima** (35°, −18°,
  −35°, 32°, 4° …) devin display-uri separate: modelul fiecărui grup e
  deplasat ca pivotul să cadă în centrul modelului (8,8,8), iar `Part`-ul din
  spec îl rotește cu unghiul sursă. Zero rotunjiri la 22,5°;
* scara 1.42, înălțimea de rulare 0.39, ecartamentul ±0.92125 și ampatamentul
  ±1.286875 vin din `source/runtime-layout.json`;
* texturile Horizon sunt copiate ca `hz_*` (15 fișiere), deci nimic nu e
  redesenat.

Vehicul nou: `/vehicle spawn horizon_off_roader` — **nu** îl înlocuiește pe
`off_roader`, exact cum cere README-ul pachetului.

Teste: `tools/test_horizon_import.py` (6) — numărul de cuboizi, grupurile,
deplasarea pivotului, absența rotațiilor ilegale, texturile și sincronizarea cu
`LandVehicleSpec`.
