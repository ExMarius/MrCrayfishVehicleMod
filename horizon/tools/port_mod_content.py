#!/usr/bin/env python3
"""Importa tot continutul modului in modul Horizon, mecanic, din sursa Java.

Ce face:
  * copiaza TOATE modelele din `src/main/resources/assets/vehicle/models/` in modul de
    continut, cu UV-urile aduse din scara texturii (64px) in scara modelelor Horizon
    (16 pe latura, cum cere `native_draw_cube` / modelul de bloc);
  * copiaza TOATE texturile, sunetele, textele (lang) si retetele, pe aceleasi cai, ca
    referintele `vehicle:...` din modele sa se rezolve la fel;
  * scrie cate un `entity_type` pentru fiecare vehicul din mod (lista din ModEntities.java);
  * genereaza tabelul de randare pentru partea nativa: entitate -> model -> offset, cu
    offset-ul calculat din cutia modelului (ca masina sa stea centrata pe entitate).

Rulare:
    python3 horizon/tools/port_mod_content.py
"""
from __future__ import annotations

import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC_ASSETS = ROOT / "src/main/resources/assets/vehicle"
SRC_DATA = ROOT / "src/main/resources/data/vehicle"
SRC_JAVA = ROOT / "src/main/java/com/mrcrayfish/vehicle"
DST = ROOT / "horizon/mod/content"
RUNTIME = ROOT / "horizon/engine-mod/mixins/vehicle/runtime.rs"
BEGIN = "// BEGIN GENERATED VEHICLES"
END = "// END GENERATED VEHICLES"

# Vehiculele modului, cu numele entitatii din Java (ModEntities) si numele din lang.
VEHICLES = [
    "quad_bike", "sports_car", "go_kart", "jet_ski", "lawn_mower", "moped",
    "sports_plane", "golf_cart", "off_roader", "tractor", "mini_bus", "dirt_bike",
    "compact_helicopter", "vehicle_trailer", "storage_trailer", "fluid_trailer",
    "seeder", "fertilizer", "sofacopter",
]


# Componenta principala a fiecarui vehicul, din `VehicleModels.java`
# (`<CONSTANTA>_BODY = LOADER.create(...)`). sofacopterul e din alt mod (cfm).
BODY = {
    "quad_bike": "vehicle/quad_bike/base",
    "sports_car": "vehicle/sports_car/base",
    "go_kart": "vehicle/go_kart/base",
    "jet_ski": "vehicle/jet_ski_body",
    "lawn_mower": "vehicle/lawn_mower_body",
    "moped": "vehicle/moped/body",
    "sports_plane": "vehicle/sports_plane/base",
    "golf_cart": "vehicle/golf_cart_body",
    "off_roader": "vehicle/off_roader_body",
    "tractor": "vehicle/tractor_body",
    "mini_bus": "vehicle/mini_bus/body",
    "dirt_bike": "vehicle/dirt_bike/body",
    "compact_helicopter": "vehicle/helicopter/base",
    "vehicle_trailer": "vehicle/trailer_body",
    "storage_trailer": "vehicle/trailer_chest_body",
    "fluid_trailer": "vehicle/trailer_fluid_body",
    "seeder": "vehicle/trailer_seeder_body",
    "fertilizer": "vehicle/trailer_fertilizer_body",
}


def body_models() -> dict[str, str]:
    return {v: m for v, m in BODY.items() if (SRC_ASSETS / f"models/{m}.json").is_file()}


def elements_of(data: dict) -> list[dict]:
    """Modelele modului vin in doua formate: `elements` (Blockbench) sau
    `components` (framework:open_model). Amandoua au from/to/faces/rotation, deci
    se pot scrie ca model de bloc."""
    return data.get("elements") or data.get("components") or []


def rescale_model(path: Path, out: Path) -> None:
    data = json.loads(path.read_text())
    elements = elements_of(data)
    size = data.get("texture_size")
    if size:
        factor = 16.0 / size[0]
        for element in elements:
            for face in element.get("faces", {}).values():
                if "uv" in face:
                    face["uv"] = [round(v * factor, 4) for v in face["uv"]]
    model = {"textures": data.get("textures", {}), "elements": elements}
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(model, indent="\t") + "\n")


def bbox(model_path: Path) -> tuple[list[float], list[float]]:
    data = json.loads(model_path.read_text())
    lo = [1e9] * 3
    hi = [-1e9] * 3
    for element in elements_of(data):
        for axis in range(3):
            lo[axis] = min(lo[axis], element["from"][axis])
            hi[axis] = max(hi[axis], element["to"][axis])
    return lo, hi


def main() -> None:
    # 1. modele (toate), cu UV-urile convertite
    count_models = 0
    for src in sorted((SRC_ASSETS / "models").rglob("*.json")):
        rel = src.relative_to(SRC_ASSETS / "models")
        rescale_model(src, DST / "assets/vehicle/models" / rel)
        count_models += 1

    # 2. texturi, sunete, texte, retete, particle
    for folder in ("textures", "sounds", "lang", "particles"):
        src = SRC_ASSETS / folder
        if not src.is_dir():
            continue
        for file in sorted(src.rglob("*")):
            if file.is_file():
                out = DST / "assets/vehicle" / folder / file.relative_to(src)
                out.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, out)
    if (SRC_ASSETS / "sounds.json").is_file():
        shutil.copyfile(SRC_ASSETS / "sounds.json", DST / "assets/vehicle/sounds.json")

    # retetele sunt datapack normal
    for src in sorted(SRC_DATA.rglob("*.json")):
        out = DST / "data/vehicle" / src.relative_to(SRC_DATA)
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, out)

    # 3. un tip de entitate per vehicul
    for vehicle in VEHICLES:
        out = DST / f"data/vehicle/horizon/entity_type/{vehicle}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps({"template": "minecraft:wandering_trader"}, indent=2) + "\n")

    # 4. tabelul de randare pentru partea nativa
    models = body_models()
    lines = [
        "// GENERAT de horizon/tools/port_mod_content.py — nu edita de mana.",
        "// (entitate, model, offset) pentru fiecare vehicul al modului: offset-ul aduce",
        "// cutia modelului in centrul entitatii, in blocuri (cuburile sunt in texeli/16).",
        "pub const VEHICLES: &[(&str, &str, [f32; 3])] = &[",
    ]
    for vehicle, model in models.items():
        model_file = SRC_ASSETS / f"models/{model}.json"
        if not model_file.is_file():
            print(f"! {vehicle}: lipseste {model}.json")
            continue
        lo, hi = bbox(model_file)
        offset = [round(-(lo[0] + hi[0]) / 32.0, 3), round(-lo[1] / 16.0, 3), round(-(lo[2] + hi[2]) / 32.0, 3)]
        lines.append(f'    ("vehicle:{vehicle}", "vehicle:{model}", [{offset[0]:.3}, {offset[1]:.3}, {offset[2]:.3}]),')
    lines.append("];")
    block = "\n".join(lines)
    text = RUNTIME.read_text()
    if BEGIN in text and END in text:
        head, rest = text.split(BEGIN, 1)
        _, tail = rest.split(END, 1)
        text = head + BEGIN + "\n" + block + "\n" + END + tail
    else:
        text = text.rstrip() + "\n\n" + BEGIN + "\n" + block + "\n" + END + "\n"
    RUNTIME.write_text(text)

    print(f"modele: {count_models}")
    print(f"vehicule randate: {len(models)}")
    for vehicle in VEHICLES:
        if vehicle not in models:
            print(f"  fara model: {vehicle}")


if __name__ == "__main__":
    main()
