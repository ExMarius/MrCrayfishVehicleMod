// [vehicle-mod] Portul sistemului nostru de mașini pe Horizon — partea server-side.
//
// Horizon nu rulează cod Java, deci `paper-plugin/` nu poate fi încărcat. Modulul ăsta e un
// bundle nativ Horizon (`horizon-mixin.toml`) care intră în arborele sursă la compilare și rulează în engine,
// cu acces la lume, entități, input-ul jucătorilor și comenzile serverului.
//
// Stadiul curent: **M2** — go-kartul: schelet care se conduce + caroserie și roți vizibile
// (entități `item_display` cu modelele din resource pack-ul servit de server).
//
// Cum e făcută, și de ce așa:
//
// * **Scheletul** e un minecart invizibil (`Invisible`, `Invulnerable`, `NoGravity`). Am ales
//   minecart-ul pentru că e singurul tip de vehicul pe care Horizon îl lasă să poarte jucători
//   prin API-ul propriu (`native_mount`, API 472, ca `/ride`), și pentru că `native_teleport_vehicle`
//   îl mută împreună cu pasagerii.
// * **Mișcarea** e calculată de noi la fiecare tick de server (`native_server_tick`), nu de
//   fizica de șine a minecart-ului: poziția e impusă de `native_teleport_vehicle`.
// * **Tastarea** vine din pachetul `PlayerInput` (`native_player_input`), deci W/S/A/D ajung
//   la noi fără să ghicim mișcarea din diferențe de poziție.
// * **Comenzile** (`/car`) se înregistrează prin `native_register_commands` și rulează în
//   `native_perform_command`.
//
// Urmează M2: caroseria (entități `item_display` cu modelele modului, aduse printr-un resource
// pack servit de server) și urcarea pe teren.

use super::Game;
use crate::mod_api::{NativeCommand, NativeLifecycleContext, NativeServerPlayer, ServerTickPhase};
use crate::mod_api::Text;
use horizon_world::nbt::{Compound, Tag};
use std::collections::BTreeMap;
use std::sync::Mutex;

/// Tipul de entitate care ține locul mașinii (invizibilă din NBT).
const CARRIER: &str = "minecraft:minecart";

/// Biții din pachetul `PlayerInput` (vanilla 26.3).
const IN_FORWARD: i32 = 1;
const IN_BACK: i32 = 2;
const IN_LEFT: i32 = 4;
const IN_RIGHT: i32 = 8;
const IN_JUMP: i32 = 16;
const IN_SNEAK: i32 = 32;

/// O mașină vie.
struct Car {
    dimension: String,
    /// Id-ul entității-schelet.
    cart: u32,
    /// Cine o conduce (UUID-ul contului) și id-ul lui de rețea (pentru coborâre).
    driver: u128,
    driver_entity: u32,
    /// Poziția, viteza, unghiul și volanul — `land::Motion`, ca în Java.
    motion: land::Motion,
    /// Ultima poziție a șoferului, ca să vedem dacă s-a dat jos singur.
    mounted: bool,
    /// Entitatea care poarta modelul vehiculului (`vehicle:go_kart`, ...). Randarea
    /// ei vine din mod (vezi `on_mob_submit`), deci nu mai e nevoie de piese.
    model: u32,
    /// Numele vehiculului, fara namespace (`go_kart`, `sports_car`, ...).
    vehicle: String,
}

static CARS: Mutex<Vec<Car>> = Mutex::new(Vec::new());
/// Ultima stare de taste a fiecărui jucător.
static INPUTS: Mutex<BTreeMap<u128, i32>> = Mutex::new(BTreeMap::new());

fn lock_cars() -> std::sync::MutexGuard<'static, Vec<Car>> {
    CARS.lock().unwrap_or_else(|e| e.into_inner())
}

fn lock_inputs() -> std::sync::MutexGuard<'static, BTreeMap<u128, i32>> {
    INPUTS.lock().unwrap_or_else(|e| e.into_inner())
}

