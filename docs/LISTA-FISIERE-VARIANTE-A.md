# Varianta A — lista exactă de fișiere pentru pluginul Paper nou

Decizie: plugin nou, cu modelele modului, declarat **GPLv3**, cu sursă, licență și credit.
Documentul listează exact ce se copiază, ce se păstrează ca licență și ce trebuie verificat
înainte de distribuire.

Sursa: repo `ExMarius/MrCrayfishVehicleMod`, branch
`arena/2f9f7b9e-mrcrayfishvehiclemod`.

---

## 0. Întâi problema semnalată: „All Rights Reserved" în modele

Am scanat toate cele 156 de modele JSON din `src/main/resources/assets/vehicle/models/` plus
cele 15 din `tools/source_assets/`. Rezultat: **59 de fișiere conțin în câmpul `credit`
textul „Model by MrCrayfish … All Rights Reserved."**, deși repo-ul are `MOD-LICENSE.txt` = GPLv3.

Distribuția pe vehicule (modelul de corp):

| Model de corp | Credit |
|---|---|
| go_kart, sports_car, quad_bike, sports_plane, helicopter, moped, **mini_bus**, dirt_bike | „All Rights Reserved" |
| jet_ski, lawn_mower, golf_cart, off_roader, tractor, trailer | fără credit |
| dune_buggy, atv, bumper_car, shopping_cart, mini_bike, smart_car, speed_boat, aluminum_boat (`tools/source_assets/`) | fără credit |

Ce înseamnă practic:

- Nota „All Rights Reserved" din metadatele modelului (text pus de Blockbench) **nu e o licență**;
  licența proiectului e cea din `MOD-LICENSE.txt` (GPLv3). Dar contradicția e reală și, până la
  clarificare, **nu putem afirma „risc zero"** pentru acele 8 vehicule.
- Recomandare: **prima versiune a pluginului să conțină doar cele 14 vehicule fără nota ARR**
  (jet_ski, lawn_mower, golf_cart, off_roader, tractor, trailer + cele 8 din `tools/source_assets/`).
  Cele 8 cu ARR se adaugă după ce se cere acordul autorului sau se înlocuiește creditul.
- Nota „Model generated using MrCrayfish's Model Creator" (72 fișiere) e doar credit de unealtă,
  nu restricție.

Despre autobuzul **FoundationGames (MIT)**: nu există în acest repo (am căutat „FoundationGames"
în tot arborele — zero rezultate). E în celălalt proiect; dacă îl folosim, se păstrează separat
nota MIT și se trece în `THIRD-PARTY.md` al pluginului.

---

## 1. Codul pluginului (al nostru) — se copiază integral

| Grup | Cale | Fișiere | Mărime |
|---|---|---|---|
| Cod principal | `paper-plugin/src/main/java/` | **65** | 908,9 KB |
| Teste | `paper-plugin/src/test/java/` | **16** | 87,1 KB |
| Build | `paper-plugin/build.gradle`, `settings.gradle`, `gradle/`, `gradlew`, `gradlew.bat`, `gradle.properties` | 10 | 170,3 KB |
| Resurse plugin | `paper-plugin/src/main/resources/plugin.yml`, `config.yml` | 2 | 11,3 KB |

Pachete din cod (toate ale noastre): `vehicle`, `physics`, `render`, `runtime`, `garage`,
`gaspump`, `jerrycan`, `mechanic`, `shop`, `persistence`, `command`, `listener`, `world`,
`restart`, plus `VehiclePlugin.java` și `ResourcePackSender.java`.

## 2. Uneltele care construiesc resource pack-ul

| Cale | Fișiere | Mărime | Ce face |
|---|---|---|---|
| `tools/build_resource_pack.py` | 1 | ~40 KB | construiește `vehicle-pack.zip` din modelele modului + `tools/source_assets/` |
| `tools/*.py` (celelalte 4) | 4 | ~142 KB | patch pack, verificări, import |
| `tools/source_assets/` | **17** (15 JSON + 3 OGG, minus README) | 347,1 KB | modelele „vintage" (dune buggy, atv, bumper car, shopping cart, mini bike, smart car, speed boat, aluminum boat) + sunete |

## 3. Asset-urile modului (GPLv3) — se copiază în pack, cu licență

