# Server de test pe Google Colab

[`MrCrayfish_Vehicle_Test_Server.ipynb`](MrCrayfish_Vehicle_Test_Server.ipynb) pornește un server **Paper 1.21.4** cu MrCrayfishVehiclePlugin într-un runtime Google Colab gratuit, fără nicio gazdă proprie, publicat pe internet prin **FRP** sau **playit.gg**.

[![Deschide în Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/ExMarius/MrCrayfishVehicleMod/blob/arena/2f9f7b9e-mrcrayfishvehiclemod/colab/MrCrayfish_Vehicle_Test_Server.ipynb)

## ⚠️ Înainte de toate

- **Colab interzice prin termenii săi găzduirea de servere de jocuri.** Riscul practic: restricționarea accesului la Colab pentru contul folosit. Folosește un **cont Google secundar**.
- Runtime gratuit: maxim **~12 ore**, deconectare după **~90 min de inactivitate**, fila trebuie să rămână deschisă. Lumea se salvează în Google Drive (celula de oprire) și se restaurează automat la următoarea rulare.
- Mașinile sunt de regulă în SUA → **~120–180 ms ping** din România.
- O alternativă fără niciun risc rămâne rularea locală pe propriul PC (Java 21 + `paper.jar` + JAR-ul din release).

## Pași

1. Deschide notebook-ul în Colab (butonul de mai sus sau File → Open notebook → GitHub).
2. **Celula de configurare** — completează:
   - `PLAYER_NAMES`: numele voastre de Minecraft (primiți automat OP + whitelist).
   - Tunel:
     - **`frp`** (recomandat, avem deja experiență cu el): adresa serverului tău `frps`, portul remote, și tokenul — ideal prin **Colab Secrets** (iconița 🔑) cu numele `FRPS_TOKEN`, ca să nu stea în notebook.
     - **`playit`**: la prima rulare celula afișează un link de *claim*; îl deschizi, faci cont gratuit, revendici agentul.
3. Runtime → **Run all** (sau celulele în ordine). Prima pornire durează 3–5 minute.
4. La final, celula de pornire afișează **adresa publică** — o pui în Minecraft → Multiplayer.
5. Comenzi în joc: `/vehicle spawn go_kart`, click dreapta pe vehicul, W/S/A/D, Space = frâna de mână, Shift = coborâre.
6. **Celula 💬 Consolă** trimite comenzi către server (ex. `list`, `say Salut`).
7. **Obligatoriu înainte să închizi**: rulează ultima celulă (*Oprire + backup*) — altfel pierzi lumea de la ultimul backup.

## Cum funcționează

| Etapă | Detalii |
|---|---|
| Java 21 | instalat prin `apt` |
| Paper | ultimul build pentru versiunea aleasă, verificat prin SHA-256 |
| Plugin + pack | din release-ul GitHub ales; **SHA-1-ul pack-ului e calculat la rulare**, deci configul nu depinde de valori hardcodate |
| Config | `server.properties` reglat pentru Colab (`allow-flight=true` — obligatoriu pentru vehicule, whitelist activ, view-distance 6); `plugins/MrCrayfishVehiclePlugin/config.yml` generat automat cu URL+SHA-1 real |
| Persistență | `world*`, `plugins/MrCrayfishVehiclePlugin`, `whitelist.json`, `ops.json` ↔ `MyDrive/mcv-backup/` |
| Tunel | `frpc` cu supervisor auto-reconnect (15s), ca pe fosta gazdă Oracle; sau playit.gg fără port-forwarding |
| Securitate | `online-mode=true`, whitelist+enforce — tunelul e public, nu publica adresa |

## Actualizarea pluginului

Când apare un release nou: oprește serverul (celula 11), schimbă `RELEASE_TAG` în celula de configurare, re-rulează celulele 3 → 8.

## Probleme frecvente

Vezi tabelul de depanare din ultima celulă markdown a notebook-ului.
