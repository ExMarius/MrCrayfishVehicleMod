#!/usr/bin/env python3
"""Vehicle bodies modelled from scratch (r42).

The vanilla Java client cannot load OBJ (or any mesh format): a resource-pack
model is a list of cuboids. So "modelled from scratch" here means authored
cuboid geometry, built by code instead of copied from the mod, with our own
16x16 sprite set (``MATERIALS``).

Every body is authored in the same model space the live rig uses, which is
why the wheels/seat anchors still line up:

    model_x = 8 + world_x / (model_unit * body_scale)
    model_z = 8 + world_z / (model_unit * body_scale)

For the off-roader (body_scale 1.4) that puts the four wheels at
x = -2 / 18, z = -6.5 / 22.5, centre y = 5.6, radius 5.6; the driver seat at
x = 3, z = 5, bench top y = 8; the ground plane at y = 0 and the roof at
y = 22. Front is +Z (the original headlights sit at z = 31).
"""

from __future__ import annotations

import struct
import zlib

# Sprite set: name -> (base colour, edge colour, tinted?).
MATERIALS: dict[str, tuple[str, str, bool]] = {
    "body": ("#E9EDF0", "#C6CDD3", True),      # painted: tinted by the dye
    "body_dark": ("#2F3338", "#1B1E22", False),
    "metal": ("#A8AFB6", "#7D858C", False),
    "rubber": ("#17181B", "#0C0D0F", False),
    "glass": ("#9CC3D4", "#7FA9BC", False),
    "seat": ("#2A2C31", "#1A1B1F", False),
    "headlight": ("#FFF3C4", "#E8D79A", False),
    "taillight": ("#C8342F", "#8E211D", False),
    "indicator": ("#F2A03C", "#C07A21", False),
    "grille": ("#3C4045", "#24272B", False),
    "plate": ("#F5F5F0", "#C9C9C2", False),
    "accent": ("#C8502F", "#96371F", False),
    "siren_red": ("#B20E16", "#7A0A0F", False),
    "siren_blue": ("#0E3EB0", "#0A2A7A", False),
}

FACE_ORDER = ("north", "east", "south", "west", "up", "down")


def _rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def material_png(name: str, size: int = 16) -> bytes:
    """A flat sprite with a slightly darker 1px border and a faint grain.

    Panel borders give the cuboids a readable edge in-game; the grain keeps
    large surfaces from looking like solid plastic.
    """
    base, edge, _tinted = MATERIALS[name]
    br, bg, bb = _rgb(base)
    er, eg, eb = _rgb(edge)
    rgba = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            border = x == 0 or y == 0 or x == size - 1 or y == size - 1
            noise = ((x * 31 + y * 17 + len(name) * 13) % 7) - 3
            r, g, b = (er, eg, eb) if border else (br, bg, bb)
            index = (y * size + x) * 4
            rgba[index] = max(0, min(255, r + noise))
            rgba[index + 1] = max(0, min(255, g + noise))
            rgba[index + 2] = max(0, min(255, b + noise))
            rgba[index + 3] = 255
    return encode_png(size, size, bytes(rgba))


def encode_png(width: int, height: int, rgba: bytes) -> bytes:
    def chunk(ctype: bytes, payload: bytes) -> bytes:
        return (struct.pack(">I", len(payload)) + ctype + payload
                + struct.pack(">I", zlib.crc32(ctype + payload) & 0xFFFFFFFF))

    scan = b"".join(b"\x00" + rgba[y * width * 4:(y + 1) * width * 4] for y in range(height))
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(scan, 9))
            + chunk(b"IEND", b""))


