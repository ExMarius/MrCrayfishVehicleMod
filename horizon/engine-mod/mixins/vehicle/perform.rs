// Rularea comenzii /car pentru un jucător.
fn vehicle_perform_command(
    game: &mut crate::game::Game<'_>,
    player: &crate::mod_api::NativeServerPlayer,
    command: &str,
    ran: &mut bool,
) {
    crate::game::vehicle::on_perform_command(game, player, command, ran);
}
