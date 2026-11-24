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
    # --- wheels (r42) -------------------------------------------------------
    "tire": ("#1A1C1F", "#0E0F11", False),
    "tread": ("#2C2F34", "#1C1E22", False),
    "rim_steel": ("#B9C0C7", "#8C939A", False),
    "rim_dark": ("#4B5158", "#34383D", False),
    "rim_alloy": ("#DEE5EC", "#B0B8C0", False),
    "rim_accent": ("#E0682A", "#AE4A18", False),
    "hub": ("#71787F", "#4E5359", False),
    "bolt": ("#33373C", "#22252A", False),
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

    def arch(self, centre_x: float, centre_y: float, centre_z: float, radius: float,
             width: float, material: str, span: float = 1.0, steps: int = 9) -> None:
        """Wheel-arch lip: overlapping blocks along the top half of a circle.

        ``span`` < 1 shortens the arc (a full 180 deg lip is a half ring); the
        blocks overlap on purpose so the result reads as one continuous lip
        instead of a dotted line of cubes.
        """
        import math

        for index in range(steps):
            angle = math.pi * (index + 0.5) / steps * span
            block = max(1.4, radius * 1.5 / steps)
            z = centre_z + math.cos(angle) * radius
            y = centre_y + math.sin(angle) * radius
            self.part((centre_x - width / 2, y - block * 0.55, z - block),
                      (centre_x + width / 2, y + block * 0.55, z + block), material)

    def model(self) -> dict:
        return {"textures": dict(self.textures), "elements": list(self.elements)}


def _wheel_arches(body: Body, wheels: list[tuple[float, float]], material: str = "body_dark") -> None:
    for x, z in wheels:
        body.arch(x, z, radius=6.4, width=1.6, material=material)


