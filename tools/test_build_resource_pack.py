import hashlib
import json
import re
import struct
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

    def test_configured_sha1_matches_deterministic_pack(self):
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
                    uv = definition.get("uv")
                    if uv is not None:
                        u1, v1, u2, v2 = uv
                        self.assertGreaterEqual(min(u1, u2), -0.001,
                                                f"{entry} element {index} face {face} uv {uv}")
                        self.assertGreaterEqual(min(v1, v2), -0.001,
                                                f"{entry} element {index} face {face} uv {uv}")

    @staticmethod
    def _png_size(data: bytes) -> tuple[int, int]:
        return struct.unpack(">II", data[16:24])

    def _resolve_texture(self, textures: dict, reference: str, depth: int = 0) -> str | None:
        # A face's own "texture" (or a "particle" entry) can itself be another
        # "#variable" indirection rather than a real resource location; follow the
        # chain to whatever it ultimately resolves to, the same way Minecraft does.
        if depth > 10 or reference is None:
            return None
        if reference.startswith("#"):
            return self._resolve_texture(textures, textures.get(reference[1:]), depth + 1)
        return reference

    def test_every_face_uv_stays_within_its_real_texture_resolution(self):
        # Minecraft always scales a model's declared UV space proportionally against
        # the texture's own real pixel resolution, whatever it is -- NOT against any
        # "texture_size" field declared in the model JSON, which Minecraft's own
        # documentation lists as Blockbench-only editing metadata the game never
        # reads (see convert_model's docstring and SOURCE_POSITION_AUDIT.md item 33).
        # The only correct bound check is therefore against each face's own real,
        # resolved destination texture file -- not a value the model merely claims.
        # Every vanilla `minecraft:` block/item texture this pack references is
        # confirmed (by inspection) to be an ordinary, non-animated 16x16 sprite.
        vanilla_namespace_size = (16, 16)
        png_size_cache: dict[str, tuple[int, int]] = {}

        def real_size(texture_reference: str) -> tuple[int, int] | None:
            if texture_reference.startswith("minecraft:"):
                return vanilla_namespace_size
            if texture_reference.startswith("vehicle:"):
                _, path = texture_reference.split(":", 1)
                entry = f"assets/vehicle/textures/{path}.png"
                if entry not in self.entries:
                    return None
                if entry not in png_size_cache:
                    png_size_cache[entry] = self._png_size(self.archive.read(entry))
                return png_size_cache[entry]
            return None

        checked_models = 0
        checked_faces = 0
        for entry in sorted(path for path in self.entries
                            if path.startswith("assets/vehicle/models/") and path.endswith(".json")):
            model = self.read_json(entry)
            textures = model.get("textures", {})
            checked_models += 1
            for index, element in enumerate(model.get("elements", [])):
                for face, definition in element.get("faces", {}).items():
                    uv = definition.get("uv")
                    texture = definition.get("texture")
                    if uv is None or texture is None:
                        continue
                    resolved = self._resolve_texture(textures, texture)
                    self.assertIsNotNone(resolved, f"{entry} element {index} face {face}: "
                                                    f"{texture} does not resolve to a real texture")
                    size = real_size(resolved)
                    self.assertIsNotNone(size, f"{entry} element {index} face {face}: "
                                                f"{resolved} has no readable real texture file")
                    width, height = size
                    u1, v1, u2, v2 = uv
                    checked_faces += 1
                    self.assertLessEqual(max(u1, u2), width + 0.001,
                                         f"{entry} element {index} face {face} uv {uv} "
                                         f"exceeds {resolved}'s real {width}x{height} resolution")
                    self.assertLessEqual(max(v1, v2), height + 0.001,
                                         f"{entry} element {index} face {face} uv {uv} "
                                         f"exceeds {resolved}'s real {width}x{height} resolution")
        self.assertGreater(checked_models, 0)
        self.assertGreater(checked_faces, 0)

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
        # r36: the roof strut's "west"/"up" faces had an isolated source authoring
        # error (UV dimensions matching none of this element's other four faces or
        # its own real geometry); patched to mirror their correctly-sized siblings
        # ("east" and "down") rather than sampling past the sprite's edge. A disclosed
        # deviation from the literal source value; see SOURCE_POSITION_AUDIT.md.
        for element in model["elements"]:
            if element["from"] == [11, 28.5, 19] and element["to"] == [21, 29.6, 20.1]:
                self.assertEqual([0, 0, 1.1, 1.1], element["faces"]["west"]["uv"])
                self.assertEqual([0, 0, 10, 1.1], element["faces"]["up"]["uv"])
                break
        else:
            self.fail("golf cart roof strut element not found")
        sounds = self.read_json("assets/vehicle/sounds.json")
        self.assertIn("entity.vehicle.helicopter_rotor", sounds)
        rotor = self.archive.read("assets/vehicle/sounds/entity/vehicle/helicopter_rotor.ogg")
        self.assertEqual(27_922, self.last_ogg_granule(rotor))

    def test_vehicle_trailer_rails_scale_their_authored_uv_into_vanilla_bounds(self):
        # r36 declared texture_size: [17, 17] on this model, assuming Minecraft would
        # rescale these two 17-model-pixel-long side rails' UV (authored 1:1 against
        # their own real length on all four faces) to fit the vanilla 16x16 canvas
        # this port substitutes for the source's own dedicated "frame" art. That
        # field is Blockbench-only editing metadata the game never reads, so the raw
        # UV (reaching 17, one unit past the substitute sprite's real bounds) was
        # still being sampled out of range -- the purple/stray pixels reported under
        # the trailer's sides. The fix instead bakes a 16/17 scale directly into
        # these faces' own UV numbers so every value actually lands at or under the
        # substitute sprite's real 16x16 bounds; see SOURCE_POSITION_AUDIT.md.
        model = self.read_json("assets/vehicle/models/item/vehicle_trailer_body.json")
        self.assertNotIn("texture_size", model)
        rails = [element for element in model["elements"]
                 if element["from"] in ([1, -0.5, -4], [13, -0.5, -4])]
        self.assertEqual(2, len(rails))
        scale = 16.0 / 17.0
        for rail in rails:
            for face, expected in (
                ("east", [0, 0, 17, 1.5]),
                ("west", [0, 0, 17, 1.5]),
                ("up", [0, 0, 2, 17]),
                ("down", [0, 0, 2, 17]),
            ):
                uv = rail["faces"][face]["uv"]
                self.assertEqual([round(value * scale, 4) for value in expected], uv)
                self.assertLessEqual(max(uv), 16.0)

    def test_standard_wheel_declares_its_authored_texture_size(self):
        # r37 declared texture_size: [32, 32] here, but later analysis found this
        # model never actually had a UV-overflow defect: its own UV stays within
        # 0-11 (the drawn 22x22-pixel corner of wheel.png's full 32x32 canvas), and
        # Minecraft always scales a model's 0-16 UV space proportionally against
        # whatever the texture's real resolution is -- exactly how unmodified
        # vanilla models already work against higher-resolution resource packs --
        # so this model's own UV already landed correctly on the drawn artwork
        # before and after r37's declaration. texture_size is additionally
        # Blockbench-only editing metadata the game does not read at all; this
        # model keeps declaring it purely so Blockbench shows the real authored
        # canvas if ever reopened. See SOURCE_POSITION_AUDIT.md for the
        # correction, and for the one model that genuinely did have UV
        # overflowing past 16 (the Vehicle Trailer's side rails).
        model = self.read_json("assets/vehicle/models/item/standard_wheel.json")
        self.assertEqual([32, 32], model["texture_size"])
        max_uv = max(
            value
            for element in model["elements"]
            for face in element["faces"].values()
            for value in face["uv"]
        )
        self.assertLessEqual(max_uv, model["texture_size"][0])

    def test_jet_ski_body_and_engine_are_complete(self):

        model = self.read_json("assets/vehicle/models/item/jet_ski_body.json")
        self.assertEqual(42, len(model["elements"]))
        self.assertIn("assets/vehicle/items/jet_ski_body.json", self.entries)
        # r35: the source model's literal, unconverted "vehicle:model/cray_industries"
        # path is not covered by Minecraft's default "blocks" sprite atlas sources and
        # rendered as a missing-texture black/purple square in-game; it now points at a
        # dedicated textures/item/ copy, like every other vehicle's Cray Industries decal.
        self.assertEqual("vehicle:item/jet_ski_logo", model["textures"]["logo"])
        self.assertIn("assets/vehicle/textures/item/jet_ski_logo.png", self.entries)
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

    def test_atv_mini_bike_smart_car_and_boat_models_use_released_geometry_and_audio(self):
        atv_body = self.read_json("assets/vehicle/models/item/atv_body.json")
        self.assertEqual(46, len(atv_body["elements"]))
        # The vendored source model (exported with MrCrayfish's own Model Creator,
        # the closest available reference for this un-decompiled vehicle) declares
        # "body": white_concrete and "frame": anvil itself. r37 restores those
        # values; an earlier port had substituted lime_concrete/gray_concrete with
        # no documented reason, rendering the default-white, dyeable ATV green.
        self.assertEqual("minecraft:block/white_concrete", atv_body["textures"]["body"])
        self.assertEqual("minecraft:block/anvil", atv_body["textures"]["frame"])
        atv_handles = self.read_json("assets/vehicle/models/item/atv_handles.json")
        self.assertEqual(11, len(atv_handles["elements"]))
        self.assertEqual("minecraft:block/black_concrete", atv_handles["textures"]["handles"])
        self.assertEqual("minecraft:block/stone", atv_handles["textures"]["frame_alt"])
        self.assertIn("assets/vehicle/sounds/entity/atv/engine.ogg", self.entries)

        mini_bike_body = self.read_json("assets/vehicle/models/item/mini_bike_body.json")
        self.assertEqual(33, len(mini_bike_body["elements"]))
        self.assertEqual("minecraft:block/red_concrete", mini_bike_body["textures"]["body"])
        mini_bike_handles = self.read_json("assets/vehicle/models/item/mini_bike_handles.json")
        self.assertEqual(16, len(mini_bike_handles["elements"]))
        self.assertEqual("minecraft:block/red_concrete", mini_bike_handles["textures"]["handles"])

        smart_car_body = self.read_json("assets/vehicle/models/item/smart_car_body.json")
        self.assertEqual(109, len(smart_car_body["elements"]))
        self.assertEqual("minecraft:block/cyan_concrete", smart_car_body["textures"]["base"])

        speed_boat_body = self.read_json("assets/vehicle/models/item/speed_boat_body.json")
        self.assertEqual(67, len(speed_boat_body["elements"]))
        self.assertEqual("minecraft:block/red_concrete", speed_boat_body["textures"]["base"])
        self.assertIn("assets/vehicle/sounds/entity/speed_boat/engine.ogg", self.entries)

        aluminum_boat_body = self.read_json("assets/vehicle/models/item/aluminum_boat_body.json")
        self.assertEqual(77, len(aluminum_boat_body["elements"]))
        # The released source's own texture map already resolves cleanly in modern vanilla
        # (see build_resource_pack.py); r34 reverted r31's substitution of an unrelated,
        # never-referenced "aluminum.png" file for the literal source values below.
        self.assertEqual("minecraft:block/white_concrete", aluminum_boat_body["textures"]["body"])
        self.assertEqual("minecraft:block/white_concrete", aluminum_boat_body["textures"]["particle"])
        # r35: "logo" now points at a dedicated textures/item/ copy instead of the
        # non-standard textures/model/ path, which Minecraft's default "blocks" sprite
        # atlas does not stitch sprites from (see SOURCE_POSITION_AUDIT.md r35 entry).
        self.assertEqual("vehicle:item/aluminum_boat_logo", aluminum_boat_body["textures"]["logo"])
        self.assertIn("assets/vehicle/textures/item/aluminum_boat_logo.png", self.entries)
        self.assertEqual("minecraft:block/anvil", aluminum_boat_body["textures"]["seat"])

        legal_angles = {-45.0, -22.5, 0.0, 22.5, 45.0}
        for model in (atv_body, atv_handles, mini_bike_body, mini_bike_handles,
                      smart_car_body, speed_boat_body, aluminum_boat_body):
            for element in model["elements"]:
                if "rotation" in element:
                    self.assertIn(element["rotation"]["angle"], legal_angles)

        for entry in ("assets/vehicle/items/atv_body.json", "assets/vehicle/items/atv_handles.json",
                     "assets/vehicle/items/mini_bike_body.json",
                     "assets/vehicle/items/mini_bike_handles.json",
                     "assets/vehicle/items/smart_car_body.json",
                     "assets/vehicle/items/speed_boat_body.json",
                     "assets/vehicle/items/aluminum_boat_body.json"):
            self.assertIn(entry, self.entries)

        sounds = self.read_json("assets/vehicle/sounds.json")
        self.assertIn("entity.atv.engine", sounds)
        self.assertEqual("vehicle:entity/atv/engine",
                         sounds["entity.atv.engine"]["sounds"][0]["name"])
        self.assertIn("entity.speed_boat.engine", sounds)
        self.assertEqual("vehicle:entity/speed_boat/engine",
                         sounds["entity.speed_boat.engine"]["sounds"][0]["name"])

    def test_bumper_car_and_shopping_cart_use_released_geometry_and_audio(self):
        bumper_car_body = self.read_json("assets/vehicle/models/item/bumper_car_body.json")
        self.assertEqual(33, len(bumper_car_body["elements"]))
        self.assertEqual("minecraft:block/white_concrete", bumper_car_body["textures"]["body"])
        self.assertIn("assets/vehicle/sounds/entity/bumper_car/engine.ogg", self.entries)
        self.assertIn("assets/vehicle/sounds/entity/bumper_car/bonk.ogg", self.entries)

        shopping_cart_body = self.read_json("assets/vehicle/models/item/shopping_cart_body.json")
        self.assertEqual(46, len(shopping_cart_body["elements"]))
        # r35: each of this body's mesh/logo decals now points at its own dedicated
        # textures/item/ copy instead of the non-standard textures/model/ path, which
        # Minecraft's default "blocks" sprite atlas does not stitch sprites from and
        # rendered as a missing-texture black/purple square in-game; see
        # SOURCE_POSITION_AUDIT.md r35 entry for the full root-cause analysis.
        self.assertEqual("vehicle:item/shopping_cart_logo", shopping_cart_body["textures"]["logo"])
        for texture in ("vehicle:item/shopping_cart_mesh_angled", "vehicle:item/shopping_cart_white_mesh",
                        "vehicle:item/shopping_cart_mesh", "vehicle:item/shopping_cart_mesh_angled_flipped",
                        "vehicle:item/shopping_cart_logo"):
            path = texture.split(":", 1)[1]
            self.assertIn(f"assets/vehicle/textures/{path}.png", self.entries)

        legal_angles = {-45.0, -22.5, 0.0, 22.5, 45.0}
        for model in (bumper_car_body, shopping_cart_body):
            for element in model["elements"]:
                if "rotation" in element:
                    self.assertIn(element["rotation"]["angle"], legal_angles)

        for entry in ("assets/vehicle/items/bumper_car_body.json",
                     "assets/vehicle/items/shopping_cart_body.json"):
            self.assertIn(entry, self.entries)

        sounds = self.read_json("assets/vehicle/sounds.json")
        self.assertIn("entity.bumper_car.bonk", sounds)
        self.assertEqual("vehicle:entity/bumper_car/bonk",
                         sounds["entity.bumper_car.bonk"]["sounds"][0]["name"])

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
