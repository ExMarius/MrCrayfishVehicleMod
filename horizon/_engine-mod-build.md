# modul de masini — ultima incercare (unelte oficiale)

## native.py build
[1m[92m   Compiling[0m fs4 v1.1.0
[1m[92m   Compiling[0m mio v1.2.4
[1m[92m   Compiling[0m horizon-client v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-client)
[1m[92m   Compiling[0m horizon-chat-filter v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-chat-filter)
[1m[92m   Compiling[0m horizon-cosmetics v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-cosmetics)
[1m[92m   Compiling[0m horizon-theme v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-theme)
[1m[92m   Compiling[0m horizon-client-lib v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-client-lib)
[1m[91merror[0m[1m: type `game::Game<'_>` is more private than the item `on_mob_submit`[0m
     [1m[94m--> [0mcrates/horizon-client/src/game.rs:12844:5
      [1m[94m|[0m
[1m[94m12844[0m [1m[94m|[0m [1m[91m/[0m     pub fn on_mob_submit(
[1m[94m12845[0m [1m[94m|[0m [1m[91m|[0m         game: &Game<'_>,
[1m[94m12846[0m [1m[94m|[0m [1m[91m|[0m         mob: &crate::game::native_mob_draw::NativeMobRender<'_>,
[1m[94m12847[0m [1m[94m|[0m [1m[91m|[0m         draw: &mut crate::game::native_block_entity_draw::BlockEntityDraw,
[1m[94m12848[0m [1m[94m|[0m [1m[91m|[0m         drawn: &mut bool,
[1m[94m12849[0m [1m[94m|[0m [1m[91m|[0m     ) {
      [1m[94m|[0m [1m[91m|_____^[0m [1m[91mfunction `on_mob_submit` is reachable at visibility `pub(crate)`[0m
      [1m[94m|[0m
[1m[92mnote[0m: but type `game::Game<'_>` is only usable at visibility `pub(self)`
     [1m[94m--> [0mcrates/horizon-client/src/game.rs:2177:1
      [1m[94m|[0m
[1m[94m 2177[0m [1m[94m|[0m struct Game<'a> {
      [1m[94m|[0m [1m[92m^^^^^^^^^^^^^^^[0m
      [1m[94m= [0m[1mnote[0m: `-D private-interfaces` implied by `-D warnings`
      [1m[94m= [0m[1mhelp[0m: to override `-D warnings` add `#[allow(private_interfaces)]`

[1m[91merror[0m: could not compile `horizon-client` (lib) due to 1 previous error
[1m[33mwarning[0m: build failed, waiting for other jobs to finish...
Traceback (most recent call last):
  File "/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/tools/native.py", line 273, in <module>
    main()
  File "/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/tools/native.py", line 257, in main
    binaries = cargo(out, "build")
               ^^^^^^^^^^^^^^^^^^^
  File "/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/tools/native.py", line 187, in cargo
    subprocess.run(command, cwd=stage_dir, env=env, check=True)
  File "/usr/lib/python3.12/subprocess.py", line 571, in run
    raise CalledProcessError(retcode, process.args,
subprocess.CalledProcessError: Command '['cargo', 'build', '--locked', '-p', 'horizon-client', '--bin', 'horizon-client', '-p', 'horizon-client-lib', '--bin', 'horizon-ctl', '--profile', 'fast', '--no-default-features']' returned non-zero exit status 101.
exit=1

## release
[1m[92m   Compiling[0m horizon-telemetry v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-telemetry)
[1m[92m   Compiling[0m horizon-anticheat v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-anticheat)
[1m[92m   Compiling[0m horizon-mcp v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-mcp)
[1m[92m   Compiling[0m fs4 v1.1.0
[1m[92m   Compiling[0m mio v1.2.4
[1m[92m   Compiling[0m horizon-chat-filter v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-chat-filter)
[1m[92m   Compiling[0m horizon-cosmetics v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-cosmetics)
[1m[92m   Compiling[0m horizon-theme v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-theme)
[1m[92m   Compiling[0m horizon-client-lib v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-client-lib)
[1m[33mwarning[0m[1m: type `game::Game<'_>` is more private than the item `on_mob_submit`[0m
     [1m[94m--> [0mcrates/horizon-client/src/game.rs:12844:5
      [1m[94m|[0m
[1m[94m12844[0m [1m[94m|[0m [1m[33m/[0m     pub fn on_mob_submit(
[1m[94m12845[0m [1m[94m|[0m [1m[33m|[0m         game: &Game<'_>,
[1m[94m12846[0m [1m[94m|[0m [1m[33m|[0m         mob: &crate::game::native_mob_draw::NativeMobRender<'_>,
[1m[94m12847[0m [1m[94m|[0m [1m[33m|[0m         draw: &mut crate::game::native_block_entity_draw::BlockEntityDraw,
[1m[94m12848[0m [1m[94m|[0m [1m[33m|[0m         drawn: &mut bool,
[1m[94m12849[0m [1m[94m|[0m [1m[33m|[0m     ) {
      [1m[94m|[0m [1m[33m|_____^[0m [1m[33mfunction `on_mob_submit` is reachable at visibility `pub(crate)`[0m
      [1m[94m|[0m
[1m[92mnote[0m: but type `game::Game<'_>` is only usable at visibility `pub(self)`
     [1m[94m--> [0mcrates/horizon-client/src/game.rs:2177:1
      [1m[94m|[0m
[1m[94m 2177[0m [1m[94m|[0m struct Game<'a> {
      [1m[94m|[0m [1m[92m^^^^^^^^^^^^^^^[0m
      [1m[94m= [0m[1mnote[0m: `#[warn(private_interfaces)]` on by default

[1m[33mwarning[0m: `horizon-client` (lib) generated 1 warning
[1m[92m    Finished[0m `release` profile [optimized] target(s) in 12m 06s
exit=0

## binar
-rwxr-xr-x 1 runner runner 85M Nov 15 16:45 out/horizon-engine-mod
