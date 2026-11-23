#!/usr/bin/env python3
"""Geometry restyle: gives every body model a silhouette of its own.

The pack may legally reuse the GPLv3 vehicle art (see
``docs/LICENTA-SI-ASSETS-RO.md``), but the user asked for bodies that are no
longer the original shapes ("modifica modelele sa fie altele decat cele
originale"). This module post-processes the converted body models:

* **proportions** - a per-vehicle width/length/height ratio (stance) applied
  around the model's own centre;
* **cabin** - a per-vehicle greenhouse ratio (chopped or raised roof) applied
  above the belt line only, so pillars/windows stay attached;
* **kit** - a bolted-on accessory generated from the model's own bounding box
  (roof rack, light bar, roll hoop, exhaust stack, antenna, side rails). Kits
  are purely additive, so they can never distort the original geometry.

Everything stays inside the vanilla model limits (elements -16..32, UV 0..16,
no new rotations) because the resource-pack tests enforce them.
"""

from __future__ import annotations

import math
from typing import Iterable

LIMIT_LOW = -16.0
LIMIT_HIGH = 32.0

KIT_TEXTURES = {
    "kit_dark": "minecraft:block/black_concrete",
    "kit_metal": "minecraft:block/iron_block",
    "kit_lamp": "minecraft:block/redstone_lamp",
    "kit_soft": "minecraft:block/black_wool",
}

# model name -> (width, height, length, cabin ratio, kit)
# width/height/length are multipliers of the ORIGINAL body; cabin is the
# vertical ratio applied above the belt line (1.0 = untouched).
PROFILES: dict[str, tuple[float, float, float, float, tuple[str, ...]]] = {
    "go_kart_body": (1.08, 0.92, 1.02, 1.00, ("roll_hoop",)),
    "lawn_mower_body": (1.06, 0.90, 1.04, 1.00, ("roll_hoop", "antenna")),
    # Bodies split into separately placed cosmetic parts (handles, doors, hood,
    # roof) keep ratio 1.0 - the parts are positioned by the spec, so rescaling
    # only the body would leave them misaligned. They still get a kit, which is
    # purely additive.
    "quad_bike_body": (1.0, 1.0, 1.0, 1.0, ("footboards",)),
    "dune_buggy_body": (1.0, 1.0, 1.0, 1.0, ("roll_hoop", "antenna")),
    "tractor_body": (1.05, 0.90, 1.03, 1.08, ("exhaust_stack", "antenna")),
    "dirt_bike_body": (1.0, 1.0, 1.0, 1.0, ("footboards",)),
    "moped_body": (1.0, 1.0, 1.0, 1.0, ("footboards", "antenna")),
    "off_roader_body": (1.08, 1.00, 1.02, 1.12, ("roof_rack", "light_bar", "antenna")),
    "police_car_body": (1.08, 1.00, 1.02, 1.12, ("roof_rack", "antenna")),
    "sports_car_body": (1.0, 1.0, 1.0, 1.0, ("side_skirt",)),
    "mini_bus_body": (1.0, 1.0, 1.0, 1.0, ("side_skirt",)),
    "golf_cart_body": (1.06, 0.94, 1.02, 1.00, ("roof_rack", "antenna")),
    "jet_ski_body": (1.08, 1.00, 1.03, 1.00, ("side_skirt",)),
    "atv_body": (1.0, 1.0, 1.0, 1.0, ("footboards",)),
    "mini_bike_body": (1.0, 1.0, 1.0, 1.0, ("footboards",)),
    "smart_car_body": (1.08, 0.93, 1.02, 1.00, ("roof_box",)),
    "speed_boat_body": (1.08, 1.00, 1.04, 1.00, ("side_skirt", "antenna")),
    "aluminum_boat_body": (1.06, 1.00, 1.02, 1.00, ("side_skirt",)),
    "bumper_car_body": (1.08, 1.00, 1.02, 1.00, ("roll_hoop",)),
    "shopping_cart_body": (1.05, 1.00, 1.00, 1.00, ()),
    "sports_plane_body": (1.0, 1.0, 1.0, 1.0, ("antenna",)),
    "compact_helicopter_body": (1.0, 1.0, 1.0, 1.0, ("antenna",)),
    "sofacopter_sofa": (1.0, 1.0, 1.0, 1.0, ()),
}


