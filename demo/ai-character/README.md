# Personaj AI pentru Minecraft — demo

Un personaj care **joacă pe serverul tău ca un jucător real**: are nume, skin, chat,
merge singur, te urmărește și comentează ce se întâmplă. Fără mod instalat la jucători.

> Eșantion funcțional, nu promisiune. Îl rulezi local în ~5 minute.

## Ce vezi în joc

- Un jucător nou (`Uke`) apare lângă tine.
- Scrii în chat `Uke vino` → vine la tine. `Uke stai` → se oprește.
- Îl întrebi ceva (`Uke ce facem azi?`) → răspunde scurt, în română, cu personalitate.
- Apare un zombi → zice o replică și fuge spre tine.
- Dai restart la server, îl repornești → își amintește ce ați făcut (`Uke memorie`).

## Instalare

Ai nevoie de **Node.js 20+** (https://nodejs.org).

```bash
cd demo/ai-character
npm install
cp .env.example .env      # Windows: copy .env.example .env
```

În `.env` completează:

| Cheie | Ce pui |
|---|---|
| `MC_HOST` / `MC_PORT` | adresa serverului tău (local: `127.0.0.1` / `25565`) |
| `MC_VERSION` | versiunea serverului (ex. `1.21.1`, `1.21.4`) |
| `BOT_NAME` | numele personajului |
| `MC_AUTH` | `offline` dacă serverul are `online-mode=false`, altfel `mojang` |
| `OWNER` | numele tău de jucător |
| `GROQ_API_KEY` | opțional — AI online gratuit (https://console.groq.com) |

Atenție: pe un server cu `online-mode=true` (auth Mojang) botul **nu poate intra**
cu un nume inventat. Pentru test, folosește un server local cu `online-mode=false`
sau pune-l pe serverul de test.

## Pornire

```bash
npm start
```

În consolă apare `[Uke] conectat pe ...`, iar în joc intră personajul.

## Comenzi în chat

| Scrii | Ce face |
|---|---|
| `Uke vino` | vine la tine |
| `Uke urmeaza-ma` | te urmărește continuu |
| `Uke stai` | se oprește pe loc |
| `Uke opreste` | rămâne pe loc fără să te mai urmărească |
| `Uke unde` | îți spune coordonatele lui |
| `Uke memorie` | îți spune ce evenimente își amintește |

Fără `GROQ_API_KEY`, răspunde cu replici locale (ca să vezi mecanismul).
Cu cheia Groq, conversația devine reală — dar **acțiunile rămân verificate de cod**:
AI-ul propune, codul execută doar comenzile permise.

## Ce urmează (după ce îți place demo-ul)

1. **Al doilea personaj** cu altă personalitate + relația dintre ei (se contrazic, cooperează).
2. **Voce**: microfon → text (Whisper) → răspuns → voce românească (Piper). Totul local, cost 0.
3. **Acțiuni mai bogate**: sapă lemn, construiește un adăpost, luptă alături de tine.
4. **Regie**: panou mic pentru tine — pauză AI, chemare personaje, obiective.

## Structura fișierelor

```
bot.js         personajul: conexiune, comenzi, urmărire, frică de monstri, memorie
package.json   dependențe (mineflayer + pathfinder)
.env.example   configurarea (se copiază în .env)
memory.json    se creează automat — memoria personajului
```

Personalitatea stă într-un singur loc, în `bot.js` (`const persona = {...}`).
Schimbi descrierea și regulile → ai alt personaj.
