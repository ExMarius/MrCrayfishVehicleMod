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

/// Materialul pe care stau modelele din pack: clientul renderizează `item_model` pe el.
const MODEL_MATERIAL: &str = "minecraft:leather_horse_armor";

/// Piesele go-kartului: (model din pack, poziție locală față de centrul mașinii, scale).
/// Pozițiile vin din `LandVehicleSpec.GO_KART` (axa X = stânga/dreapta, Z = înainte).
const GO_KART_PARTS: [(&str, [f64; 3], f32); 5] = [
    ("vehicle:go_kart_body", [0.0, 0.0, 0.0], 1.0),
    ("vehicle:standard_wheel", [-0.4375, 0.2, 0.547], 0.8),
    ("vehicle:standard_wheel", [0.4375, 0.2, 0.547], 0.8),
    ("vehicle:standard_wheel", [-0.4375, 0.2, -0.594], 0.8625),
    ("vehicle:standard_wheel", [0.4375, 0.2, -0.594], 0.8625),
];

/// Biții din pachetul `PlayerInput` (vanilla 26.3).
const IN_FORWARD: i32 = 1;
const IN_BACK: i32 = 2;
const IN_LEFT: i32 = 4;
const IN_RIGHT: i32 = 8;
const IN_JUMP: i32 = 16;
const IN_SNEAK: i32 = 32;

/// Fizica, în unități vanilla pe tick. Valorile sunt calibrate după cele din
/// `paper-plugin/.../vehicle/DriveTuning.java`, ca mașina să se simtă la fel.
const ACCEL: f64 = 0.030;
const BRAKE: f64 = 0.055;
const DRAG: f64 = 0.012;
const MAX_SPEED: f64 = 0.55; // ~11 blocuri/s
const TURN_DEGREES: f32 = 3.2;

