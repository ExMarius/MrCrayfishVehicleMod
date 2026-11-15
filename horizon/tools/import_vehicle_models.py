#!/usr/bin/env python3
"""Copiaza modelele si texturile modului original in modul Horizon.

Modul Java isi descrie modelele in `src/main/resources/assets/vehicle/models/vehicle/*.json`,
in formatul Blockbench/Minecraft cu `elements`, dar cu UV-urile in pixeli ai texturii
(`texture_size`, de obicei 64x64). Horizon incarca modelele ca modele de bloc, unde UV-urile
sunt in unitati de 16 pe latura (`native_draw_cube`: "texels of 16 per side"), adica 16
unitati acopera toata textura. De aceea UV-urile se impart la `texture_size / 16`.

Rezultatul merge in `horizon/mod/content/assets/vehicle/`, de unde il incarca modul `.hmod`,
si de unde randarea nativa il deseneaza cu `native_draw_model`.

Rulare:
    python3 horizon/tools/import_vehicle_models.py
"""
from __future__ import annotations

import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "src/main/resources/assets/vehicle"
DST = ROOT / "horizon/mod/content/assets/vehicle"

# Ce modele intra in mod (restul modului se adauga pe rand).
MODELS = [
    "vehicle/go_kart_body",
    "vehicle/go_kart_steering_wheel",
]
# Texturile la care se refera modelele de mai sus.
TEXTURES = ["vehicles/go_kart.png"]


def scale_uv(uv: list[float], factor: float) -> list[float]:
    return [round(v * factor, 4) for v in uv]


def convert_model(name: str) -> dict:
    data = json.loads((SRC / "models" / f"{name}.json").read_text())
    size = data.get("texture_size") or [16, 16]
    factor = 16.0 / size[0]
    for element in data.get("elements", []):
        for face in element.get("faces", {}).values():
            if "uv" in face:
                face["uv"] = scale_uv(face["uv"], factor)
            # rotarea fetei (Blockbench) e acceptata si de modelul de bloc
    # `texture_size` si `credit` nu fac parte din formatul de model de bloc
    data.pop("texture_size", None)
    data.pop("credit", None)
    # `particle` e o cheie de model de bloc valida; o lasam.
    return data


def main() -> None:
    for name in MODELS:
        out = DST / "models" / f"{name}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        model = convert_model(name)
        out.write_text(json.dumps(model, indent="\t") + "\n")
        faces = sum(len(e.get("faces", {})) for e in model.get("elements", []))
        print(f"{name}: {len(model.get('elements', []))} cuburi, {faces} fete -> {out.relative_to(ROOT)}")

    for tex in TEXTURES:
        src = SRC / "textures" / tex
        out = DST / "textures" / tex
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, out)
        print(f"{tex}: {src.stat().st_size} B -> {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
