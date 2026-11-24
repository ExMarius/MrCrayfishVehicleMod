#!/usr/bin/env python3
"""Import a third-party vehicle pack (Horizon and friends) 1:1 into our pack.

The point is to *move* an existing model set into the plugin without redrawing
anything: models come out with the same cuboids, the same UVs and the same
textures, only the texture references are rewritten so they live inside our
namespace.

Supported input
---------------
* Minecraft/Forge/NeoForge JSON block models (``assets/<ns>/models/**.json``),
  which is what every Java vehicle mod ships. Elements, rotations and faces are
  copied untouched.
* PNG textures (``assets/<ns>/textures/**.png``).

Not supported (reported, never silently dropped)
------------------------------------------------
* ``.obj`` / ``.gltf`` / ``.fbx`` meshes - the vanilla client cannot render
  them, they would have to be voxelised first;
* models that rely on a ``parent`` for their geometry;
* models whose textures are not plain PNG files.

Usage
-----
    python3 tools/import_external_pack.py horizon.zip --prefix hz_
    python3 tools/import_external_pack.py horizon.zip --dry-run

The importer writes into ``tools/external_models/`` (models/, textures/ and a
manifest.json) and ``tools/build_resource_pack.py`` copies that folder into the
resource pack on the next build.
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXTERNAL_ROOT = ROOT / "tools" / "external_models"

MESH_SUFFIXES = (".obj", ".gltf", ".glb", ".fbx")


def _flat_name(namespace: str, path: str, prefix: str) -> str:
    """Flattens a texture path into a file name inside our namespace."""
    cleaned = path.replace("/", "_").replace(".png", "").replace(".json", "")
    return f"{prefix}{cleaned}" if prefix else cleaned


def _unique(name: str, taken: set, namespace: str) -> str:
    if name not in taken:
        return name
    candidate = f"{name}_{namespace}"
    index = 2
    while candidate in taken:
        candidate = f"{name}_{namespace}{index}"
        index += 1
    return candidate


def _texture_candidates(namespace: str, reference: str) -> list[str]:
    """Where a texture reference could live inside the source archive."""
    namespace = reference.split(":", 1)[0] if ":" in reference else namespace
    path = reference.split(":", 1)[1] if ":" in reference else reference
    return [
        f"assets/{namespace}/textures/{path}.png",
        f"assets/{namespace}/{path}.png",
    ]


def scan(archive: zipfile.ZipFile) -> dict:
    models, textures, meshes, reports = [], [], [], []
    for name in archive.namelist():
        lower = name.lower()
        if lower.endswith(".json") and "/models/" in lower:
            models.append(name)
        elif lower.endswith(".png") and "/textures/" in lower:
            textures.append(name)
        elif lower.endswith(MESH_SUFFIXES):
            meshes.append(name)
    return {"models": sorted(models), "textures": sorted(textures), "meshes": sorted(meshes)}


def import_pack(source: Path, *, prefix: str = "", destination: Path = EXTERNAL_ROOT,
                dry_run: bool = False) -> dict:
    """Converts one pack archive. Returns a report; writes files unless dry_run."""
    report = {"source": str(source), "models": {}, "textures": {}, "skipped": [],
              "missing_textures": set(), "meshes": []}
    models_out = destination / "models"
    textures_out = destination / "textures"
    copied_textures: dict[str, str] = {}

    with zipfile.ZipFile(source) as archive:
        inventory = scan(archive)
        report["meshes"] = inventory["meshes"]

        for texture_entry in inventory["textures"]:
            # assets/<ns>/textures/<path>.png -> <prefix><ns>_<path>.png
            parts = texture_entry.split("/")
            try:
                namespace = parts[1]
                path = "/".join(parts[3:])
            except IndexError:  # pragma: no cover - malformed archive
                report["skipped"].append(texture_entry)
                continue
            flat = _unique(_flat_name(namespace, path, prefix),
                           set(copied_textures.values()), namespace)
            copied_textures[texture_entry] = flat
            if dry_run:
                continue
            textures_out.mkdir(parents=True, exist_ok=True)
            (textures_out / f"{flat}.png").write_bytes(archive.read(texture_entry))
        report["textures"] = copied_textures

        for model_entry in inventory["models"]:
            try:
                model = json.loads(archive.read(model_entry))
            except (json.JSONDecodeError, UnicodeDecodeError):
                report["skipped"].append(f"{model_entry} (not JSON)")
                continue
            if "parent" in model and not model.get("elements"):
                report["skipped"].append(f"{model_entry} (geometry comes from parent)")
                continue
            if not model.get("elements"):
                report["skipped"].append(f"{model_entry} (no elements)")
                continue

            textures = dict(model.get("textures") or {})
            for key, value in list(textures.items()):
                if not isinstance(value, str) or not value.startswith("#"):
                    if isinstance(value, str) and value.startswith("minecraft:"):
                        continue
                    if not isinstance(value, str):
                        continue
                    entry = next((candidate for candidate in _texture_candidates(
                        "", value) if candidate in copied_textures), None)
                    if entry is None:
                        report["missing_textures"].add(value)
                        continue
                    textures[key] = f"vehicle:item/{copied_textures[entry]}"
            model["textures"] = textures

            stem = Path(model_entry).stem
            # do not double the prefix when the source already carries it
            flat = stem if prefix and stem.startswith(prefix) else f"{prefix}{stem}"
            report["models"][flat] = model_entry
            if dry_run:
                continue
            models_out.mkdir(parents=True, exist_ok=True)
            (models_out / f"{flat}.json").write_text(
                json.dumps(model, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    report["missing_textures"] = sorted(report["missing_textures"])
    if not dry_run:
        destination.mkdir(parents=True, exist_ok=True)
        (destination / "manifest.json").write_text(
            json.dumps({key: value for key, value in report.items()
                        if key in ("source", "models", "textures", "skipped",
                                   "missing_textures", "meshes")},
                       indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return report


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--prefix", default="")
    parser.add_argument("--destination", type=Path, default=EXTERNAL_ROOT)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv[1:])
    if not args.archive.exists():
        print(f"Nu găsesc arhiva: {args.archive}")
        return 2
    report = import_pack(args.archive, prefix=args.prefix,
                         destination=args.destination, dry_run=args.dry_run)
    print(f"Modele importate: {len(report['models'])}")
    print(f"Texturi copiate: {len(report['textures'])}")
    if report["meshes"]:
        print(f"Mesh-uri ignorate ({len(report['meshes'])}): clientul vanilla nu le poate randa")
        for name in report["meshes"][:5]:
            print(f"  - {name}")
    if report["skipped"]:
        print(f"Modeluri sărite: {len(report['skipped'])}")
        for name in report["skipped"][:10]:
            print(f"  - {name}")
    if report["missing_textures"]:
        print(f"Texturi lipsă: {len(report['missing_textures'])}")
        for name in report["missing_textures"][:10]:
            print(f"  - {name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
