"""Tests for the body-geometry restyle (tools/restyle_geometry.py).

The restyle must change the silhouette *predictably*: proportions applied
around the model centre, the cabin ratio only above the belt line, kits added
on top of the result, and every new element inside the vanilla model limits
(elements -16..32), because the client rejects a model otherwise.
"""

import unittest

from tools import restyle_geometry as geometry

FACE_NAMES = ("north", "east", "south", "west", "up", "down")


def box(x0, y0, z0, x1, y1, z1, texture="#body"):
    return {
        "from": [x0, y0, z0],
        "to": [x1, y1, z1],
        "faces": {name: {"uv": [0, 0, 16, 16], "texture": texture} for name in FACE_NAMES},
    }


def sample_model():
    return {
        "textures": {"body": "minecraft:block/white_concrete"},
        "elements": [
            box(0, 0, 0, 16, 4, 20),      # lower body
            box(2, 4, 2, 14, 10, 12),     # cabin / roof block
        ],
    }


class RestyleGeometryTest(unittest.TestCase):
    def test_proportions_are_applied_around_the_model_centre(self):
        model = sample_model()
        geometry.restyle_model(model, (1.10, 1.00, 1.00, 1.0, ()))
        low, high = model["elements"][0]["from"], model["elements"][0]["to"]
        centre = 8.0
        self.assertAlmostEqual(low[0], centre + (0 - centre) * 1.10, places=3)
        self.assertAlmostEqual(high[0], centre + (16 - centre) * 1.10, places=3)

    def test_cabin_ratio_only_moves_geometry_above_the_belt(self):
        model = sample_model()
        geometry.restyle_model(model, (1.0, 1.0, 1.0, 1.2, ()))
        body_floor = model["elements"][0]["from"][1]
        roof_top = model["elements"][1]["to"][1]
        self.assertAlmostEqual(body_floor, 0.0, places=3)
        self.assertGreater(roof_top / 10.0, 1.08)  # stretched above the belt line

    def test_removing_the_kit_leaves_the_element_count_untouched(self):
        model = sample_model()
        geometry.restyle_model(model, (1.05, 0.95, 1.02, 1.0, ()))
        self.assertEqual(len(model["elements"]), 2)

    def test_kit_adds_elements_and_its_textures(self):
        model = sample_model()
        geometry.restyle_model(model, (1.0, 1.0, 1.0, 1.0, ("roof_rack", "light_bar")))
        self.assertGreater(len(model["elements"]), 2)
        for key in ("kit_dark", "kit_metal", "kit_lamp"):
            self.assertIn(key, model["textures"])

    def test_every_kit_stays_inside_vanilla_limits(self):
        for kit in ("roll_hoop", "side_skirt", "footboards", "exhaust_stack",
                    "antenna", "roof_box", "roof_vent", "roof_rack", "light_bar"):
            model = sample_model()
            geometry.restyle_model(model, (1.0, 1.0, 1.0, 1.0, (kit,)))
            for element in model["elements"]:
                for corner in (element["from"], element["to"]):
                    for value in corner:
                        self.assertGreaterEqual(value, -16.0, kit)
                        self.assertLessEqual(value, 32.0, kit)

    def test_every_profile_is_plausible(self):
        for stem, (width, height, length, cabin, kit) in geometry.PROFILES.items():
            self.assertGreaterEqual(width, 1.0, stem)
            self.assertLessEqual(width, 1.15, stem)
            self.assertGreaterEqual(height, 0.85, stem)
            self.assertLessEqual(height, 1.05, stem)
            self.assertGreaterEqual(length, 0.95, stem)
            self.assertLessEqual(length, 1.10, stem)
            self.assertGreaterEqual(cabin, 0.85, stem)
            self.assertLessEqual(cabin, 1.20, stem)
            self.assertIsInstance(kit, tuple, stem)


if __name__ == "__main__":
    unittest.main()
