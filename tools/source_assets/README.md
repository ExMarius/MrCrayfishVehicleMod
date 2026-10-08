# External source assets

- `cfm_sofa_single.json` is the original `sofa_single` model from MrCrayfish's Furniture Mod, branch `1.16.X`:
  `src/main/resources/assets/cfm/models/block/sofa_single.json`.
- `cfm_ceiling_fan_fans.json` is the original four-blade `ceiling_fan_fans` model from MrCrayfish's Furniture Mod, branch `1.12.2`:
  `src/main/resources/assets/cfm/models/item/ceiling_fan_fans.json`.

The original Vehicle Mod Sofacopter renderer selects `SpecialModels.BLADE`, whose model location is `cfm:ceiling_fan_fans`. The later 1.14–1.16 release branches used an aluminum boat because Furniture Mod had not yet ported the ceiling fan, while `1.16.X-dev` substituted a Sports Plane wing. Those are compatibility placeholders, not the original Sofacopter rotor. The source models here recover the intended red-sofa body and four-blade fan directly from the official Furniture Mod assets.

These models are included under the repositories' GPL-compatible source terms; the built resource pack includes this repository's `MOD-LICENSE.txt` as `LICENSE.txt`.
