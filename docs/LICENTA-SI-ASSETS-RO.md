# Licență și asset-uri: ce putem folosi într-un plugin nou

Document scurt, cu fapte verificate în acest repo. Se aplică proiectului de plugin Paper.

## Faptul principal: modul e GPLv3, nu „toate drepturile rezervate"

`MOD-LICENSE.txt` din rădăcina repo-ului este **GNU General Public License v3**. Asta
înseamnă, legal:

- **Poți** să folosești, să modifici și să distribui codul și asset-urile modului.
- **Cu condiția** ca lucrarea derivată să fie **tot GPLv3**, cu sursa disponibilă, licența
  inclusă și autorul creditat.

Deci întrebarea „cum facem să nu mai folosim resursele care au drepturi de autor" are un
răspuns simplu: **nu trebuie să le evităm** — putem să le folosim legal, dacă respectăm GPLv3.
Problema nu e permisiunea, e conformarea.

## Ce e al nostru și ce e al modului

| Ce | Unde | Proveniență |
|---|---|---|
| Codul pluginului | `paper-plugin/src/main/java/` — **81 fișiere** | al nostru |
| Cele 23 de specificații de vehicule | `paper-plugin/.../vehicle/LandVehicleSpec.java` | ale noastre (date) |
| Modele „clasice" (go-kart, sports car, …) | `src/main/resources/assets/vehicle/models/` | **modul MrCrayfish**, GPLv3 |
| Modele „de-ale noastre" (dune buggy, atv, bumper car, shopping cart, mini bike, smart car, speed boat, aluminum boat) | `tools/source_assets/*.json` | **tot modul MrCrayfish** (versiuni 1.16.X / 1.12.2) — vezi `tools/source_assets/README.md` |
| Sunete (engine .ogg) | `tools/source_assets/*.ogg` | **modul**, GPLv3 |
| Modele de mobilă (sofa, ceiling fan) | `tools/source_assets/cfm_*.json` | **Furniture Mod**, de asemenea GPL |

Concluzie care contează: **nu există asset-uri „ale noastre" în afara codului.** Toate modelele
și sunetele, inclusiv cele pe care le credeam proprii, vin din mod (sau din Furniture Mod).
Codul Java și specificațiile sunt ale noastre.

## Trei variante reale pentru pluginul nou

| Variantă | Ce implică | Cost | Risc legal |
|---|---|---|---|
| **A. Plugin nou, cu asset-urile modului** | păstrăm modelele/sunetele, pluginul și pack-ul se declară **GPLv3**, cu `LICENSE` + credit MrCrayfish + sursa publicată | mic | **zero**, dacă respectăm GPL |
| **B. Plugin nou, fără asset-uri externe** | vehicule făcute din **blocuri/iteme vanilla** (fără resource pack) | mediu | zero |
| **C. Plugin nou, cu modele desenate de noi** | modelare Blockbench de la zero pentru fiecare vehicul | mare (zile–săptămâni de muncă de artă) | zero |

Varianta C e singura care dă asset-uri „proprii" în sensul strict, dar e un proiect de artă, nu
de cod. Varianta A e cea care respectă legea și nu cere muncă nouă de modelare.

## Ce trebuie păstrat dacă mergem pe A

1. `LICENSE` = textul GPLv3 în rădăcina pluginului.
2. Credit vizibil: „Vehicle models and sounds from MrCrayfish's Vehicle Mod (GPLv3)".
3. Sursa pluginului publică (repo public), cu licența și creditul în README.
4. Dacă modificăm modele, menționăm că sunt modificate, tot sub GPLv3.
5. Nu vindem pluginul ca produs închis și nu punem restricții suplimentare peste GPL.

## Recomandare

**Varianta A.** Plugin nou (sau existentul curățat), cu asset-urile modului, declarat GPLv3.
„Plugin nou de la zero" rezolvă organizarea codului, nu drepturile de autor — iar dacă tot
folosim modelele modului, oricum trebuie GPLv3.

Dacă vrei să nu depindem de asset-urile modului, atunci **B** (blocuri vanilla) e varianta
rapidă și curată, iar **C** rămâne un proiect separat de modelare.
