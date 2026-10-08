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


def item_definition(model: str, tint: int = 0xFFFFFF) -> dict[str, object]:
    return {
        "model": {
            "type": "minecraft:model",
            "model": model,
            "tints": [
                {
                    "type": "minecraft:constant",
                    "value": tint,
                }
            ],
        }
    }


VANILLA_1_21_4_ELEMENT_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)


def convert_model(source: Path, destination: Path, textures: dict[str, str] | None = None,
                  geometry_scale: float = 1.0, legalize_rotations: bool = False) -> None:
    """Normalize Forge/Blockbench model metadata to vanilla model JSON.

    Framework accepts oversized elements, but vanilla rejects element coordinates
    outside -16..32 and replaces the whole model with its black/magenta fallback.
    Oversized geometry is scaled around the item origin (8, 8, 8); the display
    rig applies the exact inverse scale so the model keeps its source dimensions.

    Minecraft 1.21.4 also accepts only 22.5-degree element-rotation increments.
    Framework's complex-model loader accepts arbitrary values. Models using those
    source angles must opt into nearest legal rotation conversion; arbitrary angles
    did not become a vanilla feature until after the server's 1.21.4 protocol.
    """
    model = json.loads(source.read_text(encoding="utf-8"))
    if textures is not None:
        model["textures"] = textures
    components = model.pop("components", None)
    if components is not None:
        model["elements"] = components
    if geometry_scale != 1.0:
        def scaled(vector: list[float]) -> list[float]:
            return [8.0 + (coordinate - 8.0) * geometry_scale for coordinate in vector]

        for element in model.get("elements", []):
            element["from"] = scaled(element["from"])
            element["to"] = scaled(element["to"])
            rotation = element.get("rotation")
            if rotation is not None and "origin" in rotation:
                rotation["origin"] = scaled(rotation["origin"])
    if legalize_rotations:
        for element in model.get("elements", []):
            rotation = element.get("rotation")
            if rotation is None or "angle" not in rotation:
                continue
            source_angle = float(rotation["angle"])
            rotation["angle"] = min(
                VANILLA_1_21_4_ELEMENT_ANGLES,
                key=lambda candidate: abs(candidate - source_angle),
            )
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
                "description": "MrCrayfish Vehicle Plugin r26 — fourteen vehicles and five trailers",
                "pack_format": 46,
            }
        })
        copy(ROOT / "src/main/resources/vehicle_mod.png", pack / "pack.png")
        copy(ROOT / "MOD-LICENSE.txt", pack / "LICENSE.txt")

        namespace = pack / "assets/vehicle"
        for item, (model, tint) in {
            # VehicleEntity's default white dye and the default wheel item tint.
            "go_kart_body": ("vehicle:item/go_kart_body", 16383998),
            "lawn_mower_body": ("vehicle:item/lawn_mower_body", 16383998),
            "quad_bike_body": ("vehicle:item/quad_bike_body", 16383998),
            "quad_bike_handles": ("vehicle:item/quad_bike_handles", 0xFFFFFF),
            "tractor_body": ("vehicle:item/tractor_body", 16383998),
            "dirt_bike_body": ("vehicle:item/dirt_bike_body", 16383998),
            "dirt_bike_handles": ("vehicle:item/dirt_bike_handles", 16383998),
            "moped_body": ("vehicle:item/moped_body", 16383998),
            "moped_handles": ("vehicle:item/moped_handles", 16383998),
            "moped_mud_guard": ("vehicle:item/moped_mud_guard", 16383998),
            "moped_stock_seat": ("vehicle:item/moped_stock_seat", 16383998),
            "moped_stock_tray": ("vehicle:item/moped_stock_tray", 16383998),
            "moped_stock_front_light": ("vehicle:item/moped_stock_front_light", 16383998),
            "off_roader_body": ("vehicle:item/off_roader_body", 16383998),
            "sports_car_body": ("vehicle:item/sports_car_body", 16383998),
            "sports_car_steering_wheel": ("vehicle:item/sports_car_steering_wheel", 0xFFFFFF),
            "sports_car_hood": ("vehicle:item/sports_car_hood", 16383998),
            "sports_car_left_door": ("vehicle:item/sports_car_left_door", 16383998),
            "sports_car_right_door": ("vehicle:item/sports_car_right_door", 16383998),
            "sports_car_boot": ("vehicle:item/sports_car_boot", 16383998),
            "sports_car_seat": ("vehicle:item/sports_car_seat", 16383998),
            "sports_car_dashboard": ("vehicle:item/sports_car_dashboard", 16383998),
            "sports_car_roof": ("vehicle:item/sports_car_roof", 16383998),
            "mini_bus_body": ("vehicle:item/mini_bus_body", 16383998),
            "mini_bus_stock_roof": ("vehicle:item/mini_bus_stock_roof", 16383998),
            "mini_bus_front_roof": ("vehicle:item/mini_bus_front_roof", 16383998),
            "mini_bus_roof_racks": ("vehicle:item/mini_bus_roof_racks", 16383998),
            "mini_bus_left_door": ("vehicle:item/mini_bus_left_door", 16383998),
            "mini_bus_right_door": ("vehicle:item/mini_bus_right_door", 16383998),
            "mini_bus_sliding_door": ("vehicle:item/mini_bus_sliding_door", 16383998),
            "mini_bus_rear": ("vehicle:item/mini_bus_rear", 16383998),
            "mini_bus_seat": ("vehicle:item/mini_bus_seat", 16383998),
            "mini_bus_dashboard": ("vehicle:item/mini_bus_dashboard", 16383998),
            "big_tow_bar": ("vehicle:item/big_tow_bar", 16383998),
            "golf_cart_body": ("vehicle:item/golf_cart_body", 16383998),
            "jet_ski_body": ("vehicle:item/jet_ski_body", 16383998),
            "sports_plane_body": ("vehicle:item/sports_plane_body", 16383998),
            "sports_plane_wings": ("vehicle:item/sports_plane_wings", 16383998),
            "sports_plane_seat": ("vehicle:item/sports_plane_seat", 16383998),
            "sports_plane_propeller": ("vehicle:item/sports_plane_propeller", 16383998),
            "sports_plane_left_aileron": ("vehicle:item/sports_plane_left_aileron", 16383998),
            "sports_plane_right_aileron": ("vehicle:item/sports_plane_right_aileron", 16383998),
            "sports_plane_elevator": ("vehicle:item/sports_plane_elevator", 16383998),
            "sports_plane_joystick": ("vehicle:item/sports_plane_joystick", 16383998),
            "compact_helicopter_body": ("vehicle:item/compact_helicopter_body", 16383998),
            "compact_helicopter_blades": ("vehicle:item/compact_helicopter_blades", 16383998),
            "compact_helicopter_joystick": ("vehicle:item/compact_helicopter_joystick", 16383998),
            "compact_helicopter_seat": ("vehicle:item/compact_helicopter_seat", 16383998),
            "compact_helicopter_tail_rotor": ("vehicle:item/compact_helicopter_tail_rotor", 16383998),
            "sofacopter_sofa": ("vehicle:item/sofacopter_sofa", 0xFFFFFF),
            "sofacopter_arm": ("vehicle:item/sofacopter_arm", 0xFFFFFF),
            "sofacopter_blades": ("vehicle:item/sofacopter_blades", 0xFFFFFF),
            "go_kart_steering_wheel": ("vehicle:item/go_kart_steering_wheel", 0xFFFFFF),
            "tow_bar": ("vehicle:item/tow_bar", 0xFFFFFF),
            "fertilizer_body": ("vehicle:item/fertilizer_body", 16383998),
            "seeder_body": ("vehicle:item/seeder_body", 16383998),
            "storage_trailer_body": ("vehicle:item/storage_trailer_body", 16383998),
            "fluid_trailer_body": ("vehicle:item/fluid_trailer_body", 16383998),
            "vehicle_trailer_body": ("vehicle:item/vehicle_trailer_body", 16383998),
            "seed_spiker": ("vehicle:item/seed_spiker", 0xFFFFFF),
            "standard_wheel": ("vehicle:item/standard_wheel", 0xFFFFFF),
            "iron_small_engine": ("vehicle:item/iron_small_engine", 0xFFFFFF),
            "iron_large_engine": ("vehicle:item/iron_large_engine", 0xFFFFFF),
            "fuel_door_closed": ("vehicle:item/fuel_door_closed", 16383998),
            "small_fuel_door_closed": ("vehicle:item/small_fuel_door_closed", 16383998),
            "key_hole": ("vehicle:item/key_hole", 16383998),
        }.items():
            write_json(namespace / f"items/{item}.json", item_definition(model, tint))

        convert_model(
            ASSETS / "models/vehicle/go_kart/base.json",
            namespace / "models/item/go_kart_body.json",
            {"2": "vehicle:item/go_kart_body", "particle": "vehicle:item/go_kart_body"},
        )
        convert_model(
            ASSETS / "models/vehicle/lawn_mower_body.json",
            namespace / "models/item/lawn_mower_body.json",
            {
                "seat": "minecraft:block/black_wool",
                "axel": "minecraft:block/light_gray_concrete",
                "blade_cover": "minecraft:block/black_concrete",
                "logo": "vehicle:item/lawn_mower_logo",
                "body": "minecraft:block/white_concrete",
                "particle": "minecraft:block/white_concrete",
            },
        )
        convert_model(
            ASSETS / "models/vehicle/quad_bike/base.json",
            namespace / "models/item/quad_bike_body.json",
            {"1": "vehicle:item/quad_bike_body", "particle": "vehicle:item/quad_bike_body"},
        )
        convert_model(
            ASSETS / "models/vehicle/quad_bike/handles.json",
            namespace / "models/item/quad_bike_handles.json",
            {"texture": "vehicle:item/quad_bike_handles", "particle": "vehicle:item/quad_bike_handles"},
        )
        convert_model(
            ASSETS / "models/vehicle/tractor_body.json",
            namespace / "models/item/tractor_body.json",
            {
                "seat": "minecraft:block/black_wool",
                "axel": "minecraft:block/light_gray_concrete",
                "color": "minecraft:block/white_concrete",
                # The source uses the anvil atlas and a tiny Cray Industries badge.
                # Some 1.21.4 clients resolve those two legacy references as the
                # missing-texture checkerboard. Keep the body clean and deterministic.
                "detail": "minecraft:block/gray_concrete",
                "cray_industries_logo": "minecraft:block/white_concrete",
                "particle": "minecraft:block/white_concrete",
            },
        )
        convert_model(
            ASSETS / "models/vehicle/dirt_bike/body.json",
            namespace / "models/item/dirt_bike_body.json",
            {"1": "vehicle:item/dirt_bike_body", "particle": "vehicle:item/dirt_bike_body"},
        )
        convert_model(
            ASSETS / "models/vehicle/dirt_bike/handles.json",
            namespace / "models/item/dirt_bike_handles.json",
            {"2": "vehicle:item/dirt_bike_handles", "particle": "vehicle:item/dirt_bike_handles"},
        )
        for source, target in {
            "body": "moped_body",
            "handles": "moped_handles",
            "mud_guard": "moped_mud_guard",
            "cosmetics/stock_seat": "moped_stock_seat",
            "cosmetics/stock_tray": "moped_stock_tray",
            "cosmetics/stock_front_light": "moped_stock_front_light",
        }.items():
            convert_model(
                ASSETS / f"models/vehicle/moped/{source}.json",
                namespace / f"models/item/{target}.json",
                {"1": f"vehicle:item/{target}", "2": f"vehicle:item/{target}",
                 "particle": f"vehicle:item/{target}"},
            )
        convert_model(
            ASSETS / "models/vehicle/off_roader_body.json",
            namespace / "models/item/off_roader_body.json",
            {
                "indicator_light": "minecraft:block/orange_stained_glass",
                "brake_light": "minecraft:block/red_stained_glass",
                "headlight_glass": "minecraft:block/white_stained_glass",
                "headlight": "minecraft:block/redstone_lamp",
                "body": "minecraft:block/white_concrete",
                "seat": "minecraft:block/black_wool",
                "spring": "vehicle:item/off_roader_spring",
                "axel": "minecraft:block/light_gray_concrete",
                "trim": "minecraft:block/white_concrete",
                "logo": "vehicle:item/off_roader_logo",
                "window_frame": "minecraft:block/black_concrete",
                "windshield": "minecraft:block/glass",
                "bumper": "minecraft:block/iron_block",
                "grill": "vehicle:item/off_roader_grill",
                # Keep the source's dark metal look without relying on the
                # legacy anvil atlas path that can become missing-texture art.
                "frame": "minecraft:block/gray_concrete",
                "particle": "minecraft:block/white_concrete",
            },
        )
        convert_model(
            ASSETS / "models/vehicle/sports_car/base.json",
            namespace / "models/item/sports_car_body.json",
            {"8": "vehicle:item/sports_car_body", "particle": "vehicle:item/sports_car_body"},
            geometry_scale=0.5,
        )
        convert_model(
            ASSETS / "models/vehicle/sports_car/steering_wheel.json",
            namespace / "models/item/sports_car_steering_wheel.json",
            {"texture": "vehicle:item/sports_car_steering_wheel",
             "particle": "vehicle:item/sports_car_steering_wheel"},
        )
        for source, target, texture_key in (
            ("hood", "sports_car_hood", "9"),
            ("left_door", "sports_car_left_door", "11"),
            ("right_door", "sports_car_right_door", "12"),
            ("boot", "sports_car_boot", "13"),
            ("seat", "sports_car_seat", "14"),
            ("dashboard", "sports_car_dashboard", "7"),
            ("roof", "sports_car_roof", "10"),
        ):
            convert_model(
                ASSETS / f"models/vehicle/sports_car/cosmetics/{source}.json",
                namespace / f"models/item/{target}.json",
                {texture_key: f"vehicle:item/{target}", "particle": f"vehicle:item/{target}"},
                geometry_scale=0.5,
            )
        convert_model(
            ASSETS / "models/vehicle/mini_bus/body.json",
            namespace / "models/item/mini_bus_body.json",
            {"2": "vehicle:item/mini_bus_body", "particle": "vehicle:item/mini_bus_body"},
        )
        for source, target, texture_key in (
            ("stock_roof", "mini_bus_stock_roof", "1"),
            ("front_roof", "mini_bus_front_roof", "1"),
            ("roof_racks", "mini_bus_roof_racks", "2"),
            ("stock_left_door", "mini_bus_left_door", "2"),
            ("stock_right_door", "mini_bus_right_door", "2"),
            ("stock_sliding_door", "mini_bus_sliding_door", "1"),
            ("aircon_ladder", "mini_bus_rear", "2"),
            ("stock_seat", "mini_bus_seat", "2"),
            ("stock_dashboard", "mini_bus_dashboard", "2"),
        ):
            convert_model(
                ASSETS / f"models/vehicle/mini_bus/cosmetics/{source}.json",
                namespace / f"models/item/{target}.json",
                {texture_key: f"vehicle:item/{target}", "particle": f"vehicle:item/{target}"},
            )
        convert_model(
            ASSETS / "models/vehicle/big_tow_bar.json",
            namespace / "models/item/big_tow_bar.json",
            {"texture": "vehicle:item/big_tow_bar", "particle": "vehicle:item/big_tow_bar"},
        )
        convert_model(
            ASSETS / "models/vehicle/golf_cart_body.json",
            namespace / "models/item/golf_cart_body.json",
        )
        convert_model(
            ASSETS / "models/vehicle/jet_ski_body.json",
            namespace / "models/item/jet_ski_body.json",
        )
        for source, target, texture_key, geometry_scale in (
            ("base", "sports_plane_body", "base", 1.0 / 3.0),
            ("cosmetics/wings", "sports_plane_wings", "wings", 1.0 / 3.0),
            ("cosmetics/seat", "sports_plane_seat", "seat", 1.0),
            ("cosmetics/propeller", "sports_plane_propeller", "propeller", 1.0),
            ("cosmetics/left_aileron", "sports_plane_left_aileron", "left_aileron", 1.0 / 3.0),
            ("cosmetics/right_aileron", "sports_plane_right_aileron", "right_aileron", 1.0 / 3.0),
            ("cosmetics/elevator", "sports_plane_elevator", "elevator", 1.0),
            ("cosmetics/joystick", "sports_plane_joystick", "joystick", 1.0),
        ):
            convert_model(
                ASSETS / f"models/vehicle/sports_plane/{source}.json",
                namespace / f"models/item/{target}.json",
                {texture_key: f"vehicle:item/{target}",
                 "particle": f"vehicle:item/{target}"},
                geometry_scale=geometry_scale,
                legalize_rotations=True,
            )
        for source, target, texture_key, geometry_scale in (
            ("base", "compact_helicopter_body", "base", 0.25),
            ("cosmetics/blades", "compact_helicopter_blades", "blades", 1.0 / 3.0),
            ("cosmetics/joystick", "compact_helicopter_joystick", "joystick", 1.0),
            ("cosmetics/seat", "compact_helicopter_seat", "seat", 1.0),
            ("cosmetics/tail_rotor", "compact_helicopter_tail_rotor", "tail_rotor", 1.0),
        ):
            convert_model(
                ASSETS / f"models/vehicle/helicopter/{source}.json",
                namespace / f"models/item/{target}.json",
                {texture_key: f"vehicle:item/{target}",
                 "particle": f"vehicle:item/{target}"},
                geometry_scale=geometry_scale,
                legalize_rotations=True,
            )
        convert_model(
            ROOT / "tools/source_assets/cfm_sofa_single.json",
            namespace / "models/item/sofacopter_sofa.json",
            {
                "wool": "minecraft:block/red_wool",
                "support": "minecraft:block/oak_log",
                "particle": "minecraft:block/red_wool",
            },
        )
        convert_model(
            ASSETS / "models/vehicle/sofa_helicopter_arm.json",
            namespace / "models/item/sofacopter_arm.json",
            {
                "0": "minecraft:block/gray_concrete",
                "1": "minecraft:block/light_gray_concrete",
                "particle": "minecraft:block/light_gray_concrete",
            },
            legalize_rotations=True,
        )
        convert_model(
            ASSETS / "models/vehicle/sports_plane/cosmetics/wings.json",
            namespace / "models/item/sofacopter_blades.json",
            {
                "wings": "vehicle:item/sports_plane_wings",
                "particle": "vehicle:item/sports_plane_wings",
            },
            geometry_scale=1.0 / 3.0,
            legalize_rotations=True,
        )
        convert_model(
            ASSETS / "models/vehicle/go_kart_steering_wheel.json",
            namespace / "models/item/go_kart_steering_wheel.json",
        )
        convert_model(
            ASSETS / "models/vehicle/tow_bar.json",
            namespace / "models/item/tow_bar.json",
        )
        convert_model(
            ASSETS / "models/vehicle/fuel_door_closed.json",
            namespace / "models/item/fuel_door_closed.json",
            {"1": "vehicle:item/fuel_port_closed", "particle": "vehicle:item/fuel_port_closed"},
        )
        convert_model(
            ASSETS / "models/vehicle/small_fuel_door_closed.json",
            namespace / "models/item/small_fuel_door_closed.json",
            {"1": "vehicle:item/small_fuel_port_closed",
             "particle": "vehicle:item/small_fuel_port_closed"},
        )
        convert_model(
            ASSETS / "models/vehicle/key_hole.json",
            namespace / "models/item/key_hole.json",
            {
                "detail": "minecraft:block/gray_concrete",
                "inner": "minecraft:block/black_concrete",
                "base": "minecraft:block/white_concrete",
                "particle": "minecraft:block/white_concrete",
            },
        )
        for source, target in {
            "trailer_fertilizer_body": "fertilizer_body",
            "trailer_seeder_body": "seeder_body",
            "trailer_chest_body": "storage_trailer_body",
            "trailer_fluid_body": "fluid_trailer_body",
            "trailer_body": "vehicle_trailer_body",
            "seed_spiker": "seed_spiker",
        }.items():
            textures = None
            if source == "trailer_fluid_body":
                # The four Cray Industries side panels use the original pixels,
                # but move them from the legacy model/ path to a normal item
                # texture path that 1.21.4 ItemDisplay resolves reliably.
                textures = {
                    "glass": "minecraft:block/glass",
                    "cray_industries": "vehicle:item/fluid_trailer_logo",
                    "tank": "minecraft:block/white_concrete",
                    "base": "minecraft:block/anvil",
                    "frame": "minecraft:block/light_gray_concrete",
                }
            convert_model(
                ASSETS / f"models/vehicle/{source}.json",
                namespace / f"models/item/{target}.json",
                textures,
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
        # Flatten the iron engine onto the complete source geometry. This avoids
        # relying on Forge's parent-model lookup when the item is shown by a Display.
        convert_model(
            ASSETS / "models/item/large_engine.json",
            namespace / "models/item/iron_large_engine.json",
            {"large_engine": "vehicle:item/iron_large_engine",
             "particle": "vehicle:item/iron_large_engine"},
        )

        copy(
            ASSETS / "textures/vehicle/go_kart/base.png",
            namespace / "textures/item/go_kart_body.png",
        )
        copy(
            ASSETS / "textures/vehicle/quad_bike/base.png",
            namespace / "textures/item/quad_bike_body.png",
        )
        copy(
            ASSETS / "textures/vehicle/quad_bike/handles.png",
            namespace / "textures/item/quad_bike_handles.png",
        )
        copy(
            ASSETS / "textures/vehicle/dirt_bike/body.png",
            namespace / "textures/item/dirt_bike_body.png",
        )
        copy(
            ASSETS / "textures/vehicle/dirt_bike/handles.png",
            namespace / "textures/item/dirt_bike_handles.png",
        )
        for source, target in {
            "body.png": "moped_body.png",
            "handles.png": "moped_handles.png",
            "mud_guard.png": "moped_mud_guard.png",
            "cosmetics/stock_seat.png": "moped_stock_seat.png",
            "cosmetics/stock_tray.png": "moped_stock_tray.png",
            "cosmetics/stock_front_light.png": "moped_stock_front_light.png",
        }.items():
            copy(
                ASSETS / f"textures/vehicle/moped/{source}",
                namespace / f"textures/item/{target}",
            )
        copy(
            ASSETS / "textures/vehicle/mini_bus/body.png",
            namespace / "textures/item/mini_bus_body.png",
        )
        for source, target in {
            "stock_roof": "mini_bus_stock_roof",
            "front_roof": "mini_bus_front_roof",
            "roof_racks": "mini_bus_roof_racks",
            "stock_left_door": "mini_bus_left_door",
            "stock_right_door": "mini_bus_right_door",
            "stock_sliding_door": "mini_bus_sliding_door",
            "aircon_ladder": "mini_bus_rear",
            "stock_seats": "mini_bus_seat",
            "stock_dashboard": "mini_bus_dashboard",
        }.items():
            copy(
                ASSETS / f"textures/vehicle/mini_bus/cosmetics/{source}.png",
                namespace / f"textures/item/{target}.png",
            )
        copy(
            ASSETS / "textures/vehicle/big_tow_bar.png",
            namespace / "textures/item/big_tow_bar.png",
        )
        copy(
            ASSETS / "textures/vehicle/sports_car/base.png",
            namespace / "textures/item/sports_car_body.png",
        )
        copy(
            ASSETS / "textures/vehicle/sports_car/steering_wheel.png",
            namespace / "textures/item/sports_car_steering_wheel.png",
        )
        for source, target in {
            "hood": "sports_car_hood",
            "left_door": "sports_car_left_door",
            "right_door": "sports_car_right_door",
            "boot": "sports_car_boot",
            "seat": "sports_car_seat",
            "dashboard": "sports_car_dashboard",
            "roof": "sports_car_roof",
        }.items():
            copy(
                ASSETS / f"textures/vehicle/sports_car/cosmetics/{source}.png",
                namespace / f"textures/item/{target}.png",
            )
        for source, target in {
            "base": "sports_plane_body",
            "wings": "sports_plane_wings",
            "seat": "sports_plane_seat",
            "propeller": "sports_plane_propeller",
            "left_aileron": "sports_plane_left_aileron",
            "right_aileron": "sports_plane_right_aileron",
            "elevator": "sports_plane_elevator",
            "joystick": "sports_plane_joystick",
        }.items():
            copy(
                ASSETS / f"textures/vehicle/sports_plane/{source}.png",
                namespace / f"textures/item/{target}.png",
            )
        for source, target in {
            "base": "compact_helicopter_body",
            "blades": "compact_helicopter_blades",
            "joystick": "compact_helicopter_joystick",
            "seat": "compact_helicopter_seat",
            "tail_rotor": "compact_helicopter_tail_rotor",
        }.items():
            copy(
                ASSETS / f"textures/vehicle/helicopter/{source}.png",
                namespace / f"textures/item/{target}.png",
            )
        copy(
            ASSETS / "textures/model/spring.png",
            namespace / "textures/item/off_roader_spring.png",
        )
        copy(
            ASSETS / "textures/model/white_mesh.png",
            namespace / "textures/item/off_roader_grill.png",
        )
        copy(
            ASSETS / "textures/model/cray_industries.png",
            namespace / "textures/item/off_roader_logo.png",
        )
        copy(
            ASSETS / "textures/model/cray_industries.png",
            namespace / "textures/item/lawn_mower_logo.png",
        )
        copy(
            ASSETS / "textures/model/cray_industries.png",
            namespace / "textures/item/fluid_trailer_logo.png",
        )
        copy(
            ASSETS / "textures/model/cray_industries.png",
            namespace / "textures/model/cray_industries.png",
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
            ASSETS / "textures/model/iron_large_engine.png",
            namespace / "textures/item/iron_large_engine.png",
        )
        copy(
            ASSETS / "textures/model/fuel_port_closed.png",
            namespace / "textures/item/fuel_port_closed.png",
        )
        copy(
            ASSETS / "textures/model/small_fuel_port_closed.png",
            namespace / "textures/item/small_fuel_port_closed.png",
        )
        copy(
            ASSETS / "sounds/entity/go_kart/engine.ogg",
            namespace / "sounds/entity/go_kart/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/quad_bike/engine.ogg",
            namespace / "sounds/entity/quad_bike/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/tractor/engine.ogg",
            namespace / "sounds/entity/tractor/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/dirt_bike/engine.ogg",
            namespace / "sounds/entity/dirt_bike/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/moped/engine.ogg",
            namespace / "sounds/entity/moped/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/jet_ski/engine.ogg",
            namespace / "sounds/entity/jet_ski/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/sports_car/engine.ogg",
            namespace / "sounds/entity/sports_car/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/sports_plane/engine.ogg",
            namespace / "sounds/entity/sports_plane/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/mini_bus/engine.ogg",
            namespace / "sounds/entity/mini_bus/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/vehicle/helicopter_rotor.ogg",
            namespace / "sounds/entity/vehicle/helicopter_rotor.ogg",
        )
        for source in (
            "entity/vehicle/door/open.ogg",
            "entity/vehicle/door/close.ogg",
            "entity/vehicle/hood/open.ogg",
            "entity/vehicle/hood/close.ogg",
        ):
            copy(ASSETS / f"sounds/{source}", namespace / f"sounds/{source}")
        write_json(namespace / "sounds.json", {
            "entity.go_kart.engine": {
                "sounds": [{"name": "vehicle:entity/go_kart/engine", "preload": True}]
            },
            "entity.quad_bike.engine": {
                "sounds": [{"name": "vehicle:entity/quad_bike/engine", "preload": True}]
            },
            "entity.tractor.engine": {
                "sounds": [{"name": "vehicle:entity/tractor/engine", "preload": True}]
            },
            "entity.dirt_bike.engine": {
                "sounds": [{"name": "vehicle:entity/dirt_bike/engine", "preload": True}]
            },
            "entity.moped.engine": {
                "sounds": [{"name": "vehicle:entity/moped/engine", "preload": True}]
            },
            "entity.jet_ski.engine": {
                "sounds": [{"name": "vehicle:entity/jet_ski/engine", "preload": True}]
            },
            "entity.sports_car.engine": {
                "sounds": [{"name": "vehicle:entity/sports_car/engine", "preload": True}]
            },
            "entity.sports_plane.engine": {
                "sounds": [{"name": "vehicle:entity/sports_plane/engine", "preload": True}]
            },
            "entity.mini_bus.engine": {
                "sounds": [{"name": "vehicle:entity/mini_bus/engine", "preload": True}]
            },
            "entity.vehicle.helicopter_rotor": {
                "sounds": [{"name": "vehicle:entity/vehicle/helicopter_rotor", "preload": True}]
            },
            "entity.vehicle.door.open": {
                "sounds": [{"name": "vehicle:entity/vehicle/door/open", "preload": True}]
            },
            "entity.vehicle.door.close": {
                "sounds": [{"name": "vehicle:entity/vehicle/door/close", "preload": True}]
            },
            "entity.vehicle.hood.open": {
                "sounds": [{"name": "vehicle:entity/vehicle/hood/open", "preload": True}]
            },
            "entity.vehicle.hood.close": {
                "sounds": [{"name": "vehicle:entity/vehicle/hood/close", "preload": True}]
            },
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
                        default=ROOT / "paper-plugin/build/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r26.zip")
    args = parser.parse_args()
    output, sha1 = build(args.output.resolve())
    print(f"Resource pack: {output}")
    print(f"SHA-1: {sha1}")


if __name__ == "__main__":
    main()
