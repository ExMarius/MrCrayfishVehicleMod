# MrCrayfish Vehicle Plugin prototype

Paper 1.21.4 server-side port for unmodified (vanilla) clients. The current checkpoint includes the Go Kart, Lawn Mower, two-seat Quad Bike, Tractor, two-seat Dirt Bike, Moped with attachable 27-slot chest, four-seat Off Roader, two-seat Sports Car with persistent 9-slot Glove Box and 27-slot Trunk, five-seat Mini Bus, four-seat Golf Cart, two-seat Jet Ski, and all five original towables: Fertilizer, Seeder, Storage Trailer, Fluid Trailer, and Vehicle Trailer. It ports the generated properties, renderer transforms, land and released aquatic equations, charging/boosting, wheel animation, hitch offsets, trailer following/chains, farming equipment, inventories, 100-bucket fluid tank, vehicle transport, and source openable cosmetics. The reusable motorcycle rig adds source-aligned speed/steering lean plus a tilted steering axis shared by the handles and front wheel. Resource pack r21 adds the complete original Jet Ski body while retaining its already packaged source engine sample and all r20 assets; the complete matrix audit is recorded in [`SOURCE_POSITION_AUDIT.md`](SOURCE_POSITION_AUDIT.md).

The engine controller uses the original OGG assets, original pitch equations and 0.2 interpolation. Because a vanilla client does not expose Forge's continuously mutable `TickableSound`, the server replays each source sample at its actual pitch-adjusted duration and attaches it to the moving vehicle entity. This avoids the former overlapping fixed-position 18-tick impulses while remaining honest about the protocol limitation.

The current rider-pose implementation mounts every seat occupant on a minimum-scale invisible living carrier carried by the existing smooth display anchor. This asks an unmodified client to use its native mounted posture while preserving each source seat position and the accepted rider-height correction. The technical carrier is deliberately not an `AbstractHorse`: Paper 1.21.4 can loop indefinitely in its horse-only dismount-location search when a scaled carrier is invalidated. Carriers exist only while a seat is occupied and are removed on dismount. Entity-wide kill selectors are rewritten automatically: the plugin adds `tag=!mcv_plugin_vehicle` and `type=!minecraft:player` to the `/kill @e` target, protecting vehicle rigs and players while allowing the command to remove other entities. Players riding an unprotected ordinary mount are detached before that mount can be killed.

The vanilla mount-heart HUD is retained in resource pack r21 and acts as a shared ten-heart fuel gauge. Every occupied seat receives the same value from its vehicle's persisted fuel level, so the driver and passengers see matching hearts. Half-hearts round down, making the first fuel use visible immediately; the final half-heart represents empty because a living carrier cannot remain alive at zero health. A source-style action-bar overlay also reports the continuously changing `Fuel: current / capacity (percent)` value to every occupant. As in the original mod, Creative-mode drivers do not consume fuel. Ordinary horses retain their normal health display.

## Build

Requires Java 21 and Gradle 9+.

```bash
./gradlew -p paper-plugin clean build resourcePack
```

Outputs:

- `paper-plugin/build/libs/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar`
- `paper-plugin/build/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r21.zip`

Published prototype downloads:

