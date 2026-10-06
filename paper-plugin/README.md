# MrCrayfish Vehicle Plugin prototype

Paper 1.21.4 server-side port for unmodified (vanilla) clients. The prototype currently targets the Go Kart and directly ports the original generated properties, renderer matrix order, input smoothing, land-vehicle equations, charging/boosting, wheel animation, surface handling, and sounds.

## Build

Requires Java 21 and Gradle 9+.

```bash
./gradlew -p paper-plugin clean build resourcePack
```

Outputs:

- `paper-plugin/build/libs/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar`
- `paper-plugin/build/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r4.zip`

Published prototype downloads:

- [Plugin JAR](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar)
- [Mandatory resource pack](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r4.zip) (`SHA-1: 94c646cbf4e1812263703b1a287b925fb9a4d17a`; includes the GPLv3 license)

## Test

1. Install the JAR in a Paper 1.21.4 server's `plugins/` directory.
2. Host the resource-pack ZIP at a direct HTTPS URL.
3. Put the URL and SHA-1 in `plugins/MrCrayfishVehiclePlugin/config.yml`.
4. Restart and run `/vehicle spawn go_kart` as an operator.
5. Right-click the Go Kart to drive. Use W/S, A/D, Space for the handbrake, and Shift to dismount. Operators can refill the nearest kart with `/vehicle refuel`.

The Forge project remains intact as the source of truth. The Paper port uses a 1.5 × 0.5 collision volume and vanilla-style axis collision/stepping, with a 1.05-block step height so the Go Kart can traverse a full block.
