// Tastele jucătorilor (pachetul PlayerInput): W/S/A/D, salt, ghemuit.
fn vehicle_player_input(game: &mut crate::game::Game<'_>, player: u128, name: &str, flags: i32) {
    crate::game::vehicle::on_player_input(game, player, name, flags);
}
