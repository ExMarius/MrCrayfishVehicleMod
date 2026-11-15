// Fizica mașinilor: un pas la fiecare tick autoritativ de server.
fn vehicle_server_tick(
    game: &mut crate::game::Game<'_>,
    context: &crate::mod_api::NativeLifecycleContext,
    phase: crate::mod_api::ServerTickPhase,
) {
    crate::game::vehicle::on_server_tick(game, context, phase);
}