fn describe(flags: i32) -> String {
    const NAMES: [(i32, &str); 6] = [
        (IN_FORWARD, "inainte"),
        (IN_BACK, "inapoi"),
        (IN_LEFT, "stanga"),
        (IN_RIGHT, "dreapta"),
        (IN_JUMP, "salt"),
        (IN_SNEAK, "ghemuit"),
    ];
    let on: Vec<&str> = NAMES
        .iter()
        .filter(|(bit, _)| flags & bit != 0)
        .map(|(_, name)| *name)
        .collect();
    if on.is_empty() {
        "niciunul".to_string()
    } else {
        on.join("+")
    }
}

/// Comanda `/car`: pune mașina jos, sub jucător, și îl așază la volan.
pub(super) fn register_commands(_game: &mut Game<'_>, commands: &mut Vec<NativeCommand>) {
    commands.push(NativeCommand::new("car"));
    // `/masina [nume]` pune un vehicul al modului, ca entitate adevarata
    // (`vehicle:go_kart`, `vehicle:sports_car`, ...). Fara nume, le listeaza.
    commands.push(NativeCommand::new("masina"));
}


/// `/car` — pornește o mașină (sau spune unde e cea existentă).
pub(super) fn on_perform_command(
    game: &mut Game<'_>,
    player: &NativeServerPlayer,
    command: &str,
    ran: &mut bool,
) {
    let mut words = command.split_whitespace();
    let head = words.next();

    if head == Some("masina") {
        *ran = true;
        let wanted = words.next();
        let Some((id, _, _)) = (match wanted {
            None => None,
            Some(name) => VEHICLES
                .iter()
                .find(|(id, _, _)| id.rsplit(':').next() == Some(name)),
        }) else {
            let names: Vec<&str> = VEHICLES
                .iter()
                .filter_map(|(id, _, _)| id.rsplit(':').next())
                .collect();
            game.native_feedback(Text::literal(format!(
                "[masina] vehicule: {}",
                names.join(", ")
            )));
            return;
        };
        let pos = [
            player.position[0],
            player.position[1] + 0.1,
            player.position[2],
        ];
        match game.native_summon(&player.dimension, id, pos, Compound::new()) {
            Some(entity) => game.native_feedback(Text::literal(format!(
                "[masina] {} pus (entitate {entity})",
                id.rsplit(':').next().unwrap_or(id)
            ))),
            None => game.native_feedback(Text::literal("[masina] nu am putut pune vehiculul aici")),
        }
        return;
    }

    if head != Some("car") {
        return;
    }
    *ran = true;

    // `/car` fara argument = go-kart; `/car sports_car` = masina respectiva
    let vehicle: &str = match words.next() {
        None => "go_kart",
        Some(name) => match VEHICLES
            .iter()
            .find(|(id, _, _)| id.rsplit(':').next() == Some(name))
        {
            Some((id, _, _)) => id.rsplit(':').next().unwrap_or("go_kart"),
            None => {
                let names: Vec<&str> = VEHICLES
                    .iter()
                    .filter_map(|(id, _, _)| id.rsplit(':').next())
                    .collect();
                game.native_feedback(Text::literal(format!(
                    "[masina] vehicule: {}",
                    names.join(", ")
                )));
                return;
            }
        },
    };
    if player.entity_id <= 0 {
        game.native_feedback(Text::literal("[masina] nu stiu id-ul tau de retea inca, mai incearca"));
        return;
    }

    // o mașină per jucător
    {
        let cars = lock_cars();
        if cars.iter().any(|c| c.driver == player.uuid) {
            game.native_feedback(Text::literal("[masina] ai deja una; ghemuieste-te ca sa cobori"));
            return;
        }
    }

    let pos = [
        player.position[0],
        player.position[1] + 0.3,
        player.position[2],
    ];
    let mut nbt = Compound::new();
    nbt.insert("Invisible".into(), Tag::Byte(1));
    nbt.insert("Invulnerable".into(), Tag::Byte(1));
    nbt.insert("NoGravity".into(), Tag::Byte(1));
    nbt.insert("Silent".into(), Tag::Byte(1));

    let Some(cart) = game.native_summon(&player.dimension, CARRIER, pos, nbt) else {
        super::dedicated::log(
            "WARN",
            "[vehicle-mod] nu am putut pune minecart-ul (chunk neincarcat?)",
        );
        game.native_feedback(Text::literal("[masina] nu am putut pune masina aici"));
        return;
    };

    let driver_entity = player.entity_id as u32;
    if !game.native_mount(driver_entity, cart) {
        game.native_remove_entity(&player.dimension, cart);
        game.native_feedback(Text::literal("[masina] nu am putut sa te asez la volan"));
        return;
    }

    // vehiculul propriu-zis: entitatea modului, cu modelul lui, tinuta pe loc de noi
    let mut vehicle_nbt = Compound::new();
    vehicle_nbt.insert("NoAI".into(), Tag::Byte(1));
    vehicle_nbt.insert("NoGravity".into(), Tag::Byte(1));
    vehicle_nbt.insert("Invulnerable".into(), Tag::Byte(1));
    vehicle_nbt.insert("Silent".into(), Tag::Byte(1));
    vehicle_nbt.insert("PersistenceRequired".into(), Tag::Byte(1));
    let vehicle_id = format!("vehicle:{vehicle}");
    let Some(model) = game.native_summon(&player.dimension, &vehicle_id, pos, vehicle_nbt) else {
        game.native_dismount(driver_entity);
        game.native_remove_entity(&player.dimension, cart);
        game.native_feedback(Text::literal("[masina] nu am putut pune vehiculul aici"));
        return;
    };
    super::dedicated::log(
        "INFO",
        &format!("[vehicle-mod] vehicul {vehicle_id} pus (entitate {model})"),
    );

    lock_cars().push(Car {
        dimension: player.dimension.clone(),
        cart,
        driver: player.uuid,
        driver_entity,
        motion: land::Motion {
            pos,
            yaw: player.yaw,
            ..Default::default()
        },
        mounted: true,
        model,
        vehicle: vehicle.to_string(),
    });
    super::dedicated::log(
        "INFO",
        &format!(
            "[vehicle-mod] {} a pornit {} (cart {}, model {}, dim {})",
            player.name, vehicle, cart, model, player.dimension
        ),
    );
    game.native_feedback(Text::literal(
        "[masina] la volan. W/S = accelereaza/frana, A/D = vireaza, SPACE = salt, ghemuieste-te = cobori",
    ));
}