def pickup_body(*, police: bool = False) -> dict:
    """From-scratch 4x4 pickup (Trailbreaker / Patrol).

    Front = +Z. Axles at z 22.5 / -6.5, wheels at x -2 / 18 (r = 5.6), so the
    side panels run in three sections and the wheel wells stay open:
    tub -16..-12.6, doors -0.9..16.9, nose 28.1..32.
    """
    body = Body()
    sides = (-5.0, 18.6)

    # --- underbody: gives the truck weight and hides the see-through gap ----
    for z0, z1 in ((-15.2, -12.6), (-0.9, 16.9), (28.1, 30.8)):
        body.part((-4.6, 2.0, z0), (20.6, 5.0, z1), "body_dark")
    for x0 in (2.4, 10.4):
        body.part((x0, 1.2, -13.4), (x0 + 3.0, 3.2, 29.6), "body_dark")   # frame rails
    # axles + springs (visible in the wheel wells, like the source models)
    for z in (-6.5, 22.5):
        body.part((1.1, 4.4, z - 0.8), (14.9, 5.6, z + 0.8), "metal")     # axle tube
        for x0 in (1.2, 12.4):
            body.part((x0, 4.2, z - 2.6), (x0 + 2.2, 5.2, z + 2.6), "body_dark")

    # --- body sides --------------------------------------------------------
    for x0 in sides:
        for z0, z1 in ((-16.0, -12.6), (-0.9, 16.9), (28.1, 32.0)):
            body.part((x0, 5.0, z0), (x0 + 2.5, 12.6, z1), "body")
        # wheel-arch flare: a lip on the outside of the body plane, so it sits
        # clear of the wheel (which reaches x -4.8 on the left, 20.8 on the right)
        outer = x0 - 0.35 if x0 < 8 else x0 + 2.85
        for z in (-6.5, 22.5):
            body.arch(outer, 5.6, z, radius=6.5, width=0.8, material="body", span=0.86)
            body.arch(outer, 5.6, z, radius=6.2, width=1.0, material="body_dark", span=0.8)
            body.part((x0 - 0.15, 2.2, z - 8.6), (x0 + 2.6, 4.6, z - 6.4), "body_dark")
        # side step between the wheels
        body.part((x0 - 0.5, 3.4, -0.4), (x0 + 2.5, 4.4, 16.4), "metal")
        # door handle + rub strip, deliberately on different planes (no z-fight)
        body.part((x0 - 0.42, 9.8, 1.6), (x0 - 0.06, 10.8, 3.8), "metal")
        body.part((x0 - 0.3, 7.0, -0.6), (x0 - 0.06, 8.0, 16.6), "body_dark")

    # --- hood --------------------------------------------------------------
    body.part((-4.6, 12.6, 14.0), (20.6, 14.0, 31.0), "body")
    body.part((-2.4, 14.0, 16.4), (18.4, 15.0, 28.6), "body")        # power dome
    body.part((-4.6, 13.6, 29.6), (20.6, 14.4, 31.2), "body")        # leading edge
    body.part((-0.6, 14.1, 26.6), (16.6, 14.7, 29.2), "body_dark")   # vent

    # --- nose --------------------------------------------------------------
    body.part((-4.8, 6.4, 30.9), (20.8, 12.6, 31.4), "body_dark")    # fascia
    for x0 in (-4.9, -3.1):
        body.part((x0, 8.6, 31.0), (x0 + 1.8, 11.8, 31.6), "headlight")
        body.part((x0, 5.8, 31.0), (x0 + 1.8, 7.6, 31.5), "indicator")
    for x0 in (19.1, 20.9):
        body.part((x0 - 1.8, 8.6, 31.0), (x0, 11.8, 31.6), "headlight")
    for x0 in (18.7, 20.5):
        body.part((x0 - 1.8, 5.8, 31.0), (x0, 7.6, 31.5), "indicator")
    body.part((-2.2, 7.6, 31.3), (18.2, 11.6, 31.7), "grille")
    for x in (-1.6, 2.8, 7.2, 11.6, 16.0):
        body.part((x, 8.0, 31.7), (x + 1.2, 11.2, 31.9), "metal")
    body.part((-5.6, 4.4, 30.9), (21.6, 8.6, 32.0), "metal")         # bumper
    body.part((-5.6, 8.6, 31.4), (21.6, 9.4, 32.0), "body_dark")
    body.part((-3.4, 1.8, 30.4), (19.4, 4.4, 31.8), "body_dark")     # skid plate
    for x in (-3.4, 8.2):
        body.part((x, 2.6, 31.9), (x + 1.4, 4.0, 32.0), "accent")    # tow hooks
    body.part((0.4, 6.6, 32.0), (15.6, 7.8, 32.02), "plate")         # front plate

    # --- cab ---------------------------------------------------------------
    body.part((-5.0, 5.0, -0.9), (21.0, 13.4, 0.5), "body")          # rear wall
    body.part((-5.0, 13.4, -0.9), (21.0, 14.4, 0.5), "body")
    body.part((-5.0, 19.8, -0.9), (21.0, 21.4, 0.5), "body")
    body.symmetric((-5.0, 13.4, -0.9), (-3.0, 21.4, 0.5), "body")
    body.part((-4.4, 14.4, -0.4), (20.4, 19.8, 0.0), "glass")
    body.part((-5.0, 21.0, -1.1), (21.0, 22.6, 14.6), "body")        # roof
    body.part((-4.4, 22.6, -0.5), (20.4, 23.2, 13.8), "body_dark")   # roof rail
    for x0 in (-5.0, 18.4):
        body.part((x0, 13.4, -0.9), (x0 + 2.6, 14.6, 14.6), "body")  # belt rail
        body.part((x0, 20.0, -0.9), (x0 + 2.6, 21.0, 14.6), "body")  # header
        body.part((x0, 14.6, 12.8), (x0 + 2.6, 20.0, 14.6), "body")  # A-pillar
        body.part((x0, 14.6, 5.8), (x0 + 2.6, 20.0, 7.2), "body")    # B-pillar
        body.part((x0, 14.6, -0.9), (x0 + 2.6, 20.0, 0.8), "body")   # C-pillar
        body.part((x0 - 0.1, 14.6, 0.8), (x0 + 0.4, 20.0, 12.8), "glass")
        body.part((x0 - 0.7, 15.4, 12.6), (x0 + 0.2, 17.8, 13.8), "body_dark")  # mirror
        body.part((x0 - 1.2, 15.8, 12.8), (x0 - 0.6, 17.4, 13.6), "metal")
    body.part((-4.2, 13.8, 13.8), (20.2, 17.6, 14.7), "glass")       # windshield
    body.part((-4.2, 17.6, 13.4), (20.2, 20.6, 14.3), "glass")
    body.part((-5.0, 13.4, 14.4), (21.0, 14.2, 15.2), "body")        # cowl

    # --- tub behind the axle ------------------------------------------------
    body.part((-4.6, 6.2, -15.8), (20.6, 7.4, -12.6), "body_dark")   # floor
    for x0 in (sides[0], sides[1] - 0.2):
        body.part((x0, 7.4, -15.6), (x0 + 2.4, 12.8, -12.6), "body")
        body.part((x0 - 0.1, 12.8, -15.6), (x0 + 2.5, 13.4, -12.6), "body_dark")
    body.part((-5.0, 6.2, -16.0), (21.0, 13.4, -15.2), "body")       # tailgate
    body.part((-5.0, 13.4, -16.1), (21.0, 14.0, -15.0), "body_dark")
    body.part((-3.4, 8.4, -16.02), (19.4, 10.4, -15.9), "plate")
    body.part((-4.9, 10.0, -16.02), (-2.2, 13.0, -15.9), "taillight")
    body.part((18.2, 10.0, -16.02), (20.9, 13.0, -15.9), "taillight")
    body.part((-5.6, 4.4, -16.0), (21.6, 8.6, -14.8), "metal")       # rear bumper
    body.part((7.6, 4.4, -15.4), (8.8, 6.4, -14.6), "metal")         # tow hitch
    body.part((16.4, 2.6, -15.2), (17.8, 4.2, -13.6), "body_dark")   # exhaust

    # --- interior ----------------------------------------------------------
    body.part((-3.0, 5.4, 0.2), (19.0, 8.0, 8.6), "seat")            # front bench
    body.part((-3.0, 8.0, 0.8), (19.0, 12.8, 2.0), "seat")           # backrest
    body.part((1.0, 5.4, -11.0), (15.0, 8.0, -5.0), "seat")          # rear bench
    body.part((1.0, 8.0, -5.6), (15.0, 11.6, -4.6), "seat")
    body.part((-4.6, 11.4, 11.4), (20.6, 13.4, 12.8), "body_dark")   # dashboard
    body.part((1.8, 10.0, 10.2), (4.2, 11.6, 12.4), "body_dark")     # steering column

    # --- kit ---------------------------------------------------------------
    if police:
        body.part((-3.6, 23.2, 4.2), (7.6, 24.4, 9.8), "body_dark")
        body.part((-3.2, 24.4, 4.6), (1.8, 26.0, 9.4), "siren_red")
        body.part((2.2, 24.4, 4.6), (7.2, 26.0, 9.4), "siren_blue")
        body.part((-5.6, 5.2, 32.0), (21.6, 6.4, 32.8), "metal")     # push bar
        for x0 in (-4.2, 19.4):
            body.part((x0, 5.2, 32.2), (x0 + 1.4, 13.4, 32.8), "metal")
        body.part((2.0, 14.1, 26.8), (14.0, 14.8, 29.4), "siren_blue")
        body.part((-4.9, 6.0, -0.8), (-3.2, 12.2, 16.8), "body_dark")   # door panel
        body.part((19.4, 6.0, -0.8), (21.1, 12.2, 16.8), "body_dark")
    else:
        for x0 in (-4.6, 19.4):
            body.part((x0, 23.2, -0.9), (x0 + 1.2, 24.2, 13.4), "metal")
        for z in (1.0, 5.0, 9.0):
            body.part((-4.6, 23.6, z), (20.6, 24.4, z + 1.2), "body_dark")
        body.part((19.2, 12.0, 12.2), (20.4, 20.2, 13.4), "body_dark")  # snorkel
        body.part((18.8, 20.2, 11.8), (20.8, 21.8, 13.8), "metal")

    return body.model()


