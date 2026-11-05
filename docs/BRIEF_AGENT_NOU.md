# BRIEF PENTRU AGENT NOU — proiectul „MrCrayfish Vehicle Mod" (plugin Paper)

> Versiune 3.11.2026. Acest fisier traieste in repo (docs/) ca sa supravietuiasca
> reseturilor de sandbox; copiile din afara repo-ului se PIERD intre ture.

## 1. UNDE SE AFLĂ PROIECTUL

- **Repo Git:** `ExMarius/MrCrayfishVehicleMod` (GitHub), checkout local la `/home/user/MrCrayfishVehicleMod`
- **Branch de lucru OBLIGATORIU:** `arena/2f9f7b9e-mrcrayfishvehiclemod` — NU se schimbă, NU se redenumește, NU se creează alte branch-uri (Arena urmărește sesiunea după branch; alt branch = muncă pierdută). Push doar pe el. Versiunea pluginului depinde de numele branch-ului.
- **Head curent (3.11.2026):** `c2ae52e`+ (Ciclul AA = curățenie repo; AAZ/arhis = credențiale SFTP noi). Istoric complet: cicluri E→AA, toate cu CI green.
- **Backup de siguranță:** tag git `backup-2026-10-24-c9ef3e6` (+ arhiva zip menționată în istoricul sesiunii; fișierele din afara repo-ului NU persistă între ture).
- Repo-ul conține și **codul sursă original al MODULUI Forge** (`/src`) — nu-l modifica: CI extrage din `src/main/resources/assets` asseturile pentru resource pack + e referința de valori/dimensiuni.

## 2. CE ESTE PROIECTUL (o frază)

Am **rescris modul MrCrayfish's Vehicle Mod ca plugin PaperMC** ca jucătorii să se joace FĂRĂ mod instalat pe client: vehicule redate cu item displays pe server, physics proprie, economie, mecanici, garaje — totul server-side, cu resource pack trimis automat jucătorilor la join.

## 3. INFRASTRUCTURĂ / GO-LIVE

- **Server:** Minecraft Paper 1.21.4 pe **HidenCloud** (hosting romanesc, free tier — la reînnoire/recreeare se ROTESC credențialele SFTP! Precedent: ciclul AA, 3.11.2026).
- **SFTP curent (3.11.2026):** host `jacob.hidencloud.com:2022` · user `mariusuke-184939.d496911d` · parolă `X5#mK9!vP2qL` · SERVER_ID `d496911d` · panel `https://freepanel.hidencloud.com`
  - Sursa de adevăr = `env:` din `.github/workflows/hidencloud-sftp.yml` (și `hc-inspect.yml`); dacă deploy-ul moare cu „nici cheia SSH nici parola nu au mers" → userul ia credențialele NOI din panel și le actualizezi în ambele workflow-uri (SERVER_ID = sufixul după `.` din user).
  - Cheia SSH veche (`hidencloud/keys/arena_hc_ed25519`) NU e autorizată pe serverul nou — deploy-ul merge pe PAROLĂ (fallback automat în workflow). Revenire pe cheie = pereche nouă + `.pub` adăugat în panel.
- **Build CI:** workflow `paper-plugin.yml` (Gradle). **Nu există JDK local în sandbox — validarea se face EXCLUSIV prin CI green.** Nu rula `./gradlew build` local.
- **Economie:** Vault + EssentialsX (obligatorii pe server).
- **Resource pack:** generat de taskul Gradle `resourcePack` (cheamă `tools/build_resource_pack.py`), urcat ca asset pe release-ul `vehicle-plugin-prototype-v0.1.0` la fiecare deploy; `server.properties` e patch-uit automat cu hash-ul proaspăt.
- Alte workflow-uri utile: `backup-release.yml` (backup la tag `backup-release-*`), `hc-inspect.yml` (inspect server prin SFTP), `debug-compile.yml` (erori javac ca adnotări), `paper-milestone-release.yml`.

## 4. CE FUNCȚIONEAZĂ ACUM ÎN PLUGIN (feature-complete, LIVE)

Istoric complet în `docs/GUI_CRIC_original_vs_implementare.md`, audit arhitectural în `docs/AUDIT_TOTAL.md`, ghid de joc în `docs/GHID_FINAL_SISTEM.md`.

- **Vehicule:** 20+ tipuri (`vehicle/LandVehicleSpec.java`), physics server-side (`vehicle/`), randare item displays (`render/`), trailer-uri.
- **Magazin auto:** podium showcase (right-click = cumperi), catalog GUI; la cumpărare mașina intră DIRECT în garajul global; refund complet automat dacă livrarea crapă tehnic; nimic nu se pierde la inventar plin (giveOrDrop).
- **Garaje:** garaj global per jucător (`/garaj`), parcări publice cu taxă (plata strict pe livrare reușită), auto-garaj la quit, autosave 300s, hibernare anti-lag.
- **Mecanic (replica modului):** banc de lucru → cheie de mecanic (wrench, binding) + ciocan; **cric**: ridicat/coborât cu cheia PE CRIC sau din meniul bancului; coborâtă, mașina rămâne pinuită pe cric până urcă șoferul; demontare roți/motor doar ridicată; clase de repaus hardcodate (joase = pe placă 0.5, gardă mare incl. sports_car = roti pe pământ 0.0625, ridicat = 0.75 universal; `jack.offsets` = doar override).
- **Combustibil:** pompe fizice cu nozzle, jerrycan-uri normal/industrial, mixer (Blaze Juice + Ender Sap = Fuellium).
- **Cufar de transport (crate):** mașina ca item care se redeschide cu starea salvată (slotul 6 din payload = legacy, mereu false).
- **Creative GUI** pentru admini (`/vehicle creative`).

