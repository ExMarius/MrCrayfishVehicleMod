# BRIEF PENTRU AGENT NOU — proiectul „MrCrayfish Vehicle Mod" (plugin Paper)

> Versiune 17.11.2026 (ciclul BL27). Acest fisier traieste in repo (docs/) ca sa supravietuiasca
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

## 6. STAREA MOMENTULUI (17 Nov 2026, ciclul BL27)

- **Tot ce exista in §4 functioneaza si este LIVE.** Deploy + build verzi pe `5359504`.
- **Singura lucrare deschisa:** comportamentul scaunului/camerei (vezi §9). Nimic rupt: camera e libera (vanilla), montura + corpul se rotesc cu masina.
- BL27 a scos complet mecanismul de camera din server (vezi tabelul din §9) — daca un agent viitor vrea sa-l reintroduca, citeste intai deconectarile dovedite.

## 7. TON

Userul e direct, romana, apreciaza structura + actiune; testeaza LIVE pe server si raporteaza ce vede. Nu intreba „vrei sa fac X?" daca X e clar cerut — fa si raporteaza. Cand ceva e imposibil tehnic, spune-i scurt de ce + ce alternative reale exista (funcționeaza mai bine decat inca o incercare oarba).

## 8. CONECTARE / ACCES (pentru agentul urmator)

**Agentul NU se conecteaza direct la server**: sandbox-ul are allowlist doar pe `github.com`, `codeload.github.com`, `api.github.com`, `registry.npmjs.org`, `pypi.org`, `files.pythonhosted.org`. `jacob.hidencloud.com` nu este accesibil. Totul trece prin GitHub Actions:

| Ce vrei | Cum |
|---|---|
| Deploy (plugin + resource pack + server.properties) | push care atinge `paper-plugin/**`, `tools/**`, `hidencloud/**` sau workflow-ul de deploy → ruleaza `HidenCloud deploy` |
| Log-ul serverului | `date -u +"dump %Y-%m-%dT%H:%M:%SZ" > .github/logdump-request` + commit + push → workflow `Log dump` publica `diagnostics/last-log-excerpt.txt` (citibil cu `git show origin/<branch>:diagnostics/last-log-excerpt.txt`) |
| Inspectie SFTP ad-hoc | workflow manual `hc-inspect.yml` |
| Verificare CI | `gh run list --limit 2`, `gh run watch <id> --exit-status`; erorile: `gh api repos/ExMarius/MrCrayfishVehicleMod/check-runs/<jobId>/annotations` (logurile de job nu se descarca din sandbox) |

Credentiale SFTP (sursa de adevar: `env:` din `.github/workflows/hidencloud-sftp.yml` si `logdump.yml`):

- host `jacob.hidencloud.com`, port `2022`, user `mariusuke-184939.d496911d`, SERVER_ID `d496911d`, panel `https://freepanel.hidencloud.com`
- parola: `SFTP_PASS_BACKUP` din cele doua workflow-uri (plaintext, acceptat explicit de user)
- cheia `hidencloud/keys/arena_hc_ed25519` NU e autorizata pe serverul nou → deploy-ul merge pe parola (fallback automat in workflow)
- la recrearea serverului in panel credentele se ROTESC → userul le da din panel, se actualizeaza in AMBELE workflow-uri

## 9. SAGA SCAUN + CAMERA (BL7 → BL27) — CE E DOVEDIT

Cerinta userului, in cuvintele lui: scaunul sa fie **calul** („imi place cea de cal"), corpul **fix pe masina**, rotit odata cu ea („sa fie calul una cu masina"), iar **mouse-ul sa nu-l poata roti**, nici cand sta pe loc si masina vireaza. In final a clarificat: „tot ce m-ar interesa e ca HUD-ul [vederea/camera] sa se tina dupa masina cand virez; pozitia nu ma intereseaza".

### Ce functioneaza (LIVE)
- Scaun = entitate-montura (`cal` / `porc` / `armorstand`; implicit `armorstand` din BL22, comutabil cu `/vehicle seatkind <porc|cal|armorstand>`). Armor stand-ul e pasiv, deci clientul nu il roteste dupa camera asa cum face cu calul.
- Montura e rotita de **server** pe yaw-ul vehiculului in fiecare tick (`/vehicle seatyaw <anchor|mount|both|off>`, implicit `mount`) ⇒ corpul se roteste o data cu masina la cerculete.
- Urcarea functioneaza (BL20c: `teleport` pe pasager il da jos).

