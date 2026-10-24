# Meniul cricului (jack) — codul original al modului vs implementarea noastră

Document de lucru: referințe exacte (linkuri cu permalink pe commitul `54430ae`),
fragmentele de cod relevante, cronologia a ce am construit, lista dorințelor
utilizatorului și un prompt gata de copiat pentru continuarea lucrului.

- Repo: https://github.com/ExMarius/MrCrayfishVehicleMod
- Branch de lucru: `arena/2f9f7b9e-mrcrayfishvehiclemod`
- Stare la întocmire: commit `54430ae` — CI verde (build + teste + deploy)

---

## 1. Linkuri rapide

### Codul ORIGINAL din mod (client Forge, în acest repo)

| Fișier | Rol | Link |
|---|---|---|
| `EditVehicleScreen.java` | ecranul (render, fereastră 3D, mouse) | [link](https://github.com/ExMarius/MrCrayfishVehicleMod/blob/54430ae/src/main/java/com/mrcrayfish/vehicle/client/screen/EditVehicleScreen.java) |
| `EditVehicleContainer.java` | sloturile + regulile de plasare | [link](https://github.com/ExMarius/MrCrayfishVehicleMod/blob/54430ae/src/main/java/com/mrcrayfish/vehicle/inventory/container/EditVehicleContainer.java) |
| `edit_vehicle.png` | textura GUI-ului (176×184) | [link](https://github.com/ExMarius/MrCrayfishVehicleMod/blob/54430ae/src/main/resources/assets/vehicle/textures/gui/edit_vehicle.png) |
| `ClientHandler.java` | unde se deschide ecranul | [link](https://github.com/ExMarius/MrCrayfishVehicleMod/blob/54430ae/src/main/java/com/mrcrayfish/vehicle/client/ClientHandler.java) |

### Implementarea NOASTRĂ (plugin Paper, server-side, pentru client VANILLA)

| Fișier | Rol | Link |
|---|---|---|
| `MechanicGui.java` | meniul cricului reprodus pe vanilla | [link](https://github.com/ExMarius/MrCrayfishVehicleMod/blob/54430ae/paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/mechanic/MechanicGui.java) |
| `MechanicListener.java` | click-uri: schimb motor/roți, vopsea | [link](https://github.com/ExMarius/MrCrayfishVehicleMod/blob/54430ae/paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/mechanic/MechanicListener.java) |
| `MechanicManager.java` | orchestrarea (fără task de rotație) | [link](https://github.com/ExMarius/MrCrayfishVehicleMod/blob/54430ae/paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/mechanic/MechanicManager.java) |
| `LandVehicleSpec.java` | sursa de adevăr: roți, piese, motor | [link](https://github.com/ExMarius/MrCrayfishVehicleMod/blob/54430ae/paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/vehicle/LandVehicleSpec.java) |
| `tools/build_resource_pack.py` | generatorul pack-ului (backdrop + preview) | [link](https://github.com/ExMarius/MrCrayfishVehicleMod/blob/54430ae/tools/build_resource_pack.py) |
| `config.yml` | URL + SHA-1 al pack-ului | [link](https://github.com/ExMarius/MrCrayfishVehicleMod/blob/54430ae/paper-plugin/src/main/resources/config.yml) |

---

## 2. Codul ORIGINAL din mod („cum era")

### 2.1 Containerul — 2 sloturi funcționale + inventarul jucătorului

Poziții exacte din `EditVehicleContainer.java`:

```java
// SLOT 0 — MOTOR, la (8,17): acceptă doar EngineItem potrivit, max 1
this.addSlot(new Slot(vehicleInventory, 0, 8, 17) {
    public boolean mayPlace(ItemStack stack) {
        return vehicle.getEngineType() != EngineType.NONE
            && stack.getItem() instanceof EngineItem
            && ((EngineItem) stack.getItem()).getEngineType() == vehicle.getEngineType();
    }
    public int getMaxStackSize() { return 1; }
});

// SLOT 1 — ROȚI, la (8,35): acceptă doar WheelItem, max 1
this.addSlot(new Slot(vehicleInventory, 1, 8, 35) {
    public boolean mayPlace(ItemStack stack) {
        return vehicle.canChangeWheels() && stack.getItem() instanceof WheelItem;
    }
    public int getMaxStackSize() { return 1; }
});

// inventar jucător: 3 rânduri la y = 102/120/138, hotbar la y = 160
```

### 2.2 Ecranul — fereastră 176×184, iconițe condiționate, preview 3D cu mouse

Din `EditVehicleScreen.java` (aspecte esențiale):

```java
this.imageHeight = 184;                       // fereastra modului: 176×184

// randare textură: vehicul FĂRĂ motor primește iconița „dezactivat" (u=176,v=32),
// vehicul CU motor și slot gol primește iconița motor (u=176,v=0); roti: (u=176,v=16)
if (engineType != EngineType.NONE) { if (slot0 gol) blit(8,17, 176,0, 16,16); }
else { if (slot0 gol) blit(8,17, 176,32, 16,16); }

// PREVIEW: framebuffer 3D real, desenat la (26,17) cu dimensiunea 142×70
// rotație inițială: X +20°, apoi Y +45° (statică până la interacțiune)
Quaternion q = Axis.POSITIVE_X.rotationDegrees(20F);
q.mul(Axis.POSITIVE_Y.rotationDegrees(windowRotationX + 45F));

// mouse în fereastră (26,17,142×70):
//   drag STÂNGA  = pan (translate);  drag DREAPTA = rotație;  scroll = zoom
// tooltips „Engine" / „Not applicable" peste sloturile goale
```

**Concluzie-cheie:** în mod, mașina din fereastră NU se rotește singură — e statică
și o controlezi tu cu mouse-ul (dreapta = rotire, scroll = zoom).

### 2.3 Grila de pixeli — mod vs chest vanilla 54-slot (9×6)

| Zonă | Mod (176×184) | Chest vanilla 54 sloturi (176×222) |
|---|---|---|
| titlu | y 6 | y 6 |
| rânduri de sloturi sus | — | y 18,36,54,72,90,108 |
| motor / roți | (8,17) / (8,35) | — |
| fereastră preview | (26,17) 142×70 | — |
| rânduri inventar player | y 102,120,138 | y 140,158,176 |
| hotbar | y 160 | y 198 |
| label „Inventory" | y ~90 (imageHeight-94) | y 128 |

De aceea este nevoie de **remapare**: sloturile chest vanilla nu se pot muta,
deci textura modului este redesenată peste grila vanilla (vezi §3.3).

---

## 3. Implementarea NOASTRĂ („cum am produs noi")

Obiectiv: același meniu pe un **client vanilla** (fără mod), dintr-un plugin Paper.
Două constrângeri dure ale vanilla: (a) fundalul unui container chest nu poate fi
înlocuit decât prin **fontul titlului**; (b) nu există butoane/desen liber, doar
iteme în sloturi.

### 3.1 Fundalul = textura REALĂ a modului, desenată ca glifă de font

`tools/build_resource_pack.py → emit_jack_gui_backdrop()`:

- citește `edit_vehicle.png` (256×256, fereastra folosește regiunea 0..175 × 0..183)
- compune un canvas 176×222 remapând fiecare bandă peste grila chest:
  `paste(0,89,0)` (titlu+motor+roți+fereastră), `paste(101,155,139)` (cele 3 rânduri
  de inventar puse pe grila vanilla 140/158/176), `paste(159,176,197)` (hotbar pe 198),
  `paste(176,183,214)` (marginea de jos a ferestrei modului, care „închide" fereastra)
- **șterge chenarul celulei (8,53)** — în mod acolo e BUTON, nu căsuță de inventar
- **curăță interiorul ferestrei** (27..167 × 18..86) pe fundalul închis `#1c1c1c`
  (în mod acela este acoperit de framebuffer-ul 3D)
- scrie `assets/vehicle/textures/font/gui/edit_vehicle_bg.png` +
  `assets/vehicle/font/jack_gui.json` (o glifă `\uE700` de 222px + spații negative)

`MechanicGui.open()` invocă glifa în **titlul** inventarului, apoi numele vehiculului:

```java
Component title = Component.text(BACKDROP).font(GUI_FONT)      // \uE7F0\uE700\uE7F1
        .append(Component.text(vehicle.spec().displayName(), NamedTextColor.DARK_GRAY));
Inventory inventory = Bukkit.createInventory(holder, 54, title);
```

### 3.2 Sloturile funcționale, mapate pe grila chest (9 coloane)

```java
ENGINE_SLOT = 0;   // rând 0, col 0 → (8,18) ≈ motor din mod (8,17)   [decalaj 1px]
WHEEL_SLOT  = 9;   // rând 1, col 0 → (8,36) ≈ roți din mod (8,35)    [decalaj 1px]
INFO_SLOT   = 13;  // rând 1, col 4 — centrul ferestrei desenate (preview-ul)
PAINT_SLOT  = 18;  // rând 2, col 0 → (8,54): butonul VOPSEȘTE, pe panou plat
```

- pe slotul motor stă itemul motorului curent; pe cel de roți — setul de roți curent;
  slotul gol de motor arată iconița/bariera „FĂRĂ MOTOR" doar la vehiculele fără motor;
- click cu itemul NOU pe cursor ⇒ `MechanicListener` validează tipul
  (ENGINE/WHEEL) și apelează logica de schimb din `MechanicManager`;
- click pe VOPSEȘTE ⇒ pagina de paletă (16 culori, gratuit), apoi înapoi la banc;
- `INFO_SLOT` primește `Material.PAPER` cu `item_model = vehicle:vehicle_preview/<id>/frame_0`
  + numele vehiculului + date (sănătate, motor, roți, vopsea, lacăt).

### 3.3 Preview-ul compus, STATIC (cadru unic)

`tools/build_resource_pack.py → emit_vehicle_previews()`:

- parsează direct `LandVehicleSpec.java`: roțile (`wheel(...)`) și TOATE piesele
  literale `new Part(...)` (motor, volan, mânere/ghidon) cu poziții/scală/rotații;
- ancorează fiecare piesă ca în rig-ul din joc:
  `poz = anchor_meniu + (centru_piesă − anchor_lume)/(MODEL_UNIT·bodyScale·corecție)`,
  `scală_piesă = scală/(bodyScale·corecție)`, rotație Y 180° aplicată geometric,
  unghiuri reprezentabile vanilla (±22.5/±45) prin `element.rotation`;
- emite **un singur model** `frame_0.json` per vehicul cu display GUI
  `rotation [20, -135, 0]`, translație de centrare, scală ~52× pentru a umple fereastra;
- rotația automată a fost ELIMINATĂ (task-ul din `MechanicManager` șters, cadrele
  1..7 nu se mai generează) — preview-ul este static, ca unghiul inițial din mod.

### 3.4 Altele

- ATV și Dune Buggy au primit `iron_small_engine` din fabrică (ca Quad Bike):
  slotul MOTOR nu mai afișează „FĂRĂ MOTOR" și schimbul funcționează;
- pack-ul e determinist; SHA-1 e fixat în `config.yml` și în test
  (`tools/test_fresh_paper_server.sh`); CI verifică și face deploy automat.

---

## 4. Cronologie (cicluri + commituri)

| Commit | Ce s-a făcut |
|---|---|
| `0224c2b` | primul meniu 54-slot 1:1 (fundal desenat din panouri) |
| `5ccdfb8`,`87f084f` | preview 3D mare compus (caroserie+roți), apoi rotativ |
| `5553152` | font-backdrop: fundal = textura reală a modului |
| `db931e6` | montaj complet în fereastră (+motor/volan/mânere din LandVehicleSpec), VOPSEȘTE sub slotul de roți |
| `96a7233` | preview STATIC, grilă pixel-perfect jos, fără chenar la buton, motor ATV/Dune Buggy |
| `54430ae` | fix test Dune Buggy (CI verde) |

---

## 5. Ce îmi doresc (cerințele utilizatorului — starea lor)

1. **Meniul cricului unu-la-unu cu cel din mod** (vizual + funcțional) — implementat
   prin font-backdrop cu textura reală; *confirmarea vizuală finală e în curs*.
2. **Fără rotire automată a mașinii** — implementat (preview static); *de confirmat
   că unghiul static arată ca în mod* (mod: X20/Y45 față de model; nostru: yaw −135
   din cauza convenției de coordonate a modelelor — dacă pare răsucit, se ajustează).
3. **Butonul de vopsea fără căsuță de inventar** — implementat (chenarul celulei
   a fost șters din textură; itemul stă pe panou plat).
4. **Fără buton REPARĂ în acest meniu** — implementat (nu există în mod aici).
5. **Să facă exact ce face modul**: motor/roți NOI pe cursor + click pe slot =
   schimb; iteme nepotrivite = refuz; lipsa motorului la vehiculele fără motor;
   vopsire gratuită pentru owner + trusted.
6. Nevoie recurentă: **cicluri mici cu commit imediat**, teste înainte de deploy,
   mesaje în română, **niciodată „gata" fără confirmarea mea vizuală**.

### Limitări vanilla documentate (nu sunt neglijențe)

- **drag cu mouse-ul în fereastră = imposibil** fără client modat (vanilla nu
  permite captura de gesturi în interiorul unui chest); de aceea preview-ul este
  static — rotația auto a fost încercată și **respinsă de utilizator**;
- **zoom cu scroll = imposibil** idem;
- **butoane native = imposibil** — orice „buton" vanilla este un item într-un slot;
- pozițiile sloturilor chest sunt fixe pe grilă (decalaj ±1px față de (8,17)/(8,35));
- jucătorii trebuie să **accepte/re-descarce resource pack-ul** la fiecare versiune
  (altfel văd textura veche).

### De confirmat la următoarea verificare vizuală

- aspectul final al meniului (screenshot comparativ cu modul);
- unghiul static și poziția motorului pe ATV/Dune Buggy în fereastră;
- dacă unghiul −135 nu pare identic cu modul: se schimbă cifra într-o singură linie
  din `emit_vehicle_previews()`.

---

## 6. Prompt gata de copiat (pentru continuarea lucrului)

```text
Lucrezi în repo-ul https://github.com/ExMarius/MrCrayfishVehicleMod pe branch-ul
arena/2f9f7b9e-mrcrayfishvehiclemod (plugin Paper 1.21.4 care replică Vehicle Mod
lui MrCrayfish pe client vanilla; pack-ul resource e generat de tools/build_resource_pack.py,
SHA pinuit în paper-plugin/src/main/resources/config.yml; la push, GitHub Actions
construiește, testează și face deploy automat pe serverul HidenCloud; ultimul
commit bun: 54430ae).

CONTEXT — meniul cricului (jack): este reprodus 1:1 cu EditVehicleScreen din mod.
Referință originală: src/main/java/com/mrcrayfish/vehicle/client/screen/EditVehicleScreen.java
și .../inventory/container/EditVehicleContainer.java (citite-le MAI ÎNTÂI).
Implementarea curentă: paper-plugin/.../mechanic/MechanicGui.java + MechanicListener.java
+ generatoarele emit_jack_gui_backdrop() și emit_vehicle_previews() din
tools/build_resource_pack.py. Fundalul = textura reală a modului desenată ca glifă
de font în titlul inventarului; sloturi: motor 0, roți 9, preview static 13,
VOPSEȚE 18; preview-ul = model 3D compus (caroserie+roți+motor+volan) generat
direct din LandVehicleSpec.java, STATIC (codrul unic frame_0, rotation [20,-135,0]).

REGULI:
- cicluri mici; după fiecare schimbare: python3 tools/build_resource_pack.py +
  python3 -m unittest tools.test_build_resource_pack tools.test_configure_server_resource_pack
  tools.test_wheels_preset, apoi sincronizează SHA-1 în config.yml și în
  tools/test_fresh_paper_server.sh, commit + push, și urmărește CI (gh run list --branch
  arena/2f9f7b9e-mrcrayfishvehiclemod) până e verde — nu declara nimic „gata"
  fără confirmarea vizuală a utilizatorului;
- mesaje către utilizator în română;
- constrângeri vanilla documentate: fără drag/zoom cu mouse-ul în fereastră;
  butoanele sunt iteme în sloturi; grila chest nu se mișcă (±1px față de mod);
- în meniul cricului NU există buton de reparare; vopsirea e gratuită;
  mașina stă PE cric (+0.5 jos / +1.0 sus); cheie pe mașină ocupată = refuz;
  acces la GUI: owner + trusted.

SARCINA CURENTĂ: [completează aici ce dorești — ex.: „ajustează unghiul static
al preview-ului după screenshot-ul meu comparativ" / „meniul nu arată ca în mod,
am atașat comparația — găsește decalajele"].
```

## Ciclul C + D (2026-10-24) — piese 1:1 cu modul + corectii vizuale lume

- **Motor la spawn:** toate vehiculele cu tip de motor pornesc cu tierul IRON montat
  si functional (ATV/Dune Buggy au motorul explicit `SMALL_MOTOR` dar FARA montura
  vizuala, ca in mod - `LandVehicleSpec.factoryInstalledEngine() = defaultEngineType() != NONE`).
- **Roti demontabile:** slotul de roti are aceeasi logica cu cel de motor (cursor gol +
  click = demontare pe cursor; roti noi pe slot gol = instalare, fara refund fantoma).
  Fara roti: `mechanicallyDrivable() = false` (masina nu porneste) SI coborarea de pe
  cric este refuzata (JackManager, cazul LIFTED). Persistare `.mechanic.wheels-installed`
  (scrisa doar cand false, zero migrare), simetric cu `.mechanic.engine-installed`.
- **Poarta de motor:** `enginePowered` (toate cele 4 ramuri: sol/apa/aer/elicopter)
  trece acum prin `mechanicallyDrivable()` - fara motor fizic masina nu pleaca.
- **GUI:** butonul VOPSEA are aceeasi casuta vizuala ca sloturile motor/roti (sprite
  copiat 1:1 din chenarul modului in builder); preview-ul info s-a mutat din slotul
  13 in 14 (centrat pe fereastra); preview LIVE are si starea "none" de roti pentru
  vehiculele CU roti (`quad_bike/none/eng` etc. — caroserie + motor, fara geometrie de roti).
- **Vizual lume:** ridicarea pe cric cu 2 model-pixeli mai JOS (`LIFT_VISUAL_CORRECTION
  = -0.125` blocuri, aplicat pe targetul final si pe interpolare); statiile (cric +
  banc) nu mai au planul opac full-bloc la baza => stone-ul/betonul de sub ele ramane
  vizibil ca in mod, iar picioarele au pierdut fetele "down" coplanare cu podeaua;
  rafturile bancului decalate 0.05 px spre exterior (z-fighting eliminat).

## Ciclul E (2026-10-24) — rack-ul uneltelor (binding) + vopsit pe capsule + porti cric

- **Renuntam la perfectarea vizuala (momentan)**; starea LIVE ramane cea din ciclurile A-D.
- **Binding unelte**: cheie + ciocan + pistol de vopsea, legate de banc (PDC cu id-ul bancului),
  STRICT in RAM. Returnare automata la: quit (PlayerQuitEvent), iesire din raza de service
  (leash la 2s, doar pt. jucatorii cu unelte), spargerea bancului. Wipe la join (PlayerJoinEvent):
  uneltele nu supravietuiesc relog-ului/restart-ului (anti-dupe). Returnare manuala din meniu.
- **GUI sincron**: meniul bancului (click simplu pe banc) arata fiecare unealta cu stare live:
  libera / la tine (click=return) / OCUPATA de <nume> (barieră rosie, niciun click); refresh
  instant pentru toti vizitatorii aceluiasi banc (event-driven, zero task-uri suplimentare).
- **Spray exclusiv pe capsule**: pistolul de banc NU are rezervor - consuma prima capsula reala
  din inventar (culoare + 1 pulverizare); capsula directa ramane ca in mod (consum si in creative).
- **Vopsitul NU mai e gratuit**: paleta din banc consuma 1 pulverizare din capsula de ACEA
  culoare (reversare cerinta veche „gratuit"); creative = bypass.
- **Porti cric**: motor/roti (montare+demontare, GUI si click direct) si vopsirea (capsula,
  pistol, paleta) cer JackState.LIFTED. Refuzul e mesaj rosu in romana.

## Ciclul F (2026-10-24) — refacere rack + fix vizual void

- **FIX BUG (raportat din joc): void sub cric/banc.** Aparea dupa eliminarea fetelor
  de jos din Ciclul D. Placa de fund noua: full-footprint, 0.01 grosime, TEXTURA
  MODULUI (nu smooth_stone), la 0.005 deasupra lui y=0 => acopera golul, fara z-fight,
  iar blocul de sub statie ramane vizibil (host neopac). Pack/sha nou.
- **Meniul bancului refacut**: EXACT 1 cheie + 1 ciocan disponibile (spray-ul a fost
  ELIMINAT din rack); ocupata = bariera rosie cu nume + niciun click; a ta = click,
  returnare; ridicarea/coborarea masinii pe cric = BUTON in acelasi meniu (stare live:
  RIDICA / COBOARA cu numele masinii / IN MISCARE / FARA CRIC), cu re-legare automata
  a bancului la cricul din zona.
- **Spray**: se aplica DOAR din capsula tinuta in mana (cumparata / din creative);
  daca masina nu e pe cric => mesaj „du-te la mecanic" + refuz.
- **Cricul**: NU mai exista pickup cu ghemuit+clickdreapta; se ia prin spargerea
  normala a blocului (drop item), ca orice bloc (blocat cat e ridicat/cu masina).
- Raza de service si restul regulelor de binding raman neschimbate.

## Ciclul G (2026-10-24) — GUI-ul vechi STERS complet; meniul unic al bancului

- **Bug fix (raportat din joc): meniul uneltelor se inchidea la orice click si nu
  dadea nimic.** Cauza: handlerul verifica mai intai `vehicles.byId(holder.vehicleId())`,
  dar holder-ul cara id-ul BANCULUI => "masina inexistenta" => closeInventory instant.
  Dispatch-ul uneltelor/cricului nu ajungea niciodata la logica lor.
- **GUI-ul vechi de tip EditVehicleScreen (backdrop textura mod, preview rotativ,
  paleta de culori) a fost STERS integral** (clasa rescrisa, fara
  open/openPaintPage/info/preview; previewTask sters; /vehicle paint devine indrumare).
- **Meniul UNIC al mecanicii = meniul bancului**: sus butonul RIDICA/COBOARA cricul
  legat, mijloc rack-ul (1 cheie + 1 ciocan, ocupare live), jos - doar cand masina e
  complet RIDICATA - sloturile MOTOR si ROTI + cartea cu datele masinii. Flux piese
  identic cu modul (piesa pe cursor = instaleaza/schimba; cursor gol = scoate).
- **Cheia pe masina** deschide acum acelasi meniu unic: bancul legat cricului de sub
  masina, altfel cel mai apropiat banc din raza de service; fara banc => indrumare.
- Spray-ul = exclusiv capsula din mana, pe masina ridicata (mesaj "du-te la mecanic").

## Ciclul H (2026-10-24) — piese scoase doar cu unelte; sneak=nicht; fix ceas; barrier la luare

- **Demontarea NU se mai face din GUI** (sloturile MOTOR/ROTI din meniu au disparut):
  - cheia + click pe masina RIDICATA = scoti ROTILE (pe cursor, dispar de pe model);
  - ciocanul (click stanga) pe masina RIDICATA = scoti MOTORUL; incontinuare jos,
    ciocan = demontarea intregii masini in cufar (neatins);
  - montarea = click cu piesa (motor/roti) direct pe masina ridicata (porti LIFTED pastrate).
- **ghemuit + click dreapta pe elevator SI pe banc = absolut nimic** (consumat
  silent); ambele blocuri se iau DOAR prin spargerea normala (drop item).
- **Fix ceas blocat**: butonul RIDICA/COBOARA ramanea CLOCK pana redeschideai meniul.
  JackManager are acum hook de schimbare de stare => la finalul oricarei animatii
  meniurile bancului legat se reimprospateaza singure (RIDICA/COBOARA reapare imediat).
- **Unealta luata = BARRIER in slot** („LUATA DE TINE" rosu / „OCUPATA de <nume>"),
  fara click in meniu; returnarea e exclusiv automata (zona/quit/spargere banc).
