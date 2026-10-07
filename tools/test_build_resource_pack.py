import hashlib
import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools import build_resource_pack


class ResourcePackBuildTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="vehicle-pack-test-")
        cls.pack = Path(cls.temporary.name) / "pack.zip"
        cls.output, cls.sha1 = build_resource_pack.build(cls.pack)
        cls.archive = zipfile.ZipFile(cls.pack)
        cls.entries = set(cls.archive.namelist())

    @classmethod
    def tearDownClass(cls):
        cls.archive.close()
        cls.temporary.cleanup()

    def read_json(self, path):
        return json.loads(self.archive.read(path))

    def test_configured_sha1_matches_deterministic_r21_pack(self):
        config = (build_resource_pack.ROOT / "paper-plugin/src/main/resources/config.yml").read_text()
        configured = re.search(r'^\s*sha1:\s*"([0-9a-f]{40})"\s*$', config, re.MULTILINE)
        self.assertIsNotNone(configured)
        self.assertEqual(hashlib.sha1(self.pack.read_bytes()).hexdigest(), self.sha1)
        self.assertEqual(configured.group(1), self.sha1)

    def test_every_custom_item_resolves_to_a_model_and_every_custom_texture_exists(self):
        for entry in sorted(path for path in self.entries
                            if path.startswith("assets/vehicle/items/") and path.endswith(".json")):
            item = self.read_json(entry)
            model = item["model"]["model"]
            namespace, model_path = model.split(":", 1)
            self.assertEqual("vehicle", namespace, entry)
            model_entry = f"assets/vehicle/models/{model_path}.json"
            self.assertIn(model_entry, self.entries, entry)
            model_json = self.read_json(model_entry)
            for texture in model_json.get("textures", {}).values():
                if not texture.startswith("vehicle:"):
                    continue
                _, texture_path = texture.split(":", 1)
                self.assertIn(f"assets/vehicle/textures/{texture_path}.png", self.entries,
                              f"{model_entry}: {texture}")

    def test_every_custom_sound_event_resolves_to_an_ogg(self):
        for event, definition in self.read_json("assets/vehicle/sounds.json").items():
            for sound in definition["sounds"]:
                name = sound["name"] if isinstance(sound, dict) else sound
                if not name.startswith("vehicle:"):
                    continue
                _, sound_path = name.split(":", 1)
                self.assertIn(f"assets/vehicle/sounds/{sound_path}.ogg", self.entries, event)

    def test_sports_car_models_and_audio_are_complete(self):
        expected_elements = {
            "sports_car_body": 148,
            "sports_car_steering_wheel": 13,
            "sports_car_hood": 4,
            "sports_car_left_door": 6,
            "sports_car_right_door": 6,
            "sports_car_boot": 3,
            "sports_car_seat": 4,
            "sports_car_dashboard": 10,
            "sports_car_roof": 21,
        }
        for model, count in expected_elements.items():
            data = self.read_json(f"assets/vehicle/models/item/{model}.json")
            self.assertEqual(count, len(data["elements"]), model)
            self.assertIn(f"assets/vehicle/items/{model}.json", self.entries)
            self.assertIn(f"assets/vehicle/textures/item/{model}.png", self.entries)

        sounds = self.read_json("assets/vehicle/sounds.json")
        for event in (
            "entity.sports_car.engine",
            "entity.vehicle.door.open",
            "entity.vehicle.door.close",
            "entity.vehicle.hood.open",
            "entity.vehicle.hood.close",
        ):
            self.assertIn(event, sounds)
        engine = self.archive.read("assets/vehicle/sounds/entity/sports_car/engine.ogg")
        self.assertEqual(29_672, self.last_ogg_granule(engine))

    def test_mini_bus_models_and_audio_are_complete(self):
        expected_elements = {
            "mini_bus_body": 58,
            "mini_bus_stock_roof": 36,
            "mini_bus_front_roof": 20,
            "mini_bus_roof_racks": 9,
            "mini_bus_left_door": 5,
            "mini_bus_right_door": 5,
            "mini_bus_sliding_door": 2,
            "mini_bus_rear": 13,
            "mini_bus_seat": 6,
            "mini_bus_dashboard": 11,
            "big_tow_bar": 4,
        }
        for model, count in expected_elements.items():
            data = self.read_json(f"assets/vehicle/models/item/{model}.json")
            self.assertEqual(count, len(data["elements"]), model)
            self.assertIn(f"assets/vehicle/items/{model}.json", self.entries)
            self.assertIn(f"assets/vehicle/textures/item/{model}.png", self.entries)
        sounds = self.read_json("assets/vehicle/sounds.json")
        self.assertIn("entity.mini_bus.engine", sounds)
        engine = self.archive.read("assets/vehicle/sounds/entity/mini_bus/engine.ogg")
        self.assertEqual(113_610, self.last_ogg_granule(engine))

    def test_golf_cart_model_and_source_audio_are_complete(self):
        model = self.read_json("assets/vehicle/models/item/golf_cart_body.json")
        self.assertEqual(86, len(model["elements"]))
        self.assertIn("assets/vehicle/items/golf_cart_body.json", self.entries)
        self.assertEqual("minecraft:block/white_concrete", model["textures"]["body"])
        sounds = self.read_json("assets/vehicle/sounds.json")
        self.assertIn("entity.vehicle.helicopter_rotor", sounds)
        rotor = self.archive.read("assets/vehicle/sounds/entity/vehicle/helicopter_rotor.ogg")
        self.assertEqual(27_922, self.last_ogg_granule(rotor))

    def test_jet_ski_body_and_engine_are_complete(self):
        model = self.read_json("assets/vehicle/models/item/jet_ski_body.json")
        self.assertEqual(42, len(model["elements"]))
        self.assertIn("assets/vehicle/items/jet_ski_body.json", self.entries)
        self.assertEqual("vehicle:model/cray_industries", model["textures"]["logo"])
        sounds = self.read_json("assets/vehicle/sounds.json")
        self.assertIn("entity.jet_ski.engine", sounds)
        engine = self.archive.read("assets/vehicle/sounds/entity/jet_ski/engine.ogg")
        self.assertEqual(46_434, self.last_ogg_granule(engine))

    @staticmethod
    def last_ogg_granule(data):
        offset = 0
        granule = None
        while offset < len(data):
            if data[offset:offset + 4] != b"OggS":
                raise AssertionError(f"invalid Ogg page at byte {offset}")
            segment_count = data[offset + 26]
            segment_table = data[offset + 27:offset + 27 + segment_count]
            granule = int.from_bytes(data[offset + 6:offset + 14], "little")
            offset += 27 + segment_count + sum(segment_table)
        if offset != len(data) or granule is None:
            raise AssertionError("truncated Ogg stream")
        return granule


if __name__ == "__main__":
    unittest.main()
