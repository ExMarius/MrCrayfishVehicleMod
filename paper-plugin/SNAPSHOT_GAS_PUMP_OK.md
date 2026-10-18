# GAS PUMP — VERSIUNI INGHEȚATE (VALIDATE DE UTILIZATOR)

## v2 — stare finală (2026-10-18, commit `e5b122f`, tag `gaspump-perfect-v2`) ✅ ACTIV

Utilizatorul a aprobat: „perfect, doar că nu e unit cu pistolul" — limită ACCEPTATĂ.

- **Pompă docată:** pistol + furtun perfecte — identic ca v1 (vezi constantele de mai jos)
- **Pistol ținut = display plutitor** (fără item în inventar):
  - rotație = yaw-ul privirii holderului (fără offset 180°)
  - capătul lanțului + display-ul teleportează la **același punct** în fiecare tick
  - furtunul intră **0.22 blocuri în corpul pistolului** (`NOZZLE_CONNECT_DEPTH`) — pe fața de vizualizare
  - holderul NU vede ultimele **2** segmente (`HOLDER_HIDDEN_SEGMENTS=2`) → FP curat, unirea rămâne vizibilă
  - `setTeleportDuration(0)` + predicție viteză ×2 → furtunul nu rămâne în spate la fugă
- **Anti-dupe:** dacă există iteme-nozzle vechi în invenare, se șterg automat la punerea înapoi (`removeNozzleItems`)

## v1 — pompă docată perfectă (2026-10-18, commit `f7366e7`, tag `gaspump-perfect-v1`)

### Constantele validate (GasPumpRig.java)

| Constantă | Valoare | Semnificație |
|---|---|---|
| `HOSE_OUTLET_LOCAL` | `(-0.575, 1.64, 0.1125)` | ieșire furtun din pompă — stânga |
| `NOZZLE_REST_LOCAL` | `(0.5525, 1.5, 0.18)` | dock pistol |
| `NOZZLE_YAW_OFFSET` | `180` | rotirea modelului pistol docat |
| `NOZZLE_CONNECT_DEPTH` | `0.22` | adâncimea furtunului în pistol ținut |
| `HOLDER_HIDDEN_SEGMENTS` | `2` | segmente ascunse holderului în FP |

### Reguli de rotație validate

- **Corpul pompei** rotește și ancora furtunului: `rotY(yaw) * HOSE_OUTLET_LOCAL`
- **Dock pistol** = `root + rotY(yaw + 180°) * NOZZLE_REST_LOCAL` (convenție Bukkit: `x' = x·cos − z·sin`, `z' = x·sin + z·cos`)
- **Pistol ținut** rotație = yaw privire holder, niciun offset

## Restaurare instant

```bash
git checkout gaspump-perfect-v2 -- paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/gaspump/
git checkout gaspump-perfect-v1 -- paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/gaspump/  # pentru dock-only
git commit -m "restore gas pump inghetat" && git push
```

(Deploy automat ~5 min + restart confirmat.)
