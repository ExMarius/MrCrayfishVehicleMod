# Source position and matrix audit

This document records the Paper 1.21.4 position audit against the generated 1.16 vehicle properties and the original renderers. Values are in blocks unless a value is explicitly identified as model pixels. The audit uses the serialized files under `src/generated/resources/data/vehicle/vehicles/properties/` as runtime authority; this matters where the generator serializes `0.9375` as `0.938` or `-0.0625` as `-0.062`.

## Common matrix conversion

Let `u = 1/16`, `S = bodyScale`, `A = offsetToGround` (called `axleOffset` in Java), and `W = the generated wheelOffset`.

- Body model origin: `B = (0, S * (0.5 + (A + W) * u), 0)`.
- Wheel rendering cancels the common `+0.5` and `A` translations. A wheel center is `(S * side * x * u, S * (W + y) * u, S * z * u)`, followed by the rotated half-width offset. Its display scale is `S * wheelScale`.
- Seats use the source entity equation, not the visual body origin: `S * (-x, y + A + W, z) * u`. The Paper carrier correction is applied separately.
- Engine center inherits `B`, the property translation, `renderPart`'s `-0.5 Y`, and `renderEngine`'s additional `+0.5 * engineScale Y`.
- Fuel fillers and ignition models inherit `B`, the property translation, and `renderPart`'s `-0.5 Y`.
- The wheelie pivot is `(0, S * W * u, S * rearAxleOffset * u)`. Source call order is preserved: rear-axle wheelie, then motorcycle roll around the entity root.
- A visual tow bar is rendered before axle/wheel translation and before the wheelie matrix: `(S * x * u, 0.5 + S * y * u, S * z * u)`. Trailer physics uses the same point without visual `+0.5 Y`.
- `ItemDisplay`'s intrinsic 180-degree Y presentation is cancelled on the right rotation. Source-side right-wheel, tow-bar, engine, filler, and ignition rotations are then reapplied in the same matrix order.

All currently implemented body transforms have zero body translation/rotation, so there is no hidden non-zero body-transform term in these tables.

## Vehicle audit

| Vehicle | Body origin Y | Wheel centers Y (front/rear) | Source seat Y | Engine center | Steering center | Tow visual Z |
|---|---:|---:|---:|---|---|---:|
| Go Kart | 0.637500 | 0.200000 / 0.215625 | -0.050000 | (0, 0.600000, -0.687500) | (0, 0.6800875, 0.5026625) | n/a |
| Lawn Mower | 0.709375 | 0.240625 / 0.303125 | 0.631250 | n/a (source does not render its engine) | (0, 1.209375, -0.187500) | -1.250000 |
| Quad Bike | 0.818125 | 0.302500 / 0.302500 | 0.611875, 0.646250 | (0, 0.611875, -0.068750) | (0, 1.230625, 0.206250) | -1.100000 |
| Tractor | 0.668750 | 0.356250 / 0.700000 | 0.731250 | (0, 0.968750, 0.468750) | (0, 1.3210963, -0.4565224) | -1.531250 |
| Dirt Bike | 0.850000 | 0.350000 / 0.350000 | 0.850000, 0.912500 | (0, 0.712500, 0) | (0, 0.850000, 0) before fork steering | n/a |
| Moped | 0.765000 | 0.240000 / 0.240000 | 0.465000 | n/a (source sets `renderEngine=false`) | handles `(0, 1.0855425, 0.6305325)` before fork steering | n/a |
| Off Roader | 1.102500 | 0.490000 / 0.490000 | 0.752500, 0.752500, 0.708750, 0.708750 (vanilla rear-seat adaptation) | n/a (source sets `renderEngine=false`) | (-0.437500, 1.572701, 0.299799) | n/a |
| Sports Car | 0.662500 | 0.350000 / 0.350000 | 0.037500, 0.037500 | (0, 0.763125, 1.187500) | (-0.250000, 0.59399375, 0.1023625) | n/a |
| Mini Bus | 1.118000 | 0.386750 / 0.386750 | 0.711750 (all five) | n/a (source sets `renderEngine=false`) | (-0.406250, 1.516441875, 1.27057125) | -2.031250 |