def _bbox(elements: list[dict]) -> tuple[float, float, float, float, float, float]:
    xs: list[float] = []
    ys: list[float] = []
    zs: list[float] = []
    for element in elements:
        for corner in (element["from"], element["to"]):
            xs.append(corner[0])
            ys.append(corner[1])
            zs.append(corner[2])
    return min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)


def _roof(elements: list[dict], bbox) -> tuple[float, float, float, float, float]:
    """Roof plane: (top_y, min_x, max_x, min_z, max_z) from the widest flat element."""
    min_x, max_x, _min_y, max_y, min_z, max_z = bbox
    best = None
    best_area = 0.0
    for element in elements:
        top = max(element["from"][1], element["to"][1])
        if top < max_y - max(1.0, (max_y - bbox[2]) * 0.12):
            continue
        area = abs(element["to"][0] - element["from"][0]) * abs(element["to"][2] - element["from"][2])
        if area > best_area:
            best_area = area
            best = element
    if best is None:
        return max_y, min_x, max_x, min_z, max_z
    return (max(best["from"][1], best["to"][1]),
            min(best["from"][0], best["to"][0]), max(best["from"][0], best["to"][0]),
            min(best["from"][2], best["to"][2]), max(best["from"][2], best["to"][2]))


def _clamp(value: float) -> float:
    return max(LIMIT_LOW, min(LIMIT_HIGH, value))


def _box(x0: float, y0: float, z0: float, x1: float, y1: float, z1: float,
         texture: str) -> dict:
    low = [_clamp(x0), _clamp(y0), _clamp(z0)]
    high = [_clamp(x1), _clamp(y1), _clamp(z1)]
    if high[0] <= low[0]:
        high[0] = low[0] + 0.2
    if high[1] <= low[1]:
        high[1] = low[1] + 0.2
    if high[2] <= low[2]:
        high[2] = low[2] + 0.2
    faces = {name: {"uv": [0, 0, 16, 16], "texture": f"#{texture}"}
             for name in ("north", "east", "south", "west", "up", "down")}
    return {"from": low, "to": high, "faces": faces}


