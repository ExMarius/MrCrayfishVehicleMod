#!/usr/bin/env python3
"""Build the vanilla-client resource pack used by the Paper prototype."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import tempfile
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/vehicle"


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def item_definition(model: str) -> dict[str, object]:
    return {
        "model": {
            "type": "minecraft:model",
            "model": model,
            "tints": [
                {
                    "type": "minecraft:constant",
                    "value": -1,
                }
            ],
        }
    }


def convert_model(source: Path, destination: Path, textures: dict[str, str] | None = None) -> None:
    """Normalize Forge/Blockbench model metadata to vanilla model JSON."""
    model = json.loads(source.read_text(encoding="utf-8"))
    if textures is not None:
        model["textures"] = textures
    components = model.pop("components", None)
    if components is not None:
        model["elements"] = components
    model.pop("loader", None)
    model.pop("groups", None)
    # texture_size is understood by the source loader but unnecessary for vanilla item models.
    model.pop("texture_size", None)
    write_json(destination, model)


def copy(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def build(output: Path) -> tuple[Path, str]:
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="vehicle-resource-pack-") as temporary:
        pack = Path(temporary)
        write_json(pack / "pack.mcmeta", {
            "pack": {
                "description": "MrCrayfish Vehicle Plugin — vanilla client assets",
                "pack_format": 46,
            }
        })
        copy(ROOT / "src/main/resources/vehicle_mod.png", pack / "pack.png")
        copy(ROOT / "MOD-LICENSE.txt", pack / "LICENSE.txt")

        namespace = pack / "assets/vehicle"
        for item, model in {
            "go_kart_body": "vehicle:item/go_kart_body",
            "go_kart_steering_wheel": "vehicle:item/go_kart_steering_wheel",
            "standard_wheel": "vehicle:item/standard_wheel",
            "iron_small_engine": "vehicle:item/iron_small_engine",
        }.items():
            write_json(namespace / f"items/{item}.json", item_definition(model))

        convert_model(
            ASSETS / "models/vehicle/go_kart/base.json",
            namespace / "models/item/go_kart_body.json",
            {"2": "vehicle:item/go_kart_body", "particle": "vehicle:item/go_kart_body"},
        )
        convert_model(
            ASSETS / "models/vehicle/go_kart_steering_wheel.json",
            namespace / "models/item/go_kart_steering_wheel.json",
        )
        convert_model(
            ASSETS / "models/item/standard_wheel.json",
            namespace / "models/item/standard_wheel.json",
            {"particle": "vehicle:item/standard_wheel", "wheel": "vehicle:item/standard_wheel"},
        )
        convert_model(
            ASSETS / "models/item/small_engine.json",
            namespace / "models/item/small_engine.json",
            {"small_engine": "vehicle:item/small_engine", "particle": "vehicle:item/small_engine"},
        )
        convert_model(
            ASSETS / "models/item/iron_small_engine.json",
            namespace / "models/item/iron_small_engine.json",
            {"small_engine": "vehicle:item/iron_small_engine"},
        )

        copy(
            ASSETS / "textures/vehicle/go_kart/base.png",
            namespace / "textures/item/go_kart_body.png",
        )
        copy(
            ASSETS / "textures/model/wheel.png",
            namespace / "textures/item/standard_wheel.png",
        )
        copy(
            ASSETS / "textures/model/small_engine.png",
            namespace / "textures/item/small_engine.png",
        )
        copy(
            ASSETS / "textures/model/iron_small_engine.png",
            namespace / "textures/item/iron_small_engine.png",
        )
        copy(
            ASSETS / "sounds/entity/go_kart/engine.ogg",
            namespace / "sounds/entity/go_kart/engine.ogg",
        )
        write_json(namespace / "sounds.json", {
            "entity.go_kart.engine": {
                "sounds": [{"name": "vehicle:entity/go_kart/engine"}]
            }
        })

        if output.exists():
            output.unlink()
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for path in sorted(pack.rglob("*")):
                if path.is_file():
                    info = zipfile.ZipInfo(path.relative_to(pack).as_posix())
                    info.date_time = (2020, 1, 1, 0, 0, 0)
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o644 << 16
                    archive.writestr(info, path.read_bytes(), compresslevel=9)

    sha1 = hashlib.sha1(output.read_bytes()).hexdigest()
    return output, sha1


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path,
                        default=ROOT / "paper-plugin/build/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r3.zip")
    args = parser.parse_args()
    output, sha1 = build(args.output.resolve())
    print(f"Resource pack: {output}")
    print(f"SHA-1: {sha1}")


if __name__ == "__main__":
    main()
