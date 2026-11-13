# engine cu modul de masini — ultima incercare

revisiune: 02fd15886bc700275247afa3e18ec9125e04fb34

## clone
From https://github.com/horizon-rust/horizon
 * branch            02fd15886bc700275247afa3e18ec9125e04fb34 -> FETCH_HEAD
fetch exit=0
checkout exit=0
02fd15886bc700275247afa3e18ec9125e04fb34

## apply
[apply] modul vehicle a fost lipit in engine
[apply] horizon-src/crates/horizon-client/src/game/vehicle.rs
[apply] horizon-src/crates/horizon-client/src/game/native_hooks.rs
apply exit=0

## build

[1m[91merror[E0308][0m[1m: mismatched types[0m
   [1m[94m--> [0mcrates/horizon-client/src/game/vehicle.rs:256:35
    [1m[94m|[0m
[1m[94m256[0m [1m[94m|[0m         car.pos[2] += car.speed * yaw.cos();
    [1m[94m|[0m                                   [1m[91m^^^^^^^^^[0m [1m[91mexpected `f64`, found `f32`[0m

[1m[91merror[E0277][0m[1m: cannot multiply `f64` by `f32`[0m
   [1m[94m--> [0mcrates/horizon-client/src/game/vehicle.rs:256:33
    [1m[94m|[0m
[1m[94m256[0m [1m[94m|[0m         car.pos[2] += car.speed * yaw.cos();
    [1m[94m|[0m                                 [1m[91m^[0m [1m[91mno implementation for `f64 * f32`[0m
    [1m[94m|[0m
    [1m[94m= [0m[1mhelp[0m: the trait `std::ops::Mul<f32>` is not implemented for `f64`
    [1m[94m= [0m[1mhelp[0m: the following other types implement trait `std::ops::Mul<Rhs>`:
              `&f64` implements `std::ops::Mul<&DMat2>`
              `&f64` implements `std::ops::Mul<&DMat3>`
              `&f64` implements `std::ops::Mul<&DMat4>`
              `&f64` implements `std::ops::Mul<&DVec2>`
              `&f64` implements `std::ops::Mul<&DVec3>`
              `&f64` implements `std::ops::Mul<&DVec4>`
              `&f64` implements `std::ops::Mul<DMat2>`
              `&f64` implements `std::ops::Mul<DMat3>`
            and 21 others

[1mSome errors have detailed explanations: E0277, E0308.[0m
[1mFor more information about an error, try `rustc --explain E0277`.[0m
[1m[91merror[0m: could not compile `horizon-client` (lib) due to 4 previous errors
[1m[33mwarning[0m: build failed, waiting for other jobs to finish...
exit=101

## binar
OK=0