def _kit_boxes(kit: Iterable[str], elements: list[dict], bbox) -> list[dict]:
    min_x, max_x, min_y, max_y, min_z, max_z = bbox
    roof_y, rx0, rx1, rz0, rz1 = _roof(elements, bbox)
    width = max_x - min_x
    depth = max_z - min_z
    height = max_y - min_y
    centre_x = (min_x + max_x) / 2.0
    centre_z = (min_z + max_z) / 2.0
    boxes: list[dict] = []
    # A usable roof is one that covers most of the body's width and a fair part
    # of its length. Comparing against the whole body footprint would reject
    # pickup-style bodies, whose bed makes the footprint much longer than the cab.
    roof_span_x = (rx1 - rx0) / max(width, 1e-6)
    roof_span_z = (rz1 - rz0) / max(depth, 1e-6)
    has_flat_roof = roof_span_x >= 0.5 and roof_span_z >= 0.12

    def clamp_top(value: float) -> float:
        return min(value, LIMIT_HIGH - 1.0)

    for item in kit:
        if item in ("roof_rack", "light_bar", "roof_box", "roof_vent") and not has_flat_roof:
            continue
        if item == "roof_rack":
            rail = max(0.5, min(1.2, width * 0.045))
            low, high = rz0 + (rz1 - rz0) * 0.20, rz0 + (rz1 - rz0) * 0.80
            for side in (rx0 + rail, rx1 - rail * 2):
                boxes.append(_box(side, roof_y + 0.5, low,
                                  side + rail, roof_y + 1.1, high, "kit_metal"))
            for fraction in (0.25, 0.5, 0.75):
                z = low + (high - low) * fraction
                boxes.append(_box(rx0 + rail, roof_y + 0.9, z - rail / 2,
                                  rx1 - rail, roof_y + 1.3, z + rail / 2, "kit_dark"))
        elif item == "light_bar":
            bar_y = roof_y + 0.6
            half = min(width * 0.22, depth * 0.35)
            boxes.append(_box(centre_x - half, bar_y, centre_z - 0.7,
                              centre_x + half, bar_y + 1.1, centre_z + 0.7,
                              "kit_dark"))
            for offset in (-0.6, 0.0, 0.6):
                x = centre_x + half * offset
                boxes.append(_box(x - half * 0.18, bar_y + 0.3, centre_z - 0.55,
                                  x + half * 0.18, bar_y + 1.0, centre_z + 0.55,
                                  "kit_lamp"))
        elif item == "roll_hoop":
            top = clamp_top(max_y + max(1.6, height * 0.20))
            thickness = max(0.5, min(1.0, width * 0.045))
            for side in (min_x + width * 0.20, max_x - width * 0.20 - thickness):
                boxes.append(_box(side, max_y - height * 0.20, centre_z - thickness,
                                  side + thickness, top, centre_z + thickness,
                                  "kit_metal"))
            boxes.append(_box(min_x + width * 0.20, top - 0.7, centre_z - thickness * 1.1,
                              max_x - width * 0.20, top - 0.1, centre_z + thickness * 1.1,
                              "kit_dark"))
        elif item == "side_skirt":
            rail = max(0.45, min(0.9, width * 0.035))
            y0 = min_y + height * 0.16
            y1 = min_y + height * 0.28
            for side in (min_x, max_x - rail):
                boxes.append(_box(side, y0, min_z + depth * 0.10,
                                  side + rail, y1, max_z - depth * 0.10, "kit_dark"))
        elif item == "exhaust_stack":
            pipe = max(0.45, min(0.9, width * 0.035))
            top = clamp_top(max_y + max(2.0, height * 0.22))
            boxes.append(_box(max_x - width * 0.18, min_y + height * 0.45,
                              centre_z - pipe,
                              max_x - width * 0.18 + pipe, top,
                              centre_z + pipe, "kit_dark"))
        elif item == "antenna":
            rod = max(0.3, min(0.6, width * 0.022))
            anchor_y = roof_y if has_flat_roof else max_y
            anchor_x = (rx1 - width * 0.10) if has_flat_roof else centre_x
            top = clamp_top(anchor_y + max(2.2, height * 0.26))
            boxes.append(_box(anchor_x, anchor_y + 0.4, centre_z - rod,
                              anchor_x + rod, top, centre_z + rod, "kit_metal"))
        elif item == "footboards":
            rail = max(0.4, min(0.8, width * 0.04))
            y0 = min_y + height * 0.22
            y1 = min_y + height * 0.32
            for side in (min_x, max_x - rail):
                boxes.append(_box(side, y0, centre_z - depth * 0.14,
                                  side + rail, y1, centre_z + depth * 0.14, "kit_dark"))
        elif item == "roof_box":
            boxes.append(_box(centre_x - width * 0.24, roof_y + 0.5,
                              centre_z - depth * 0.16,
                              centre_x + width * 0.24, roof_y + max(1.2, height * 0.10),
                              centre_z + depth * 0.16, "kit_soft"))
        elif item == "roof_vent":
            boxes.append(_box(centre_x - width * 0.14, roof_y + 0.4,
                              centre_z - depth * 0.10,
                              centre_x + width * 0.14, roof_y + 0.9,
                              centre_z + depth * 0.10, "kit_dark"))
    return boxes


def restyle_model(model: dict, profile: tuple[float, float, float, float, tuple[str, ...]]) -> dict:
    width_ratio, height_ratio, length_ratio, cabin_ratio, kit = profile
    elements = model.get("elements") or []
    if not elements:
        return model
    bbox = _bbox(elements)
    min_x, max_x, min_y, max_y, min_z, max_z = bbox
    centre_x = (min_x + max_x) / 2.0
    centre_z = (min_z + max_z) / 2.0
    ground = min_y
    belt = min_y + (max_y - min_y) * 0.55

    for element in elements:
        for key in ("from", "to"):
            x, y, z = element[key]
            x = centre_x + (x - centre_x) * width_ratio
            z = centre_z + (z - centre_z) * length_ratio
            if y > belt and cabin_ratio != 1.0:
                y = belt + (y - belt) * cabin_ratio
            y = ground + (y - ground) * height_ratio
            element[key] = [round(_clamp(x), 4), round(_clamp(y), 4), round(_clamp(z), 4)]
        rotation = element.get("rotation")
        if rotation and "origin" in rotation:
            x, y, z = rotation["origin"]
            rotation["origin"] = [round(_clamp(centre_x + (x - centre_x) * width_ratio), 4),
                                  round(_clamp(y), 4),
                                  round(_clamp(centre_z + (z - centre_z) * length_ratio), 4)]

    # Kits are placed on the *already restyled* body, so they sit on the new roof.
    if kit:
        model.setdefault("textures", {}).update(KIT_TEXTURES)
        new_bbox = _bbox(elements)
        elements.extend(_kit_boxes(kit, elements, new_bbox))
    return model
