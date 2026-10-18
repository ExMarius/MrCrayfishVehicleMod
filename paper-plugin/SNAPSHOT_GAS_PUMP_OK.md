# GAS PUMP — VERSIUNE INGHEȚATĂ (VALIDATĂ DE UTILIZATOR)

**Data / commit:** 2026-10-18 / `f7366e7` (tag: `gaspump-perfect-v1`)

Aspect, poziții pistol + furtun: **PERFECTE, confirmate vizual de utilizator**.
NU se atinge NIMIC din pachetul `com.mrcrayfish.vehicle.paper.gaspump` fără acord explicit.

## Constantele validate (GasPumpRig.java)

| Constantă | Valoare | Semnificație |
|---|---|---|
| `HOSE_OUTLET_LOCAL` | `(-0.575, 1.64, 0.1125)` | ieșire furtun din pompă — partea stângă |
| `NOZZLE_REST_LOCAL` | `(0.5525, 1.5, 0.18)` | punctul de dock al pistolului |
| `NOZZLE_YAW_OFFSET` | `180` | rotirea modelului pistolului |

## Reguli de rotație validate

- **Corpul pompei** se rotește cu `setRotation(yaw)` → ancora furtunului se rotește cu **aceeași** rotație: `rotY(yaw) * HOSE_OUTLET_LOCAL` (fix f7366e7 — înainte era fixă în lume).
- **Dock-ul pistolului** = `root + rotY(yaw + 180°) * NOZZLE_REST_LOCAL` (convenția Bukkit: `x' = x·cos − z·sin`, `z' = x·sin + z·cos`) — trebuie să folosească EXACT aceeași rotire ca `place(..., NOZZLE_YAW_OFFSET)`.
- Pistolul ținut în mână: poziție după ochi (pitch inclus) — comportament **validat**, nudges activi: `-0.4 y, +0.35 în față, +0.3 în dreapta` (handHeldNozzleLocation original).

## Restaurare instant

```bash
git checkout gaspump-perfect-v1 -- paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/gaspump/
git commit -m "restore gas pump inghetat" && git push
```

(Se așteaptă ~5 min deploy automat + restart confirmat.)