/// Starea tastelor de la un jucător (pachetul `PlayerInput`).
pub(super) fn on_player_input(_game: &mut Game<'_>, player: u128, name: &str, flags: i32) {
    lock_inputs().insert(player, flags);
    super::dedicated::log(
        "INFO",
        &format!("[vehicle-mod] input {name}: {flags:#09b} ({})", describe(flags)),
    );
}

/// Fizica: un pas pentru fiecare mașină, la fiecare tick de server.
pub(super) fn on_server_tick(
    game: &mut Game<'_>,
    _context: &NativeLifecycleContext,
    phase: ServerTickPhase,
) {
    if !matches!(phase, ServerTickPhase::ServerTickStart { .. }) {
        return;
    }

    let mut cars = lock_cars();
    let inputs = lock_inputs();
    let mut gone: Vec<usize> = Vec::new();

    for (index, car) in cars.iter_mut().enumerate() {
        let flags = inputs.get(&car.driver).copied().unwrap_or(0);

        // coborâre: ghemuit sau salt ținut apăsat
        if car.mounted && (flags & IN_SNEAK != 0) {
            game.native_dismount(car.driver_entity);
            game.native_remove_entity(&car.dimension, car.cart);
            game.native_remove_entity(&car.dimension, car.model);
            super::dedicated::log("INFO", "[vehicle-mod] soferul a coborat");
            gone.push(index);
            continue;
        }

        // fizica modului: `LandVehicleEntity.updateVehicleMotion()`
        let inputs = land::Inputs {
            throttle: if flags & IN_FORWARD != 0 {
                1.0
            } else if flags & IN_BACK != 0 {
                -1.0
            } else {
                0.0
            },
            steer: if flags & IN_LEFT != 0 {
                -1.0
            } else if flags & IN_RIGHT != 0 {
                1.0
            } else {
                0.0
            },
            handbrake: false,
        };
        land::update_turning(&mut car.motion, inputs);
        land::update_vehicle_motion(&mut car.motion, inputs, true);

        game.native_teleport_vehicle(&car.dimension, car.cart, car.motion.pos);

        // vehiculul urmează scheletul: aceeași poziție, aceeași rotire
        game.native_move_entity(&car.dimension, car.model, car.motion.pos, car.motion.yaw, 0.0);
    }

    for index in gone.into_iter().rev() {
        cars.remove(index);
    }
}