Every listed wheel has a calculated contact Y of exactly `0`. Tractor and Dirt Bike wheel X scales now use serialized `0.938`; the other generated and auto-scaled wheel values match their property equations.

### Powered accessory centers

| Vehicle | Closed fuel filler center | Ignition center |
|---|---|---|
| Go Kart | (0, 0.137500, 0) | none (`canLockWithKey=false`) |
| Lawn Mower | (-0.3515625, 0.865625, 0.3515625) | none (`canLockWithKey=false`) |
| Quad Bike | (0, 1.044175, 0.515625) | (-0.343750, 0.577500, 0.446875) |
| Tractor | (-0.375000, 0.918750, -0.031250) | (-0.171875, 0.918750, -0.109375) |
| Dirt Bike | (0, 1.2734375, 0.2251875) | none (`canLockWithKey=false`) |
| Moped | (0, 0.165000, 0) | hidden (`canLockWithKey=false`) |
| Off Roader | (-1.050000, 1.321250, -0.568750) | (0, 1.015000, 0.542500) |
| Sports Car | (-0.625000, 0.568750, -0.875000) | (-0.312500, 0.443750, 0.406250) |
| Mini Bus | (-0.975000, 1.280500, -0.7109375) | (0, 1.0164375, 1.584375) |

The r19 pack includes the original closed full/small fuel-port geometry. It also includes the key-hole geometry, while the Paper rig correctly leaves it hidden in the current default state: the source renders ignition/key parts only after its dynamic `NEEDS_KEY` state is enabled, and the Paper key system has not yet been ported.

### Dirt Bike fork

The exact source fork matrix is `Rx(-22.5) * Ry(steeringRatio * 25) * Rx(+22.5)` around a pivot `10.5/16` blocks forward of the body origin. It transforms both the handles and the separately rendered front wheel. At a 25-degree visual steering angle:

- handles center: `(-0.25623173, 0.87173843, 0.05248117)`;
- front-wheel center: `(0.006498318, 0.34944868, 0.8786690)`.

The rear wheel remains outside the fork matrix. The front wheel retains the source renderer's explicit 180-degree Y rotation.

The Moped uses the same tilted-axis equation around its renderer's `11.5/16 * 1.2 = 0.8625` block pivot. Its handles, mud guard, and manually rendered front wheel all share this fork matrix; unlike the Dirt Bike, the Moped front wheel has no extra 180-degree Y rotation. At a 25-degree visual steering angle their centers are:

- handles `(-0.03873031, 1.0888283, 0.6384652)`;
- mud guard `(-0.037937243, 0.47604856, 0.8941278)`;
- front wheel `(-0.009121702, 0.24077387, 1.0584683)`.

The stock seat/tray center is `(0, 0.69, -0.4875)` and the stock front-light center is `(0, 0.915, 0.7629)`. The closed vanilla chest display is centered at `(0, 1.065, -0.7875)` with scale `0.6`; its center compensates for the vanilla chest item's center anchor while preserving the source renderer's bottom position and 180-degree orientation.

### Off Roader renderer

The generated body scale is `1.4`; each wheel's generated scale is another `1.4`, producing display scale `1.96`. Wheel centers are at outside X `±1.12`, Y `0.49`, and Z `±1.26875`, so every tire has exact ground contact at Y `0`.

`OffRoaderRenderer` translates its steering wheel to `(-0.3125, 0.35, 0.2)`, rotates local X by `-45` degrees, translates local Y by `-0.02`, and scales by `0.75`. After body scaling and the rotated local translation, the world center is `(-0.4375, 1.572701, 0.299799)` with scale `1.05`. Runtime steering then rotates it around its local Y axis by `steering / 35 * 25`, preserving the source call order.

