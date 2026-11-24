#!/usr/bin/env python3
"""Turns the Horizon transfer package into pack models + a part manifest.

Input : tools/horizon/horizon-off-roader-transfer.zip (the transfer bundle the
        HorizonMods repo publishes; it contains the original cuboids, the
        textures and ``paper-resource-pack.zip`` with the body already split
        into rotation groups).
Output: tools/external_models/{models,textures}/  (copied into the pack by
        tools/build_resource_pack.py) plus tools/horizon/horizon_parts.json,
        the manifest the Java spec is generated from.

Why groups: the body has panels rotated by angles vanilla cannot express
(35 deg, -18 deg, 4 deg ...). Vanilla only accepts multiples of 22.5 up to 45
deg, so each rotation becomes its own ItemDisplay; the group's elements are
shifted so that the group pivot sits on the model centre (8, 8, 8), which is
what LandVehicleSpec.Part rotates around.
"""

from __future__ import annotations

import io
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRANSFER = ROOT / "tools" / "horizon" / "horizon-off-roader-transfer.zip"
OUT = ROOT / "tools" / "external_models"
MANIFEST = ROOT / "tools" / "horizon" / "horizon_parts.json"

PREFIX = "hz_"
BODY_GROUPS = 18          # part_00 .. part_17 in the transfer pack
WHEEL_MODELS = ("wheel_sports", "wheel_standard")


def _shift(model: dict, pivot: list[float]) -> dict:
    """Moves the group so its pivot lands on the model centre (8, 8, 8)."""
    dx, dy, dz = (8.0 - pivot[0], 8.0 - pivot[1], 8.0 - pivot[2])
    for element in model["elements"]:
        element["from"] = [round(element["from"][0] + dx, 4),
                           round(element["from"][1] + dy, 4),
                           round(element["from"][2] + dz, 4)]
        element["to"] = [round(element["to"][0] + dx, 4),
                         round(element["to"][1] + dy, 4),
                         round(element["to"][2] + dz, 4)]
        rotation = element.get("rotation")
        if rotation:
            rotation["origin"] = [round(rotation["origin"][0] + dx, 4),
                                  round(rotation["origin"][1] + dy, 4),
                                  round(rotation["origin"][2] + dz, 4)]
    return model


def _retexture(model: dict, mapping: dict[str, str]) -> dict:
    textures = model.get("textures") or {}
    model["textures"] = {key: mapping.get(value, value) for key, value in textures.items()}
    return model


def main(argv=None) -> int:
    if not TRANSFER.exists():
        print(f"Lipsește {TRANSFER}")
        return 2
    transfer = zipfile.ZipFile(TRANSFER)
    inner = zipfile.ZipFile(io.BytesIO(transfer.read("paper-resource-pack.zip")))
    parts = json.loads(inner.read("go_kart_parts.json"))["parts"]

    models_dir = OUT / "models"
    textures_dir = OUT / "textures"
    models_dir.mkdir(parents=True, exist_ok=True)
    textures_dir.mkdir(parents=True, exist_ok=True)

    # textures: horizonvehicles:item/kart_body -> vehicle:item/hz_kart_body
    mapping: dict[str, str] = {}
    for entry in inner.namelist():
        if entry.endswith(".png") and "/textures/" in entry:
            name = Path(entry).stem
            (textures_dir / f"{PREFIX}{name}.png").write_bytes(inner.read(entry))
            mapping[f"horizonvehicles:item/{name}"] = f"vehicle:item/{PREFIX}{name}"
    print(f"Texturi: {len(mapping)}")

    manifest: dict = {"body": None, "parts": [], "wheels": {}}

    for index in range(BODY_GROUPS):
        key = f"off_roader_body_part_{index:02d}"
        entry = f"assets/horizonvehicles/models/item/{key}.json"
        model = _retexture(json.loads(inner.read(entry)), mapping)
        group = parts["off_roader_body"][index]
        rotation = group.get("rotation")
        stem = f"{PREFIX}off_roader_body" if index == 0 else f"{PREFIX}off_roader_g{index:02d}"
        if rotation is None:
            if index != 0:
                print(f"  ! {key} nu are rotație, dar nu e grupul principal")
        else:
            _shift(model, rotation["origin"])
        (models_dir / f"{stem}.json").write_text(
            json.dumps(model, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        record = {"model": stem, "elements": len(model["elements"])}
        if rotation is None:
            manifest["body"] = record
        else:
            record.update({"pivot": rotation["origin"], "axis": rotation["axis"],
                           "angle": rotation["angle"]})
            manifest["parts"].append(record)

    for wheel in WHEEL_MODELS:
        key = f"{wheel}_part_00"
        model = _retexture(
            json.loads(inner.read(f"assets/horizonvehicles/models/item/{key}.json")), mapping)
        stem = f"{PREFIX}{wheel}"
        (models_dir / f"{stem}.json").write_text(
            json.dumps(model, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        manifest["wheels"][wheel] = {"model": stem, "elements": len(model["elements"]),
                                     "dropped_group": f"{wheel}_part_01"}

    MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
                        encoding="utf-8")
    print(f"Body: {manifest['body']['model']} ({manifest['body']['elements']} elemente)")
    print(f"Grupuri rotite: {len(manifest['parts'])}")
    for record in manifest["parts"]:
        print(f"  {record['model']}: {record['angle']:+.1f}° pe {record['axis']} "
              f"la {record['pivot']} ({record['elements']} elem)")
    print(f"Roti: {', '.join(w['model'] for w in manifest['wheels'].values())}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