class Body:
    """Tiny modelling helper: every part is an axis-aligned cuboid."""

    def __init__(self) -> None:
        self.elements: list[dict] = []
        self.textures: dict[str, str] = {}
        for name, (_base, _edge, tinted) in MATERIALS.items():
            self.textures[name] = f"vehicle:item/mat_{name}"

    def part(self, low, high, material: str, faces: str = "all") -> None:
        # vanilla elements must stay inside -16..32 on every axis
        tinted = MATERIALS[material][2]
        x0, y0, z0 = (max(-16.0, min(32.0, value)) for value in low)
        x1, y1, z1 = (max(-16.0, min(32.0, value)) for value in high)
        x0, x1 = sorted((x0, x1))
        y0, y1 = sorted((y0, y1))
        z0, z1 = sorted((z0, z1))
        if x1 - x0 < 0.01 or y1 - y0 < 0.01 or z1 - z0 < 0.01:
            return
        faces_out: dict[str, dict] = {}
        for face in FACE_ORDER:
            if faces != "all" and face not in faces:
                continue
            definition: dict = {"uv": [0, 0, 16, 16], "texture": f"#{material}"}
            if tinted:
                definition["tintindex"] = 0
            faces_out[face] = definition
        self.elements.append({
            "from": [round(x0, 3), round(y0, 3), round(z0, 3)],
            "to": [round(x1, 3), round(y1, 3), round(z1, 3)],
            "faces": faces_out,
        })

    def symmetric(self, low, high, material: str, faces: str = "all") -> None:
        """Adds the box and its mirror image across the body's centre plane."""
        self.part(low, high, material, faces)
        centre = 8.0
        mirrored_low = (2 * centre - high[0], low[1], low[2])
        mirrored_high = (2 * centre - low[0], high[1], high[2])
        self.part(mirrored_low, mirrored_high, material, faces)

    def arch(self, centre_x: float, centre_z: float, radius: float, width: float,
             material: str, steps: int = 4) -> None:
        """Stepped wheel arch (a cuboid approximation of a semicircle)."""
        for index in range(steps):
            angle = (index + 0.5) / steps * 3.14159
            import math
            x_offset = math.cos(angle) * radius
            y_offset = math.sin(angle) * radius
            self.part((centre_x - width / 2, 4.6 + y_offset - radius * 0.28,
                       centre_z + x_offset - radius / steps),
                      (centre_x + width / 2, 4.6 + y_offset + radius * 0.16,
                       centre_z + x_offset + radius / steps),
                      material)

    def model(self) -> dict:
        return {"textures": dict(self.textures), "elements": list(self.elements)}


def _wheel_arches(body: Body, wheels: list[tuple[float, float]], material: str = "body_dark") -> None:
    for x, z in wheels:
        body.arch(x, z, radius=6.4, width=1.6, material=material)


