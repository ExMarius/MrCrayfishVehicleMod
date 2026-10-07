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

The r16 pack includes the original closed full/small fuel-port geometry. It also includes the key-hole geometry, while the Paper rig correctly leaves it hidden in the current default state: the source renders ignition/key parts only after its dynamic `NEEDS_KEY` state is enabled, and the Paper key system has not yet been ported.

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

### Vehicle Trailer passenger offsets

The serialized source offsets are:

- Go Kart `(0, -0.031, -0.375)`;
- Lawn Mower `(0, -0.010, -1.000)`;
- Quad Bike `(0, 0, -0.550)`;
- Tractor `(0, 0, 0)`;
- Dirt Bike `(0, -0.062, -0.312)`;
- Moped `(0, -0.031, -0.65)`.

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

Fertilizer and Seeder cargo displays now use the original per-stack count divisors, grid spacing, layer staggering, rotation order, and `0.45 * bodyScale` scale. Their three world-interaction points are evaluated every tick like the original entities. Seeder inventory acceptance retains the original Forge seeds-tag set; planting retains the source crop-block restriction.

## Corrections made by this audit

1. Tractor and Dirt Bike wheel X scales changed from generator input `0.9375` to runtime serialized `0.938`.
2. Dirt Bike Vehicle Trailer offsets changed from generator inputs `(-0.0625, -0.3125)` to runtime serialized `(-0.062, -0.312)`.
3. Tow bars no longer inherit the boost-wheelie matrix; the source renders them before that matrix.
4. Source-positioned closed fuel fillers were added to the rig and r15 pack; ignition transforms/assets were audited without incorrectly forcing source-default-hidden key holes visible.
5. Fertilizer/Seeder source cargo-pile transforms were added.
6. Fertilizer/Seeder work points now run every tick, matching the original entities while stationary.
7. Independent regression tests now cover common body equations, wheel centers/scales/contact, seats, engines, steering, fuel/ignition parts, tow bars, Vehicle Trailer offsets, rear-axle wheelie order, motorcycle fork matrices, every trailer hitch/wheel/part equation, and cargo layouts.
8. The r16 Moped port adds its serialized physics, all body/cosmetic parts, exact fork-linked handles/mud guard/front wheel, original engine sample, fuel filler, Vehicle Trailer offset, and persistent attachable 27-slot chest.

## Exact ports versus vanilla-client adaptations

Exact matrix/equation ports:

- generated body, wheel, seat, hitch, tow, engine, steering, filler, ignition, trailer-part, fluid-bound, wheelie, motorcycle-roll, and Dirt Bike fork coordinates;
- source renderer translation/rotation order and generated-property scaling;
- source Vehicle Trailer passenger offsets and trailer hitch distances;
- source cargo layout equations and trailer work-point positions.

Vanilla-client adaptations that intentionally remain:

- riders use invisible minimum-scale Pigs rather than modded seat rendering. The source seat coordinate is preserved, then the accepted global `+0.25` block rider correction and the Pig passenger-offset compensation are applied;
- the Storage Trailer chest keeps the user-requested additional `+0.5` block centered-item correction because vanilla's chest item anchor differs from the source bottom-anchored `ChestModel`;
- the Moped's attached chest uses a closed vanilla chest item at the source-compensated center. Its 27-slot inventory, attachment state, contents, open/close sounds, content drops, and runtime drop point are ported; attachment also preserves the source's selected-chest behavior (the stack is not decremented), but the vanilla display cannot animate the custom source lid; sneak-right-click replaces the unavailable mod-wrench removal packet;
- the fluid uses a `BlockDisplay` with exact source cuboid bounds rather than the source custom translucent tessellator;
- ItemDisplays, interpolation, native item models, and vanilla interaction hitboxes replace Forge client render/ray-trace objects;
- custom per-limb player pose animation, damage wobble/destroy overlays, open fuel-door animation, and inserted-key animation are not representable with the current vanilla-client rig. These limitations do not change the audited static part coordinates.
