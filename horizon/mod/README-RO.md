# Modul de mașini ca mod Horizon (nu ca plugin)

Decizie: reproducem modul original **ca mod Horizon**, cu entități proprii, așa cum e modul
Java. `paper-plugin/` rămâne pe bară — nu se mai dezvoltă, dar nici nu se șterge.

## Ce înseamnă „mod Horizon", concret

Un mod Horizon are două părți, și amândouă sunt necesare aici:

| Parte | Fișier | Ce pune | Cine îl are |
|---|---|---|---|
| **conținut** | `.hmod` | tipuri de entitate (`vehicle:go_kart`), iteme, blocuri, modele, texturi, texte | serverul **și** clientul |
| **nativ** | `.hnmod` | cod Rust: fizică, condus, combustibil, randarea mașinii, HUD | se compilează în joc |

Amândouă intră în joc prin `horizon-mods` (uneltele oficiale), iar clientul le încarcă la
pornire. De aceea:

> **Clientul trebuie să fie Horizon launcher**, nu launcher-ul Mojang. Un client vanilla nu
> are cum să vadă o entitate nouă: protocolul îi poate trimite doar entități vanilla.

Asta nu e o preferință de-a mea — e cum e făcut Horizon: conținutul adăugat de moduri e
refuzat/ignorat de un client care nu rulează aceleași moduri.

## Structura din repo

```
horizon/mod/
├── content/                     partea .hmod
│   ├── horizon-mod.toml         manifestul (id, side = both, capability registries)
│   ├── data/vehicle/horizon/
│   │   └── entity_type/         tipurile de entitate (go_kart, quad_bike, ...)
│   └── assets/vehicle/          modele, texturi, sunete, texte
└── native/                      partea .hnmod (codul)
    ├── compatibility.json
    └── mixins/vehicle/
        ├── horizon-mixin.toml   punctele de hook
        ├── runtime.rs           server: fizica, condusul, scaunul
        └── render.rs            client: desenează mașina (native_mob_submit)
```

## Etape

| Etapă | Ce livrează | Stare |
|---|---|---|
| **A** | `vehicle:go_kart` există ca tip de entitate, se poate da `/summon`, se poate încălța | în lucru |
| **B** | un client Horizon vede mașina desenată din cuburile modelului (`native_mob_submit`) | |
| **C** | condus: fizică + input + scaun (codul server-side pe care îl avem deja, mutat pe entitatea nouă) | |
| **D** | restul modului: combustibil, roți care se învârtesc, sunet, benzinărie, mecanic, garaj | |

## Ce rămâne din munca de până acum

- Fizica și condusul din `horizon/engine-mod/mixins/vehicle/runtime.rs` — se mută pe entitatea
  `vehicle:go_kart` în locul minecart-ului-schelet.
- Modelele: `tools/build_resource_pack.py` știe deja să scoată modelele din mod ca JSON cu
  cuburi (`elements`), exact primitiva pe care o desenează randarea Horizon.
- Resource pack-ul rămâne util doar pentru clienți vanilla; pe clientul Horizon modelele vin
  din mod, deci nu mai e nevoie de el.
