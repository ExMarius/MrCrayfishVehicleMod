# MrCrayfish Vehicle Plugin prototype

Paper 1.21.4 server-side port for unmodified (vanilla) clients. The current checkpoint includes the Go Kart, Lawn Mower, and two-seat Quad Bike on a shared property-driven land-vehicle runtime. It directly ports the original generated properties, renderer matrix order, input smoothing, land-vehicle equations, charging/boosting, wheel animation, surface handling, sounds, and the Lawn Mower's bush-cutting drops.

## Build

Requires Java 21 and Gradle 9+.

```bash
./gradlew -p paper-plugin clean build resourcePack
```

Outputs:

- `paper-plugin/build/libs/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar`
- `paper-plugin/build/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r7.zip`

Published prototype downloads:

- [Plugin JAR](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar)
- [Mandatory resource pack](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r7.zip) (`SHA-1: 40f97bca4c1082f9f8afd2f0c542c859829537ba`; includes the GPLv3 license)

## Test

1. Install the JAR in a Paper 1.21.4 server's `plugins/` directory.
2. Host the resource-pack ZIP at a direct HTTPS URL.
3. Put the URL and SHA-1 in `plugins/MrCrayfishVehiclePlugin/config.yml`.
4. Restart and run `/vehicle spawn go_kart`, `/vehicle spawn lawn_mower`, or `/vehicle spawn quad_bike` as an operator.
5. Right-click a vehicle to drive. Use W/S, A/D, Space for the handbrake, and Shift to dismount. Operators can refill the nearest vehicle with `/vehicle refuel`.

The Forge project remains intact as the source of truth. Each Paper vehicle uses its original collision dimensions and property transforms with vanilla-style axis collision/stepping. The accepted Go Kart retains its 1.05-block step height so it can traverse a full block.
