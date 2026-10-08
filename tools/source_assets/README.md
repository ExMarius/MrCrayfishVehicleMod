# External source assets

- `cfm_sofa_single.json` is the original `sofa_single` model from MrCrayfish's Furniture Mod, branch `1.16.X`:
  `src/main/resources/assets/cfm/models/block/sofa_single.json`.
- `cfm_ceiling_fan_fans.json` is the original four-blade `ceiling_fan_fans` model from MrCrayfish's Furniture Mod, branch `1.12.2`:
  `src/main/resources/assets/cfm/models/item/ceiling_fan_fans.json`.
- `vehicle_dune_buggy_body.json` and `vehicle_dune_buggy_handles.json` are the exact released Vehicle Mod `1.16.X` Dune Buggy models:
  `src/main/resources/assets/vehicle/models/vehicle/dune_buggy_body.json` and `dune_buggy_handles.json`.
- `vehicle_bumper_car_engine.ogg` is the exact released Vehicle Mod `1.16.X` Bumper Car engine sample selected by `DuneBuggyEntity#getEngineSound()`:
  `src/main/resources/assets/vehicle/sounds/entity/bumper_car/engine.ogg`.
- `cfm_bath.json` is the original `bath` block model from MrCrayfish's Furniture Mod, `master` branch:
  `src/main/resources/assets/cfm/models/block/bath.json`, rotated 90 degrees around the Y axis (vertex
  positions remapped and faces relabeled: north/south become west/east and vice versa) so its long axis
  lines up with the vehicle rig's forward Z axis instead of CFM's native X axis. It was originally
  authored as a two-block-wide item (for the `bath_top`/`bath_bottom` pair) and, like the vehicle body
  models above it, already uses only vanilla block textures (water, white concrete, cyan terracotta,
  stone) as its own native design, so no dedicated PNG art is needed for it either.

The original Vehicle Mod Sofacopter renderer selects `SpecialModels.BLADE`, whose model location is `cfm:ceiling_fan_fans`. The later 1.14–1.16 release branches used an aluminum boat because Furniture Mod had not yet ported the ceiling fan, while `1.16.X-dev` substituted a Sports Plane wing. Those are compatibility placeholders, not the original Sofacopter rotor. The source models here recover the intended red-sofa body and four-blade fan directly from the official Furniture Mod assets.

These models are included under the repositories' GPL-compatible source terms; the built resource pack includes this repository's `MOD-LICENSE.txt` as `LICENSE.txt`.