> ⚠️ **ACCES = FĂRĂ CHEI (ciclul Z — decizie fermă):** nu există item „cheie de mașină", nu există încuiere/`locked`/perm `mcv.vehicle.lock`. Accesul la volan = proprietarul + `/vehicle trust <nume>` (mai mulți prieteni; `untrust`/`trusted`), persistat. Pasagerii urcă liber. NU reintroduce sisteme de chei. „Cheia de mecanic" = unealta de service a bancului, cu totul altceva.

## 5. CUM LUCRĂM (reguli permanente stabilite cu userul)

1. **DELEGARE TOTALĂ:** userul a delegat totul — e.ditezi/commiți/pushi fără să ceri permisiunea per-pas; raportezi rezultatul + CI green. User scrie în română, răspunzi în română.
2. **FLUX STANDARD:** patch cu Python (assert anchors, unice) → verificare braces {} → `git fetch origin <branch>` preventiv înainte de commit → `git add` SELECTIV → `git diff --cached --stat` sanity (fără create-mode aberante) → commit `Ciclul <litera>: ...` → push → **CI polling (~150s + re-poll): niciodată „gata" fără SUCCESS pe build (+ deploy când atingi codul).**
3. **Git drift între ture:** indexul git se poate reseta la commitul de bază `d6fb1f7` deși working tree-ul e corect (zeci de M/D/??). Fix: `git fetch` + `git reset --mixed origin/arena/2f9f7b9e-mrcrayfishvehiclemod` (NICIODATĂ `--hard`), apoi status curat + verificare cu markeri (grep după ultimele schimbări).
4. **CI subtletăți:** commits DOAR-docs nu declanșează workflow (path filter) — normal; timeout polling ≠ eșec (re-pollează); `cancelled` = suprascris de concurrency.
5. **Workflow-urile se validează local ÎNAINTE de push:** parse YAML (pyyaml) + `bash -n` pe fiecare bloc `run:` (lecția AA.4/AA.5: o continuare de linie dublu-escapată a omorât deploy-ul cu exit 2 fără urmă în log). Logurile joburilor NU se pot descărca din sandbox** (Azure blob storage off-allowlist). Erorile se citesc prin annotations API: `gh api repos/ExMarius/MrCrayfishVehicleMod/check-runs/<jobId>/annotations`. `gh run rerun` poate eșua cu „workflow file may be broken" (default-branch-ul are alt set de workflow-uri vechi Oracle/Falix — nu le atinge) → redeploy corect = push pe path-urile filtrate.
6. **Nu dezvălui credențiale în chat** dincolo de ce a dat userul deja (parola SFTP e deja plaintext în workflow — starea actuală acceptată de user).
7. **Documente de ghidaj:** `docs/AUDIT_TOTAL.md` (verdict/subsistem + nice-to-have rămas: seeder/fertilizer funcționalitate, props cu preț, announce lobby la restart, teste unitare în CI), `docs/GUI_CRIC...` (changelog E→AA), `docs/GHID_FINAL_SISTEM.md` (ghid jucători), `paper-plugin/DEPLOY-LOG.md` (lecții deploy).

## 6. STAREA MOMENTULUI (3 Nov 2026)

- **Deploy reparat și VERDE (AA.5)** — auth prin cheie pe serverul nou (cheia e per-cont HidenCloud!), upload OK. Atenție: serverul e PROASPĂT recreat → `server.properties` lipsea la deploy → pack-hash nesincronizat încă; după ce userul pornește serverul prima dată, orice redeploy sincronizează hash-ul.
- Ciclul AA proaspăt: curățenie repo (șterse Untitled.zip, kenney_car-kit.zip, probe-result.txt, oracle-panel/, oracle-vm/; rămân în history) + reparații post-Z (mesaj untrust fără cheie, ghid elevator/trust realist, README build corect) + **rotație credențiale SFTP** (nile.a4e04808 → jacob.d496911d).
- Nicio lucrare deschisă; sistemul e producție-ready.
- Nice-to-have (doar la cerere): seeder/fertilizer, props economic, announce restart, teste în CI, cheie SSH nouă autorizată în panel.

## 7. TON

Userul e direct, română, apreciază structură + acțiune; testează LIVE pe server și raportează ce vede. Nu întreba „vrei să fac X?" dacă X e clar cerut — fă și raportează.
