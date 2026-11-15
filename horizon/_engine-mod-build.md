# modul de masini — ultima incercare (unelte oficiale)

## native.py build
[1m[92m   Compiling[0m horizon-mods v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-mods)
[1m[92m   Compiling[0m rayon v1.12.0
[1m[92m   Compiling[0m horizon-mod-api-gen v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-mod-api-gen)
[1m[92m   Compiling[0m horizon-telemetry v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-telemetry)
[1m[92m   Compiling[0m winresource v0.1.34
[1m[92m   Compiling[0m horizon-anticheat v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-anticheat)
[1m[92m   Compiling[0m horizon-mcp v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-mcp)
[1m[92m   Compiling[0m fs4 v1.1.0
[1m[92m   Compiling[0m mio v1.2.4
[1m[92m   Compiling[0m horizon-client v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-client)
[1m[92m   Compiling[0m horizon-chat-filter v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-chat-filter)
[1m[92m   Compiling[0m horizon-cosmetics v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-cosmetics)
[1m[92m   Compiling[0m horizon-theme v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-theme)
[1m[92m   Compiling[0m horizon-client-lib v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-client-lib)
[1m[91merror[E0596][0m[1m: cannot borrow `*self` as mutable, as it is behind a `&` reference[0m
    [1m[94m--> [0mcrates/horizon-client/src/game/native_hooks.rs:3657:32
     [1m[94m|[0m
[1m[94m3657[0m [1m[94m|[0m             vehicle_mob_submit(&mut *self, mob, &mut *draw, &mut *drawn)
     [1m[94m|[0m                                [1m[91m^^^^^^^^^^[0m [1m[91m`self` is a `&` reference, so it cannot be borrowed as mutable[0m
     [1m[94m|[0m
[1m[96mhelp[0m: consider changing this to be a mutable reference
     [1m[94m|[0m
[1m[94m3643[0m [1m[94m| [0m        &[92mmut [0mself,
     [1m[94m|[0m          [92m+++[0m

[1mFor more information about this error, try `rustc --explain E0596`.[0m
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
[1m[92m   Compiling[0m pollster v0.4.0
[1m[92m   Compiling[0m unicode-bidi v0.3.18
[1m[92m   Compiling[0m horizon-client-render v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-client-render)
[1m[92m   Compiling[0m horizon-mods v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-mods)
[1m[92m   Compiling[0m rayon v1.12.0
[1m[92m   Compiling[0m horizon-client v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-client)
[1m[92m   Compiling[0m horizon-telemetry v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-telemetry)
[1m[92m   Compiling[0m horizon-anticheat v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-anticheat)
[1m[92m   Compiling[0m horizon-mcp v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-mcp)
[1m[92m   Compiling[0m fs4 v1.1.0
[1m[92m   Compiling[0m mio v1.2.4
[1m[92m   Compiling[0m horizon-chat-filter v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-chat-filter)
[1m[92m   Compiling[0m horizon-cosmetics v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-cosmetics)
[1m[92m   Compiling[0m horizon-theme v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-theme)
[1m[92m   Compiling[0m horizon-client-lib v0.1.0 (/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage/vehicle/crates/horizon-client-lib)
[1m[91merror[E0596][0m[1m: cannot borrow `*self` as mutable, as it is behind a `&` reference[0m
    [1m[94m--> [0mcrates/horizon-client/src/game/native_hooks.rs:3657:32
     [1m[94m|[0m
[1m[94m3657[0m [1m[94m|[0m             vehicle_mob_submit(&mut *self, mob, &mut *draw, &mut *drawn)
     [1m[94m|[0m                                [1m[91m^^^^^^^^^^[0m [1m[91m`self` is a `&` reference, so it cannot be borrowed as mutable[0m
     [1m[94m|[0m
[1m[96mhelp[0m: consider changing this to be a mutable reference
     [1m[94m|[0m
[1m[94m3643[0m [1m[94m| [0m        &[92mmut [0mself,
     [1m[94m|[0m          [92m+++[0m

[1mFor more information about this error, try `rustc --explain E0596`.[0m
[1m[91merror[0m: could not compile `horizon-client` (lib) due to 1 previous error
[1m[33mwarning[0m: build failed, waiting for other jobs to finish...
exit=101

## binar
