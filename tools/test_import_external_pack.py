"""Tests for the third-party pack importer (tools/import_external_pack.py)."""

from __future__ import annotations

import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import import_external_pack  # noqa: E402
import restyle  # noqa: E402


def build_sample_pack(path: Path) -> None:
    """A miniature 'Horizon' pack: one body model, one texture, one mesh."""
    rgba = bytes((200, 30, 30, 255)) * (4 * 4)
    png = restyle.encode_png(4, 4, rgba)
    model = {
        "textures": {"body": "horizon:item/car_body", "particle": "horizon:item/car_body"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 8, 32],
             "faces": {face: {"uv": [0, 0, 16, 16], "texture": "#body"}
                       for face in ("north", "east", "south", "west", "up", "down")}},
        ],
    }
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("assets/horizon/models/item/hz_sedan.json", json.dumps(model))
        archive.writestr("assets/horizon/textures/item/car_body.png", png)
        archive.writestr("assets/horizon/models/item/hz_sedan.obj", "v 0 0 0\n")


class ImportExternalPackTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.pack = self.root / "horizon.zip"
        build_sample_pack(self.pack)

    def test_dry_run_reports_without_writing(self):
        destination = self.root / "out"
        report = import_external_pack.import_pack(self.pack, destination=destination,
                                                  dry_run=True)
        self.assertIn("hz_sedan", report["models"])
        self.assertEqual(len(report["meshes"]), 1)
        self.assertFalse((destination / "models").exists())

    def test_model_is_copied_one_to_one(self):
        destination = self.root / "out"
        import_external_pack.import_pack(self.pack, prefix="hz_", destination=destination)
        model = json.loads((destination / "models" / "hz_sedan.json").read_text())
        self.assertEqual(model["elements"][0]["from"], [0, 0, 0])
        self.assertEqual(model["elements"][0]["to"], [16, 8, 32])
        self.assertEqual(model["elements"][0]["faces"]["up"]["uv"], [0, 0, 16, 16])

    def test_texture_references_are_repointed_into_our_namespace(self):
        destination = self.root / "out"
        import_external_pack.import_pack(self.pack, prefix="hz_", destination=destination)
        model = json.loads((destination / "models" / "hz_sedan.json").read_text())
        self.assertEqual(model["textures"]["body"], "vehicle:item/hz_item_car_body")
        self.assertTrue((destination / "textures" / "hz_item_car_body.png").exists())
        self.assertEqual(model["elements"][0]["faces"]["up"]["texture"], "#body")

    def test_manifest_is_written(self):
        destination = self.root / "out"
        import_external_pack.import_pack(self.pack, prefix="hz_", destination=destination)
        manifest = json.loads((destination / "manifest.json").read_text())
        self.assertIn("hz_sedan", manifest["models"])
        self.assertTrue(manifest["meshes"])

    def test_models_without_elements_are_skipped_not_silently_dropped(self):
        pack = self.root / "empty.zip"
        with zipfile.ZipFile(pack, "w") as archive:
            archive.writestr("assets/x/models/item/empty.json",
                             json.dumps({"parent": "horizon:item/base"}))
        destination = self.root / "out2"
        report = import_external_pack.import_pack(pack, destination=destination)
        self.assertFalse(report["models"])
        self.assertTrue(report["skipped"])


class BuilderContractTest(unittest.TestCase):
    """The importer output must be exactly what the builder copies."""

    def test_imported_layout_is_models_and_textures(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            pack = root / "horizon.zip"
            build_sample_pack(pack)
            destination = root / "external_models"
            import_external_pack.import_pack(pack, prefix="hz_", destination=destination)
            self.assertTrue((destination / "models" / "hz_sedan.json").exists())
            self.assertTrue(list((destination / "textures").glob("*.png")))

    def test_deleted_models_file_defaults_to_nothing(self):
        path = Path(__file__).resolve().parent / "deleted_models.txt"
        self.assertTrue(path.exists())
        entries = [line.split("#", 1)[0].strip()
                   for line in path.read_text(encoding="utf-8").splitlines()]
        self.assertEqual([entry for entry in entries if entry], [])


if __name__ == "__main__":
    unittest.main()
