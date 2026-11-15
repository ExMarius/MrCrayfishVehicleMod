// Modelele modului care nu sunt numite de niciun blockstate: Horizon le coace la
// incarcarea resurselor, ca sa le putem desena cu `native_draw_model`.
fn vehicle_standalone_models(models: &mut Vec<String>) {
    crate::game::vehicle::standalone_models(models);
}