The authoritative source seat centers are `(-0.4375, 0.7525, -0.2625)`, `(0.4375, 0.7525, -0.2625)`, `(-0.4375, 1.40875, -1.26875)`, and `(0.4375, 0.70875, -1.65375)`. The third source entry relies on a custom standing/hanging per-limb player pose that a vanilla mounted player cannot display. Per the accepted vanilla-client correction, its runtime Y is lowered to `0.70875`, matching the other rear rider while retaining its source X/Z; both rear runtime centers are therefore low. Mounting mirrors `SeatTracker#getClosestAvailableSeatToPlayer`, so approaching and clicking from the rear can select a free rear position even while a front seat is free. All occupants retain the accepted horse-style pose.

### Sports Car renderer, cosmetics, and storage

The generated wheel offset is `2.6` model pixels. All four 1.4-scale wheels are centered at outside X `±0.875`, Y `0.35`, front Z `1.25`, and rear Z `-1.1875`; their contact Y is exactly zero. The two source seat centers are `(-0.4375, 0.0375, -0.3125)` and `(0.4375, 0.0375, -0.3125)` before the accepted global rider correction.

`SportsCarRenderer` places the steering wheel at source translation `(-4, -1.0961, 1.6378)` pixels, scale `0.7`, and local X rotation `-67.5` degrees. After the common body matrix its center is `(-0.25, 0.59399375, 0.1023625)`. The rendered large engine preserves the generated `(0, 3.01, 19)`-pixel transform, `0.825` scale, and renderer half-scale Y correction, yielding center `(0, 0.763125, 1.1875)`.

The base model is not a complete car by itself. The Paper rig therefore renders all seven generated default cosmetics at their source pivots:

| Cosmetic | Pivot center | Source open action |
|---|---|---|
| Hood | (0, 1.006250, 0.843750) | X `-60°`, 12 ticks |
| Left door | (0.937500, 0.225000, 0.625000) | Y `-75°`, 12 ticks |
| Right door | (-0.937500, 0.225000, 0.625000) | Y `+90°`, 12 ticks |
| Boot/spoiler | (0, 1.131250, -1.468750) | X `+90°`, 12 ticks |
| Seat | (0, 0.225000, 0) | static |
| Dashboard | (0, 1.037500, 0.750000) | static |
| Roof | (0, 1.037500, 0) | static |

Openable parts use the source `easeOutBack` curve and original door/hood open-close samples. Their target states are persisted and carried through activation, hibernation, restarts, and Vehicle Trailer transport. Server-side oriented ray tests replace `CosmeticRayTraceData`; they compare the openable model bounds with the storage bounds by nearest hit, including each part's current rotation.

The Glove Box is a persistent 9-slot inventory with source interaction bounds `(0.125, 0.38125, 0.1875)` through `(0.5, 0.63125, 0.3125)`. The Trunk is a persistent 27-slot inventory with bounds `(-0.4375, 0.4125, -1.1875)` through `(0.4375, 0.6, -0.75)`. As in the source ray tracer, the closed boot is closer when approached from the rear: opening it exposes the trunk interaction region behind it.

### Mini Bus renderer and cosmetics

The serialized wheel X scale is `0.938`; wheel Y/Z scale is `1.19`, giving generated wheel offset `4.76` pixels. With body scale `1.3`, the four wheel centers are at outside X `±0.883675`, Y `0.38675`, and Z `±1.096875`; each wheel has exact ground contact. The five seat centers preserve the source two-front/two-middle/one-rear arrangement at X `±0.40625`, Y `0.71175`, and Z `0.73125`, `-0.24375`, or `-1.21875` as applicable.

All eight default cosmetics are rendered: stock roof, roof racks, left/right front doors, left sliding door, air-conditioner/ladder rear decoration, seats, and dashboard. The source alternative front-roof model is also packaged but is not the generated default selection. Cosmetic pivots include front doors `(±0.934375, 0.87425, 1.665625)` and sliding door `(0.934375, 0.87425, -0.609375)`. The front doors use `±75°` Y rotations over 12 ticks; the sliding door uses `+105°` Y over 20 ticks. All retain source `easeOutBack`, sounds, persisted target state, and rotated nearest-hit boxes.

