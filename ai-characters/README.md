# AI Character — personaje care joacă pe serverul tău

Personaje AI pentru o serie YouTube: intră pe server **ca jucători reali** (nume, skin,
chat, mers natural), vorbesc românește cu personalitate constantă, te urmează, reacționează
la pericole și **își amintesc** ce s-a întâmplat, inclusiv după restart.

Nu e mod: nu trebuie să instaleze nimeni nimic. Personajele sunt boți care se conectează
la serverul tău obișnuit.

---

## Ce știe să facă (v0.1)

- **Vorbește** în chat, în română, cu personalitate proprie (AI online gratuit sau local).
- **Ascultă comenzi**: `vino`, `urmeaza-ma`, `stai`, `opreste`, `unde`, `memorie`.
- **Te urmărește** singur când te depărtezi.
- **Reacționează la monstri**: zice o replică și fuge spre tine.
- **Ține minte**: evenimente recente + fapte, salvate per personaj (`memory/<id>.json`).
- **Regie** pentru tine: pauză/reluare AI, chemare, replici forțate.

## Instalare (o dată)

1. Instalează **Node.js 20+**: https://nodejs.org
2. În folderul proiectului:

```bash
npm install
copy .env.example .env      # Windows
# sau: cp .env.example .env  (Linux/Mac)
```

3. Completează `.env`:

| Variabilă | Ce pui |
|---|---|
| `MC_HOST` / `MC_PORT` | adresa serverului (`127.0.0.1` / `25565` pentru local) |
| `MC_VERSION` | versiunea serverului (ex. `1.21.1`, `1.21.4`) |
| `MC_AUTH` | `offline` dacă serverul are `online-mode=false` |
| `OWNER` | numele tău de jucător |
| `GROQ_API_KEY` | opțional, gratuit de la https://console.groq.com |

> Pe un server cu `online-mode=true` (cont premium), boții nu pot intra cu nume inventate.
> Pentru teste: server local cu `online-mode=false`.

## Pornire

```bash
npm start
```

În consolă apare lista personajelor și o **consolă de regie**.

## Comenzi în joc

Scrie în chat (poți prefixa cu numele personajului):

| Comandă | Efect |
|---|---|
| `Uke vino` | vine la tine |
| `Uke urmeaza-ma` | te urmărește continuu |
| `Uke stai` | se oprește pe loc |
| `Uke opreste` | rămâne pe loc |
| `Uke unde` | îți spune coordonatele |
| `Uke memorie` | spune ce își amintește |
| orice altceva | răspunde ca personajul |

## Consola de regie (pentru filmări)

```
list                 ce personaje sunt, unde sunt, dacă AI-ul e activ
pause Uke            oprește AI-ul (personajul tace, dar ascultă)
play Uke             pornește AI-ul
call Mara            cheamă personajul la tine
say Uke hai sa mergem
memory Uke           ce își amintește
quit
```

## Personalități

Fiecare personaj e un fișier în `characters/`:

```json
{
  "id": "uke",
  "name": "Uke",
  "description": "Esti Uke, un tovaras de aventura...",
  "rules": ["Raspunde scurt...", "Fara emoji..."],
  "greetings": ["Salut!"],
  "dangerLines": ["Sunt monstri aproape!"],
  "filler": ["Spune-mi mai mult."]
}
```

Adaugi un fișier nou → ai un personaj nou. Momentan sunt două: **Uke** (glumeț) și **Mara** (precaută).

## Siguranță

Creierul AI **doar vorbește**. Comenzile sunt un set fix, verificate de cod, iar botul
nu sapă (`canDig = false`). Deci un model AI nu poate strica lumea „din greșeală".

## Ce urmează

1. **Voce**: microfon → text (Whisper) → răspuns → voce românească (Piper), totul local.
2. **Relații între personaje**: se ceartă, cooperează, își amintesc unul de altul.
3. **Acțiuni**: adunat lemn, construit adăpost, luptă alături de tine.
4. **Obiective pe termen lung**: fiecare personaj are un plan al lui, nu doar reacții.

## Structura

```
src/index.js       pornire + consola de regie
src/character.js   botul: comenzi, urmarire, frica de monstri, memorie
src/brain.js       AI: personalitate + context din memorie (Groq gratuit / local)
src/memory.js      memorie persistenta per personaj
characters/*.json  personalitatile
```
