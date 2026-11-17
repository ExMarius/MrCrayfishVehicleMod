# Prompt pentru agentul pluginului — copiază tot ce e mai jos

---

Oprește-te din lucrul la modele. Nu vreau încă un model convertit și „aproape gata". Vreau
**un vehicul pe care să intru pe server și să-l conduc**. Aici ai exact ce trebuie făcut, în
ordine, cu definiția clară a lui „gata".

## 0. Faptul care blochează tot (citește primul)

Serverul pe care intru eu (`jacob.hidencloud.com:24601`) rulează **Horizon** (engine Rust), nu
Paper. Un plugin Paper **nu poate rula** pe el. Deci, până nu existe un server Paper, orice „am
urcat pluginul" e inutil: nu ai unde să-l încarci.

Alege și spune-mi în primul răspuns care variantă:

- **A. Convertesc serverul existent** în Paper 1.21.4 din instalatorul panelului (HidenCloud →
  Automatic Universal Jar Installer → PaperMC → 1.21.4). Consecință: Horizon moare definitiv,
  dar rămân folderele și lumea. **Cere-mi confirmarea înainte**, pentru că e ireversibil.
- **B. Server nou** în panel, cu Paper 1.21.4, pe care punem pluginul. Recomandat.

Fără un server Paper funcțional nu mai raporta nimic despre plugin ca „gata".

## 1. Nu rescrie ce există

Codul pluginului **există și funcționează deja** în repo-ul
`ExMarius/MrCrayfishVehicleMod`, branch `arena/2f9f7b9e-mrcrayfishvehiclemod`, directorul
`paper-plugin/`: 65 fișiere principale + 16 teste. Are deja condus terestru/apă/zbor, benzinărie,
garaj, mecanic, magazin, remorci, persistență, sunet de motor, `ResourcePackSender`.

Sarcina ta **nu** e să rescrii asta. Sarcina ta e:

1. Copiază `paper-plugin/` în proiectul tău (pașii exacți: `docs/LISTA-FISIERE-VARIANTE-A.md`).
2. Fă-l să compileze pe GitHub Actions (Java 21, `api-version: '1.21.4'`).
3. Urci JAR-ul pe serverul Paper și îl pornești.
4. Verifici în joc.

Dacă build-ul pică, repară build-ul. Nu „simplifica" codul ca să treacă testele.

## 2. Primul vehicul: alege unul care nu cere muncă de model

Nu autobuzul (OBJ nu se poate încărca în clientul vanilla — vezi `docs/AUTOBUZ-SI-LIVRARE.md`).
Alege un vehicul care are deja model cuboid și **fără nota „All Rights Reserved"**:
`lawn_mower`, `golf_cart`, `tractor`, `off_roader`, `jet_ski` sau unul din `tools/source_assets/`
(`dune_buggy`, `atv`, `shopping_cart`, `bumper_car`, `mini_bike`, `smart_car`, `speed_boat`,
`aluminum_boat`).

Model + spec există deja pentru toate. Nu e nimic de convertit: doar să fie legate corect
`LandVehicleSpec` → `LandVehicleRig` → `LandVehicle.tick`.

## 3. Definiția lui „gata" (acceptanță, verificabilă)

Un vehicul e gata **doar** dacă toate sunt adevărate:

| # | Criteriu | Cum se verifică |
|---|---|---|
| 1 | JAR-ul e construit de CI | link către run-ul verde + `sha256` al fișierului |
| 2 | JAR-ul e pe server | `ls -l plugins/` prin SFTP, mărime identică |
| 3 | Serverul a pornit cu pluginul | linia din `console.log`: pluginul apare la `Enabling` fără excepții |
| 4 | Comanda funcționează | `/vehicle` listează vehiculele în chat |
| 5 | Vehiculul apare vizual | captură din joc în care se vede modelul, nu un minecart |
| 6 | Se conduce | W/S/A/D mișcă vehiculul; se urcă și se coboară corect |
| 7 | Supraviețuiește unui restart | după restart, vehiculul e încă acolo (persistență) |

Raportează **fiecare** rând cu dovada lui. Dacă un rând lipsește, spune „nu e gata" și spune ce
lipsește.

## 4. Cum se urcă pe server (mecanica exactă)

- SFTP: `jacob.hidencloud.com:2022`, user `mariusuke-184939.d496911d`, cheie
  `hidencloud/keys/arena_hc_ed25519` (atenție: cheia e publică în repo — nu o folosi pentru
  altceva și nu o copia în alte locuri; pentru deploy e tolerată până la rotire,
  vezi `docs/ACCES-SERVER-RO.md`).
- **`put` nu suprascrie** fișierul existent: fă `-rm plugins/xxx.jar`, apoi `put`, apoi verifică
  mărimea cu `ls -l`.
- Fișierele merg în `plugins/` din folderul serverului (la Horizon era `horizon/`; pe Paper e
  rădăcina serverului).
- Repornirea serverului o face **utilizatorul** din panel (nu ai token de API). Cere-i explicit:
  „dă Stop, apoi Start".
- Logul: nu ai consolă live. Descarcă `logs/latest.log` prin SFTP și citește-l.
- Resource pack: pack-ul construit de `tools/build_resource_pack.py` trebuie servit de
  `ResourcePackSender` și acceptat de client. Dacă modelul nu apare, verifică întâi pack-ul,
  apoi `item_model`, apoi transformările.

## 5. Ce să NU faci

1. Nu mai raporta „aproximativ", „previzualizare", „randare aproximativă". Orice imagine care nu e
   din joc se etichetează clar ca atare și nu se prezintă ca rezultat.
2. Nu lucra la autobuz, la OBJ sau la modele noi până nu e un vehicul jucabil pe server.
3. Nu spune „gata" fără dovada de la secțiunea 3.
4. Nu inventa comenzi sau configurări: citește `plugin.yml` și `config.yml` din proiect.
5. Nu atinge `horizon/` din repo-ul vechi (proiect închis).

## 6. Formatul răspunsului tău

```
SERVER: <variantă A sau B> — stare: <...>
BUILD: <link run> | sha256: <...>
URCAT: plugins/<nume>.jar, <mărime> B, verificat la <ora>
LOG: <3 linii relevante din latest.log>
ÎN JOC: <ce am verificat și cum>
GATA: <da/nu> — criterii lipsă: <...>
URMĂTORUL PAS: <o singură acțiune>
```

Începe cu secțiunea 0: spune-mi ce variantă de server alegi și ce trebuie să fac eu (dacă e ceva).
