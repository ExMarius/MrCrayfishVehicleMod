# Acces la server — ce are nevoie agentul nou

Documentul **nu conține** chei sau tokenuri. Spune unde sunt și ce trebuie refăcut.

## Ce există

| Element | Valoare |
|---|---|
| Panel | `https://freepanel.hidencloud.com` (API client: `/api/client`) |
| Server | id **`d496911d`**, nod **jacob**, 3072 MB RAM, ARM64 |
| Adresă joc | `jacob.hidencloud.com:24601` |
| SFTP | `jacob.hidencloud.com`, port **2022**, user `mariusuke-184939.d496911d` |
| Engine/OS | egg `Minecraft - Java` (33), docker `pterodactyl-yolks:java_25` |

## Cum se autentifică automatizarea

Toate workflow-urile din `.github/workflows/` care ating serverul folosesc **cheia SSH**, în
ordinea asta:

1. `secrets.HIDENCLOUD_SFTP_KEY` (recomandat) — dacă secretul există, se scrie în `/tmp/hc_key`;
2. altfel, fișierul `hidencloud/keys/arena_hc_ed25519` din repo (rezervă).

Pentru API-ul panelului (start/stop/restart), workflow-urile așteaptă `secrets.HIDENCLOUD_API_KEY`.

## PROBLEMĂ DE SECURITATE — de rezolvat înainte de a da acces altcuiva

Cheia privată **`hidencloud/keys/arena_hc_ed25519` este publicată pe branch-ul
`arena/2f9f7b9e-mrcrayfishvehiclemod`, într-un repo public (fork)**. Oricine cunoaște numele
branch-ului poate citi cheia și poate intra pe SFTP la server.

Pași, în ordinea asta:

1. **Rotește cheia** în panelul HidenCloud (SFTP/SSH keys): șterge cheia veche, generează una nouă.
2. GitHub → repo → **Settings → Secrets and variables → Actions → New repository secret**:
   - `HIDENCLOUD_SFTP_KEY` = conținutul cheii private noi (inclusiv liniile BEGIN/END);
   - `HIDENCLOUD_API_KEY` = tokenul de API al panelului (Account → API Credentials).
3. Abia după ce secretele există și un workflow de test trece: șterge fișierul din repo
   (`git rm hidencloud/keys/arena_hc_ed25519`) și, ideal, din istoric.
4. Nu pune niciodată cheia sau tokenul în chat, în issues sau în fișiere din repo.

Până la pasul 3, workflow-urile merg și cu fișierul din repo — de aceea nu l-am șters încă.

## Ce NU are agentul nou

- **Tokenul de API al panelului**: nu e în repo (a fost șters). Fără el nu poate porni/opri
  serverul; doar tu, din interfața web. Se rezolvă prin secretul de la punctul 2.
- **Acces la consola live**: nu există; doar `console.log` prin SFTP
  (workflow `panel-log.yml`, rezultatul în `horizon/_console.md`).
- **Un server Paper**: serverul actual e Horizon (Rust). Pentru pluginul Paper trebuie fie
  reinstalat jar-ul din panel (Paper 1.21.4), fie un server nou — atenție, reinstalarea îl
  transformă și Horizon nu mai pornește.
