# 🧪 Plugins Test — server de test pe Google Colab

[`Plugins_Test.ipynb`](Plugins_Test.ipynb) pornește un server **Paper 1.21.4** cu MrCrayfishVehiclePlugin **plus panoul web de control din `oracle-panel/`** într-un runtime Google Colab gratuit, publicat prin tunelul **FRP** (serverul tău `frps`): jocul pe portul `25565`, panoul pe `8081`.

[![Deschide în Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/ExMarius/MrCrayfishVehicleMod/blob/arena/2f9f7b9e-mrcrayfishvehiclemod/colab/Plugins_Test.ipynb)

## ⚠️ Înainte de toate

- **Colab interzice prin ToS servere de jocuri** → folosește un **cont Google secundar**.
- Runtime gratuit: max **~12h**, deconectare după **~90 min de inactivitate**; fila rămâne deschisă. Lumea, datele pluginului și parola panoului se păstrează în Google Drive (`MyDrive/plugins-test-backup/`).
- Mașinile Colab sunt de regulă în SUA → **~120–180 ms ping** din România.
- O alternativă fără riscuri: server local pe propriul PC (Java 21 + `server.jar` aici descărcabile prin celulele notebook-ului).

## Configurare inițială (o singură dată)

Notebook-ul **nu conține niciun secret**. Creează în Colab → iconița **🔑 Secrets** (barra stângă):

| Secret | Valoare |
|---|---|
| `FRPS_ADDR` | adresa IP/domeniu a serverului tău `frps` |
| `FRPS_TOKEN` | tokenul din configul `frps` |
| `FRPS_PORT` | (opțional, implicit `7000`) portul `bindPort` al `frps` |

Pe `frps` trebuie libere porturile remote **25565** (joc) și **8081** (panou) — aceleași ca în configul FRP folosit anterior.

## Pașii de fiecare dată

> ⚠️ **17 oct. 2026:** PaperMC a retras definitiv API-ul v2 (HTTP 410). Dacă ai o copie a notebook-ului în Drive dinainte de această dată, șterge-o și reia versiunea curentă (descărcarea Paper folosește acum API-ul v3).

1. Deschide notebook-ul în Colab (butonul de mai sus).
2. Celula de configurare: `PLAYER_NAMES` (numele voastre de Minecraft — primesc OP + whitelist automat).
3. **Runtime → Run all**. Prima pornire: 4–6 minute.
4. Celula de pornire afișează:
   - 🟢 **Minecraft**: `FRPS_ADDR:25565` → Direct Connect în joc
   - 🟢 **Panou web**: `http://FRPS_ADDR:8081` — utilizator `admin`, parola din celula 🔑 (**implicit `12345`**, o schimbi după login din panou)
5. Consola: din **panoul web** sau din celula 💬 a notebook-ului.
6. În joc: `/vehicle spawn go_kart`, click dreapta, W/S/A/D, Space = frâna, Shift = coborâre.
7. **La final, obligatoriu**: celula 12 (⏹️ oprire grațioasă + backup în Drive).

## Cum funcționează

| Etapă | Detalii |
|---|---|
| Dependințe | Java 21 + `screen` prin `apt`. Panoul e stdlib-only (fără pip/Flask) |
| Infrastructură | `oracle-panel/` din repo se instalează în `/content/mcserver` (`bin/`, `panel/`) — aceleași scripturi ca pe fosta gazdă Oracle |
| Paper | ultimul build pentru versiunea aleasă, verificat SHA-256 |
| Plugin + pack | din release-ul ales; SHA-1-ul pack-ului e **calculat la rulare**, configul e generat din el |
| FRP | `frpc` v0.60.0, `frpc.yaml` cu proxy-urile `minecraft` (25565) + `webpanel` (8081), supervisor cu reconectare la 15s |
| Persistență | `world*`, datele pluginului, whitelist/ops, `panel/auth.json` ↔ Google Drive |
| Securitate | `online-mode=true`, whitelist forțat, `allow-flight=true` (obligatoriu la vehicule) |

## Actualizarea pluginului

Release nou → celula 12 (oprire) → schimbi `RELEASE_TAG` în celula de configurare → re-rulezi celulele 4, 7 și 9.

## Depanare + resetare parolă panou

Tabelul de depanare e în ultima celulă a notebook-ului. Parolă panou pierdută → re-rulează celula 🔑 (resetează la valoarea din câmp, implicit `12345`).

> 🔐 Dacă tokenul FRP a fost vreodată partajat (ex. într-un link Colab public), **rotește-l pe `frps` și actualizează Secretul** — oricine îl are poate înregistra proxy-uri pe același server.
