# MrCrayfish Vehicle Plugin prototype

Paper 1.21.4 server-side port for unmodified (vanilla) clients. The current checkpoint includes the Go Kart, Lawn Mower, two-seat Quad Bike, and all five original towables: Fertilizer, Seeder, Storage Trailer, Fluid Trailer, and Vehicle Trailer. It ports the generated properties, renderer transforms, land-vehicle equations, charging/boosting, wheel animation, hitch offsets, trailer following/chains, farming equipment, inventories, 100-bucket fluid tank, and vehicle transport.

The engine controller uses the original OGG assets, original pitch equations and 0.2 interpolation. Because a vanilla client does not expose Forge's continuously mutable `TickableSound`, the server replays each source sample at its actual pitch-adjusted duration and attaches it to the moving vehicle entity. This avoids the former overlapping fixed-position 18-tick impulses while remaining honest about the protocol limitation.

## Build

Requires Java 21 and Gradle 9+.

```bash
./gradlew -p paper-plugin clean build resourcePack
```

Outputs:

- `paper-plugin/build/libs/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar`
- `paper-plugin/build/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r8.zip`

Published prototype downloads:

- [Plugin JAR](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar)
- [Mandatory resource pack](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r8.zip) (`SHA-1: 3d709a33ade14fded2fcffc33c658a2187072658`; includes the GPLv3 license)

## Test

1. Install the JAR in a Paper 1.21.4 server's `plugins/` directory.
2. Host the resource-pack ZIP at a direct HTTPS URL.
3. Put the URL and SHA-1 in `plugins/MrCrayfishVehiclePlugin/config.yml`.
4. Restart and use `/vehicle spawn <type>` as an operator. Tab completion lists all three vehicles and five trailers.
5. Right-click a vehicle to drive. Use W/S, A/D, Space for the handbrake, and Shift to dismount.
6. Sneak-right-click a trailer to pull it, then sneak-right-click a Lawn Mower, Quad Bike, or Storage Trailer to hitch it. Storage Trailer is the chain-capable trailer.
7. Right-click Fertilizer/Seeder/Storage Trailer to open its inventory. Fertilizer accepts bone meal, Seeder accepts crop seeds, and farming equipment can consume supplies through an upstream Storage Trailer.
8. Use water, lava, or powder-snow buckets on Fluid Trailer. Its capacity is 100 buckets.
9. Right-click Vehicle Trailer near an unoccupied vehicle to load it; right-click again to unload it.
10. Operators can refill the nearest powered vehicle with `/vehicle refuel` and remove the nearest vehicle/trailer with `/vehicle remove`.

The Forge project remains intact as the source of truth. Each Paper vehicle uses its original collision dimensions and property transforms with vanilla-style axis collision/stepping. The accepted Go Kart retains its 1.05-block step height so it can traverse a full block.
