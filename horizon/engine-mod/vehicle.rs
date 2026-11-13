//! [vehicle-mod] Portul sistemului nostru de mașini pe Horizon — partea server-side.
//!
//! Horizon nu rulează cod Java, deci `paper-plugin/` nu poate fi încărcat. Modulul ăsta se
//! lipește direct în arborele sursă Horizon (`apply.py`) și rulează în interiorul engine-ului,
//! cu acces la lume, entități, input-ul jucătorilor și comenzile serverului.
//!
//! Stadiul curent: **M1** — o mașină care există, se conduce și se părăsește.
//!
//! Cum e făcută, și de ce așa:
//!
//! * **Scheletul** e un minecart invizibil (`Invisible`, `Invulnerable`, `NoGravity`). Am ales
//!   minecart-ul pentru că e singurul tip de vehicul pe care Horizon îl lasă să poarte jucători
//!   prin API-ul propriu (`native_mount`, API 472, ca `/ride`), și pentru că `native_teleport_vehicle`
//!   îl mută împreună cu pasagerii.
//! * **Mișcarea** e calculată de noi la fiecare tick de server (`native_server_tick`), nu de
//!   fizica de șine a minecart-ului: poziția e impusă de `native_teleport_vehicle`.
//! * **Tastarea** vine din pachetul `PlayerInput` (`native_player_input`), deci W/S/A/D ajung
//!   la noi fără să ghicim mișcarea din diferențe de poziție.
//! * **Comenzile** (`/car`) se înregistrează prin `native_register_commands` și rulează în
//!   `native_perform_command`.
//!
//! Urmează M2: caroseria (entități `item_display` cu modelele modului, aduse printr-un resource
//! pack servit de server) și urcarea pe teren.

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
}

/// `/car` — pornește o mașină (sau spune unde e cea existentă).
pub(super) fn on_perform_command(
    game: &mut Game<'_>,
    player: &NativeServerPlayer,
    command: &str,
    ran: &mut bool,
) {
    let mut words = command.split_whitespace();
    if words.next() != Some("car") {
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

    lock_cars().push(Car {
        dimension: player.dimension.clone(),
        cart,
        driver: player.uuid,
        driver_entity,
        pos,
        yaw: player.yaw,
        speed: 0.0,
        mounted: true,
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
        let yaw = car.yaw.to_radians();
        car.pos[0] -= car.speed * yaw.sin();
        car.pos[2] += car.speed * yaw.cos();

        game.native_teleport_vehicle(&car.dimension, car.cart, car.pos);
    }

    for index in gone.into_iter().rev() {
        cars.remove(index);
    }
}
