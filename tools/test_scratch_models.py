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

    def test_materials_are_emitted_as_png(self):
        png = scratch_models.material_png("body")
        self.assertTrue(png.startswith(b"\x89PNG\r\n\x1a\n"))
        self.assertIn(b"IHDR", png[:40])


if __name__ == "__main__":
    unittest.main()