CANDIDATE_BODIES = {
    "off_roader_body": lambda: pickup_body(),
    "police_car_body": lambda: pickup_body(police=True),
}

# Enabled bodies. Left empty on purpose for now: the first from-scratch pickup
# is valid (tests pass, nothing clips a wheel) but it is not yet better than
# the r41 silhouette, so it stays out of the pack until the design is agreed.
# Add e.g. "off_roader_body": lambda: pickup_body() to ship it.
SCRATCH_BODIES: dict[str, object] = {}


# ---------------------------------------------------------------------------
# Wheels (r42)
# ---------------------------------------------------------------------------
# Every wheel keeps the envelope the rig expects: centred on (8, 8, 8), 16-unit
# diameter (y/z 0..16 -> radius 8) and 4 units wide (x 6..10), so any
# LandVehicleSpec can mount it without a scale correction.
#
# The shape is a 16x16 pixel mask (like the vanilla wheel sprites), emitted as
# row runs, so the wheel reads as a circle instead of a stack of cubes. No
# element rotation is needed, which also keeps the offline preview exact.

import math as _math


def _mask(predicate) -> set:
    cells = set()
    for uy in range(16):
        for uz in range(16):
            if predicate(uy + 0.5 - 8.0, uz + 0.5 - 8.0):
                cells.add((uy, uz))
    return cells


