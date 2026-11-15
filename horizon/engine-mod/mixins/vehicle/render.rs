// Randarea pe client: cand un `vehicle:go_kart` intra in cadru, desenam caroseria
// din modelul modului in locul modelului vanilla al entitatii.
fn vehicle_mob_submit(
    game: &mut crate::game::Game<'_>,
    mob: &crate::game::native_mob_draw::NativeMobRender<'_>,
    draw: &mut crate::game::native_block_entity_draw::BlockEntityDraw,
    drawn: &mut bool,
) {
    crate::game::vehicle::on_mob_submit(game, mob, draw, drawn);
}