// ---------------------------------------------------------------------------
// Randarea pe client (etapa B): entitatea `vehicle:go_kart` desenata cu modelul
// modului, nu cu modelul vanilla al template-ului.
//
// Modelul e in formatul de model de bloc (cuburi in texeli, 16 pe bloc), deci il
// desenam ca pe un model de bloc "de sine statator": il inregistram la pornire
// (`native_standalone_models`) si il desenam cu `native_draw_model`. Fata de
// model: +z, ca la modul original (`EntityVehicleRenderer`: doar `-yaw`, fara
// intoarcere pe 180°), deci se potriveste cu cadrul entitatii (+z = inainte).
// ---------------------------------------------------------------------------

/// Modelele care trebuie coapte ca sa le putem desena: unul per vehicul.
pub fn standalone_models(models: &mut Vec<String>) {
    for (_, model, _) in VEHICLES {
        models.push((*model).to_string());
    }
}

/// `native_mob_submit`: fiecare vehicul e desenat cu modelul lui din mod.
pub fn on_mob_submit(
    game: &Game<'_>,
    mob: &crate::game::native_mob_draw::NativeMobRender<'_>,
    draw: &mut crate::game::native_block_entity_draw::BlockEntityDraw,
    drawn: &mut bool,
) {
    let Some((_, model, offset)) = VEHICLES.iter().find(|(id, _, _)| *id == mob.kind) else {
        return;
    };
    draw.push();
    draw.translate(*offset);
    let _ = game.native_draw_model(draw, model, 0xFF_FFFF);
    draw.pop();
    // entitatea e desenata de noi: modelul vanilla (negustorul) nu se mai deseneaza
    *drawn = true;
}

// BEGIN GENERATED VEHICLES
// GENERAT de horizon/tools/port_mod_content.py — nu edita de mana.
// (entitate, model, offset) pentru fiecare vehicul al modului: offset-ul aduce
// cutia modelului in centrul entitatii, in blocuri (cuburile sunt in texeli/16).
pub const VEHICLES: &[(&str, &str, [f32; 3])] = &[
    ("vehicle:quad_bike", "vehicle:vehicle/quad_bike/base", [-0.5, 0.031, -0.459]),
    ("vehicle:sports_car", "vehicle:vehicle/sports_car/base", [-0.5, 0.0, -0.469]),
    ("vehicle:go_kart", "vehicle:vehicle/go_kart/base", [-0.5, 0.062, -0.559]),
    ("vehicle:jet_ski", "vehicle:vehicle/jet_ski_body", [-0.5, 0.125, -0.641]),
    ("vehicle:lawn_mower", "vehicle:vehicle/lawn_mower_body", [-0.5, 0.0, -0.562]),
    ("vehicle:moped", "vehicle:vehicle/moped/body", [-0.5, 0.0, -0.447]),
    ("vehicle:sports_plane", "vehicle:vehicle/sports_plane/base", [-0.5, 0.094, 0.64]),
    ("vehicle:golf_cart", "vehicle:vehicle/golf_cart_body", [-0.5, 0.0, -0.456]),
    ("vehicle:off_roader", "vehicle:vehicle/off_roader_body", [-0.5, 0.062, -0.5]),
    ("vehicle:tractor", "vehicle:vehicle/tractor_body", [-0.5, -0.062, -0.501]),
    ("vehicle:mini_bus", "vehicle:vehicle/mini_bus/body", [-0.5, 0.188, -0.531]),
    ("vehicle:dirt_bike", "vehicle:vehicle/dirt_bike/body", [-0.5, 0.013, -0.209]),
    ("vehicle:compact_helicopter", "vehicle:vehicle/helicopter/base", [-0.5, 0.0, 1.36]),
    ("vehicle:vehicle_trailer", "vehicle:vehicle/trailer_body", [-0.5, 0.031, -0.688]),
    ("vehicle:storage_trailer", "vehicle:vehicle/trailer_chest_body", [-0.5, 0.031, -0.75]),
    ("vehicle:fluid_trailer", "vehicle:vehicle/trailer_fluid_body", [-0.5, 0.031, -0.719]),
    ("vehicle:seeder", "vehicle:vehicle/trailer_seeder_body", [-0.5, 0.219, -0.844]),
    ("vehicle:fertilizer", "vehicle:vehicle/trailer_fertilizer_body", [-0.5, 0.031, -0.812]),
];
// END GENERATED VEHICLES

