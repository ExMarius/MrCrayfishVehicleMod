# GHID DE COMANDĂ — MrCrayfish Vehicle Plugin (Paper 1.21.4)

Versiune LIVE pe server: `3669ca5` (25 octombrie 2026, CI complet verde).
Portal de inspectie: `docs/GUI_CRIC_original_vs_implementare.md` = jurnalul tehnic Cicluri A→K.

---

## ARHITECTURA (receptiune rapida, pentru orice model ulterior)

**Paper-only** (`paper-plugin/` + CI GitHub Actions care buildeaza si deploy-uie
pe SSH pe server). Resursele 3D/sunetele vin intr-un resource-pack extern pinned cu sha1.
Token-i si chestiile sensibile sunt in secrets GitHub, niciodata in cod.

**Legi anti-lag** (batute in cuie prin cicluri):
- Niciun vehicle-owner-check in `PlayerMoveEvent` (doar chunk-activate + coliziune slab DOAR low).
- Coliziunea cu lamai -> 1 tick / entity, fara explosion, fara damage (damage global = `false` acum).
- ChunkUnload = hibernare completa; entitati fara vehicle in registry se curata la chunkLoad (`mcv_*` orfane).
- Podiumurile (showcase) NU se salveaza in vehicles.yml, NU se hibernaza, NU se dau pe niciun flow normal.

---

## COMENZI (player + admin)

| Comanda | Permisiune | Ce face |
| --- | --- | --- |
| `/vehicle spawn <tip>` | vehicle.admin | Spawn manual vehicul (admin) |
| `/vehicle remove` | vehicle.admin | Sterge cea mai apropiata masina (sare showcase-urile; pe podium = refuz cu mesaj) |
| `/vehicle list` | vehicle.admin | Lista vehicule in lume |
| `/vehicle save` | vehicle.admin | Salveaza acum vehiculele (fortat) |
| `/vehicle pack` | vehicle.admin | Retrimite resource-pack-ul la player-ul curent |
| `/vehicle trust <player>` / `untrust` / `trusted` | vehicle.use | Drepturi de condus/folosire pe masina ta |
| `/vehicle paint\|vopsea\|vopseste` | mcv.mechanic.paint (op) | Meniul de culoare pe masina vizata |
| `/vehicle podium set <vehicul> <pret>` / `remove` | vehicle.admin | Podea de showroom (auto-creeaza/reconstruieste) |
| `/vehicle magazin\|shop\|dealer` | vehicle.use | Deschide ShopGui (catalog+piese) |
| `/vehicle creative` | mcv.creative | Meniul complet de service in creative |
| `/vehicle gaspump` / `jerrycan normal\|industrial` | vehicle.admin | Iti da item-urile speciale |
| `/garaj [set\|remove]` | vehicle.garage | Cercul personal de parcare (ieșire gratuită de acasa) |

---

## SISTEMUL MECANIC (Player journey testat)

1. **Banc de lucru** (`JackManager` + blenderblocks): pui un jack bloc langa vehicul. Relatie **1:1** cu un banc; max 5 blocuri intre ele.
2. **Elevator**: cheie mecanica din banc (max 2, fiecare = operator) -> shift-click pe vehicul -> `elevator lifted/lowering/idle`. Min **10 blocuri** intre doua elevatoare (`jack.min-distance` in config).
3. **Unelte din banc**: cheia si ciocanul apar direct in inventar; apoi dispar din banc si in meniu devin BARRIER "LUATA/Ocupata" (doar un player o tine, reluare doar la spargerea bancului/checarea zonei).
4. **Reparatie/Demontare**: vehicul TREBUIE RIDICAT (`require-lift: true` in config); cheia pe vehicul suspendat = scoti roțile; ciocan-[left-click] pe vehicul suspendat = scoti motorul.
5. **Repaire items** (`mechanic.repair-items`): REDSTONE, IRON etc sunt consumate la reparatia completa (NU bani - banii in banc se negociaza RP).
6. **Spray** (nu e unealta de banc) = vopseste direct masina 1:1 ca in mod.
7. **Paint System propriu** (`mechanic.paint-menu`). Gratuit, GUI propriu — NU consuma item.
8. **Inchidere masina** (cheia masinii). Nou: `case "lock"` in comanda primeste /vehicle-adjacent (elemenul fizic cu data de incuiere este in LandVehicle).

