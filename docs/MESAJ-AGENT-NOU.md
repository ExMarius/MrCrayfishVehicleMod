# Mesaj gata de lipit în sesiunea noului agent

> Copiază tot ce e între liniile de mai jos.

---

Proiect: **portul modului MrCrayfish's Vehicle Mod pe Horizon**, plus un plugin Paper nou
pentru vehiculele proprii. Vreau să continui de unde s-a oprit.

## Ce există deja (nu reconstrui)

**Repo `ExMarius/MrCrayfishVehicleMod`, branch `arena/2f9f7b9e-mrcrayfishvehiclemod`,
ultimul commit `6ff2f1d`.** Acolo sunt:

- sursa modului original Java: `src/` (313 clase) — **nu modifica**;
- portul Horizon: `horizon/`
  - `horizon/mod/content/` — partea `.hmod` (156 modele, 143 texturi, 174 chei de text,
    sunete, 7 rețete, 18 tipuri de entitate `vehicle:*`);
  - `horizon/engine-mod/mixins/vehicle/` — partea nativă Rust (`.hnmod`, v0.4.0):
    randare (`native_mob_submit`), fizică portată din `LandVehicleEntity.updateVehicleMotion()`,
    condus, comenzi `/car` și `/masina`;
  - `horizon/tools/port_mod_content.py` — importă conținutul din `src/` în `.hmod`;
  - `horizon/tools/pack_hnmod.py` — împachetează `.hnmod`;
  - `horizon/tools/parity.py` + `horizon/mod/parity.json` — harta de paritate cu cele 313 clase;
  - **`horizon/PREDARE-RO.md`** — predarea completă: ce e gata, ce lipsește, cum se construiește,
    cum se urcă pe server, ce trebuie făcut manual. **Citește-l primul.**
  - `horizon/mod/PORT-RO.md` — planul pe etape; `horizon/mod/PARITY.md` — starea pe clase.

**Livrabile publicate** (release `horizon-mod` în același repo):
`vehicle.hmod` (1.726.941 B), `vehicle.hnmod` (nume fix, 23.163 B), `instaleaza-modul.bat`.

**Server:** panel HidenCloud, id `d496911d`, nod `jacob`, ARM64, `jacob.hidencloud.com:24601`.
Pe server: `horizon/horizon-engine-mod` (binar ARM64), `horizon/horizon-mods/vehicle.hmod`,
`horizon/horizon-mods.txt` = `mod.vehicle:true`. Urcarea se face **doar** prin GitHub Actions
(`.github/workflows/horizon-engine-push.yml`, `horizon-mod-push.yml`, `engine-check.yml`,
`panel-log.yml`), declanșate de fișiere `.github/*.trigger`.

**Al doilea repo** `ExMarius/HorizonMods`, branch `arena/fb5f91ff-horizonmods`: acolo e proiectul
Rust „pe bară" (`dist/gokart_native.hnmod`, `dist/gokart-content.hmod`, `update-windows.bat`),
`docs/paper-transfer-plan-ro.md` și scheletul de plugin Paper `paper-vehicles/`.

## Ce vreau mai departe

1. **Horizon**: continuă portul unu-la-unu, cu `horizon/mod/PARITY.md` ca listă de lucru.
   Următoarele firești: proprietăți per vehicul (fișierele `VehicleProperties` lipsesc din repo —
   trebuie găsite sau refăcute), itemele (27), blocurile (16), combustibil/motoare.
2. **Plugin Paper nou** (director separat, fără resursele MrCrayfish): doar vehiculele noastre,
   cu modele pentru care avem licență clară. Compilarea se face pe GitHub Actions (Java 21 +
   Paper API), nu local.

## Reguli

- Nu copia modele/texturi/sunete din `horizon/mod/content/assets/vehicle/` într-un plugin
  public: sunt ale modului original (`MOD-LICENSE.txt`).
- Nu modifica `src/` (sursa modului original).
- Livrarea pe server se face prin workflow-urile existente, nu prin SFTP manual.
- Pașii care rămân la om: `vehicle.hnmod` în `horizon-native-mods/`, butonul **Build** din
  launcher, **Stop → Start** în panel. Restul trebuie automatizat.