// ---------------------------------------------------------------------------
// Port 1:1 al fizicii: `LandVehicleEntity.updateVehicleMotion()` din modul Java.
//
// Numele si ordinea calculelor sunt cele din sursa; valorile care in mod vin din
// `VehicleProperties` (fisierul de proprietati al fiecarui vehicul, care nu e in
// acest repo) sunt luate din valorile lor implicite, din `PoweredProperties` si
// `LandProperties`, si stranse mai jos, ca sa se vada exact ce lipseste.
// ---------------------------------------------------------------------------
pub mod land {
    /// `PoweredProperties.DEFAULT_ENGINE_POWER`.
    pub const ENGINE_POWER: f64 = 15.0;
    /// `LandProperties.DEFAULT_BRAKE_POWER`.
    pub const BRAKE_POWER: f64 = -1.0;
    /// `LandProperties.DEFAULT_MAX_REVERSE_SPEED`.
    pub const MAX_REVERSE_SPEED: f64 = 5.0;
    /// `PoweredProperties.DEFAULT_MAX_STEERING_ANGLE`.
    pub const MAX_STEERING_ANGLE: f32 = 35.0;
    /// `LandVehicleEntity.updateVehicleMotion`: `float drag = 0.001F`.
    pub const DRAG: f64 = 0.001;
    /// `SurfaceHelper.getFriction` pentru piatra/pamant.
    pub const FRICTION: f64 = 0.6;
    /// `LandVehicleEntity`: accelerația și forțele se aplică la scara asta (0.05).
    pub const STEP: f64 = 0.05;
    /// `Config.SERVER.globalSpeedLimit` — limita serverului, in blocuri/tick.
    pub const SPEED_LIMIT: f64 = 0.55;
    /// Cât de repede se învârte volanul spre țintă, în grade pe tick.
    pub const STEER_RATE: f32 = 8.0;

    /// Ce intră în ecuație din tastatură.
    #[derive(Clone, Copy, Default)]
    pub struct Inputs {
        pub throttle: f64,
        pub steer: f32,
        pub handbrake: bool,
    }

    /// Starea de mișcare a vehiculului, echivalentul `velocity`/`yRot`/`steeringAngle`.
    #[derive(Clone, Copy, Default)]
    pub struct Motion {
        pub pos: [f64; 3],
        pub velocity: [f64; 3],
        pub yaw: f32,
        pub steering: f32,
    }

    fn len(v: [f64; 3]) -> f64 {
        (v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).sqrt()
    }

    fn scale(v: [f64; 3], k: f64) -> [f64; 3] {
        [v[0] * k, v[1] * k, v[2] * k]
    }

    fn add(a: [f64; 3], b: [f64; 3]) -> [f64; 3] {
        [a[0] + b[0], a[1] + b[1], a[2] + b[2]]
    }

    fn normalize(v: [f64; 3]) -> [f64; 3] {
        let length = len(v);
        if length < 1e-9 {
            [0.0, 0.0, 0.0]
        } else {
            scale(v, 1.0 / length)
        }
    }

    fn dot(a: [f64; 3], b: [f64; 3]) -> f64 {
        a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    }

    fn lerp(a: [f64; 3], b: [f64; 3], t: f64) -> [f64; 3] {
        [
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t,
        ]
    }

    /// `Vector3d.directionFromRotation` pentru un vehicul drept: 0° = +Z, 90° = -X.
    fn forward(yaw: f32) -> [f64; 3] {
        let yaw = f64::from(yaw.to_radians());
        [-yaw.sin(), 0.0, yaw.cos()]
    }

    /// `Vector3d.yRot` în jurul lui Y.
    fn rotate_y(v: [f64; 3], degrees: f32) -> [f64; 3] {
        let a = f64::from(degrees.to_radians());
        let (sin, cos) = (a.sin(), a.cos());
        [v[0] * cos + v[2] * sin, v[1], -v[0] * sin + v[2] * cos]
    }

