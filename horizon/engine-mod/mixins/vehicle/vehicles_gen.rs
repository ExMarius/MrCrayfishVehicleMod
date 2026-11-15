// GENERAT de horizon/tools/port_mod_content.py — nu edita de mana.
// (entitate, model, offset) pentru fiecare vehicul al modului: offset-ul aduce
// cutia modelului in centrul entitatii, in blocuri (cuburile sunt in texeli/16).
pub const VEHICLES: &[(&str, &str, [f32; 3])] = &[
    ("vehicle:quad_bike", "vehicle:vehicle/quad_bike/base", [-0.0, -6.25e+07, -0.0]),
    ("vehicle:sports_car", "vehicle:vehicle/sports_car/base", [-0.0, -6.25e+07, -0.0]),
    ("vehicle:go_kart", "vehicle:vehicle/go_kart/base", [-0.0, -6.25e+07, -0.0]),
    ("vehicle:jet_ski", "vehicle:vehicle/jet_ski_body", [-0.5, 0.125, -0.641]),
    ("vehicle:lawn_mower", "vehicle:vehicle/lawn_mower_body", [-0.5, 0.0, -0.562]),
    ("vehicle:moped", "vehicle:vehicle/moped/body", [-0.5, 0.0, -0.447]),
    ("vehicle:sports_plane", "vehicle:vehicle/sports_plane/base", [-0.0, -6.25e+07, -0.0]),
    ("vehicle:golf_cart", "vehicle:vehicle/golf_cart_body", [-0.5, 0.0, -0.456]),
    ("vehicle:off_roader", "vehicle:vehicle/off_roader_body", [-0.5, 0.062, -0.5]),
    ("vehicle:tractor", "vehicle:vehicle/tractor_body", [-0.5, -0.062, -0.501]),
    ("vehicle:mini_bus", "vehicle:vehicle/mini_bus/body", [-0.5, 0.188, -0.531]),
    ("vehicle:dirt_bike", "vehicle:vehicle/dirt_bike/body", [-0.5, 0.013, -0.209]),
];
