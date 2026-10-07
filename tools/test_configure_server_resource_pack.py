import re
import unittest

from tools.configure_server_resource_pack import plugin_pack, synchronize


CONFIG = """\
resource-pack:
  url: "https://example.invalid/vehicle-r17.zip"
  sha1: "0123456789abcdef0123456789abcdef01234567"
  required: true
  prompt: "Vehicle pack required."

physics:
  global-speed-limit: 100.0
"""


class ConfigureServerResourcePackTest(unittest.TestCase):
    def test_reads_validated_plugin_values_and_stable_id(self):
        values = plugin_pack(CONFIG)

        self.assertEqual("https://example.invalid/vehicle-r17.zip", values["resource-pack"])
        self.assertEqual("0123456789abcdef0123456789abcdef01234567", values["resource-pack-sha1"])
        self.assertEqual("true", values["require-resource-pack"])
        self.assertEqual('{"text":"Vehicle pack required."}', values["resource-pack-prompt"])
        self.assertRegex(values["resource-pack-id"], re.compile(
            r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))
        self.assertEqual(values["resource-pack-id"], plugin_pack(CONFIG)["resource-pack-id"])

    def test_updates_only_pack_properties_and_removes_duplicates(self):
        original = """\
#Minecraft server properties
online-mode=false
resource-pack=https://old.invalid/old.zip
resource-pack-sha1=
resource-pack=https://duplicate.invalid/old.zip
motd=Vehicle test
"""
        result = synchronize(original, plugin_pack(CONFIG))

        self.assertIn("online-mode=false\n", result)
        self.assertIn("motd=Vehicle test\n", result)
        lines = result.splitlines()
        self.assertEqual(1, sum(line.startswith("resource-pack=") for line in lines))
        self.assertEqual(1, sum(line.startswith("resource-pack-sha1=") for line in lines))
        self.assertEqual(1, sum(line.startswith("resource-pack-id=") for line in lines))
        self.assertEqual(1, sum(line.startswith("resource-pack-prompt=") for line in lines))
        self.assertEqual(1, sum(line.startswith("require-resource-pack=") for line in lines))
        self.assertIn("resource-pack=https://example.invalid/vehicle-r17.zip\n", result)
        self.assertIn("require-resource-pack=true\n", result)

    def test_rejects_invalid_hash(self):
        with self.assertRaisesRegex(ValueError, "40 hexadecimal"):
            plugin_pack(CONFIG.replace(
                "0123456789abcdef0123456789abcdef01234567", "not-a-sha1"))


if __name__ == "__main__":
    unittest.main()
