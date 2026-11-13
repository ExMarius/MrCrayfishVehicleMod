//! [vehicle-mod] Portul sistemului nostru de mașini pe Horizon — partea server-side.
//!
//! Horizon nu rulează cod Java, deci pluginul din `paper-plugin/` nu poate fi încărcat.
//! Modulul ăsta se lipește direct în arborele sursă Horizon (`apply.py`) și rulează în
//! interiorul engine-ului, cu acces la lume, entități și input-ul jucătorilor.
//!
//! Legăturile cu engine-ul sunt cele documentate public de Horizon ca puncte de extensie
//! (`native_server_tick`, `native_player_input`, `native_summon`, `native_mount`, ...),
//! apelate din `game/native_hooks.rs`. Modul adaugă doar un singur apel în fiecare.
//!
//! Stadiul curent: M0 — dovada că un engine propriu, compilat din sursă, rulează pe panel
//! și că input-ul jucătorului ajunge în codul nostru. Urmează M1: mașina (rig de display-uri
//! + fizică + scaun).

use super::Game;
use crate::mod_api::{NativeLifecycleContext, ServerTickPhase};
use std::sync::atomic::{AtomicU64, Ordering};

/// Câte tick-uri de server au trecut de la pornire (folosit pentru log rar și, mai târziu,
/// pentru pașii de fizică).
static TICKS: AtomicU64 = AtomicU64::new(0);

/// Ultima stare de input primită de la fiecare jucător, ca binar: 1 = înainte, 2 = înapoi,
/// 4 = stânga, 8 = dreapta, 16 = salt, 32 = ghemuit, 64 = sprint. O ținem aici pentru M1,
/// când mașina va citi throttle-ul șoferului între două pachete de input.
fn input_bits(flags: i32) -> String {
    const NAMES: [(i32, &str); 7] = [
        (1, "inainte"),
        (2, "inapoi"),
        (4, "stanga"),
        (8, "dreapta"),
        (16, "salt"),
        (32, "ghemuit"),
        (64, "sprint"),
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

/// Apelat la fiecare tick autoritativ de server, înainte de tick-ul nivelurilor.
pub(super) fn on_server_tick(
    _game: &mut Game<'_>,
    _context: &NativeLifecycleContext,
    phase: ServerTickPhase,
) {
    if !matches!(phase, ServerTickPhase::ServerTickStart { .. }) {
        return;
    }
    let n = TICKS.fetch_add(1, Ordering::Relaxed) + 1;
    // O linie la fiecare 30 de secunde: destul ca să se vadă în consola panelului că
    // engine-ul propriu rulează, fără să umple log-ul.
    if n % 600 == 1 {
        super::dedicated::log(
            "INFO",
            &format!("[vehicle-mod] engine propriu activ (tick {n}); modul e incarcat"),
        );
    }
}

/// Apelat când un jucător trimite starea tastelor (pachetul `PlayerInput`).
pub(super) fn on_player_input(_game: &mut Game<'_>, _player: u128, name: &str, flags: i32) {
    super::dedicated::log(
        "INFO",
        &format!(
            "[vehicle-mod] input de la {name}: {:#09b} ({})",
            flags,
            input_bits(flags)
        ),
    );
}
