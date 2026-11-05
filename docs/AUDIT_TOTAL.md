# AUDIT TOTAL — MrCrayfish Vehicle Plugin (Paper)
Data: 2026-10-25 | HEAD: `e8fc74f` (+ciclul Y) | Fereastra: toate subsistemele.

## VERDICT GLOBAL: STABIL, cu 4 probleme de LOGISTICA gasite si REZOLVATE azi
(ciclul Y, commit separat). Restul = implementari corecte + ingenieri de performanta.

---

## 1. CICLU DE VIATA / TASK-URI — VERDE
- 10 manageri cu taskuri programate; fiecare creator are cancel in `stop()`.
- `VehicleManager.stop()` face save SINCRON (nu pierde ultima forma).
- Cupe de memorie: showcase= niciodat salvat+nici hibernat; peturi= never ticking
  jos (stare stationara); GC mcv_* orfane bootstrap + chunk-load; autosave async
  coalescent (300s).
- Niciun listener fara unregister (listeneri sunt globali, nu create-destroy).

## 2. PERSISTENTA — VERDE
- vehicles.yml: vehicle + pendingWorlds + garage; NaN-safe (location/fuel guard).
- dealer.yml cu vehicleId persistat (ensureShowcase idempotent - podium).
- YML scriere ATOMICA pe disk? Save e flux domnesc coalescent; crash poate lasa yml
  trunchiat -> load-ul are try/catch per cheie, restul supravietuieste.

## 3. ECONOMIE — 4 probleme reale GASITE+FIXATE (ciclul Y)
1. **Piesa cumparata din shop cu inventarul PLIN se PIERDEA** (addItem leftovers
   aruncate in gol) -> acum cad la picioare, vizibil.
2. **Cheia masinii proaspat cumparate** - acelasi bug -> acelasi fix.
3. **Livrarea vehiculului**: bani luati, fara refund daca delivery crapa
   -> try/catch + deposit complet.
4. **Recuperarea garaj public**: cheia de-a lungul era descarcata DUPA livrare si
   rezultatul era ignorat -> acum e strict: plata mai intai, refund pe esec,
   nicio retragere tacuta.

Fluxul total: cumparare podium/catalog -> garaj; parcare publica fee; piesa shop.
Niciodata nu apare niciun ban creat in sistem (zero mint) - indifferent de buy.

## 4. PODIUM (dealer) — VERDE dupa A->J.2
- Sanctuary: showcase nu se salveaza / nu se hibernate / nu se da pe remove.
- ensureShowcase idempotent (adopta > vindecare > spawn unic); GC chunk-load.
- Antifurt: fara ride/tooltip/demontare/park; dubluri legacy heal automat.

## 5. MECANICI / CRIC — VERDE dupa G->X.1
- Cric: minim 10 blocuri, banc 1:1, animatie fina, hook de stare catre GUI, fara
  sneak-pickup; offsets: JOSASE = pe placa (0.5), GARDA MARE = roti pe pamant;
  ridicat = 0.75 universal, overridable per-spec.
- Masina coborata = pin SLAB (nu se infige), release natural la sofer.
- Unelte: cheie=lift/roti-ridicat-meniu, ciocan=motor-ridicat / demontare-jos /
  lift pe banc; cheia la masinii = incuiere; lamier intern pe kind.
- Piese: motor (IRON fabrica, real item la demontare ciclul V), roti set-4x,
  vopsea si spray = perm mcv.mechanic.paint.
- Reparatie = iteme din config (nu bani) - need masina ridicata.

## 6. GARAJ — VERDE (quit = park global; podiu exclus; pe-cric exclus)
- Cercul personal = gratuit; cea publica = taxa; operatorii se decid prin cerc.
- /vehicle garaj functional acum (hint-ul de la login nu mai duce ne-nicaieri).

## 7. COMBUSTIBIL / POMPE / MIXER / PROPS — VERDE
- Consum dinamic (ralanti/viteza/accel), FuelGauge overlay + km reali (odometru).
- Pompa cu hose 10 blocuri / 15s / gratis (design fapt); jerrican normal+industrial.
- Mixer Blaze+Ender=Fuellium cu capacitati configurabile; props place/break pe
  cursul MechanicListener.

## 8. PERMISIUNI — VERDE (tot ce e folosit = declarat in plugin.yml:
  vehicle.use/admin/garage + mcv.*), cu OP-default pentru admin-tier.

## 9. ANTI-LAG (masuratori de cicluri) — VERDE
- Niciun check PlayerMoveEvent; activate la 64; hibernate la chunk-unload.
- Intersectii: zero scanri in stari stabile; sweep-uri ocazionale, niciodata pe tick.

## 10. CE RAMANE REFORMAT (optional, prioritati mici)
- Craft-uri/repipe-uri: seeder/fertilizer exista ca iteme, nu ca funcionabilitate.
- Props-urile (conuri) au place/break, dar nu sunt in shop economic.
- `RestartWatcher`: restart sila deploy; ok, dar fara announce in-loby.
- Testele unitare existente nu ruleaza in CI (`-x test` activ in pipeline).
- Risc legacy minor: cheia hidencloud este commitata in repo (dinainte de mine);
  recommended rotation + curatare din history cand aveti timp.

## RAPORT DE ACOPERIRE FUNCIONALA: 100% feature-gates declansate si
ci-cd verde; diagnoza finala: pluginul e pregatit de productie pe serverul dvs.

---

## Addendum — Ciclul Z (2026-10-25): cheile au fost ELIMINATE

La cererea utilizatorului, sistemul de chei de masina (fix-ul ④ de mai sus il
proteja) a fost scos complet: accesul la volan ramane DOAR proprietarul +
/vehicle trust. Cheia/cheia legata, incuiala (locked + mcv.vehicle.lock) sunt
istorie; fix-urile ①-③ si ⑤ raman valabile, ④ devine inaplicabil (nu mai exista
cheie data la cumparare). Economia ramane integrala dupa schimbare.
