# Predare: portul Horizon (starea proiectului)

Document pentru cine preia proiectul (om sau agent). Toate datele de mai jos sunt
verificate, nu presupuse.

- **Repo:** `ExMarius/MrCrayfishVehicleMod`
- **Branch de lucru:** `arena/2f9f7b9e-mrcrayfishvehiclemod`
- **Ultimul commit:** `6ff2f1d` — „instalator Windows: pune vehicle.hnmod in folderul jocului"
- **Ce e în repo:** sursa modului original Java (`src/`, ~313 clase) **și** portul Horizon
  (`horizon/`). Modul original nu a fost modificat.

---

## 1. Ce s-a livrat

### Modul de conținut (`.hmod`) — date

| | |
|---|---|
| Sursă | `horizon/mod/content/` |
| Build | `.github/workflows/horizon-mod-build.yml` (`cargo horizon-mod build`, uneltele oficiale Horizon) |
| Livrare | release **`horizon-mod`**, asset **`vehicle.hmod`** — 1.726.941 B |
| Conținut | 156 modele, 143 texturi, 174 chei de text, sunete, 7 rețete, **18 tipuri de entitate** |
| Entități | `vehicle:go_kart`, `sports_car`, `quad_bike`, `moped`, `dirt_bike`, `mini_bus`, `tractor`, `off_roader`, `golf_cart`, `lawn_mower`, `jet_ski`, `sports_plane`, `compact_helicopter`, `vehicle_trailer`, `storage_trailer`, `fluid_trailer`, `seeder`, `fertilizer` |

Generatorul de conținut: `horizon/tools/port_mod_content.py` — citește sursa Java, copiază
modelele (convertind UV-urile din scara texturii în scara modelelor Horizon), texturile,
textele și rețetele, și scrie câte un `entity_type` per vehicul. Se rulează după orice
schimbare în `src/`.

### Modul nativ (`.hnmod`) — cod Rust

| | |
|---|---|
| Sursă | `horizon/engine-mod/mixins/vehicle/` |
| Manifest | `horizon-mixin.toml` (v0.4.0, id `vehicle`, hooks 472, Horizon `02fd158…`, mixins `e8cd0aa…`) |
| Build | `.github/workflows/horizon-engine-mod.yml` (ARM64, `native.py build`) |
| Împachetare client | `horizon/tools/pack_hnmod.py` (formatul oficial `.hnmod`) |
| Livrare | release **`horizon-mod`**, asset **`vehicle.hnmod`** — 23.163 B |

Ce face acum codul:

- **Randare** (`native_standalone_models` + `native_mob_submit`): fiecare vehicul e desenat cu
  modelul lui, centrat pe entitate. Tabelul (entitate → model → offset) e generat de
  `port_mod_content.py` în `runtime.rs`, între marcajele `BEGIN/END GENERATED VEHICLES`.
- **Fizică** (`pub mod land` în `runtime.rs`): port rând-cu-rând al metodei
  `LandVehicleEntity.updateVehicleMotion()` — accelerație, drag, frecare, frână de mână,
  limita de viteză (`CommonUtils.clampSpeed`), modelul roților față/spate, `lerp` pe tracțiune
  și corecția de yaw (`yRot -= wrapDegrees(...)`).
- **Condus**: `/car [nume]` pune un schelet invizibil (minecart, pentru scaun) + entitatea
  modului; te așază la volan cu `native_mount`; la fiecare tick teleportează scheletul
  (`native_teleport_vehicle`, duce pasagerii) și mută entitatea-model. Ghemuit = cobori.
- **`/masina [nume]`**: pune vehiculul ca entitate, fără condus (pentru inspectat modele).

Helper-ele de hook (câte un fișier subțire per punct de intrare):
`tick.rs`, `input.rs`, `register.rs`, `perform.rs`, `models.rs`, `render.rs`.

### Serverul (panel HidenCloud)

| | |
|---|---|
| Server | id `d496911d`, nod **jacob**, **ARM64**, 3072 MB RAM |
| Adresă | `jacob.hidencloud.com:24601` |
| SFTP | `jacob.hidencloud.com:2022`, user `mariusuke-184939.d496911d`, cheie în `hidencloud/keys/arena_hc_ed25519` |
| Engine pe server | `horizon/horizon-engine-mod` (85 MB, ARM64) |
| Mod pe server | `horizon/horizon-mods/vehicle.hmod` (1.726.941 B) + `horizon/horizon-mods.txt` = `mod.vehicle:true` |
| Verificare | `.github/workflows/engine-check.yml` compară sha256 release ↔ panel și caută textele modului în binar |

Pipeline de urcat pe server (toate prin GitHub Actions, din `.github/*.trigger`):