    /// Unghiul unui vector în plan, în grade, ca `CommonUtils.yaw`.
    fn yaw_of(v: [f64; 3]) -> f32 {
        (-v[0]).atan2(v[2]).to_degrees() as f32
    }

    /// Diferența de unghi, înfășurată în (-180, 180], ca `MathHelper.wrapDegrees`.
    fn wrap_degrees(mut angle: f32) -> f32 {
        angle %= 360.0;
        if angle >= 180.0 {
            angle -= 360.0;
        }
        if angle < -180.0 {
            angle += 360.0;
        }
        angle
    }

    /// `CommonUtils.clampSpeed`.
    fn clamp_speed(v: [f64; 3]) -> [f64; 3] {
        let length = len(v).clamp(0.0, SPEED_LIMIT);
        scale(normalize(v), length)
    }

    /// `updateTurning`: volanul merge spre ținta cu o viteză fixă.
    pub fn update_turning(motion: &mut Motion, inputs: Inputs) {
        let target = (inputs.steer * MAX_STEERING_ANGLE).clamp(-MAX_STEERING_ANGLE, MAX_STEERING_ANGLE);
        let delta = (target - motion.steering).clamp(-STEER_RATE, STEER_RATE);
        motion.steering += delta;
    }

    /// `LandVehicleEntity.updateVehicleMotion()`, rând cu rând.
    pub fn update_vehicle_motion(motion: &mut Motion, inputs: Inputs, on_ground: bool) {
        let forward = forward(motion.yaw);

        let engine_power = if on_ground { ENGINE_POWER } else { 0.0 };
        let brake_power = if on_ground { BRAKE_POWER } else { 0.0 };
        let throttle = inputs.throttle.clamp(-1.0, 1.0);

        let mut forward_force = engine_power * throttle;
        if throttle < 0.0 {
            forward_force *= 0.4;
        }
        let mut acceleration = scale(forward, forward_force * STEP);

        if len(motion.velocity) < 0.05 {
            motion.velocity = [0.0, 0.0, 0.0];
        }
        let handbrake_force = scale(motion.velocity, if inputs.handbrake { brake_power } else { 0.0 } * STEP);
        let friction_force = scale(motion.velocity, -FRICTION * STEP);
        let drag_force = scale(motion.velocity, len(motion.velocity) * -DRAG * STEP);
        acceleration = add(add(acceleration, drag_force), add(friction_force, handbrake_force));
        motion.velocity = add(motion.velocity, acceleration);
        motion.velocity = clamp_speed(motion.velocity);

        // Roțile: față/spate, cu offset-urile de axe (0 în proprietățile implicite).
        let front_axle = 0.0;
        let rear_axle = 0.0;
        let world_front = add(
            add(motion.pos, scale(forward, front_axle)),
            scale(rotate_y(motion.velocity, motion.steering), STEP),
        );
        let world_rear = add(
            add(motion.pos, scale(forward, rear_axle)),
            scale(motion.velocity, STEP),
        );

        let heading = normalize([
            world_front[0] - world_rear[0],
            world_front[1] - world_rear[1],
            world_front[2] - world_rear[2],
        ]);
        let next_position = add(world_rear, scale(heading, -rear_axle));
        let movement = [
            next_position[0] - motion.pos[0],
            next_position[1] - motion.pos[1],
            next_position[2] - motion.pos[2],
        ];

        // `SurfaceHelper.getSurfaceTraction` — 1.0 pe teren uscat.
        let surface_traction = 1.0;
        if dot(heading, normalize(motion.velocity)) > 0.0 {
            motion.velocity = lerp(motion.velocity, scale(heading, len(motion.velocity)), surface_traction);
        } else {
            let reverse = scale(heading, -len(motion.velocity).min(MAX_REVERSE_SPEED));
            motion.velocity = lerp(motion.velocity, reverse, surface_traction);
        }

        // `yRot -= wrapDegrees(yaw(forward) - yaw(heading))`
        let delta_yaw = wrap_degrees(yaw_of(forward) - yaw_of(heading));
        motion.yaw -= delta_yaw;
        motion.yaw = motion.yaw.rem_euclid(360.0);

        // `motion` se adaugă poziției, ca `this.move(MoverType.SELF, this.motion)`
        motion.pos[0] += movement[0];
        motion.pos[1] += movement[1];
        motion.pos[2] += movement[2];
    }
}