### Ce e IMPOSIBIL din server — fiecare incercare a produs deconectare sau zero efect
| Metoda | Rezultat |
|---|---|
| `Player#teleport` / pachet de pozitionare | pasager dat jos; trimis ca pachet brut: **`Invalid move player packet received`** (deconectare) |
| `ClientboundPlayerLookAtPacket` (reflexie, BL21) | zero efect pe client |
| camera legata de o entitate auxiliara (`ClientboundSetCameraPacket`, BL26) | **`Cannot interact with self!`** (deconectare) |
| `Player#lookAt(Location/double,double,double)` | nu exista in API-ul Paper 1.21.4 (nu compileaza) |

Concluzie: **clientul vanilla refuza orice control al camerei cat timp jucatorul e pasager**. Modul original poate face asta pentru ca ruleaza pe client (modul Framework + Vehicle sunt cod client; repo-ul Framework nu contine logica de scaune — verificat integral).

### Alternative reale, in ordinea propusa userului
1. **A (live acum):** camera libera, corpul/montura rotite cu masina. Zero risc.
2. **B — „sofer fara calarie":** jucatorul nu mai e pasager, este tinut pe masina prin pozitionare din server; atunci clientul ACCEPTA pachetele si camera se poate bloca pe yaw-ul masinii (pitch liber). Cost: nu mai sta asezat, micro-tremur, rescrierea detectarii soferului (`LandVehicle.driver()` citeste pasagerii scaunului), 1-2h + risc.
3. **C — client mod pentru 1.21.4** (ca originalul): singura varianta 1:1, proiect separat mare.
4. **D — indicator de viraj in HUD** (action bar / font propriu) care se misca cu volanul: fezabil server-side, rapid.