| Workflow | Ce face |
|---|---|
| `horizon-engine-mod.yml` | compilează modul nativ ARM64, publică binarul + `.hnmod` |
| `horizon-engine-push.yml` | urcă binarul pe panel **forțat** (`-rm`, `put`, `chmod 755`, verificare de mărime) |
| `horizon-mod-push.yml` | urcă `vehicle.hmod` + `horizon-mods.txt` |
| `engine-check.yml` | compară release vs panel (sha256 + `strings`) |
| `panel-log.yml` | aduce `horizon/console.log` în `horizon/_console.md` |
| `horizon-mod-build.yml` | construiește `.hmod` cu `cargo horizon-mod` |
| `publish-hnmod.yml` | publică `.hnmod` sub nume fix `vehicle.hnmod` |
| `publish-installer.yml` | publică instalatorul Windows |

Trigger-ele sunt fișiere `.github/*.trigger` care primesc un timestamp (conținutul nu contează).

Instalator Windows: `horizon/tools/windows/instaleaza-modul.bat` — caută folderul jocului
(cel cu `horizon-mods`), creează `horizon-native-mods`, descarcă `vehicle.hnmod` acolo.

### Harta de paritate

`horizon/tools/parity.py` + `horizon/mod/parity.json` → generează `horizon/mod/PARITY.md` cu
starea fiecăreia dintre cele **313 clase Java** (`ported` / `partial` / `render` / `data` / `todo`).
La predare: 3 `ported`, 2 `partial`, 56 `render`, 2 `data`, 250 `todo`.

---

## 2. Ce NU e făcut (și de ce)

1. **Condusul e pe un schelet de minecart**, nu pe entitatea modului. Motiv tehnic:
   `native_teleport_vehicle` mută vehiculul cu pasagerii, dar entitățile-mod au hitbox de
   negustor (1,95 înălțime) — dacă jucătorul se așază direct pe ele, stă prea sus.
2. **Proprietățile per vehicul lipsesc**: `VehicleProperties` / `PoweredProperties` se încarcă din
   fișiere JSON care **nu sunt în acest repo**. De aceea fizica folosește valorile implicite
   (`ENGINE_POWER 15`, `BRAKE_POWER -1`, `MAX_STEERING_ANGLE 35°`, `MAX_REVERSE_SPEED 5`).
3. **Itemele (27) și blocurile (16)** nu sunt încă declarate. Se pot face în două feluri: date
   (`.hmod`, `data/vehicle/horizon/item|block/…`) sau cod (`native_items`, `native_blocks`).
4. **Motoare/combustibil, damage, cuplare remorcă, ecranul de editare/vopsire** — neîncepute.
5. **sofacopter** lipsește: e din alt mod (`cfm`) și nu are model aici.

---

## 3. Ce trebuie făcut manual (nu se poate automatiza)

Pe PC-ul jucătorului, la fiecare versiune nouă de mod nativ:

1. `vehicle.hnmod` în `<folderul jocului>/horizon-native-mods/` (instalatorul `.bat` face asta).
2. Launcher → Mods → **Build** (compilează local, pentru platforma jucătorului).
3. Panel → **Stop → Start** (ca serverul să încarce `.hmod`-ul nou).

`vehicle.hmod` **nu** trebuie recopiat decât dacă se schimbă conținutul (modele/texturi/entități).

---

## 4. Drepturi de autor — de citit înainte de orice mutare

Modelele și texturile din `horizon/mod/content/assets/vehicle/` provin **din sursa modului
MrCrayfish** (`src/main/resources/assets/vehicle/`), la fel ca sunetele. Licența modului este în
`MOD-LICENSE.txt` (rădăcina repo-ului). Concluzii practice:

- Portul de față e o **adaptare a modului original**, cu autorul creditat; nu e „assets noi".
- Un **plugin Paper nou** care vrea să evite resursele originale **nu are voie** să copieze
  modelele/texturile/sunetele din `horizon/mod/content/assets/vehicle/`.
- Pentru un vehicul propriu (ex. autobuzul cu licență MIT) se pot folosi doar resursele lui.

---

## 5. Cum se continuă

1. Citește `horizon/mod/PORT-RO.md` (planul pe etape) și `horizon/mod/PARITY.md` (ce e portat).
2. Următorul pas firesc: închide `todo`-urile din pachetul `entity/` (proprietăți per vehicul,
   combustibil, motoare), apoi itemele și blocurile.
3. După orice modificare în `src/` rulează `python3 horizon/tools/port_mod_content.py`, apoi
   `python3 horizon/tools/parity.py`.
4. Pentru livrare: bump de versiune în `horizon/engine-mod/mixins/vehicle/horizon-mixin.toml`,
   apoi trigger pe `horizon-engine-mod.yml` (publică `.hnmod` + binarul) și `publish-hnmod.yml`.
