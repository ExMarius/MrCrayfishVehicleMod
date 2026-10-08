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

    def test_configured_sha1_matches_deterministic_r28_pack(self):
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

    def test_every_generated_element_stays_inside_vanilla_model_bounds(self):
        for entry in sorted(path for path in self.entries
                            if path.startswith("assets/vehicle/models/") and path.endswith(".json")):
            model = self.read_json(entry)
            textures = model.get("textures", {})
            for index, element in enumerate(model.get("elements", [])):
                for key in ("from", "to"):
                    vector = element[key]
                    self.assertEqual(3, len(vector), f"{entry} element {index} {key}")
                    for coordinate in vector:
                        self.assertGreaterEqual(coordinate, -16.0,
                                                f"{entry} element {index} {key}")
                        self.assertLessEqual(coordinate, 32.0,
                                             f"{entry} element {index} {key}")
                rotation = element.get("rotation")
                if rotation is not None:
                    self.assertIn(rotation.get("axis"), ("x", "y", "z"),
                                  f"{entry} element {index} rotation axis")
                    self.assertIn(float(rotation.get("angle", 0.0)),
                                  build_resource_pack.VANILLA_1_21_4_ELEMENT_ANGLES,
                                  f"{entry} element {index} rotation angle")
                for face, definition in element.get("faces", {}).items():
                    texture = definition.get("texture", "")
                    if texture.startswith("#"):
                        self.assertIn(texture[1:], textures,
                                      f"{entry} element {index} face {face}: {texture}")

    def test_every_pack_png_has_a_valid_nonempty_ihdr(self):
        for entry in sorted(path for path in self.entries if path.endswith(".png")):
            data = self.archive.read(entry)
            self.assertTrue(data.startswith(b"\x89PNG\r\n\x1a\n"), entry)
            self.assertGreaterEqual(len(data), 24, entry)
            self.assertGreater(int.from_bytes(data[16:20], "big"), 0, entry)
            self.assertGreater(int.from_bytes(data[20:24], "big"), 0, entry)

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

    def test_sports_plane_models_textures_and_source_audio_are_complete(self):
        expected_elements = {
            "sports_plane_body": 75,
            "sports_plane_wings": 12,
            "sports_plane_seat": 5,
            "sports_plane_propeller": 3,
            "sports_plane_left_aileron": 1,
            "sports_plane_right_aileron": 1,
            "sports_plane_elevator": 2,
            "sports_plane_joystick": 3,
        }
        for model, count in expected_elements.items():
            data = self.read_json(f"assets/vehicle/models/item/{model}.json")
            self.assertEqual(count, len(data["elements"]), model)
            self.assertIn(f"assets/vehicle/items/{model}.json", self.entries)
            self.assertIn(f"assets/vehicle/textures/item/{model}.png", self.entries)
        sounds = self.read_json("assets/vehicle/sounds.json")
        self.assertIn("entity.sports_plane.engine", sounds)
        engine = self.archive.read("assets/vehicle/sounds/entity/sports_plane/engine.ogg")
        self.assertEqual(38_533, self.last_ogg_granule(engine))

    def test_compact_helicopter_models_textures_and_source_audio_are_complete(self):
        expected_elements = {
            "compact_helicopter_body": 91,
            "compact_helicopter_blades": 4,
            "compact_helicopter_joystick": 3,
            "compact_helicopter_seat": 8,
            "compact_helicopter_tail_rotor": 4,
        }
        for model, count in expected_elements.items():
            data = self.read_json(f"assets/vehicle/models/item/{model}.json")
            self.assertEqual(count, len(data["elements"]), model)
            self.assertIn(f"assets/vehicle/items/{model}.json", self.entries)
            self.assertIn(f"assets/vehicle/textures/item/{model}.png", self.entries)
        sounds = self.read_json("assets/vehicle/sounds.json")
        self.assertIn("entity.vehicle.helicopter_rotor", sounds)
        rotor = self.archive.read("assets/vehicle/sounds/entity/vehicle/helicopter_rotor.ogg")
        self.assertEqual(27_922, self.last_ogg_granule(rotor))

    def test_sofacopter_models_use_the_original_sofa_arm_and_ceiling_fan_rotor(self):
        expected_elements = {
            "sofacopter_sofa": 11,
            "sofacopter_arm": 6,
            "sofacopter_blades": 10,
        }
        for model, count in expected_elements.items():
            data = self.read_json(f"assets/vehicle/models/item/{model}.json")
            self.assertEqual(count, len(data["elements"]), model)
            self.assertIn(f"assets/vehicle/items/{model}.json", self.entries)
        sofa = self.read_json("assets/vehicle/models/item/sofacopter_sofa.json")
        self.assertEqual("minecraft:block/red_wool", sofa["textures"]["wool"])
        self.assertEqual("minecraft:block/oak_log", sofa["textures"]["support"])
        arm = self.read_json("assets/vehicle/models/item/sofacopter_arm.json")
        self.assertEqual("minecraft:block/gray_concrete", arm["textures"]["0"])
        blades = self.read_json("assets/vehicle/models/item/sofacopter_blades.json")
        self.assertEqual("minecraft:block/gray_concrete", blades["textures"]["0"])
        self.assertEqual("minecraft:block/white_concrete", blades["textures"]["1"])
        self.assertEqual("fan_base_1", blades["elements"][0]["name"])
        self.assertEqual("fan_4", blades["elements"][-1]["name"])
        self.assertEqual([-3.4, 7.0, 6.5], blades["elements"][6]["from"])
        self.assertEqual([9.5, 8.0, 19.4], blades["elements"][-1]["to"])

    def test_dune_buggy_models_use_the_released_vanilla_block_textures_and_bumper_car_engine(self):
        body = self.read_json("assets/vehicle/models/item/dune_buggy_body.json")
        self.assertEqual(20, len(body["elements"]))
        self.assertEqual("minecraft:block/yellow_concrete", body["textures"]["body"])
        self.assertEqual("minecraft:block/yellow_wool", body["textures"]["seat"])
        self.assertEqual("minecraft:block/light_gray_concrete", body["textures"]["engine_base"])
        self.assertEqual("minecraft:block/gray_concrete", body["textures"]["engine_part"])
        handles = self.read_json("assets/vehicle/models/item/dune_buggy_handles.json")
        self.assertEqual(16, len(handles["elements"]))
        self.assertEqual("minecraft:block/yellow_concrete", handles["textures"]["handles"])
        self.assertEqual("minecraft:block/light_gray_concrete", handles["textures"]["axel"])
        self.assertEqual("minecraft:block/red_concrete", handles["textures"]["base"])
        legal_angles = {-45.0, -22.5, 0.0, 22.5, 45.0}
        for model in (body, handles):
            for element in model["elements"]:
                if "rotation" in element:
                    self.assertIn(element["rotation"]["angle"], legal_angles)
        for entry in ("assets/vehicle/items/dune_buggy_body.json",
                     "assets/vehicle/items/dune_buggy_handles.json"):
            self.assertIn(entry, self.entries)
        self.assertIn("assets/vehicle/sounds/entity/bumper_car/engine.ogg", self.entries)
        sounds = self.read_json("assets/vehicle/sounds.json")
        self.assertIn("entity.bumper_car.engine", sounds)
        self.assertEqual("vehicle:entity/bumper_car/engine",
                         sounds["entity.bumper_car.engine"]["sounds"][0]["name"])

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
