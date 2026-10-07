# MrCrayfish Vehicle Plugin prototype

Paper 1.21.4 server-side port for unmodified (vanilla) clients. The current checkpoint includes the Go Kart, Lawn Mower, two-seat Quad Bike, Tractor, two-seat Dirt Bike, Moped with attachable 27-slot chest, and all five original towables: Fertilizer, Seeder, Storage Trailer, Fluid Trailer, and Vehicle Trailer. It ports the generated properties, renderer transforms, land-vehicle equations, charging/boosting, wheel animation, hitch offsets, trailer following/chains, farming equipment, inventories, 100-bucket fluid tank, and vehicle transport. The reusable motorcycle rig adds source-aligned speed/steering lean plus a tilted steering axis shared by the handles and front wheel. Resource pack r16 adds every original Moped body/cosmetic/fork asset and engine sample, while retaining the r15 source-positioned fuel fillers, audited Quad Bike/Tractor ignition assets, and Fertilizer/Seeder cargo piles; the complete matrix audit is recorded in [`SOURCE_POSITION_AUDIT.md`](SOURCE_POSITION_AUDIT.md).

The engine controller uses the original OGG assets, original pitch equations and 0.2 interpolation. Because a vanilla client does not expose Forge's continuously mutable `TickableSound`, the server replays each source sample at its actual pitch-adjusted duration and attaches it to the moving vehicle entity. This avoids the former overlapping fixed-position 18-tick impulses while remaining honest about the protocol limitation.

The current rider-pose implementation mounts every seat occupant on a minimum-scale invisible living carrier carried by the existing smooth display anchor. This asks an unmodified client to use its native mounted posture while preserving each source seat position and the accepted rider-height correction. The technical carrier is deliberately not an `AbstractHorse`: Paper 1.21.4 can loop indefinitely in its horse-only dismount-location search when a scaled carrier is invalidated. Carriers exist only while a seat is occupied and are removed on dismount. Entity-wide kill selectors are rewritten automatically: the plugin adds `tag=!mcv_plugin_vehicle` and `type=!minecraft:player` to the `/kill @e` target, protecting vehicle rigs and players while allowing the command to remove other entities. Players riding an unprotected ordinary mount are detached before that mount can be killed.

The vanilla mount-heart HUD is retained in resource pack r16 and acts as a shared ten-heart fuel gauge. Every occupied seat receives the same value from its vehicle's persisted fuel level, so the driver and passengers see matching hearts. Half-hearts round down, making the first fuel use visible immediately; the final half-heart represents empty because a living carrier cannot remain alive at zero health. A source-style action-bar overlay also reports the continuously changing `Fuel: current / capacity (percent)` value to every occupant. As in the original mod, Creative-mode drivers do not consume fuel. Ordinary horses retain their normal health display.

## Build

Requires Java 21 and Gradle 9+.

```bash
./gradlew -p paper-plugin clean build resourcePack
```

Outputs:

- `paper-plugin/build/libs/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar`
- `paper-plugin/build/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r16.zip`

Published prototype downloads:

- [Plugin JAR](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar)
- [Mandatory resource pack](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r16.zip) (`SHA-1: cc3ffde9525357e2200582df9c8e31fa5c9640d3`; includes the GPLv3 license)

## FalixNodes deployment

The deployment job reads `FALIX_SFTP_HOST`, `FALIX_SFTP_PORT`,
`FALIX_SFTP_USERNAME`, and `FALIX_SFTP_PASSWORD` exclusively from GitHub
Actions repository secrets. It runs only while the temporary
`.github/falix-deploy-request` marker exists. Secret values must never be
committed, written to release notes, or supplied as workflow inputs.

## Test

1. Install the JAR in a Paper 1.21.4 server's `plugins/` directory.
2. Host the resource-pack ZIP at a direct HTTPS URL.
3. Put the URL and SHA-1 in `plugins/MrCrayfishVehiclePlugin/config.yml`.
4. Restart and use `/vehicle spawn <type>` as an operator. Tab completion lists all six vehicles and five trailers.
5. Right-click a vehicle to drive. Use W/S, A/D, Space for the handbrake, and Shift to dismount.
6. Spawn a Moped and verify its source-positioned body, cosmetics, steering-linked handles/mud guard/front wheel, lean, fuel filler, and original engine sample. Right-click it while holding a vanilla Chest to attach storage; matching the source, attachment does not decrement the selected chest stack. Click the rear chest to open its 27 slots, or sneak-right-click that chest to detach it and drop its contents.
7. On the Dirt Bike, verify both seats, steering-linked handles/front wheel, wheel spin, speed-dependent body lean, full-block traversal, exhaust, and the original engine sample.
8. Sneak-right-click a trailer to pull it, then right-click a Lawn Mower, Quad Bike, Tractor, or Storage Trailer to hitch it. Press Shift again to release a player-pulled trailer at its current position, matching the original crouch-to-release behavior. Storage Trailer is the chain-capable trailer.
9. Right-click Fertilizer/Seeder/Storage Trailer to open its inventory. Fertilizer accepts bone meal, Seeder accepts crop seeds, and farming equipment can consume supplies through an upstream Storage Trailer.
10. Use water, lava, or powder-snow buckets on Fluid Trailer. Its capacity is 100 buckets.
11. Sneak-right-click an unoccupied vehicle to carry it, then sneak-right-click Vehicle Trailer to load it, matching the original pickup flow. To unload it, sneak-right-click the carried vehicle and right-click the ground.
12. Operators can refill the nearest powered vehicle with `/vehicle refuel` and remove the nearest vehicle/trailer with `/vehicle remove`.

The Forge project remains intact as the source of truth. Each Paper vehicle uses its original collision dimensions and property transforms with vanilla-style axis collision/stepping. The accepted Go Kart retains its 1.05-block step height so it can traverse a full block; the Dirt Bike uses the source land-vehicle one-block step height.
