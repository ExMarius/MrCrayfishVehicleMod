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
                  geometry_scale: float = 1.0, legalize_rotations: bool = False,
                  texture_size: list[int] | None = None,
                  element_patch: dict | None = None,
                  texture_uv_scale: dict[str, float] | None = None) -> None:
    """Normalize Forge/Blockbench model metadata to vanilla model JSON.

    Framework accepts oversized elements, but vanilla rejects element coordinates
    outside -16..32 and replaces the whole model with its black/magenta fallback.
    Oversized geometry is scaled around the item origin (8, 8, 8); the display
    rig applies the exact inverse scale so the model keeps its source dimensions.

    Minecraft 1.21.4 also accepts only 22.5-degree element-rotation increments.
    Framework's complex-model loader accepts arbitrary values. Models using those
    source angles must opt into nearest legal rotation conversion; arbitrary angles
    did not become a vanilla feature until after the server's 1.21.4 protocol.

    texture_size is retained purely as Blockbench-editing metadata (so the model can
    be reopened and edited against its originally-authored canvas); per Minecraft's
    own model-format documentation it is one of the fields "used by Blockbench" that
    "aren't used by Minecraft" at all, so the game itself never reads it or rescales
    anything because of it — writing it alone does NOT fix oversized UV. (An earlier
    r36/r37 round of this port incorrectly assumed the game honored it; see
    SOURCE_POSITION_AUDIT.md for the correction.) Any UV value actually needs to be
    baked into real 0-16 numbers up front — see texture_uv_scale below — for models
    whose source authors UV past the substitute sprite's real 16x16 bounds.

    texture_uv_scale optionally multiplies every uv coordinate on faces that use a
    given "#name" texture variable by a fixed factor, baked directly into the
    emitted numbers (not relying on any engine-side rescaling). Use this when a
    source model's own UV for a specific texture was authored 1:1 against an
    element's real pixel length exceeding 16 (the vanilla sprite this port
    substitutes for the source's own dedicated art is only 16x16), which Minecraft's
    model format documents as having "inconsistent" results; see
    SOURCE_POSITION_AUDIT.md (Vehicle Trailer side rails).

    element_patch optionally corrects a specific face's authored UV on a single
    element, identified by its own "from"/"to" coordinates, when the source's own
    value is an isolated authoring error (not a UV-overflow mismatch) rather than
    a reproducible discrepancy; see SOURCE_POSITION_AUDIT.md for the one documented
    use of this (Golf Cart's roof strut).
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
    if element_patch is not None:
        target_from = element_patch["from"]
        target_to = element_patch["to"]
        for element in model.get("elements", []):
            if element.get("from") == target_from and element.get("to") == target_to:
                for face_name, uv in element_patch["faces"].items():
                    element["faces"][face_name]["uv"] = uv
                break
        else:
            raise ValueError(f"element_patch target not found in {source}")
    if texture_uv_scale is not None:
        for texture_name, scale in texture_uv_scale.items():
            variable = f"#{texture_name}"
            for element in model.get("elements", []):
                for face in element.get("faces", {}).values():
                    if face.get("texture") == variable and "uv" in face:
                        face["uv"] = [round(coordinate * scale, 4) for coordinate in face["uv"]]
    model.pop("loader", None)
    model.pop("groups", None)
    if texture_size is not None:
        model["texture_size"] = texture_size
    else:
        # texture_size is understood by the source loader but unnecessary for vanilla
        # item models whose UV was authored against the default 16x16 canvas.
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
                "description": "MrCrayfish Vehicle Plugin r38 — twenty-three vehicles and five trailers",
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
            "dune_buggy_body": ("vehicle:item/dune_buggy_body", 0xF2B116),
            "dune_buggy_handles": ("vehicle:item/dune_buggy_handles", 0xF2B116),
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
            "atv_body": ("vehicle:item/atv_body", 16383998),
            "atv_handles": ("vehicle:item/atv_handles", 16383998),
            "mini_bike_body": ("vehicle:item/mini_bike_body", 16383998),
            "mini_bike_handles": ("vehicle:item/mini_bike_handles", 16383998),
            "smart_car_body": ("vehicle:item/smart_car_body", 16383998),
            "speed_boat_body": ("vehicle:item/speed_boat_body", 16383998),
            "aluminum_boat_body": ("vehicle:item/aluminum_boat_body", 16383998),
            "bumper_car_body": ("vehicle:item/bumper_car_body", 16383998),
            "shopping_cart_body": ("vehicle:item/shopping_cart_body", 16383998),
            # Gas pump visual rig (Paper plugin display-entity port of GasPumpBlock's two
            # halves, its idle nozzle, and the hose it draws toward a fueling player).
            "gas_pump_bottom": ("vehicle:item/gas_pump_bottom", 0xFFFFFF),
            "gas_pump_top": ("vehicle:item/gas_pump_top", 0xFFFFFF),
            "gas_pump_nozzle": ("vehicle:item/gas_pump_nozzle", 0xFFFFFF),
            "gas_hose_segment": ("vehicle:item/gas_hose_segment", 0xFFFFFF),
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
            ROOT / "tools/source_assets/vehicle_dune_buggy_body.json",
            namespace / "models/item/dune_buggy_body.json",
            {
                "seat": "minecraft:block/yellow_wool",
                "engine_base": "minecraft:block/light_gray_concrete",
                "engine_part": "minecraft:block/gray_concrete",
                "body": "minecraft:block/yellow_concrete",
                "particle": "minecraft:block/yellow_concrete",
            },
        )
        convert_model(
            ROOT / "tools/source_assets/vehicle_dune_buggy_handles.json",
            namespace / "models/item/dune_buggy_handles.json",
            {
                "handles": "minecraft:block/yellow_concrete",
                "axel": "minecraft:block/light_gray_concrete",
                "base": "minecraft:block/red_concrete",
                "particle": "minecraft:block/yellow_concrete",
            },
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
            element_patch={
                # This tiny roof-strut element's own "west" and "up" faces author UV
                # dimensions that match none of this same element's four other faces
                # or its own real geometry (10x1.1x1.1 model pixels): "west" is
                # [0,0,-11.9,5.1] instead of matching "east"'s correctly-sized
                # [0,0,1.1,1.1], and "up" is [0,0,12,6.1] instead of matching
                # "down"'s correctly-sized [0,0,10,1.1]. Every sibling face proves
                # the element's real UV footprint; this is an isolated authoring
                # error in the source's own asset, not a reproducible discrepancy
                # (the other five faces across this model and the rest of the body
                # are internally consistent). Per explicit user direction, the two
                # mismatched faces are patched here to mirror their correctly-sized
                # counterparts rather than left to sample whatever sprite happens to
                # sit next to white concrete in the atlas; see SOURCE_POSITION_AUDIT.md
                # r36 entry. This is a disclosed deviation from the literal source
                # value, not a literal reproduction of it.
                "from": [11, 28.5, 19],
                "to": [21, 29.6, 20.1],
                "faces": {
                    "west": [0, 0, 1.1, 1.1],
                    "up": [0, 0, 10, 1.1],
                },
            },
        )
        convert_model(
            ASSETS / "models/vehicle/jet_ski_body.json",
            namespace / "models/item/jet_ski_body.json",
            {
                # The source model's "seat"/"white"/"detail"/"body" keys already resolve
                # cleanly in modern vanilla and are carried over unchanged. r35: "logo"
                # was the literal, unconverted "vehicle:model/cray_industries" path, which
                # is not covered by Minecraft's default "blocks" sprite atlas sources and
                # renders as a missing-texture black/purple square in-game. Point it at
                # the same dedicated textures/item/ copy convention used by every other
                # vehicle's Cray Industries decal; see SOURCE_POSITION_AUDIT.md r35 entry
                # for the full root-cause analysis.
                "seat": "minecraft:block/black_wool",
                "white": "minecraft:block/white_concrete",
                "logo": "vehicle:item/jet_ski_logo",
                "detail": "minecraft:block/anvil",
                "body": "minecraft:block/white_concrete",
            },
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
            ROOT / "tools/source_assets/cfm_ceiling_fan_fans.json",
            namespace / "models/item/sofacopter_blades.json",
            {
                "0": "minecraft:block/gray_concrete",
                "1": "minecraft:block/white_concrete",
                "particle": "minecraft:block/white_concrete",
            },
            legalize_rotations=True,
        )
        convert_model(
            ROOT / "tools/source_assets/vehicle_atv_body.json",
            namespace / "models/item/atv_body.json",
            {
                # The vendored source model (exported with MrCrayfish's own Model
                # Creator, the closest available reference for this un-decompiled
                # vehicle) declares "body": white_concrete and "frame": anvil itself.
                # An earlier port substituted lime_concrete/gray_concrete here with
                # no documented reason, turning the dyeable default-white ATV green;
                # restored to the vendored file's own choices. See
                # SOURCE_POSITION_AUDIT.md r37 entry.
                "seat": "minecraft:block/black_wool",
                "axel": "minecraft:block/light_gray_concrete",
                "body": "minecraft:block/white_concrete",
                "frame": "minecraft:block/anvil",
                "particle": "minecraft:block/white_concrete",
            },
        )
        convert_model(
            ROOT / "tools/source_assets/vehicle_atv_handles.json",
            namespace / "models/item/atv_handles.json",
            {
                # Same restoration as atv_body above: the vendored source model
                # declares "handles": black_concrete and "frame_alt": stone itself.
                "handles": "minecraft:block/black_concrete",
                "frame_alt": "minecraft:block/stone",
                "frame_main": "minecraft:block/gray_concrete",
                "particle": "minecraft:block/black_concrete",
            },
        )
        convert_model(
            ROOT / "tools/source_assets/vehicle_mini_bike_body.json",
            namespace / "models/item/mini_bike_body.json",
            {
                "seat": "minecraft:block/black_wool",
                "axel": "minecraft:block/light_gray_concrete",
                "body": "minecraft:block/red_concrete",
                "particle": "minecraft:block/red_concrete",
            },
        )
        convert_model(
            ROOT / "tools/source_assets/vehicle_mini_bike_handles.json",
            namespace / "models/item/mini_bike_handles.json",
            {
                "handles": "minecraft:block/red_concrete",
                "axel": "minecraft:block/light_gray_concrete",
                "body": "minecraft:block/gray_concrete",
                "particle": "minecraft:block/red_concrete",
            },
        )
        convert_model(
            ROOT / "tools/source_assets/vehicle_smart_car_body.json",
            namespace / "models/item/smart_car_body.json",
            {
                "seat": "minecraft:block/black_wool",
                "indicator_light": "minecraft:block/orange_stained_glass",
                "axel": "minecraft:block/light_gray_concrete",
                "brake_light": "minecraft:block/red_stained_glass",
                "steering_wheel_shaft": "minecraft:block/gray_concrete",
                "headlights": "minecraft:block/white_stained_glass",
                "logo": "minecraft:block/light_gray_concrete",
                "windshield": "minecraft:block/light_blue_stained_glass",
                "grill": "minecraft:block/gray_concrete",
                "frame": "minecraft:block/black_concrete",
                "base": "minecraft:block/cyan_concrete",
                "particle": "minecraft:block/cyan_concrete",
            },
        )
        convert_model(
            ROOT / "tools/source_assets/vehicle_speed_boat_body.json",
            namespace / "models/item/speed_boat_body.json",
            {
                "seat": "minecraft:block/white_wool",
                "glass": "minecraft:block/light_blue_stained_glass",
                "steering_wheel_shaft": "minecraft:block/gray_concrete",
                "logo": "minecraft:block/light_gray_concrete",
                "detail": "minecraft:block/light_gray_concrete",
                "tint": "minecraft:block/white_concrete",
                "base": "minecraft:block/red_concrete",
                "particle": "minecraft:block/red_concrete",
            },
        )
        convert_model(
            ROOT / "tools/source_assets/vehicle_aluminum_boat_body.json",
            namespace / "models/item/aluminum_boat_body.json",
            {
                # The released 1.16.X source model's own texture map already resolves
                # cleanly in modern vanilla: a plain white-concrete hull/particle (like
                # several sibling r29-batch vehicles), the mod's own real Cray Industries
                # decal for "logo", and "minecraft:block/anvil" for "seat" (a legitimate,
                # resolvable vanilla texture also used as-is by the Fluid Trailer's "base"
                # key below). None of these need substituting; see SOURCE_POSITION_AUDIT.md
                # for why r31's "recovered aluminum hull texture" was reverted in r34.
                # r35: "logo" now points at a dedicated textures/item/ copy (like every
                # other vehicle's Cray Industries decal) instead of the non-standard
                # textures/model/ path, which Minecraft's default "blocks" sprite atlas
                # does not stitch sprites from; see SOURCE_POSITION_AUDIT.md r35 entry.
                "seat": "minecraft:block/anvil",
                "logo": "vehicle:item/aluminum_boat_logo",
                "body": "minecraft:block/white_concrete",
                "particle": "minecraft:block/white_concrete",
            },

        )
        convert_model(
            ROOT / "tools/source_assets/vehicle_bumper_car_body.json",
            namespace / "models/item/bumper_car_body.json",
            {
                "seat": "minecraft:block/black_wool",
                "rubber": "minecraft:block/black_concrete",
                # Keep the source's dark detailing without the legacy anvil atlas path.
                "detail": "minecraft:block/gray_concrete",
                "body": "minecraft:block/white_concrete",
                "shaft": "minecraft:block/light_gray_concrete",
                "particle": "minecraft:block/white_concrete",
            },
        )
        convert_model(
            ROOT / "tools/source_assets/vehicle_shopping_cart_body.json",
            namespace / "models/item/shopping_cart_body.json",
            {
                # All of this body's source textures are the mod's own existing
                # vehicle:model assets (mesh patterns and the Cray Industries logo),
                # not CFM or the legacy anvil atlas, so they carry over unchanged.
                # r35: each one is now copied to its own textures/item/ file and
                # referenced via vehicle:item/, matching the convention already used
                # for every other vehicle's mesh/logo decals (e.g. off_roader_grill,
                # off_roader_logo). The old textures/model/ path is not covered by
                # Minecraft's default "blocks" sprite atlas sources, so sprites placed
                # there never get stitched in and render as the missing-texture
                # black/purple checkerboard in-game; see SOURCE_POSITION_AUDIT.md r35.
                "plastic_frame": "minecraft:block/light_gray_concrete",
                "plastic_mesh_two": "vehicle:item/shopping_cart_mesh_angled",
                "metal_mesh": "vehicle:item/shopping_cart_white_mesh",
                "metal": "minecraft:block/white_concrete",
                "logo": "vehicle:item/shopping_cart_logo",
                "plastic_mesh_one": "vehicle:item/shopping_cart_mesh_angled_flipped",
                "plastic_mesh_three": "vehicle:item/shopping_cart_mesh",
                "particle": "minecraft:block/light_gray_concrete",
            },
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
            texture_uv_scale = None
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
            if source == "trailer_body":
                # The two 17-model-pixel-long side rails ("frame" texture, substituted
                # with vanilla's 16x16 light_gray_concrete) author their UV 1:1 against
                # their own real length, consistent across all four of their faces, up
                # to UV coordinate 17 -- one unit past the substitute sprite's real
                # 16x16 bounds. Minecraft's own model-format documentation states UV
                # outside 0-16 has "inconsistent" results (it does not clamp or tile;
                # it samples past the sprite's edge into whatever the atlas happens to
                # stitch next to it), which is the source of the purple/stray pixels
                # reported under the trailer's sides. r36 tried to fix this by
                # declaring texture_size: [17, 17] on the model, assuming the game
                # would rescale UV to fit -- but that field is Blockbench-only editing
                # metadata the game never reads (see convert_model's docstring), so
                # that round silently fixed nothing. Bake the 16/17 scale into the
                # "frame" faces' own UV numbers instead, which is engine-agnostic and
                # actually changes what gets sampled; the substitute texture is a flat
                # solid color, so the sub-pixel shift this introduces is invisible.
                # The other two textures sharing this same model ("bed_frame",
                # "bed_panel") already stay within 0-16 and are left untouched. See
                # SOURCE_POSITION_AUDIT.md for the full correction.
                texture_uv_scale = {"frame": 16.0 / 17.0}
            convert_model(
                ASSETS / f"models/vehicle/{source}.json",
                namespace / f"models/item/{target}.json",
                textures,
                texture_uv_scale=texture_uv_scale,
            )

        convert_model(
            ASSETS / "models/item/standard_wheel.json",
            namespace / "models/item/standard_wheel.json",
            {"particle": "vehicle:item/standard_wheel", "wheel": "vehicle:item/standard_wheel"},
            # r37 declared texture_size: [32, 32] here on the theory that Minecraft
            # would otherwise reinterpret this model's own UV (confined to a max
            # coordinate of 11, i.e. the top-left region of wheel.png where its real
            # 22x22-pixel artwork lives out of the full 32x32 canvas) against a wrong
            # 16x16 assumption. That theory does not hold: Minecraft always scales a
            # model's declared 0-16 UV space proportionally against the texture's own
            # real resolution, whatever it is (this is exactly how higher-resolution
            # resource packs already work against unmodified vanilla models), so
            # wheel.png's unmodified 32x32 copy was already read correctly before and
            # after r37 -- 11/16 of its full size lands exactly on its drawn 22/32
            # boundary either way. texture_size is additionally Blockbench-only
            # editing metadata the game does not use at all (see convert_model's
            # docstring); r37's declaration here was a harmless no-op kept only so
            # Blockbench shows the model's real authored canvas if ever reopened.
            # See SOURCE_POSITION_AUDIT.md for the correction and for where a
            # genuine instance of this same UV-overflow family of bug was found and
            # actually fixed (the Vehicle Trailer's side rails).
            texture_size=[32, 32],
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

        # Gas pump visual rig: the block's own two-half model plus its idle nozzle,
        # ported as display-entity items the same way vehicle bodies already are.
        # The hose itself has no original model (the mod drew it procedurally in
        # Java) so gas_hose_segment is new geometry: a single straight rod that the
        # Paper plugin chains and bends through the ported Hermite-spline math.
        convert_model(
            ASSETS / "models/block/gas_pump_bottom.json",
            namespace / "models/item/gas_pump_bottom.json",
            {"1": "vehicle:item/gas_pump", "particle": "vehicle:item/gas_pump"},
        )
        convert_model(
            ASSETS / "models/block/gas_pump_top.json",
            namespace / "models/item/gas_pump_top.json",
            {"1": "vehicle:item/gas_pump", "particle": "vehicle:item/gas_pump"},
        )
        convert_model(
            ASSETS / "models/vehicle/nozzle.json",
            namespace / "models/item/gas_pump_nozzle.json",
            {"2": "vehicle:item/gas_pump_nozzle", "particle": "vehicle:item/gas_pump_nozzle"},
        )
        convert_model(
            ASSETS / "models/vehicle/gas_hose_segment.json",
            namespace / "models/item/gas_hose_segment.json",
            {"1": "vehicle:item/gas_hose_segment", "particle": "vehicle:item/gas_hose_segment"},
        )

        copy(
            ASSETS / "textures/model/gas_pump.png",
            namespace / "textures/item/gas_pump.png",
        )
        copy(
            ASSETS / "textures/model/nozzle.png",
            namespace / "textures/item/gas_pump_nozzle.png",
        )
        copy(
            ASSETS / "textures/model/gas_hose_segment.png",
            namespace / "textures/item/gas_hose_segment.png",
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
            namespace / "textures/item/aluminum_boat_logo.png",
        )
        copy(
            ASSETS / "textures/model/cray_industries.png",
            namespace / "textures/item/shopping_cart_logo.png",
        )
        copy(
            ASSETS / "textures/model/cray_industries.png",
            namespace / "textures/item/jet_ski_logo.png",
        )
        copy(
            ASSETS / "textures/model/mesh.png",
            namespace / "textures/item/shopping_cart_mesh.png",
        )
        copy(
            ASSETS / "textures/model/mesh_angled.png",
            namespace / "textures/item/shopping_cart_mesh_angled.png",
        )
        copy(
            ASSETS / "textures/model/mesh_angled_flipped.png",
            namespace / "textures/item/shopping_cart_mesh_angled_flipped.png",
        )
        copy(
            ASSETS / "textures/model/white_mesh.png",
            namespace / "textures/item/shopping_cart_white_mesh.png",
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
            ROOT / "tools/source_assets/vehicle_bumper_car_engine.ogg",
            namespace / "sounds/entity/bumper_car/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/bumper_car/bonk.ogg",
            namespace / "sounds/entity/bumper_car/bonk.ogg",
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
            ASSETS / "sounds/entity/atv/engine.ogg",
            namespace / "sounds/entity/atv/engine.ogg",
        )
        copy(
            ASSETS / "sounds/entity/speed_boat/engine.ogg",
            namespace / "sounds/entity/speed_boat/engine.ogg",
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
            "entity.bumper_car.engine": {
                "sounds": [{"name": "vehicle:entity/bumper_car/engine", "preload": True}]
            },
            "entity.bumper_car.bonk": {
                "sounds": [{"name": "vehicle:entity/bumper_car/bonk", "preload": True}]
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
            "entity.atv.engine": {
                "sounds": [{"name": "vehicle:entity/atv/engine", "preload": True}]
            },
            "entity.speed_boat.engine": {
                "sounds": [{"name": "vehicle:entity/speed_boat/engine", "preload": True}]
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
                        default=ROOT / "paper-plugin/build/MrCrayfishVehiclePlugin-resource-pack-1.21.4-r38.zip")
    args = parser.parse_args()
    output, sha1 = build(args.output.resolve())
    print(f"Resource pack: {output}")
    print(f"SHA-1: {sha1}")


if __name__ == "__main__":
    main()
