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

## 4. Scaunul: călărețul stă lipit de mașină

`seat.glue-every-tick: true` (implicit) înseamnă că scaunul primește poziția și
rotația în **fiecare** tick, cu exact întârzierea de un tick pe care o are
desenul caroseriei (`interpolationDuration = 1`). Astfel:

* la viteză mare scaunul nu mai rămâne în urmă (înainte se muta doar la
  abateri mari, deci „aluneca" până la 10 cm);
* la viraje, scaunul și călărețul se rotesc o dată cu mașina, fără tremurat;
* dacă tot apare o deriva, se poate opri/reporni live:
  `/vehicle seatglue on|off` (salvat în `config.yml`).

Reglaj fin pe înălțime: `/vehicle seatheight -0.85` (valoarea 26.3).

## 5. Comenzi utile pentru test

```
/vehicle spawn police_car      # Patrol 4x4 (off-roader + girofare)
/vehicle spawn sports_car      # Apex GT roșu din fabrică
/vehicle paint                 # spray can (revopsire corectă)
/vehicle seatglue on           # sudează călărețul pe scaun
/vehicle seatheight -0.85      # înălțimea călărețului
```
