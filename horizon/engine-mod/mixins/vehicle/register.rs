// Comanda /car, înregistrată în arborele de comenzi al serverului.
fn vehicle_register_commands(
    game: &mut crate::game::Game<'_>,
    commands: &mut Vec<crate::mod_api::NativeCommand>,
) {
    crate::game::vehicle::register_commands(game, commands);
}