def _emit_mask(body: Body, cells: set, x0: float, x1: float, material: str) -> None:
    for uy in range(16):
        run = None
        for uz in range(16):
            if (uy, uz) in cells:
                if run is None:
                    run = uz
            elif run is not None:
                body.part((x0, uy, run), (x1, uy + 1, uz), material)
                run = None
        if run is not None:
            body.part((x0, uy, run), (x1, uy + 1, 16), material)


def _radius(uy: float, uz: float) -> float:
    return _math.hypot(uy, uz)


def _angle(uy: float, uz: float) -> float:
    return (_math.degrees(_math.atan2(uy, uz)) + 360.0) % 360.0


def _in_sector(uy: float, uz: float, count: int, width: float, rotation: float) -> bool:
    sector = 360.0 / count
    offset = (_angle(uy, uz) - rotation) % sector
    return abs(offset - sector / 2.0) <= width / 2.0


def wheel_body(style: str) -> dict:
    """Three wheel designs, drawn from scratch.

    * ``alloy`` - light 5-spoke alloy on a low-profile street tyre
    * ``trail`` - knobby mud tyre, steel rim, beadlock bolts (off-road)
    * ``heavy`` - deep-dish industrial wheel, 4 wide slots, big hub (trucks)
    """
    body = Body()
    tyre = _mask(lambda uy, uz: 5.8 <= _radius(uy, uz) <= 8.0)
    face = _mask(lambda uy, uz: _radius(uy, uz) <= 5.8)
    ring = _mask(lambda uy, uz: 4.8 <= _radius(uy, uz) <= 5.8)
    hub_ring = _mask(lambda uy, uz: _radius(uy, uz) <= 2.2)
    nut = _mask(lambda uy, uz: _radius(uy, uz) <= 1.0)

    if style == "alloy":
        holes = _mask(lambda uy, uz: 2.9 <= _radius(uy, uz) <= 4.7
                      and _in_sector(uy, uz, 5, 30.0, 18.0))
        tread = _mask(lambda uy, uz: 7.2 <= _radius(uy, uz) <= 8.0)
        rim, accent = "rim_alloy", "rim_accent"
    elif style == "trail":
        holes = _mask(lambda uy, uz: 3.4 <= _radius(uy, uz) <= 4.4
                      and _in_sector(uy, uz, 6, 26.0, 0.0))
        tread = _mask(lambda uy, uz: 6.6 <= _radius(uy, uz) <= 8.0)
        rim, accent = "rim_dark", "rim_steel"
    else:  # heavy
        holes = _mask(lambda uy, uz: 2.8 <= _radius(uy, uz) <= 4.6
                      and _in_sector(uy, uz, 4, 44.0, 45.0))
        tread = _mask(lambda uy, uz: 7.0 <= _radius(uy, uz) <= 8.0)
        rim, accent = "rim_dark", "rim_steel"

    # tyre carcass (full width) + tread blocks proud on both sidewalls
    _emit_mask(body, tyre, 6.0, 10.0, "tire")
    _emit_mask(body, tread, 5.8, 6.05, "tread")
    _emit_mask(body, tread, 9.95, 10.2, "tread")
    # dark backing so the cut-outs do not look straight through the wheel
    _emit_mask(body, face, 7.5, 8.5, "bolt")
    # rim face: spokes = face minus the cut-outs
    # rim face; the lip and the cut-outs sit on *adjacent* planes (never
    # overlapping) so no two faces end up coplanar and flicker in game
    _emit_mask(body, face - holes, 6.3, 9.7, rim)
    _emit_mask(body, ring, 6.0, 6.3, accent)
    _emit_mask(body, ring, 9.7, 10.0, accent)
    _emit_mask(body, holes, 6.12, 6.3, "bolt")
    _emit_mask(body, holes, 9.7, 9.88, "bolt")
    # hub + centre nut
    _emit_mask(body, hub_ring, 5.95, 10.05, "hub")
    _emit_mask(body, nut, 5.85, 10.15, accent)
    return body.model()


SCRATCH_WHEELS = {
    "wheel_alloy": lambda: wheel_body("alloy"),
    "wheel_trail": lambda: wheel_body("trail"),
    "wheel_heavy": lambda: wheel_body("heavy"),
}
