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
