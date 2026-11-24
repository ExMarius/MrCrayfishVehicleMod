"""Guards for the from-scratch bodies (r42).

Nothing here looks at the original mod's geometry: these bodies are authored
cuboid art, so the checks are about pack validity and about the hard points the
Java rig depends on.
"""

from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import scratch_models  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ITEM_MODELS = ROOT / "paper-plugin/src/main/resources/resource-pack/vehicle/models/item"

# Wheel boxes in model space (body_scale 1.4): centres x -2 / 18, z 22.5 / -6.5,
# radius 5.6. Anything the body draws inside these volumes would clip a wheel.
WHEEL_BOXES = [
    (-4.8, 0.0, 16.9, 0.8, 11.2, 28.1),
    (15.2, 0.0, 16.9, 20.8, 11.2, 28.1),
    (-4.8, 0.0, -12.1, 0.8, 11.2, -0.9),
    (15.2, 0.0, -12.1, 20.8, 11.2, -0.9),
]


class ScratchBodyTest(unittest.TestCase):
    def models(self):
        # the shipping roster (``SCRATCH_BODIES``) is empty until the design is
        # agreed, so the guards run over every body the module can build
        for stem, factory in scratch_models.CANDIDATE_BODIES.items():
            yield stem, factory()

    def test_every_element_stays_inside_the_vanilla_limit(self):
        for stem, model in self.models():
            for element in model["elements"]:
                for value in element["from"] + element["to"]:
                    self.assertGreaterEqual(value, -16.0, stem)
                    self.assertLessEqual(value, 32.0, stem)

    def test_every_face_references_a_known_material(self):
        for stem, model in self.models():
            for element in model["elements"]:
                self.assertTrue(element["faces"], stem)
                for face in element["faces"].values():
                    key = face["texture"].lstrip("#")
                    self.assertIn(key, scratch_models.MATERIALS, stem)

    def test_only_the_paint_is_tinted(self):
        tinted = {name for name, material in scratch_models.MATERIALS.items() if material[2]}
        self.assertEqual(tinted, {"body"})
        for stem, model in self.models():
            for element in model["elements"]:
                for face in element["faces"].values():
                    key = face["texture"].lstrip("#")
                    self.assertEqual("tintindex" in face, key in tinted, stem)

    def test_nothing_clips_a_wheel(self):
        for stem, model in self.models():
            for element in model["elements"]:
                lo, hi = element["from"], element["to"]
                for wx0, wy0, wz0, wx1, wy1, wz1 in WHEEL_BOXES:
                    overlap = (min(hi[0], wx1) - max(lo[0], wx0) > 0.05
                               and min(hi[1], wy1) - max(lo[1], wy0) > 0.05
                               and min(hi[2], wz1) - max(lo[2], wz0) > 0.05)
                    self.assertFalse(overlap, f"{stem}: {element['from']}..{element['to']}")

    def test_front_of_the_vehicle_is_plus_z(self):
        # headlights at +z, taillights at -z: the rig assumes front = +Z
        for stem, model in self.models():
            textures = {face["texture"].lstrip("#")
                        for element in model["elements"] for face in element["faces"].values()}
            self.assertIn("headlight", textures, stem)
            self.assertIn("taillight", textures, stem)
            front = [e for e in model["elements"]
                     if any(f["texture"] == "#headlight" for f in e["faces"].values())]
            rear = [e for e in model["elements"]
                    if any(f["texture"] == "#taillight" for f in e["faces"].values())]
            self.assertGreater(min(max(e["from"][2], e["to"][2]) for e in front), 20.0, stem)
            self.assertLess(max(min(e["from"][2], e["to"][2]) for e in rear), -8.0, stem)

    def test_seat_cushions_reach_the_rider_anchor(self):
        # LandVehicleSpec.seat() anchors the rider at 8.6 model units; the bench
        # must reach y = 8 or the player floats above the seat.
        for stem, model in self.models():
            seat_tops = [max(e["from"][1], e["to"][1]) for e in model["elements"]
                         if any(f["texture"] == "#seat" for f in e["faces"].values())]
            self.assertTrue(seat_tops, stem)
            self.assertGreaterEqual(max(seat_tops), 8.0, stem)

    def wheel_models(self):
        for stem, factory in scratch_models.SCRATCH_WHEELS.items():
            yield stem, factory()

    def test_wheels_use_the_envelope_the_rig_expects(self):
        # 16 units across (y/z 0..16) and 4 units wide (x 6..10 +- tread), so any
        # LandVehicleSpec mounts them without a scale correction.
        for stem, model in self.wheel_models():
            xs, ys, zs = [], [], []
            for element in model["elements"]:
                xs += [element["from"][0], element["to"][0]]
                ys += [element["from"][1], element["to"][1]]
                zs += [element["from"][2], element["to"][2]]
            self.assertGreaterEqual(min(xs), 5.5, stem)
            self.assertLessEqual(max(xs), 10.5, stem)
            self.assertGreaterEqual(min(ys), -0.01, stem)
            self.assertLessEqual(max(ys), 16.01, stem)
            self.assertGreaterEqual(min(zs), -0.01, stem)
            self.assertLessEqual(max(zs), 16.01, stem)
            self.assertAlmostEqual(min(ys) + max(ys), 16.0, delta=0.01, msg=stem)
            self.assertAlmostEqual(min(zs) + max(zs), 16.0, delta=0.01, msg=stem)

    def test_wheels_have_no_tint_and_known_materials(self):
        for stem, model in self.wheel_models():
            self.assertTrue(model["elements"], stem)
            for element in model["elements"]:
                for face in element["faces"].values():
                    self.assertIn(face["texture"].lstrip("#"), scratch_models.MATERIALS, stem)
                    self.assertNotIn("tintindex", face, stem)

    def test_every_wheel_type_has_a_model(self):
        # whatever WheelType.java lists must exist either as one of our models
        # or as a model converted from the mod's assets
        java = (ROOT / "paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/"
                "mechanic/WheelType.java").read_text(encoding="utf-8")
        names = [part.split('"')[1] for part in java.split("(")[1:] if '"' in part]
        models = [name for name in names if name.endswith("_wheel")]
        self.assertTrue(models)
        converted = {"standard_wheel", "sports_wheel", "off_road_wheel"}
        for model in models:
            self.assertTrue(model in scratch_models.SCRATCH_WHEELS or model in converted,
                            f"WheelType {model} has no model in the pack")

    def test_wheels_are_listed_in_the_creative_gui(self):
        gui = (ROOT / "paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/"
               "mechanic/CreativeGui.java").read_text(encoding="utf-8")
        self.assertIn("WheelType.values()", gui)

    def test_materials_are_emitted_as_png(self):
        png = scratch_models.material_png("body")
        self.assertTrue(png.startswith(b"\x89PNG\r\n\x1a\n"))
        self.assertIn(b"IHDR", png[:40])


if __name__ == "__main__":
    unittest.main()


class PackVersionConsistencyTest(unittest.TestCase):
    """The pack name is spelled in three places; they must agree.

    r42 shipped a red build because build.gradle.kts still produced "-r41" while
    the workflow and config.yml expected "-r42". This guards that trio.
    """

    def test_same_release_tag_everywhere(self):
        import re

        root = ROOT
        gradle = (root / "paper-plugin/build.gradle.kts").read_text(encoding="utf-8")
        workflow = (root / ".github/workflows/paper-plugin.yml").read_text(encoding="utf-8")
        config = (root / "paper-plugin/src/main/resources/config.yml").read_text(encoding="utf-8")
        pattern = re.compile(r"resource-pack-26\.3-(r\d+)\.zip")
        tags = set(pattern.findall(gradle)) | set(pattern.findall(workflow)) | set(pattern.findall(config))
        self.assertEqual(len(tags), 1, f"tag-uri diferite in pack: {sorted(tags)}")
