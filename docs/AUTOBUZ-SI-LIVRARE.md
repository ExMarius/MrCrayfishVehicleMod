# Autobuzul (model OBJ) și livrarea pe server

Două subiecte, amândouă blocante. Amândouă au răspuns scurt.

---

## 1. OBJ → client vanilla: de ce nu se poate „converte" direct

Un client Minecraft vanilla (inclusiv cu resource pack) știe **un singur fel de geometrie**:

- cuburi **aliniate la axe** (`from` / `to`), fiecare cu maxim **o rotație pe un singur ax**
  (`rotation: {origin, axis, angle}`), cu UV per față.

Un OBJ are triunghiuri/quad-uri arbitrare, în orice orientare. Nu există nicio cale prin care
clientul vanilla să deseneze un triunghi liber: nu e o limitare a pluginului, a Paper-ului sau a
`ItemDisplay`-ului, ci a formatului de model. (De asta mergea în Horizon, care își randează singur
geometria — și de ce acel proiect cerea client Horizon la fiecare jucător.)

### Opțiuni reale

| Variantă | Ce înseamnă | Rezultat |
|---|---|---|
| **A. Rebuild în Blockbench** | autobuzul e reconstruit din cuburi (30–80 de elemente), folosind OBJ-ul doar ca referință pentru proporții și textură | formă fidelă „din cuburi", performant, **recomandat** |
| **B. Voxelizare automată** | script care eșantionează suprafața OBJ în cuburi mici (0.05–0.1 bloc) | formă apropiată, dar **mii de elemente** → pack greu, FPS scăzut, fiecare cub are UV-uri aproximative |
| **C. Renunțăm la autobuz** | folosim un vehicul deja cuboid din mod (14 fără nota ARR) | zero muncă de artă |

Ce **nu** funcționează: „import OBJ în Blockbench și export model Java" — Blockbench importă OBJ ca
**mesh**, iar exportul Java Block/Item scrie doar cuburi; mesh-ul se pierde sau se voxelizează.

### Dacă merge pe A, ce trebuie păstrat

1. Proporțiile din OBJ (lungime/lățime/înălțime în blocuri).
2. Textura: UV-urile trebuie să cadă pe aceleași zone din PNG; se face în Blockbench, pe textura
   existentă.
3. Punctele de montare: 6 roți + 13 locuri + șofer se definesc în `LandVehicleSpec` (sau un spec
   nou pentru autobuz), în coordonate de model, nu în OBJ.

---

## 2. Livrarea: cheia SSH publicată

Poziția agentului nou e corectă: o cheie privată publicată se consideră compromisă. Dar dacă nu o
folosim, **nu mai există cale automată de urcat build-ul pe server**. Trei variante:

| Variantă | Cine urcă | Cost | Risc |
|---|---|---|---|
| **1. Rotire + secret** | workflow-ul din Actions, cu `HIDENCLOUD_SFTP_KEY` | 3 minute o dată | zero |
| **2. Cheia veche** | workflow-ul din Actions, cu fișierul din repo | zero | oricine are cheia intră pe server |
| **3. Manual** | utilizatorul, cu File Manager din panel | la fiecare build | zero, dar muncă manuală repetată |

Recomandare: **1**. „Fără secrets" era ca să nu fie fricțiune, dar fricțiunea reală e acum mai
mare: fără o cheie nouă nu se poate livra automat, iar cea veche e publică.

Pași pentru 1:
1. Panel → SFTP/SSH keys → șterge cheia veche, generează una nouă, o atașează serverului.
2. GitHub → repo → Settings → Secrets and variables → Actions → `HIDENCLOUD_SFTP_KEY` = cheia nouă.
3. Workflow-urile sunt deja pregătite: citesc secretul întâi, fișierul doar ca rezervă
   (vezi `docs/ACCES-SERVER-RO.md`).
4. După ce un deploy de test trece: se șterge `hidencloud/keys/arena_hc_ed25519` din repo.
