#!/usr/bin/env python3
"""Offline isometric previewer for the pack's vehicle models.

Used while redesigning the bodies: renders a model JSON to a PNG so the shape
(and where a bolted-on part lands) can be checked *before* a build goes to the
server. It is not a game-accurate renderer - faces are filled with the average
colour of their sprite and shaded per face - but it is faithful about geometry.

Usage:
    python3 tools/model_preview.py out.png assets/vehicle/models/item/off_roader_body.json
    python3 tools/model_preview.py out.png model.json --textures tex.json --size 900
"""

from __future__ import annotations

import math
import json
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import restyle  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/vehicle"

# Enough vanilla block colours for the models that are mapped onto block sprites.
BLOCK_COLOURS = {
    "white_concrete": (207, 213, 214),
    "light_gray_concrete": (125, 125, 115),
    "gray_concrete": (54, 57, 61),
    "black_concrete": (8, 10, 15),
    "red_concrete": (142, 33, 33),
    "yellow_concrete": (241, 175, 21),
    "green_concrete": (73, 91, 36),
    "blue_concrete": (44, 46, 143),
    "iron_block": (220, 220, 220),
    "glass": (170, 205, 220),
    "redstone_lamp": (125, 78, 40),
    "red_stained_glass": (150, 20, 20),
    "orange_stained_glass": (216, 110, 20),
    "white_stained_glass": (240, 245, 245),
    "black_wool": (22, 22, 26),
    "yellow_wool": (248, 198, 39),
    "oak_planks": (162, 130, 78),
    "gray_wool": (66, 66, 70),
    "smooth_stone": (158, 158, 158),
    "quartz_block": (235, 229, 222),
    "coal_block": (16, 16, 16),
    "copper_block": (192, 107, 79),
    "gold_block": (247, 213, 74),
}

FACE_BRIGHTNESS = {
    "up": 1.00,
    "down": 0.55,
    "north": 0.80,
    "south": 0.72,
    "east": 0.88,
    "west": 0.66,
}

FACE_CORNERS = {
    # (name, 4 corners as (x, y, z) unit offsets, outward normal)
    "up": ([(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)], (0, 1, 0)),
    "down": ([(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], (0, -1, 0)),
    "north": ([(0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)], (0, 0, -1)),
    "south": ([(1, 0, 1), (1, 1, 1), (0, 1, 1), (0, 0, 1)], (0, 0, 1)),
    "east": ([(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)], (1, 0, 0)),
    "west": ([(0, 0, 1), (0, 1, 1), (0, 1, 0), (0, 0, 0)], (-1, 0, 0)),
}


def average_png_colour(path: Path) -> tuple[int, int, int]:
    width, height, rgba = restyle.decode_png(path)
    total = [0, 0, 0]
    count = 0
    step = max(1, (width * height) // 256)
    for index in range(0, width * height, step):
        i = index * 4
        if rgba[i + 3] < 40:
            continue
        total[0] += rgba[i]
        total[1] += rgba[i + 1]
        total[2] += rgba[i + 2]
        count += 1
    if not count:
        return (128, 128, 128)
    return (total[0] // count, total[1] // count, total[2] // count)


def texture_colour(reference: str, pack: zipfile.ZipFile | None) -> tuple[int, int, int]:
    if isinstance(reference, str) and reference.startswith("vehicle:item/mat_"):
        import scratch_models

        name = reference.rsplit("mat_", 1)[1]
        material = scratch_models.MATERIALS.get(name)
        if material:
            value = material[0].lstrip("#")
            return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)
    if not isinstance(reference, str):
        return (128, 128, 128)
    if reference.startswith("#"):
        return (128, 128, 128)
    namespace, _, name = reference.partition(":")
    if namespace == "minecraft":
        base = name.split("/")[-1]
        return BLOCK_COLOURS.get(base, (120, 120, 120))
    if pack is not None:
        sprite = f"assets/{namespace}/textures/{name}.png"
        if sprite in pack.namelist():
            import tempfile

            with tempfile.TemporaryDirectory() as temporary:
                probe = Path(temporary) / "sprite.png"
                probe.write_bytes(pack.read(sprite))
                return average_png_colour(probe)
    direct = ASSETS / f"textures/{name}.png"
    if direct.exists():
        return average_png_colour(direct)
    return (128, 128, 128)


def rotate(point, rotation):
    if not rotation:
        return point
    axis = rotation.get("axis", "y")
    angle = math.radians(rotation.get("angle", 0.0))
    ox, oy, oz = rotation.get("origin", [8, 8, 8])
    x, y, z = point
    x -= ox
    y -= oy
    z -= oz
    cos, sin = math.cos(angle), math.sin(angle)
    if axis == "x":
        y, z = y * cos - z * sin, y * sin + z * cos
    elif axis == "y":
        x, z = x * cos + z * sin, -x * sin + z * cos
    else:
        x, y = x * cos - y * sin, x * sin + y * cos
    return (x + ox, y + oy, z + oz)


def render(model: dict, *, pack: zipfile.ZipFile | None = None, size: int = 720,
           yaw_degrees: float = 38.0, pitch_degrees: float = 24.0) -> tuple[int, int, bytes]:
    textures = model.get("textures") or {}
    cache: dict[str, tuple[int, int, int]] = {}

    def colour_for(face: dict) -> tuple[int, int, int]:
        key = face.get("texture")
        if key is None:
            return (120, 120, 120)
        if key not in cache:
            cache[key] = texture_colour(textures.get(key.lstrip("#"), key), pack)
        return cache[key]

    quads = []
    xs, ys, zs = [], [], []
    for element in model.get("elements", []):
        lo, hi = element["from"], element["to"]
        rotation = element.get("rotation")
        for face_name, (corners, normal) in FACE_CORNERS.items():
            face = (element.get("faces") or {}).get(face_name)
            if not face:
                continue
            points = []
            for ux, uy, uz in corners:
                point = (lo[0] if ux == 0 else hi[0],
                         lo[1] if uy == 0 else hi[1],
                         lo[2] if uz == 0 else hi[2])
                point = rotate(point, rotation)
                points.append(point)
                xs.append(point[0]); ys.append(point[1]); zs.append(point[2])
            quads.append((points, normal, face))

    if not quads:
        return 1, 1, b"\x00\x00\x00\xff"

    cx = (min(xs) + max(xs)) / 2.0
    cy = (min(ys) + max(ys)) / 2.0
    cz = (min(zs) + max(zs)) / 2.0
    extent = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs), 1.0)
    scale = (size * 0.82) / extent
    yaw = math.radians(yaw_degrees)
    pitch = math.radians(pitch_degrees)

    def project(point):
        x, y, z = point[0] - cx, point[1] - cy, point[2] - cz
        rx = x * math.cos(yaw) - z * math.sin(yaw)
        rz = x * math.sin(yaw) + z * math.cos(yaw)
        screen_x = size / 2.0 + rx * scale
        screen_y = size / 2.0 - (y * math.cos(pitch) - rz * math.sin(pitch)) * scale
        return (screen_x, screen_y, rz)

    pixels = bytearray(b"\x1a\x1c\x21\xff" * (size * size))
    depth_buffer = [float("inf")] * (size * size)
    for points, normal, face in quads:
        projected = [project(point) for point in points]
        # Backface culling using the screen-space winding.
        area = 0.0
        for i in range(len(projected)):
            x1, y1, _ = projected[i]
            x2, y2, _ = projected[(i + 1) % len(projected)]
            area += x1 * y2 - x2 * y1
        if area >= 0:  # wound away from the camera
            continue
        name = next((name for name, (_, n) in FACE_CORNERS.items() if n == normal), "up")
        base = colour_for(face)
        brightness = FACE_BRIGHTNESS.get(name, 0.8)
        if face.get("tintindex") is not None:
            brightness *= 0.92
        colour = tuple(min(255, int(channel * brightness)) for channel in base)
        # A z-buffer per pixel (instead of painter sorting) keeps overlapping
        # cuboids honest: no more "floating panel" artefacts while modelling.
        for triangle in ((0, 1, 2), (0, 2, 3)):
            fill_triangle(pixels, depth_buffer, size,
                          [projected[i] for i in triangle], colour)
    return size, size, bytes(pixels)