The source renderer places the steering wheel at `(-5, 4.9039, 15.6378)` pixels, scale `0.7`, and X `-67.5°`, producing center `(-0.40625, 1.516441875, 1.27057125)` and display scale `0.91`. The repository registers `vehicle/mini_bus/steering_wheel` but contains no such model or texture; its own Mini Bus ray transforms instead explicitly select `GO_KART_STEERING_WHEEL`. The Paper pack therefore reuses that original model rather than exposing a missing-model fallback. Mini Bus alone overrides the standard tow model with the source `BIG_TOW_BAR`, centered at `(0, 0.5, -2.03125)`.

### Vehicle Trailer passenger offsets

The serialized or source-default offsets are:

- Go Kart `(0, -0.031, -0.375)`;
- Lawn Mower `(0, -0.010, -1.000)`;
- Quad Bike `(0, 0, -0.550)`;
- Tractor `(0, 0, 0)`;
- Dirt Bike `(0, -0.062, -0.312)`;
- Moped `(0, -0.031, -0.65)`;
- Off Roader `(0, 0, 0)` (source default);
- Sports Car `(0, 0, 0)` (source default);
- Mini Bus `(0, 0, 0)` (source default).

The Vehicle Trailer contributes the source `+0.5 Y` passenger-riding offset before these values.

## Trailer audit

Every trailer has `S=1.1`, `A=-0.5`, `W=5`, body origin `Y=0.859375`, wheel center `Y=0.34375`, wheel radius `0.34375`, and contact `Y=0`.

| Trailer | Outside wheel-center X | Wheel Z | Hitch distance | Additional source-rendered parts |
|---|---:|---:|---:|---|
| Fertilizer | ±0.790625 | 0 | -1.168750 | roller center `(0, 0.309375, -0.48125)`, Z +90 then wheel-spin X; source cargo piles |
| Seeder | ±1.203125 | 0 | -1.100000 | seven rollers X `-0.825` through `+0.825`, Y `0.144375`; source cargo piles |
| Storage Trailer | ±0.790625 | 0 | -1.100000 | closed chest and tow bar Z `-0.825` |
| Fluid Trailer | ±0.790625 | -0.171875 | -1.718750 | fluid minimum `(-0.42625, 0.653125, -1.089)`, full size `(0.83875, 0.680625, 1.837)` |
| Vehicle Trailer | ±0.996875 | -0.171875 | -1.581250 | carried vehicle at source passenger offset |

Fertilizer and Seeder cargo displays now use the original per-stack count divisors, grid spacing, layer staggering, rotation order, and `0.45 * bodyScale` scale. Their three world-interaction points are evaluated every tick like the original entities. Seeder inventory acceptance retains the original Forge seeds-tag set; planting retains the source crop-block restriction. Consuming one item reconciles the existing cargo displays in place instead of respawning the entire pile at the moving trailer root; only a real source pile-count threshold adds or removes a display, preventing planting/fertilizing movement from pushing the visible cargo outside the trailer.

## Corrections made by this audit

