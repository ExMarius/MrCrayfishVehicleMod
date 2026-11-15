# Portul modului de mașini pe Horizon — stare

Modul original: **MrCrayfish's Vehicle Mod** (`src/`, Java/Forge). Aici îl reproducem ca
**mod Horizon**: conținut (`.hmod`) + cod nativ (`.hnmod`). Nu e plugin, nu e demo.

## Inventarul modului original (ce trebuie reprodus)

| Parte | Câte | Sursă |
|---|---|---|
| Vehicule (entități) | **19** | `init/ModEntities.java` |
| Componente de model | **156** fișiere | `assets/vehicle/models/` |
| Texturi | **143** | `assets/vehicle/textures/` |
| Iteme | **27** | `init/ModItems.java` |
| Blocuri | **16** | `init/ModBlocks.java` |
| Rețete | 7 | `data/vehicle/recipes/` |
| Chei de text | 174 | `assets/vehicle/lang/en_us.json` |
| Sunete | 3 categorii | `assets/vehicle/sounds/` |

## Ce e deja în mod (v0.2.0)

- **Toate cele 156 de modele** importate, cu UV-urile convertite în scara modelelor Horizon
  (`horizon/tools/port_mod_content.py`). Include ambele formate ale modului: `elements`
  (Blockbench) și `components` (`framework:open_model`).
- **Toate texturile, sunetele, textele și rețetele**, pe aceleași căi, ca referințele
  `vehicle:...` din modele să se rezolve.
- **Tip de entitate pentru fiecare vehicul**: `vehicle:go_kart`, `vehicle:sports_car`,
  `vehicle:quad_bike`, `vehicle:moped`, `vehicle:dirt_bike`, `vehicle:mini_bus`,
  `vehicle:tractor`, `vehicle:off_roader`, `vehicle:golf_cart`, `vehicle:lawn_mower`,
  `vehicle:jet_ski`, `vehicle:sports_plane`, `vehicle:compact_helicopter`,
  `vehicle:vehicle_trailer`, `vehicle:storage_trailer`, `vehicle:fluid_trailer`,
  `vehicle:seeder`, `vehicle:fertilizer`.
  (sofacopterul e din alt mod — `cfm` — și nu are model aici.)
- **Randare pentru toate cele 18**, generată din modele: `vehicles_gen` din `runtime.rs`
  ține (entitate → model → offset), iar `native_mob_submit` desenează fiecare vehicul cu
  modelul lui, centrat pe entitate.
- Comanda **`/masina [nume]`**: pune vehiculul ca entitate adevărată (fără nume, listează).

## Ce urmează (ca să fie „unu la unu")

| Etapă | Ce | Stare |
|---|---|---|
| 1 | Conținut: modele, texturi, texte, entități, randare | **gata** |
| 2 | Condus pe entitatea adevărată (nu pe minecart): fizică per vehicul, scaune, coborâre | în lucru |
| 3 | Itemele (27) + blocurile (16) ca intrări în mod, cu rețetele lor | |
| 4 | Motoare/combustibil: `jerry_can`, benzinărie, fluid pipes, atelier (`workstation`) | |
| 5 | Interfața de editare a vehiculului (vopsire, piese) — ecran propriu (`native_screen`) | |
| 6 | Detalii: volan care se rotește, roți, sunet de motor, damage, trailer cuplare | |

Rulează `python3 horizon/tools/port_mod_content.py` după orice schimbare în sursa Java:
regenerează conținutul și tabelul de randare.