| Grup | Cale | Fișiere | Mărime |
|---|---|---|---|
| Modele | `src/main/resources/assets/vehicle/models/` | **156** | 879,7 KB |
| Texturi | `src/main/resources/assets/vehicle/textures/` | **143** | 286,5 KB |
| Sunete | `src/main/resources/assets/vehicle/sounds/` | **37** | 1.042,3 KB |
| Texte (lang) | `src/main/resources/assets/vehicle/lang/` | **11** | 148,3 KB |
| Blockstates | `src/main/resources/assets/vehicle/blockstates/` | 16 | 12,7 KB |
| Rețete | `src/main/resources/data/vehicle/recipes/` | 7 | 2,8 KB |

Total asset-uri: ~372 fișiere, ~2,37 MB.

**Dacă mergem pe varianta „doar cele 14 vehicule curate"**: din `models/vehicle/` se păstrează
modelele pentru jet_ski, lawn_mower, golf_cart, off_roader, tractor + trailer(e), plus
componentele comune (`fuel_door*`, `key_hole`, `tow_bar`, `small_fuel_door*`, `nozzle`,
`gas_hose_segment`, `jack_piston_head`, `seed_spiker`, `vehicle_crate_panel*`) — dar
`fuel_door*` și `key_hole` sunt printre cele 59 cu ARR, deci intră la capitolul „de clarificat".

## 4. Licențe și credite — obligatoriu în pluginul nou

| Fișier | De ce |
|---|---|
| `MOD-LICENSE.txt` → copiat ca `LICENSE` | textul GPLv3 al modului |
| `CREDITS-fml.txt`, `MinecraftForge-Credits.txt` | creditele cerute de moștenirea Forge |
| `Paulscode IBXM Library License.txt`, `Paulscode SoundSystem CodecIBXM License.txt` | licențele bibliotecilor de sunet |
| `tools/source_assets/README.md` | spune de unde vin modelele „vintage" (1.16.X / 1.12.2 / Furniture Mod) |

Plus, în README-ul pluginului:
> Vehicle models and sounds from MrCrayfish's Vehicle Mod (GPLv3, https://github.com/MrCrayfish/MrCrayfishVehicleMod).
> Some models carry an "All Rights Reserved" note in their metadata; see THIRD-PARTY.md.

## 5. Comenzi de copiere (din acest repo în repo-ul pluginului)

```bash
# 1. codul nostru
mkdir -p paper-vehicles/src/main/java paper-vehicles/src/test/java paper-vehicles/src/main/resources
cp -r paper-plugin/src/main/java/*        paper-vehicles/src/main/java/
cp -r paper-plugin/src/test/java/*        paper-vehicles/src/test/java/
cp -r paper-plugin/src/main/resources/*   paper-vehicles/src/main/resources/
cp paper-plugin/build.gradle paper-plugin/settings.gradle paper-plugin/gradle.properties paper-vehicles/
cp -r paper-plugin/gradle paper-plugin/gradlew paper-plugin/gradlew.bat paper-vehicles/

# 2. uneltele de pack + modelele vintage
mkdir -p paper-vehicles/tools
cp tools/*.py paper-vehicles/tools/
cp -r tools/source_assets paper-vehicles/tools/

# 3. asset-urile modului (pentru pack)
mkdir -p paper-vehicles/assets-src
cp -r src/main/resources/assets/vehicle/models      paper-vehicles/assets-src/
cp -r src/main/resources/assets/vehicle/textures    paper-vehicles/assets-src/
cp -r src/main/resources/assets/vehicle/sounds      paper-vehicles/assets-src/
cp -r src/main/resources/assets/vehicle/lang        paper-vehicles/assets-src/
cp -r src/main/resources/assets/vehicle/blockstates paper-vehicles/assets-src/

# 4. licențe
cp MOD-LICENSE.txt paper-vehicles/LICENSE
cp CREDITS-fml.txt "MinecraftForge-Credits.txt" paper-vehicles/
cp "Paulscode IBXM Library License.txt" "Paulscode SoundSystem CodecIBXM License.txt" paper-vehicles/
```

## 6. Ordinea de lucru recomandată

1. Copiază 1–4 de mai sus (cod + unelte + asset-uri + licențe).
2. **Nu adăuga încă** cele 8 vehicule cu „All Rights Reserved" în lista jucabilă
   (`LandVehicleSpec`): go_kart, sports_car, quad_bike, sports_plane, helicopter, moped,
   mini_bus, dirt_bike.
3. Build pe GitHub Actions (Java 21 + Paper API), nu local.
4. Test pe Paper 1.21.4: un vehicul, apoi două.
5. `THIRD-PARTY.md` cu: modul (GPLv3), Furniture Mod (GPL), autobuzul FoundationGames (MIT),
   modelele cu nota ARR (stare: de clarificat).