def pickup_body(*, police: bool = False) -> dict:
    """From-scratch 4x4 pickup (Trailbreaker / Patrol).

    Front = +Z. Axles at z 22.5 / -6.5, wheels at x -2 / 18 (r = 5.6), so the
    side panels are split into three runs to leave the wheel wells open:
    bed -16..-13, doors -0.5..16.5, nose 28.5..32.
    """
    body = Body()
    left, right = -5.0, 18.5
    for x0 in (left, right):
        x1 = x0 + 2.5
        for z0, z1 in ((-16.0, -13.0), (-0.5, 16.5), (28.5, 32.0)):
            body.part((x0, 4.6, z0), (x1, 12.4, z1), "body")
        # rocker panel between the wheels
        body.part((x0 - 0.2, 4.0, -0.5), (x1, 6.2, 16.5), "body_dark")
        # fender bulges over both axles (body colour, thin dark lip)
        for z in (-6.5, 22.5):
            body.part((x0 - 0.8, 11.8, z - 7.6), (x0 + 0.3, 14.0, z + 7.6), "body")
            body.part((x0 - 0.8, 11.3, z - 7.6), (x0 + 0.3, 11.8, z + 7.6), "body_dark")
            body.part((x0 - 0.9, 11.8, z - 7.9), (x0 + 0.4, 13.8, z - 6.9), "body_dark")
            body.part((x0 - 0.9, 11.8, z + 6.9), (x0 + 0.4, 13.8, z + 7.9), "body_dark")
        # door handle + rub strip
        body.part((x0 - 0.3, 9.6, 1.4), (x1, 10.6, 3.4), "metal")
        body.part((x0 - 0.2, 6.8, -0.5), (x1, 7.8, 16.5), "body_dark")

    # --- chassis / floor ---------------------------------------------------
    for x0, x1 in ((2.6, 5.4), (10.6, 13.4)):
        body.part((x0, 2.2, -14.0), (x1, 4.0, 30.0), "body_dark")
    for z0, z1 in ((-15.4, -12.6), (-0.5, 16.5), (28.4, 30.6)):
        body.part((-4.4, 3.8, z0), (20.4, 5.2, z1), "body_dark")

    # --- hood --------------------------------------------------------------
    body.part((-4.6, 12.4, 14.0), (20.6, 13.8, 31.0), "body")
    body.part((-2.4, 13.8, 16.4), (18.4, 14.8, 28.6), "body")       # power dome
    body.part((-4.6, 13.4, 29.6), (20.6, 14.2, 31.2), "body")       # leading edge
    body.part((-0.6, 13.9, 26.8), (16.6, 14.5, 29.4), "body_dark")  # vent

    # --- nose --------------------------------------------------------------
    body.part((-4.8, 6.4, 30.9), (20.8, 12.4, 31.4), "body_dark")   # fascia
    body.part((-2.0, 7.4, 31.3), (18.0, 11.4, 31.7), "grille")
    for x in (-1.4, 3.0, 7.4, 11.8, 16.2):
        body.part((x, 7.8, 31.7), (x + 1.2, 11.0, 31.9), "metal")   # grille bars
    body.part((-4.6, 8.6, 30.9), (-3.0, 11.6, 31.5), "headlight")
    body.part((19.0, 8.6, 30.9), (20.6, 11.6, 31.5), "headlight")
    body.part((-4.9, 5.6, 31.0), (-3.2, 7.4, 31.5), "indicator")
    body.part((19.2, 5.6, 31.0), (20.9, 7.4, 31.5), "indicator")
    body.part((-5.6, 4.4, 31.0), (21.6, 8.6, 32.6), "metal")        # bumper
    body.part((-5.6, 8.6, 31.9), (21.6, 9.4, 32.6), "body_dark")
    body.part((-3.4, 1.8, 30.4), (19.4, 4.4, 31.8), "body_dark")    # skid plate
    for x in (-3.4, 8.2):
        body.part((x, 2.6, 32.2), (x + 1.4, 4.0, 33.0), "accent")   # tow hooks

    # --- cab ---------------------------------------------------------------
    # rear wall: frame + inset window (a solid wall would hide the glass)
    body.part((-5.0, 4.6, -0.9), (21.0, 13.2, 0.5), "body")
    body.part((-5.0, 13.2, -0.9), (21.0, 14.2, 0.5), "body")
    body.part((-5.0, 19.6, -0.9), (21.0, 21.4, 0.5), "body")
    body.symmetric((-5.0, 13.2, -0.9), (-3.2, 21.4, 0.5), "body")
    body.part((-4.4, 14.2, -0.4), (20.4, 19.6, 0.0), "glass")
    # roof
    body.part((-5.0, 20.8, -0.9), (21.0, 22.2, 14.4), "body")
    body.part((-4.4, 22.2, -0.3), (20.4, 22.8, 13.6), "body_dark")
    # side frame: sill rail, header, pillars, then the glass pane inset 0.5
    for x0 in (-5.0, 18.4):
        body.part((x0, 13.2, -0.9), (x0 + 2.6, 14.4, 14.4), "body")     # belt rail
        body.part((x0, 19.8, -0.9), (x0 + 2.6, 20.8, 14.4), "body")     # header
        body.part((x0, 14.4, 12.6), (x0 + 2.6, 19.8, 14.4), "body")     # A-pillar
        body.part((x0, 14.4, 5.8), (x0 + 2.6, 19.8, 7.2), "body")       # B-pillar
        body.part((x0, 14.4, -0.9), (x0 + 2.6, 19.8, 0.8), "body")      # C-pillar
        body.part((x0 - 0.1, 14.4, 0.8), (x0 + 0.5, 19.8, 12.6), "glass")
        body.part((x0 - 0.9, 15.2, 12.4), (x0 + 0.2, 17.6, 13.6), "body_dark")  # mirror
        body.part((x0 - 1.4, 15.6, 12.6), (x0 - 0.8, 17.2, 13.4), "metal")
    # windshield (two steps read as a rake) + cowl
    body.part((-4.2, 13.6, 13.6), (20.2, 17.2, 14.5), "glass")
    body.part((-4.2, 17.2, 13.2), (20.2, 20.4, 14.1), "glass")
    body.part((-5.0, 13.2, 14.2), (21.0, 14.0, 15.0), "body")

    # --- bed / rear tub -----------------------------------------------------
    # The rear wheels (r 5.6, centre z -6.5) leave x 0.8..15.2 clear, so the
    # floor and the bench pass between them; the tub sits behind the axle and
    # the open middle keeps the wheels visible, technical-pickup style.
    body.part((-4.6, 6.0, -15.6), (20.6, 7.2, -12.6), "body_dark")   # tub floor
    body.part((0.8, 6.0, -12.6), (15.2, 7.2, -0.9), "body_dark")     # between wheels
    for x0 in (-5.0, 18.6):
        body.part((x0, 7.2, -15.4), (x0 + 2.4, 12.8, -12.6), "body")  # tub sides
        body.part((x0 - 0.1, 12.8, -15.4), (x0 + 2.5, 13.4, -12.6), "body_dark")
    body.part((-5.0, 6.0, -16.0), (21.0, 13.4, -15.2), "body")        # tailgate
    body.part((-3.4, 8.2, -16.02), (19.4, 10.2, -15.9), "plate")
    body.part((-4.9, 9.8, -16.02), (-2.2, 12.8, -15.9), "taillight")
    body.part((18.2, 9.8, -16.02), (20.9, 12.8, -15.9), "taillight")
    body.part((-5.6, 4.4, -16.0), (21.6, 8.6, -14.8), "metal")        # rear bumper
    body.part((16.4, 2.4, -15.2), (17.8, 4.0, -13.4), "body_dark")    # exhaust

    # --- interior ----------------------------------------------------------
    # bench tops sit at y = 8: that is where LandVehicleSpec.seat() puts the
    # rider (sourceOffset y = 4 - 1 + 5.6 = 8.6 units, minus the 0.6 cushion)
    body.part((-3.0, 5.6, 0.2), (19.0, 8.0, 8.6), "seat")            # front bench
    body.part((-3.0, 8.0, 0.6), (19.0, 13.0, 1.8), "seat")           # backrest
    body.part((-3.0, 8.0, 1.8), (19.0, 10.4, 2.4), "seat")           # headrests
    body.part((1.0, 5.6, -11.0), (15.0, 8.0, -5.0), "seat")          # rear bench
    body.part((1.0, 8.0, -5.6), (15.0, 11.6, -4.6), "seat")
    body.part((-4.6, 11.2, 11.4), (20.6, 13.0, 12.8), "body_dark")   # dashboard
    body.part((2.0, 9.6, 10.2), (4.2, 11.4, 12.4), "body_dark")      # steering column

    # --- kit ---------------------------------------------------------------
    if police:
        body.part((-3.6, 22.8, 4.2), (7.6, 24.2, 9.8), "body_dark")
        body.part((-3.2, 24.2, 4.6), (1.8, 25.8, 9.4), "siren_red")
        body.part((2.2, 24.2, 4.6), (7.2, 25.8, 9.4), "siren_blue")
        body.part((-5.0, 5.4, 32.6), (21.0, 6.6, 33.4), "metal")     # push bar
        for x0 in (-4.0, 19.0):
            body.part((x0, 5.4, 32.8), (x0 + 1.4, 13.0, 33.6), "metal")
        body.part((2.0, 13.9, 27.0), (14.0, 14.6, 29.6), "siren_blue")
    else:
        for x0 in (-4.6, 19.4):
            body.part((x0, 22.8, -1.0), (x0 + 1.2, 23.8, 13.0), "metal")
        for z in (1.0, 5.0, 9.0):
            body.part((-4.6, 23.2, z), (20.6, 24.0, z + 1.2), "body_dark")
        body.part((19.2, 11.4, 12.2), (20.4, 19.6, 13.4), "body_dark")   # snorkel
        body.part((18.8, 19.6, 11.8), (20.8, 21.2, 13.8), "metal")

    return body.model()


# Every body the generator can produce (guarded by tools/test_scratch_models.py).
CANDIDATE_BODIES = {
    "off_roader_body": lambda: pickup_body(),
    "police_car_body": lambda: pickup_body(police=True),
}

# Enabled bodies. Left empty on purpose for now: the first from-scratch pickup
# is valid (tests pass, nothing clips a wheel) but it is not yet better than
# the r41 silhouette, so it stays out of the pack until the design is agreed.
# Add e.g. "off_roader_body": lambda: pickup_body() to ship it.
SCRATCH_BODIES: dict[str, object] = {}