- [Plugin JAR](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-0.1.0-SNAPSHOT.jar)
- [Mandatory resource pack](https://github.com/ExMarius/MrCrayfishVehicleMod/releases/download/vehicle-plugin-prototype-v0.1.0/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r21.zip) (`SHA-1: 7190152d7a45d927064f57e82acabe85ddd8dc96`; includes the GPLv3 license)

## FalixNodes deployment

The deployment job reads `FALIX_SFTP_HOST`, `FALIX_SFTP_PORT`,
`FALIX_SFTP_USERNAME`, and `FALIX_SFTP_PASSWORD` exclusively from GitHub
Actions repository secrets. It runs only while the temporary
`.github/falix-deploy-request` marker exists. The job also synchronizes the same
pack URL, SHA-1, stable pack ID, prompt, and required flag into `server.properties`
and keeps a remote backup before any change. Paper then supplies the pack once
during login; when the plugin detects that same server-level URL, it suppresses
its delayed post-join send so clients do not perform two resource reloads. Secret
values must never be committed, written to release notes, or supplied as workflow
inputs.

## Test

1. Install the JAR in a Paper 1.21.4 server's `plugins/` directory.
2. Host the resource-pack ZIP at a direct HTTPS URL.
3. Put the URL and SHA-1 in `plugins/MrCrayfishVehiclePlugin/config.yml`.
4. Restart and use `/vehicle spawn <type>` as an operator. Tab completion lists all eleven vehicles and five trailers.
5. Right-click a vehicle to drive. Use W/S, A/D, Space for the handbrake, and Shift to dismount.
6. Spawn a Moped and verify its source-positioned body, cosmetics, steering-linked handles/mud guard/front wheel, lean, fuel filler, and original engine sample. Right-click it while holding a vanilla Chest to attach storage; matching the source, attachment does not decrement the selected chest stack. Click the rear chest to open its 27 slots, or sneak-right-click that chest to detach it and drop its contents.
7. On the Dirt Bike, verify both seats, steering-linked handles/front wheel, wheel spin, speed-dependent body lean, full-block traversal, exhaust, and the original engine sample.
8. Spawn an Off Roader and verify all four seats, large wheels, steering wheel, source fuel-filler position, wheelie/boost behavior, and the original shared Jet Ski engine sample. Approach and right-click it from the rear to select the closest free rear seat. Because vanilla cannot render the source standing/hanging limb pose, both rear riders intentionally use the same lower Y and accepted horse-style posture.
9. Spawn a Sports Car and verify its base plus all seven default cosmetics, two source-positioned seats, four 1.4-scale wheels, rendered large engine, steering wheel, fuel filler, charging/boosting, and original engine loop. Right-click the hood, either door, or boot to run the source 12-tick eased opening action with the original sounds. Open the boot, then target the storage region behind it to access the 27-slot Trunk; target the passenger-side dashboard region for the 9-slot Glove Box. Verify both inventories and open states survive restart/hibernation and Vehicle Trailer transport.
10. Spawn a Mini Bus and verify all five source seats, body and eight default cosmetics, four 1.19-scale wheels, fuel filler, ignition position, original engine loop, and the source oversized tow bar. The front doors open over 12 ticks and the left sliding door over 20 ticks with source easing/sounds. Hitch every compatible trailer and verify the `-25`-pixel source hitch offset. The source repository references a missing dedicated steering-wheel asset; the Paper port intentionally uses the original Go Kart wheel identified by the source Mini Bus ray transforms rather than allowing a purple/black fallback.
11. Spawn a Golf Cart and verify the complete 86-element original body, four wheels, steering wheel, fuel and ignition transforms, front seats, and two rear-facing rear seats. The original entity class is an unfinished `HelicopterEntity` subclass that cannot provide working land motion; this port explicitly applies the generated axle, wheel, steering, electric-power and seat properties to the established source land equations. It retains the unusual helicopter-rotor sample selected by those generated properties rather than substituting an invented motor sound.
12. Spawn a Jet Ski in water and verify its complete 42-element body, two seats, small fuel filler, steering-linked Quad Bike handles, splash/bubble wake, original engine loop, source surface-height rise with speed, underwater buoyancy, and carried momentum after leaving the water. The 1.16.X-dev `BoatEntity#updateVehicleMotion` is empty; r21 restores the last complete released 1.16.X water-state and buoyancy equations rather than pretending that unfinished method was functional.
13. Sneak-right-click a trailer to pull it, then right-click a Lawn Mower, Quad Bike, Tractor, Mini Bus, or Storage Trailer to hitch it. Press Shift again to release a player-pulled trailer at its current position, matching the original crouch-to-release behavior. Storage Trailer is the chain-capable trailer.
14. Right-click Fertilizer/Seeder/Storage Trailer to open its inventory. Fertilizer accepts bone meal, Seeder accepts crop seeds, and farming equipment can consume supplies through an upstream Storage Trailer. While planting or fertilizing, verify that the visible seed/bone-meal pile remains attached to the trailer instead of jumping outward when an item is consumed.
15. Use water, lava, or powder-snow buckets on Fluid Trailer. Its capacity is 100 buckets.
16. Sneak-right-click an unoccupied vehicle to carry it, then sneak-right-click Vehicle Trailer to load it, matching the original pickup flow. To unload it, sneak-right-click the carried vehicle and right-click the ground.
17. Operators can refill the nearest powered vehicle with `/vehicle refuel` and remove the nearest vehicle/trailer with `/vehicle remove`.

The Forge project remains intact as the source of truth. Each Paper vehicle uses its original collision dimensions and property transforms with vanilla-style axis collision/stepping. The accepted Go Kart retains its 1.05-block step height so it can traverse a full block; the Dirt Bike uses the source land-vehicle one-block step height.