## ECONOMIA — FIVEM-STYLE, doar cu Vault/EssentialsX
- Preturi per tip in `config.yml: dealership.prices` (`shopping_cart: 100` pana la `sports_plane: 500000`).
- Piese consumabile (motor/roti/spray) in `mechanic.parts.prices`.
- Garaj retrieval: `economy.public-garage-retrieval-fee: 500` in locul public, `0` in locul personal.
- Fuellium se fabrica din Blaze Juice + Ender Sap in **FluidMixer** (`mechanic.fluid-mixer` in config).

## STABILITATE CERTIFICATA (cicluri A→J)
Vezi `docs/GUI_CRIC_original_vs_implementare.md` sectiunile E→H→I→J:
- *Bug "ping-pong elevator" (Buton blocat pe ceas)*: ATAT de trasabil history de ce nu s-a automatizat separare state; Hook `stateChangedHook` esentiat la 8 puncte.
- *Bug "meniu disparuea cand ieși de langa*": `MechanicGui` -> guard pe stationId, masina se rezolva prin cric.
- *Bug "masina spawneaza 2x pe podium la restart"*: `vehiclesWithin` sarea showcase-urile + `vehicleId` nepersistat. FIX = metoda `ensureShowcase` idempotenta (adopta dupa id -> cauta showcase pe podium -> masina normala a modelului -> spawneaza UNOA) + `mcv_*` orphan-cleanup la chunkLoad.
- *Bug "podiumul REDEVINE masina normala dupa restart/chunk"* (ciclul J.2): podiumurile se hiberneazau in pendingWorlds + erau serialize in yaml ca masini normale. FIX = pgardere anti-hibernate pentru showcase. Scriavisan.
- *Bug "/vehicle remove dadea jos podiumul"*: `nearest` includea showcase. FIX = `nearest(loc, r, includeShowcase=false)` + refuz cu mesaj explicit.

---

## CHECKLIST FINAL DE "GATA" PRACTIC (testat azi 25 oct)

- [x] Meniul mecanic 27-slot concept revizuit complet (fara sloturi de piese).
- [x] Cric 1:1 cu banc, min 10 blocuri intre cricuri, sneak+click pe cric = nimic.
- [x] `WRENCH` pe masina ridicata = jos roțile (cursor); ciocan[attack] = motorul.
- [x] Unelte-VIZIBILE ca fiind luate (BARRIER cu owner) pana revin.
- [x] Un singur podium = 1:1 masina, vehicleId persistat, *self-heal* la restart/chunk-uri &.
- [x] `/vehicle remove` nu atinge podiumul; dedica-te `/vehicle podium remove`.
- [x] Anti-lag: 0 check de player in tick curent; afara de `ChunkActivate` la raza 64.
- [x] Storage trimming: niciun vehicle normal nu ramane in lume la quit (`parkAllOwnedBy`).
- [x] Garaj = UNUL singur per player, pret contingent de cerc (0$ acasa, 500$ public).
- [x] Alimentare pompa cu hose 10 blocuri, fill 15s; jerrican-uri prin click pe masina.
- [x] Consum dinamic de benzina (ralanti/viteze/acceleratie).
- [x] Repo curat: `.gitignore` corect (fara jars, fara pack zip, fara worlds).
- [x] Doc = aceasta singura sursa de adevar (tehnic + player + admin).

---

## CE A MAI RAMAS "NICE-TO-HAVE" (optional, nu e necesar pentru PROD)

- N-am testat fizic fiecare path din JAVA pe server real (require JAR loading — fara JDK local).
- `RestartWatcher` reda banner verbal; opreste corect (fyi).
- `StorageSeeder/Fertilizer` items exista in meniu; functionale sunt masinile, nu seeder-ele.
- Washers/interesting props (traffic_cone, boost_pad, fluid_pipe) exista —no shop price deocamdata.
- Credite/mentionuiale mod original in plugin.yml (a stabilit legatura cu comunitatea).

### Deci gata? Aproape. Ramas: test pe LIVE server (jucatorul o face), optional test cu `RestartWatcher` si curs de finalizare cerut cu ceas.

Ultima data LIVE confirmat: `3669ca5`. Nu mai ating asta pana nu zici aprova.
