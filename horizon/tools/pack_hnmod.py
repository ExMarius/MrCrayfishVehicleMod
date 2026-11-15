#!/usr/bin/env python3
"""Impacheteaza bundle-ul nativ ca `.hnmod`, cum il face `native.py package`.

Un `.hnmod` e un zip mic, deterministic, cu:
  horizon-native.json        manifestul (schema, id, version, game, horizon, mixins, hooks)
  mixins/<id>/*.rs, *.toml   sursele bundle-ului (helper-ele + horizon-mixin.toml)
  mixins/<id>/LICENSE        licenta

Launcher-ul il compileaza apoi local, pentru platforma jucatorului
(`horizon-native-mods/<id>.hnmod` + butonul "Build").

Rulare:
    python3 horizon/tools/pack_hnmod.py [--out director]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUNDLE = ROOT / "horizon/engine-mod/mixins/vehicle"
COMPAT = ROOT / "horizon/engine-mod/compatibility.json"
LICENSE = ROOT / "horizon/engine-mod/LICENSE"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(ROOT / "out"))
    args = parser.parse_args()

    toml = (BUNDLE / "horizon-mixin.toml").read_text()
    mod_id = toml.split('id = "', 1)[1].split('"', 1)[0]
    version = toml.split('version = "', 1)[1].split('"', 1)[0]
    compat = json.loads(COMPAT.read_text())

    manifest = {
        "schema": 1,
        "id": mod_id,
        "version": version,
        "game": compat["game"],
        "horizon": compat["horizon_revision"],
        "mixins": compat["horizon_mixins_revision"],
        "hooks": compat["hooks"],
    }

    files: dict[str, bytes] = {
        "horizon-native.json": (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()
    }
    for path in sorted(BUNDLE.rglob("*")):
        if path.is_file() and path.suffix in (".rs", ".toml"):
            relative = path.relative_to(BUNDLE).as_posix()
            files[f"mixins/{mod_id}/{relative}"] = path.read_bytes()
    if LICENSE.is_file():
        files.setdefault(f"mixins/{mod_id}/LICENSE", LICENSE.read_bytes())

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / f"{mod_id}-{version}.hnmod"
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, content in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            info.create_system = 3
            archive.writestr(info, content)

    print(json.dumps({
        "file": str(out),
        "size": out.stat().st_size,
        "sha256": hashlib.sha256(out.read_bytes()).hexdigest(),
    }, indent=2))


if __name__ == "__main__":
    main()