/// O mașină vie.
struct Car {
    dimension: String,
    /// Id-ul entității-schelet.
    cart: u32,
    /// Cine o conduce (UUID-ul contului) și id-ul lui de rețea (pentru coborâre).
    driver: u128,
    driver_entity: u32,
    pos: [f64; 3],
    yaw: f32,
    /// Viteza pe direcția de mers, în blocuri/tick (poate fi negativă la mers înapoi).
    speed: f64,
    /// Ultima poziție a șoferului, ca să vedem dacă s-a dat jos singur.
    mounted: bool,
    /// Piesele vizuale: (id entitate, poziție locală).
    parts: Vec<(u32, [f64; 3])>,
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

/// Pune o piesă de caroserie: un `item_display` care ține un item al cărui model vine din
/// resource pack (`minecraft:item_model`). Corecția de 180° e cea din plugin: un item display
/// desenează modelul cu +Z spre ecran, invers față de o entitate.
fn spawn_part(
    game: &mut Game<'_>,
    dimension: &str,
    model: &str,
    pos: [f64; 3],
    scale: f32,
) -> Option<u32> {
    let mut item = Compound::new();
    item.insert(
        "id".into(),
        Tag::String(MODEL_MATERIAL.into()),
    );
    item.insert("count".into(), Tag::Int(1));
    let mut components = Compound::new();
    components.insert("minecraft:item_model".into(), Tag::String(model.into()));
    // alb explicit: fără el, tint-ul `dye` cu default -1 iese transparent pe client
    components.insert("minecraft:dyed_color".into(), Tag::Int(0xF9FFFE));
    item.insert("components".into(), Tag::Compound(components));

    let mut transformation = Compound::new();
    transformation.insert(
        "right_rotation".into(),
        Tag::List(vec![
            Tag::Float(0.0f32.to_bits()),
            Tag::Float(1.0f32.to_bits()),
            Tag::Float(0.0f32.to_bits()),
            Tag::Float(0.0f32.to_bits()),
        ]),
    );
    transformation.insert(
        "scale".into(),
        Tag::List(vec![
            Tag::Float(scale.to_bits()),
            Tag::Float(scale.to_bits()),
            Tag::Float(scale.to_bits()),
        ]),
    );

    let mut nbt = Compound::new();
    nbt.insert("item".into(), Tag::Compound(item));
    nbt.insert("transformation".into(), Tag::Compound(transformation));

    game.native_summon(dimension, "minecraft:item_display", pos, nbt)
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

    // caroseria și roțile
    let mut parts: Vec<(u32, [f64; 3])> = Vec::new();
    for (model, local, scale) in GO_KART_PARTS {
        let world = [
            pos[0] + local[0],
            pos[1] + local[1],
            pos[2] + local[2],
        ];
        match spawn_part(game, &player.dimension, model, world, scale) {
            Some(id) => parts.push((id, local)),
            None => super::dedicated::log(
                "WARN",
                &format!("[vehicle-mod] piesa {model} nu a putut fi pusa"),
            ),
        }
    }
    super::dedicated::log(
        "INFO",
        &format!("[vehicle-mod] {} piese vizuale pentru go-kart", parts.len()),
    );

    lock_cars().push(Car {
        dimension: player.dimension.clone(),
        cart,
        driver: player.uuid,
        driver_entity,
        pos,
        yaw: player.yaw,
        speed: 0.0,
        mounted: true,
        parts,
    });
    super::dedicated::log(
        "INFO",
        &format!(
            "[vehicle-mod] {} a pornit masina (cart {}, dim {})",
            player.name, cart, player.dimension
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
            for (id, _) in &car.parts {
                game.native_remove_entity(&car.dimension, *id);
            }
            super::dedicated::log("INFO", "[vehicle-mod] soferul a coborat");
            gone.push(index);
            continue;
        }

        // accelerație / frână
        let forward = flags & IN_FORWARD != 0;
        let back = flags & IN_BACK != 0;
        if forward {
            car.speed += ACCEL;
        } else if back {
            if car.speed > 0.0 {
                car.speed -= BRAKE;
            } else {
                car.speed -= ACCEL * 0.6;
            }
        } else {
            // frecare: se oprește lin
            if car.speed.abs() <= DRAG {
                car.speed = 0.0;
            } else {
                car.speed -= DRAG * car.speed.signum();
            }
        }
        car.speed = car.speed.clamp(-MAX_SPEED * 0.45, MAX_SPEED);

        // viraj: A/D, mai eficient cu cât merge mai repede (ca o mașină adevărată)
        let turn_scale = if car.speed.abs() < 0.02 {
            0.35
        } else {
            1.0
        };
        if flags & IN_LEFT != 0 {
            car.yaw -= TURN_DEGREES * turn_scale;
        }
        if flags & IN_RIGHT != 0 {
            car.yaw += TURN_DEGREES * turn_scale;
        }
        car.yaw = car.yaw.rem_euclid(360.0);

        // Înălțimea rămâne cea de la pornire: M1 nu are încă citirea terenului, deci
        // mașina nu urc urcă/coboară. M2 adaugă proba de sol și gravitația.

        // deplasare pe direcția yaw-ului vanilla: 0° = +Z, 90° = -X
        let yaw = f64::from(car.yaw.to_radians());
        car.pos[0] -= car.speed * yaw.sin();
        car.pos[2] += car.speed * yaw.cos();

        game.native_teleport_vehicle(&car.dimension, car.cart, car.pos);

        // piesele urmează mașina: poziția locală rotită cu yaw-ul, apoi teleportate
        let (sin, cos) = (yaw.sin(), yaw.cos());
        for (id, local) in &car.parts {
            let world = [
                car.pos[0] + local[0] * cos - local[2] * sin,
                car.pos[1] + local[1],
                car.pos[2] + local[0] * sin + local[2] * cos,
            ];
            game.native_move_entity(&car.dimension, *id, world, car.yaw, 0.0);
        }
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
