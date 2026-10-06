# MrCrayfish Vehicle Plugin prototype

Paper 1.21.4 server-side port for unmodified (vanilla) clients. The prototype currently targets the Go Kart and ports the original land-vehicle equations, model layout, input mapping, and sounds.

## Build

Requires Java 21 and Gradle 9+.

```bash
./gradlew -p paper-plugin clean build resourcePack
```

Outputs:

- `paper-plugin/build/libs/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar`
- `paper-plugin/build/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r2.zip`

Published prototype downloads:

- [Plugin JAR](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar)
- [Mandatory resource pack](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r2.zip) (`SHA-1: f8cec7c71c78fb2b1d4f88b1a2de1228f3fe66f1`; includes the GPLv3 license)

## Test

1. Install the JAR in a Paper 1.21.4 server's `plugins/` directory.
2. Host the resource-pack ZIP at a direct HTTPS URL.
3. Put the URL and SHA-1 in `plugins/MrCrayfishVehiclePlugin/config.yml`.
4. Restart and run `/vehicle spawn go_kart` as an operator.
5. Right-click the Go Kart to drive. Use W/S, A/D, Space for the handbrake, and Shift to dismount. Operators can refill the nearest kart with `/vehicle refuel`.

This is an MVP. It intentionally keeps the original Forge project intact while the Paper implementation is validated.
