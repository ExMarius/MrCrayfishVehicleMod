# Port status — Vehicle Mod 1.16 → Paper 1.21.4 (audit total)

Audit față-în-față între modul Forge (`src/main/java/com/mrcrayfish/vehicle/`)
și pluginul Paper (`paper-plugin/`, resource pack r38). Scop: paritate unu-la-unu.

## ✅ DEJA PORTAT (funcțional pe server)

| Zonă | Detalii |
|---|---|
| 23 entități vehicul | GoKart, Lawn Mower, Quad Bike, Dune Buggy, Tractor, Dirt Bike, Moped, Off Roader, Sports Car, Mini Bus, Golf Cart, ATV, Mini Bike, Smart Car, Couch, Bumper Car, Shopping Cart + Jet Ski, Speed Boat, Aluminum Boat + Sports Plane, Compact Helicopter, Sofacopter |
| Toate cele 5 trailere | Fertilizer, Seeder, Storage (chain), Fluid (100 găleți), Vehicle (transport) |
| Fizica exactă din sursă | ecuații uscat/apă/avion/elicopter, suspensie, wheelie/boost/charging, hitch offsets |
| Modelare | modelele originale + matricele exacte (audit în SOURCE_POSITION_AUDIT.md), animații roți/rotor/elice, cosmetice care se deschid (uși/capote/portbagaje) |
| Sunete motor | sample-uri originale OGG, pitch-ul din sursă, controller interpolat |
| Benzină | gauge tip inimi + bară, consum, gratuit în Creative |
| Stație de benzină | pompă + furtun + distanță maximă + sunet original gluglu |
| Inventare | piept Moped 27, trunk 27 + glove 9 la Sports Car, storage trailer |
| Persistență | autosave, hibernare la distanță, respawn la apropiere |
| Farming | seeder/fertilizer funcționale pe trailere |
| Protecții | kill @e safety, postură rider cu carrier invizibil |

## ❌ LIPSESC din mod (găsit la audit)

| # | Feature | În mod | În Paper | Impact RP |
|---|---|---|---|---|
| 1 | **Obținere vehicule** | Workstation (bloc) + asamblare: motor + roți + caroserie → vehicul | doar `/vehicle spawn` (op) | **jucătorii nu pot lua vehicule singuri — CRITIC** |
| 2 | **Motoruri** (12) | small/large/electric × iron/gold/diamond/netherite | niciun item | viteze/tier-uri diferite lipsesc |
| 3 | **Roți** (7) | standard/sports/offroad/all-terrain/plastic/racing/snow | niciun item | customizare + viteze pe suprafețe |
| 4 | **Fluide + industrie** | fuelium/ender sap/blaze juice, extractor, mixer, drum, pipe | fuel GRATIS la pompă | economia benzinei lipsește |
| 5 | **Jerry can** | alimentare mobilă din inventar | doar sunetul există | drumurile lungi nu au rezervor |
| 6 | **Chei + locks** | item Key, NEEDS_KEY, lock/unlock | modelele keyhole există în pack, dar inutile | furt de mașini ne-protejat în RP |
| 7 | **Spray can / recolor** | vopsire vehicul cu vopsele | lipsește | customizare (ATV-ul „verde" = simptom) |
| 8 | **Horn (claxon)** | sunet + apăsare | lipsește complet | trafic RP fără claxoane |
| 9 | **Daune + distrugere** | health, explozie + drop, daune coliziune | riguri invulnerabile | accidentele nu au consecințe |
| 10 | **Jack (rampa)** | ridică vehiculul (reparații/încărcare electrică) | lipsește | atelier mecanic lipsește |
| 11 | **Traffic cone + vehicle crate** | decor + ambalare transport | lipsește | piete/drumuri mai goale |
| 12 | **Motoare electrice + solar** | tier electric + charging | lipsește | ecosistem inutilizabil fără #4 |
| 13 | **Advancements** | da | nu | minor |

## 🎯 Ordinea portării propusă pentru UKEMC ROLEPLAY

1. **`/vehicle buy/shop` cu bani (Vault)** — rezolvă #1 mai bine decât workstation-ul pentru un server RP (prețuri pe categorii, permisiuni pe grad)
2. **Benzina costă bani la pompă (Vault)** — #4 convertit în economie (per_1s/umplere = X bani)
3. **Jerry can item** (#5) — rezervă portabilă; folosește sunetul gluglu existent
4. **Chei + lock/unlock** (#6) — keyhole-urile din pack se aprind singur de îndată ce sistemul e activ
5. **Spray can recolor** (#7) — click cu vopsea → culoarea vehiculului (persistent)
6. **Horn claxon** (#8) — buton dedicat (sneak+click), sunet din mod (`vehicle:horn`)
7. **Daune + reparații cu wrench** (#9) — coliziuni → daune; wrench = repară
8. Blocuri decor (traffic cone, vehicle crate) (#11), Jack (#10), electric (#12) — la final

Sursele sunetelor pentru horn/lock/spray există deja în mod (`src/main/resources/assets/vehicle/sounds/`) —
se pot include direct în resource pack fără artwork nou.
