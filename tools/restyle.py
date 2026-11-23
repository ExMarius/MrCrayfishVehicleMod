#!/usr/bin/env python3
"""Our own vehicle badge, plus the tiny deterministic PNG helpers around it.

The plugin may legally reuse the GPLv3 art of the original mod (see
``docs/LICENTA-SI-ASSETS-RO.md``), but the user asked for a fleet that no longer
carries the mod's identity, and explicitly for no MrCrayfish naming/branding.
Every ``logo`` sprite in the resource pack used to be the mod's "Cray
Industries" mark; this module draws our own chevron badge from scratch and
overwrites those sprites with it.

The vehicle bodies themselves stay neutral (white) because the spray can paints
them through a ``dye`` tint: a pre-coloured atlas would be multiplied by the
player's dye and come out muddy. The per-vehicle identity therefore lives in
``LandVehicleSpec.FACTORY_PAINTS`` (factory colour) + the new names.

Everything is deterministic: same inputs -> same PNG bytes -> same pack SHA-1.
"""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

EMBLEM_ACCENT = "#F2801D"


def _hex_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def decode_png(path: Path) -> tuple[int, int, bytearray]:
    """Decode an 8-bit RGBA, non-interlaced PNG (the format all our art uses)."""
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise RuntimeError(f"{path}: not a PNG")
    pos = 8
    width = height = None
    idat = bytearray()
    while pos < len(data):
        length, ctype = struct.unpack(">I4s", data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + length]
        if ctype == b"IHDR":
            width, height, depth, color, _c, _f, interlace = struct.unpack(">IIBBBBB", chunk)
            if depth != 8 or color != 6 or interlace != 0:
                raise RuntimeError(f"{path}: unsupported PNG variant "
                                   f"(depth={depth} color={color} interlace={interlace})")
        elif ctype == b"IDAT":
            idat.extend(chunk)
        elif ctype == b"IEND":
            break
        pos += 12 + length
    raw = zlib.decompress(bytes(idat))
    stride = width * 4
    out = bytearray(width * height * 4)
    prev = bytearray(stride)
    src = 0
    for y in range(height):
        filt = raw[src]
        src += 1
        row = bytearray(raw[src:src + stride])
        src += stride
        if filt == 1:
            for i in range(4, stride):
                row[i] = (row[i] + row[i - 4]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                row[i] = (row[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                left = row[i - 4] if i >= 4 else 0
                row[i] = (row[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = row[i - 4] if i >= 4 else 0
                b = prev[i]
                c = prev[i - 4] if i >= 4 else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                row[i] = (row[i] + pr) & 0xFF
        elif filt != 0:
            raise RuntimeError(f"{path}: unknown PNG filter {filt}")
        out[y * stride:(y + 1) * stride] = row
        prev = row
    return width, height, out


def encode_png(width: int, height: int, rgba: bytes) -> bytes:
    def chunk(ctype: bytes, payload: bytes) -> bytes:
        return (struct.pack(">I", len(payload)) + ctype + payload
                + struct.pack(">I", zlib.crc32(ctype + payload) & 0xFFFFFFFF))

    scan = b"".join(b"\x00" + rgba[y * width * 4:(y + 1) * width * 4] for y in range(height))
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(scan, 9))
            + chunk(b"IEND", b""))


def emblem_png(size: int = 32, accent: str = EMBLEM_ACCENT) -> bytes:
    """Our own badge: a bold chevron inside a rounded ring (drawn from scratch).

    Nothing here comes from the mod's art - the mark is generated procedurally,
    which is why it can replace the Cray Industries logo without attribution
    questions.
    """
    ar, ag, ab = _hex_rgb(accent)
    rgba = bytearray(size * size * 4)
    cx = cy = (size - 1) / 2.0
    outer = size * 0.46
    inner = size * 0.36

    def put(x: int, y: int, colour: tuple[int, int, int], a: int = 255) -> None:
        if 0 <= x < size and 0 <= y < size:
            i = (y * size + x) * 4
            rgba[i:i + 4] = bytes((colour[0], colour[1], colour[2], a))

    for y in range(size):
        for x in range(size):
            dx, dy = x - cx, y - cy
            dist = (dx * dx + dy * dy) ** 0.5
            # ring
            if inner <= dist <= outer:
                edge = min(dist - inner, outer - dist)
                put(x, y, (18, 18, 20), 255 if edge > 1.2 else 140)
                continue
            # chevron: two thick strokes meeting at the bottom centre
            if abs(dy) <= size * 0.42:
                arm = abs(dx) / (size * 0.34)
                height = (1.0 - arm) * size * 0.62 - (size * 0.30)
                if arm <= 1.0 and abs(dy) <= abs(height) and dy * (1 if dy >= 0 else -1) >= -size * 0.05:
                    put(x, y, (ar, ag, ab), 255)
    return encode_png(size, size, bytes(rgba))


def write_emblem(destination: Path, accent: str = EMBLEM_ACCENT, size: int = 32) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(emblem_png(size, accent))