1. Tractor and Dirt Bike wheel X scales changed from generator input `0.9375` to runtime serialized `0.938`.
2. Dirt Bike Vehicle Trailer offsets changed from generator inputs `(-0.0625, -0.3125)` to runtime serialized `(-0.062, -0.312)`.
3. Tow bars no longer inherit the boost-wheelie matrix; the source renders them before that matrix.
4. Source-positioned closed fuel fillers were added to the rig and r15 pack; ignition transforms/assets were audited without incorrectly forcing source-default-hidden key holes visible.
5. Fertilizer/Seeder source cargo-pile transforms were added.
6. Fertilizer/Seeder work points now run every tick, matching the original entities while stationary.
7. Independent regression tests now cover common body equations, wheel centers/scales/contact, seats, engines, steering, fuel/ignition parts, tow bars, Vehicle Trailer offsets, rear-axle wheelie order, motorcycle fork matrices, every trailer hitch/wheel/part equation, and cargo layouts.
8. The r16 Moped port adds its serialized physics, all body/cosmetic parts, exact fork-linked handles/mud guard/front wheel, original engine sample, fuel filler, Vehicle Trailer offset, and persistent attachable 27-slot chest.
9. The r17 Off Roader port adds its four-seat geometry, nested `1.4 * 1.4` wheel scaling, exact rotated-local steering transform, fuel/ignition transforms, 25,000-unit tank, and original shared Jet Ski engine sample.
10. Vehicle entry now chooses the closest available source seat, exposing the Off Roader's rear hanging positions without requiring every earlier seat to be occupied.
11. Fertilizer/Seeder cargo entities are reconciled in place while supplies are consumed, removing movement-induced visual jumps without changing the source pile matrices.
12. The source Off Roader's elevated standing/hanging rear seat is lowered to the other rear seat's Y because vanilla forces every mounted player into a seated pose.
13. The r18 Sports Car port adds its two-seat generated physics, complete base plus seven-cosmetic body, rendered large engine, steering/filler transforms, four source open actions and samples, original engine loop, and persistent independent Glove Box/Trunk inventories.
14. The obsolete r4-only milestone workflow was repaired and advanced to validate and publish the current r19 artifact instead of referencing a pack the current build no longer produced.
15. The Sports Car steering Y center was corrected from `0.59400625` to the exact renderer result `0.59399375` after re-evaluating the source `-1.0961`-pixel translation.
16. The r19 Mini Bus port adds five-seat generated geometry, eight default cosmetics, front-door/sliding-door actions, exact filler/ignition/tow transforms, the source big tow bar, and the 113,610-sample original engine loop. The missing dedicated source steering asset is replaced only with the Go Kart wheel named by the original Mini Bus ray transforms.

## Exact ports versus vanilla-client adaptations

Exact matrix/equation ports:

- generated body, wheel, seat, hitch, tow, engine, steering, filler, ignition, trailer-part, fluid-bound, wheelie, motorcycle-roll, and motorcycle fork coordinates;
- source renderer translation/rotation order and generated-property scaling;
- source Vehicle Trailer passenger offsets and trailer hitch distances;
- source cargo layout equations and trailer work-point positions;
- Sports Car cosmetic pivots/open angles/easing, storage capacities and interaction bounds, and persistent action/inventory state;
- Mini Bus wheel/seat/cosmetic/openable/filler/ignition/tow geometry and big-tow-bar selection.

Vanilla-client adaptations that intentionally remain:

- riders use invisible minimum-scale Pigs rather than modded seat rendering. The source seat coordinate is preserved, then the accepted global `+0.25` block rider correction and the Pig passenger-offset compensation are applied;
- the Storage Trailer chest keeps the user-requested additional `+0.5` block centered-item correction because vanilla's chest item anchor differs from the source bottom-anchored `ChestModel`;
- the Moped's attached chest uses a closed vanilla chest item at the source-compensated center. Its 27-slot inventory, attachment state, contents, open/close sounds, content drops, and runtime drop point are ported; attachment also preserves the source's selected-chest behavior (the stack is not decremented), but the vanilla display cannot animate the custom source lid; sneak-right-click replaces the unavailable mod-wrench removal packet;
- the fluid uses a `BlockDisplay` with exact source cuboid bounds rather than the source custom translucent tessellator;
- ItemDisplays, interpolation, native item models, and vanilla interaction hitboxes replace Forge client render/ray-trace objects;
- the Mini Bus uses the original Go Kart steering-wheel asset named by its source ray transforms because the renderer's separately registered Mini Bus steering model is absent from the repository;
- custom per-limb player pose animation, damage wobble/destroy overlays, open fuel-door animation, and inserted-key animation are not representable with the current vanilla-client rig. These limitations do not change the audited static part coordinates.
