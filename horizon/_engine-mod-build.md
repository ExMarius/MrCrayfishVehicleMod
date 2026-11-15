# modul de masini — ultima incercare (unelte oficiale)

## native.py build
vehicle 0.1.0 on Horizon 02fd158
Downloading Horizon 02fd158
Building horizon-mixins e8cd0aa
[1m[33mwarning[0m[1m: unused field `rust-version` in `workspace.package`[0m
 [1m[94m--> [0mCargo.toml:9:1
  [1m[94m|[0m
[1m[94m9[0m [1m[94m|[0m rust-version = "1.95"
  [1m[94m|[0m [1m[33m^^^^^^^^^^^^[0m
  [1m[94m|[0m
  [1m[94m= [0m[1mnote[0m: `cargo::unused_workspace_package_fields` is set to `warn` by default
[1m[96mhelp[0m: consider removing the field `workspace.package.rust-version`
{
  "code": "E_MODULE",
  "message": "support module source cannot have file attributes or a shebang"
}
Traceback (most recent call last):
  File "/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/tools/native.py", line 273, in <module>
    main()
  File "/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/tools/native.py", line 252, in main
    out = stage(mods, revision)
          ^^^^^^^^^^^^^^^^^^^^^
  File "/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/tools/native.py", line 163, in stage
    subprocess.run([str(tool(mods)), "apply", "--workspace", str(tree), "--mods", str(bundles), "--out", str(fresh)],
  File "/usr/lib/python3.12/subprocess.py", line 571, in run
    raise CalledProcessError(retcode, process.args,
subprocess.CalledProcessError: Command '['/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/horizon-mixins/e8cd0aa38cffef49cb2e088ef716baa107148c02/bin/horizon-mixins', 'apply', '--workspace', '/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/horizon/02fd15886bc700275247afa3e18ec9125e04fb34', '--mods', '/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/mods/vehicle', '--out', '/home/runner/work/MrCrayfishVehicleMod/MrCrayfishVehicleMod/horizon-mods/native/target/stage.new']' returned non-zero exit status 1.
exit=1

## release
lipseste stage-ul
exit=1

## binar
