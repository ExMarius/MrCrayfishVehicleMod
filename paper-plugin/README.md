# MrCrayfish Vehicle Plugin prototype

Paper 1.21.4 server-side port for unmodified (vanilla) clients. The current checkpoint includes the Go Kart, Lawn Mower, two-seat Quad Bike, Tractor, two-seat Dirt Bike, and all five original towables: Fertilizer, Seeder, Storage Trailer, Fluid Trailer, and Vehicle Trailer. It ports the generated properties, renderer transforms, land-vehicle equations, charging/boosting, wheel animation, hitch offsets, trailer following/chains, farming equipment, inventories, 100-bucket fluid tank, and vehicle transport. The reusable motorcycle rig adds source-aligned speed/steering lean plus a tilted steering axis shared by the handles and front wheel.

The engine controller uses the original OGG assets, original pitch equations and 0.2 interpolation. Because a vanilla client does not expose Forge's continuously mutable `TickableSound`, the server replays each source sample at its actual pitch-adjusted duration and attaches it to the moving vehicle entity. This avoids the former overlapping fixed-position 18-tick impulses while remaining honest about the protocol limitation.

The current rider-pose experiment mounts every seat occupant on a minimum-scale invisible horse carried by the existing smooth display anchor. This asks an unmodified client to use its native horse-riding posture while preserving each source seat position and the accepted rider-height correction. Carriers exist only while a seat is occupied and are removed on dismount. Entity-wide `/kill @e` commands are blocked while the plugin is active because removing a mounted horse forces Minecraft's expensive horse dismount search; administrative selectors must include `tag=!mcv_plugin_vehicle`.

Resource pack r12 makes Minecraft's three dedicated vehicle-heart HUD sprites transparent, so the invisible carrier's health is not shown. Player health remains unchanged. This vanilla resource-pack mechanism also hides mount hearts while riding an ordinary horse with the mandatory pack active.

## Build

Requires Java 21 and Gradle 9+.

```bash
./gradlew -p paper-plugin clean build resourcePack
```

Outputs:

- `paper-plugin/build/libs/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar`
- `paper-plugin/build/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r12.zip`

Published prototype downloads:

- [Plugin JAR](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar)
- [Mandatory resource pack](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r12.zip) (`SHA-1: 20ca3d9d063d4d2a17e8d6524ae1b0ec13a71ef8`; includes the GPLv3 license)

## Test

1. Install the JAR in a Paper 1.21.4 server's `plugins/` directory.
2. Host the resource-pack ZIP at a direct HTTPS URL.
3. Put the URL and SHA-1 in `plugins/MrCrayfishVehiclePlugin/config.yml`.
4. Restart and use `/vehicle spawn <type>` as an operator. Tab completion lists all five vehicles and five trailers.
5. Right-click a vehicle to drive. Use W/S, A/D, Space for the handbrake, and Shift to dismount.
6. On the Dirt Bike, verify both seats, steering-linked handles/front wheel, wheel spin, speed-dependent body lean, full-block traversal, exhaust, and the original engine sample.
7. Sneak-right-click a trailer to pull it, then right-click a Lawn Mower, Quad Bike, Tractor, or Storage Trailer to hitch it. Storage Trailer is the chain-capable trailer.
8. Right-click Fertilizer/Seeder/Storage Trailer to open its inventory. Fertilizer accepts bone meal, Seeder accepts crop seeds, and farming equipment can consume supplies through an upstream Storage Trailer.
9. Use water, lava, or powder-snow buckets on Fluid Trailer. Its capacity is 100 buckets.
10. Sneak-right-click an unoccupied vehicle to carry it, then sneak-right-click Vehicle Trailer to load it, matching the original pickup flow. To unload it, sneak-right-click the carried vehicle and right-click the ground.
11. Operators can refill the nearest powered vehicle with `/vehicle refuel` and remove the nearest vehicle/trailer with `/vehicle remove`.

The Forge project remains intact as the source of truth. Each Paper vehicle uses its original collision dimensions and property transforms with vanilla-style axis collision/stepping. The accepted Go Kart retains its 1.05-block step height so it can traverse a full block; the Dirt Bike uses the source land-vehicle one-block step height.
