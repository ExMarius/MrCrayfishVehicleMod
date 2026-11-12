# AI Bridge — personaje AI ca jucători-fantomă (NeoForge 1.21.1)

Modul care ține personajele **în server**, ca jucători fără client. Pentru joc și
pentru celelalte moduri sunt jucători normali: au inventar, pot ține iteme din orice
mod, pot săpa și pune blocuri. Creierul AI rămâne în afara jocului (Node.js) și
trimite decizii prin puntea HTTP locală.

## Descărcare

JAR-ul se compilează automat pe GitHub: **Releases → `ai-bridge-latest` → `aibridge-0.1.0.jar`**
(workflow `AI Bridge mod (compile)`).

## Instalare pe server

1. Serverul e deja NeoForge **1.21.1** (21.1.172).
2. Urcă `aibridge-0.1.0.jar` în `mods/` (lângă celelalte moduri).
3. Repornește serverul.
4. În consolă trebuie să apară:

```
[AIBridge] gata. /npc spawn <nume> pentru primul personaj.
[AIBridge] punte HTTP pornita pe 127.0.0.1:8765 (doar local)
```

Dacă lipsesc liniile ⇒ modul nu s-a încărcat (verifică versiunea serverului).

## Prima probă (în joc, ca OP)

```
/npc spawn Uke      # apare lângă tine, în lista de jucători
/npc list
/npc walk Uke 100 65 200
/npc say Uke bine v-am gasit
/npc remove Uke
```

## Puntea HTTP (doar pe server, 127.0.0.1)

```bash
curl http://127.0.0.1:8765/state

curl -X POST http://127.0.0.1:8765/command \
  -H "Content-Type: application/json" \
  -d '{"npc":"Uke","action":"walk","x":100,"y":65,"z":200}'
```

Acțiuni: `spawn`, `remove`, `walk`, `stop`, `look` (yaw/pitch), `say` (text).

> **Notă:** puntea ascultă doar pe `127.0.0.1`. Ca să fie accesată de pe PC-ul tău,
> deschide un tunel SSH către server (`ssh -L 8765:127.0.0.1:8765 ...`), nu o expune pe internet.

## Ce urmează (etapa 2)

- puntea să trimită și **starea** (inventar, blocuri din jur, viață) către creierul AI;
- acțiuni noi: `mine`, `place`, `use` (item din mână), `attack`;
- `src/bridge.js` în proiectul Node ca să vorbească direct cu puntea.

## Compilare locală (opțional)

```bash
./gradlew build     # JAR în build/libs/
```
