"""Guards for the 1:1 Horizon import (tools/horizon_import.py).

These make sure the transfer package is converted faithfully: the main body
keeps every cuboid, every rotated group is present with its angle/pivot, the
group models really are shifted so the pivot sits on the model centre, and the
Java spec lists exactly the same models.
"""

from __future__ import annotations

import io
import json
import re
import sys
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

ROOT = Path(__file__).resolve().parents[1]
TRANSFER = ROOT / "tools/horizon/horizon-off-roader-transfer.zip"
MANIFEST = ROOT / "tools/horizon/horizon_parts.json"
EXTERNAL = ROOT / "tools/external_models"
SPEC = ROOT / "paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/vehicle/LandVehicleSpec.java"


def transfer_parts() -> dict:
    transfer = zipfile.ZipFile(TRANSFER)
    inner = zipfile.ZipFile(io.BytesIO(transfer.read("paper-resource-pack.zip")))
    return json.loads(inner.read("go_kart_parts.json"))["parts"]


class HorizonImportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

    def test_body_keeps_every_cuboid_of_the_source(self):
        source = transfer_parts()["off_roader_body"]
        expected = sum(group["element_count"] for group in source)
        body = json.loads((EXTERNAL / "models" / f"{self.manifest['body']['model']}.json")
                          .read_text(encoding="utf-8"))
        groups = sum(record["elements"] for record in self.manifest["parts"])
        self.assertEqual(len(body["elements"]) + groups, expected)
        self.assertEqual(len(body["elements"]), 163)

    def test_every_rotated_group_is_present(self):
        source_groups = [group for group in transfer_parts()["off_roader_body"]
                         if group["rotation"]]
        self.assertEqual(len(self.manifest["parts"]), len(source_groups))
        for record, source in zip(self.manifest["parts"], source_groups):
            self.assertEqual(record["angle"], source["rotation"]["angle"])
            self.assertEqual(record["axis"], source["rotation"]["axis"])
            self.assertEqual(record["pivot"], source["rotation"]["origin"])

    def test_group_models_are_shifted_so_the_pivot_is_the_model_centre(self):
        for record in self.manifest["parts"]:
            model = json.loads((EXTERNAL / "models" / f"{record['model']}.json")
                               .read_text(encoding="utf-8"))
            xs, ys, zs = [], [], []
            for element in model["elements"]:
                for point in (element["from"], element["to"]):
                    xs.append(point[0]); ys.append(point[1]); zs.append(point[2])
            # after shifting, the pivot (which was at record['pivot']) is at 8,8,8
            self.assertTrue(xs and ys and zs, record["model"])
            self.assertLessEqual(abs((min(xs) + max(xs)) / 2 - 8.0), 8.0, record["model"])

    def test_no_model_keeps_an_illegal_rotation(self):
        allowed = {-45.0, -22.5, 0.0, 22.5, 45.0}
        for path in sorted((EXTERNAL / "models").glob("*.json")):
            model = json.loads(path.read_text(encoding="utf-8"))
            for element in model["elements"]:
                rotation = element.get("rotation")
                if rotation:
                    self.assertIn(float(rotation["angle"]), allowed,
                                  f"{path.name}: {rotation['angle']}")

    def test_textures_exist_for_every_reference(self):
        textures = {path.stem for path in (EXTERNAL / "textures").glob("*.png")}
        for path in sorted((EXTERNAL / "models").glob("*.json")):
            model = json.loads(path.read_text(encoding="utf-8"))
            for value in (model.get("textures") or {}).values():
                if value.startswith("vehicle:item/"):
                    self.assertIn(value.split("/")[-1], textures, path.name)

    def test_spec_lists_the_same_groups(self):
        spec = SPEC.read_text(encoding="utf-8")
        block = spec.split("HORIZON_OFF_ROADER = new LandVehicleSpec(")[1].split(");")[0]
        listed = set(re.findall(r'"(hz_off_roader_g\d+)"', block))
        expected = {record["model"] for record in self.manifest["parts"]}
        self.assertEqual(listed, expected)
        self.assertIn(f'"{self.manifest["body"]["model"]}"', block)
        self.assertIn('Map.entry(HORIZON_OFF_ROADER.id, HORIZON_OFF_ROADER)', spec)


if __name__ == "__main__":
    unittest.main()