def fill_triangle(pixels: bytearray, depth_buffer: list, size: int, triangle, colour) -> None:
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = triangle
    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return
    left = max(0, int(math.floor(min(x0, x1, x2))))
    right = min(size - 1, int(math.ceil(max(x0, x1, x2))))
    top = max(0, int(math.floor(min(y0, y1, y2))))
    bottom = min(size - 1, int(math.ceil(max(y0, y1, y2))))
    red, green, blue = colour
    for y in range(top, bottom + 1):
        py = y + 0.5
        row = y * size
        for x in range(left, right + 1):
            px = x + 0.5
            w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area
            w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area
            w2 = 1.0 - w0 - w1
            if w0 < 0 or w1 < 0 or w2 < 0:
                continue
            depth = w0 * z0 + w1 * z1 + w2 * z2
            index = row + x
            if depth >= depth_buffer[index]:
                continue
            depth_buffer[index] = depth
            offset = index * 4
            pixels[offset] = red
            pixels[offset + 1] = green
            pixels[offset + 2] = blue
            pixels[offset + 3] = 255


def fill_polygon(pixels: bytearray, size: int, points, colour) -> None:
    ys = [point[1] for point in points]
    top = max(0, int(math.floor(min(ys))))
    bottom = min(size - 1, int(math.ceil(max(ys))))
    edges = []
    for i in range(len(points)):
        x1, y1 = points[i][0], points[i][1]
        x2, y2 = points[(i + 1) % len(points)][0], points[(i + 1) % len(points)][1]
        if y1 != y2:
            edges.append((x1, y1, x2, y2))
    if not edges:
        return
    for y in range(top, bottom + 1):
        scan = y + 0.5
        crossings = []
        for x1, y1, x2, y2 in edges:
            if (y1 <= scan < y2) or (y2 <= scan < y1):
                ratio = (scan - y1) / (y2 - y1)
                crossings.append(x1 + ratio * (x2 - x1))
        crossings.sort()
        for i in range(0, len(crossings) - 1, 2):
            start = max(0, int(round(crossings[i])))
            end = min(size - 1, int(round(crossings[i + 1])))
            row = y * size
            for x in range(start, end + 1):
                index = (row + x) * 4
                pixels[index:index + 4] = bytes((colour[0], colour[1], colour[2], 255))


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(__doc__)
        return 2
    destination = Path(argv[1])
    model_path = Path(argv[2])
    model = json.loads(model_path.read_text(encoding="utf-8"))
    pack = None
    if "--pack" in argv:
        pack = zipfile.ZipFile(argv[argv.index("--pack") + 1])
    size = int(argv[argv.index("--size") + 1]) if "--size" in argv else 720
    width, height, rgba = render(model, pack=pack, size=size)
    destination.write_bytes(restyle.encode_png(width, height, rgba))
    print(f"Preview: {destination} ({width}x{height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