### Capcane dovedite (nu le reincerca)
- `Player#teleport` pe un pasager il da jos de pe scaun („nu merge sa urc").
- Pachete de pozitionare/rotatie pe un pasager = deconectare imediata.
- `ClientboundSetCameraPacket` spre o entitate auxiliara in timpul calariei = deconectare.
- `setCollidable` nu exista pe Boat; `EntityType.BOAT`→`OAK_BOAT`; `World#hasCollisionsIn()`; `setInvisible` pe `OAK_BOAT`; `hideEntity` pe entitatea calarita.
- Build-ul „fresh server" poate pica pe timeout de infrastructura (`cycle 1 did not become ready within 240 seconds`) chiar cu cod bun — verifica anotarile inainte sa consideri regresie.

### Comenzi de test inca prezente (se sterg dupa validarea finala)
`/vehicle seatkind`, `/vehicle seatyaw`, `/vehicle seatlook`, `/vehicle seatlift`, `/vehicle seattest`. Dupa validare: hardcode `seatKind`/`seatYawMode`, sterge comenzile.

### Backlog user (nepornit)
„sloturi" (nedetaliat), vitezometru HUD (font propriu, langa inimile de cal, in stanga — formele agreate), indicator combustibil. Bara XP = 100% vanilla, fara sidebar/scoreboard.

## 7. TON

Userul e direct, română, apreciază structură + acțiune; testează LIVE pe server și raportează ce vede. Nu întreba „vrei să fac X?" dacă X e clar cerut — fă și raportează.

## 10. ACTUALIZARE SCAUN/CAMERA (BL27 → BL48, 17-18 nov 2026)

**Dovada masurata (/vehicle seatdebug, log real de pe server):**
```
vehicleYaw=-61.6 | anchor=-61.6 | mount=HORSE yaw=-61.6 pitch=0.0 pass=1 riderYaw=139.0 riding=true
vehicleYaw= 39.1 | anchor= 39.1 | mount=HORSE yaw= 39.1 pitch=0.0 pass=1 riderYaw=139.0 riding=true
vehicleYaw=140.4 | anchor=140.4 | mount=HORSE yaw=140.4 pitch=0.0 pass=1 riderYaw=139.0 riding=true
```
Interpretare: **serverul face totul corect** (ancora si calul urmeaza yaw-ul masinii la fiecare tick). Singura veriga care nu se roteste este `riderYaw` = propriul jucator, pentru ca **clientul isi deseneaza propriul corp dupa camera** si refuza orice pachet de pozitionare in timp ce este pasager.

**Cauze dovedite in aceasta serie:**
- scaunul PASAGER al ancorei primeste rotatia parintelui in fiecare tick → `setRotation` pe scaun era anulat (BL46: scaunul a devenit entitate libera, mutata/rotita de server);
- calul CU sa = controlling passenger → clientul ii impune rotatia dupa camera (BL41: cal fara sa);
- barca/cal fortate cu teleport+viteza per tick = tremurat (BL45: fara corectii manuale);
- `EntityType.BOAT` nu exista in 1.21.4 (BL32: `EntityType.OAK_BOAT`);
- `Boat` nu are `setCollidable`/`setRemoveWhenFarAway`, are un singur loc de pasager (BL31);
- `Entity#setPose` nu exista in Paper 1.21.4 (BL39a).

**Stare live (BL48a, `a609579`):** scaun = cal vizibil (marime 1.0), fara sa, fara corectii manuale; ancora scaunului primeste yaw-ul masinii. `seatRide=false` dezactivat (acum pasager).

**BL48 (in test):** `/vehicle seatlock <on|off>` trimite pachetul de pozitionare ABSOLUT (pozitia curenta + yaw-ul masinii). Auto-protectie: daca jucatorul se deconecteaza in 10s dupa pachet, `noteQuit()` opreste automat feature-ul (`lookLockAutoOff`). Daca merge → corpul si camera se rotesc cu masina fara mod de client; daca nu → ramane modul de client.

**Mod de client (gata, optional):** `client-mod/` (Fabric 1.21.4, mixin pe `LocalPlayer#tick` prin reflexie, fara dependente de nume mapate). Jar publicat in release: `mcv-seatlock-1.0.0.jar`. CI: `.github/workflows/client-mod.yml` (build cu Mojang official mappings; **yarn 1.21.4 producea un jar fara clasele client** - nu reveni la yarn).

**Comenzi de test prezente:** `seatkind`, `seatyaw`, `seatlook`, `seatride`, `seatdebug`, `seatlock`, `inputtest`, `seattest`, `seatlift`.

## 11. Ciclul BL51-BL60 (test A/B pe serverul real, 2026-11-10)

**Comanda de test:** `/vehicle variants` spawneaza 4 masini cu variante de scaun
diferite, fiecare cu setari proprii (per-vehicul, nu globale):
A = cal fara sa, F = cal + rotatie adaugata la privire, G = barca fixata,
H = barca cu scaun interior (barca 100% server-side).

**Rezultatul testelor (user):** A/B/C/D/G/H = "nu e bun"; E/F = "aproape, dar
trepidaie"; dupa netezire (BL56/BL57) tot "niciunul nu e bun".

**Ce s-a dovedit, cu log:**
1. Calul/barca se rotesc perfect cu masina (`vehicleYaw` == `mount yaw`), dar
   `riderYaw` ramane constant => clientul vanilla isi roteste corpul dupa
   CAMERA, nu dupa vehicul. Rotirea scaunului nu poate rezolva asta.
2. Trepidatia la variantele care forteaza camera vine din
   `ClientboundPlayerPositionPacket`: (a) pozitia absoluta e cu un tick in urma
   => corpul e tras inapoi; (b) salturile de yaw => mers in trepte. Fix-uri
   aplicate: pozitie relativa (delta 0) + rotatie graduala (BL55/BL56).
3. `moved too quickly` din log (anti-cheat-ul de miscare) explica tremuratul
   la varianta "tinut pe scaun" (D).

**Concluzie de produs:** "corp fix + cap liber" (ca in mod) NU se poate obtine
server-side cu client vanilla. Singura cale completa ramane modul de client
(`client-mod/`, publicat in release), care e optional pentru jucatori.

**Starea implicita a serverului:** cal fara sa, pasager, fara fortarea camerei
(`lookLock=false`), fara variante aplicate. Stabil.
