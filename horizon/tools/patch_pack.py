#!/usr/bin/env python3
"""Pregătește resource pack-ul modului pentru un client vanilla 26.3.

`tools/build_resource_pack.py` construiește pack-ul pentru 1.21.4 (pack_format 46). Aici:
  * acceptăm pack-ul pe orice versiune mai nouă (`supported_formats`), ca 26.3 să-l încarce;
  * adăugăm o textură transparentă pentru minecart, ca scheletul mașinii să nu se vadă
    (clientul vanilla nu are cum să ascundă o entitate din server).

Utilizare: python3 horizon/tools/patch_pack.py IN.zip OUT.zip
Scrie și sha1-ul în stdout, pe ultima linie: `sha1=<hex>`.
"""

import hashlib
import sys
import zipfile
import zlib

# Pack-ul e construit pentru 1.21.4; îl declarăm compatibil cu orice versiune mai nouă.
PACK_META = """{
  "pack": {
    "description": "MrCrayfish Vehicle Mod - masini (Horizon)",
    "pack_format": 46,
    "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}
  }
}
"""


def transparent_png() -> bytes:
    """Un PNG RGBA 1x1 complet transparent, scris manual (fără Pillow)."""
    def chunk(kind: bytes, data: bytes) -> bytes:
        body = kind + data
        return (
            len(data).to_bytes(4, "big")
            + body
            + (zlib.crc32(body) & 0xFFFFFFFF).to_bytes(4, "big")
        )

    header = b"\x89PNG\r\n\x1a\n"
    ihdr = chunk(b"IHDR", (1).to_bytes(4, "big") + (1).to_bytes(4, "big") + bytes([8, 6, 0, 0, 0]))
    raw = b"\x00" + bytes([0, 0, 0, 0])  # un rând, un pixel, alpha 0
    idat = chunk(b"IDAT", zlib.compress(raw, 9))
    return header + ihdr + idat + chunk(b"IEND", b"")


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    source, target = sys.argv[1], sys.argv[2]
    minecart = transparent_png()

    with zipfile.ZipFile(source) as src, zipfile.ZipFile(
        target, "w", compression=zipfile.ZIP_DEFLATED
    ) as out:
        for entry in src.infolist():
            name = entry.filename
            if name == "pack.mcmeta":
                out.writestr(name, PACK_META)
                continue
            if name == "assets/minecraft/textures/entity/minecart.png":
                continue
            out.writestr(entry, src.read(name))
        names = src.namelist()
        if "assets/minecraft/textures/entity/minecart.png" not in names:
            out.writestr("assets/minecraft/textures/entity/minecart.png", minecart)
        if "pack.png" not in names:
            out.writestr("pack.png", minecart)

    digest = hashlib.sha1(open(target, "rb").read()).hexdigest()
    print(f"pack: {target}")
    print(f"sha1={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
