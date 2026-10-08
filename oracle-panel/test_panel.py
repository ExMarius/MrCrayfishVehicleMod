import importlib.util
import json
import os
import stat
import tempfile
import unittest
from pathlib import Path


PANEL_PATH = Path(__file__).with_name("panel.py")
SPEC = importlib.util.spec_from_file_location("oracle_panel", str(PANEL_PATH))
panel = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(panel)


class OraclePanelTest(unittest.TestCase):
    def test_password_hash_round_trip_and_rejects_wrong_password(self):
        definition = panel.password_hash("a sufficiently long password")
        self.assertTrue(panel.verify_password("a sufficiently long password", definition))
        self.assertFalse(panel.verify_password("wrong password", definition))
        self.assertEqual("pbkdf2-sha256", definition["algorithm"])
        self.assertGreaterEqual(definition["iterations"], 600000)

    def test_initial_auth_is_created_once_with_private_permissions(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.assertTrue(panel.initialize_auth(str(root)))
            auth = json.loads((root / "panel/auth.json").read_text())
            initial = (root / "panel/initial-password.txt").read_text().splitlines()
            password = initial[1].split(": ", 1)[1]
            self.assertEqual("admin", auth["username"])
            self.assertTrue(panel.verify_password(password, auth["password"]))
            self.assertEqual(0o600, stat.S_IMODE(os.stat(root / "panel/auth.json").st_mode))
            before = (root / "panel/auth.json").read_bytes()
            self.assertFalse(panel.initialize_auth(str(root)))
            self.assertEqual(before, (root / "panel/auth.json").read_bytes())

    def test_log_tail_removes_terminal_and_minecraft_format_codes(self):
        with tempfile.TemporaryDirectory() as temporary:
            log = Path(temporary) / "latest.log"
            log.write_bytes(b"old\n\x1b[31mred\x1b[0m\n\xc2\xa7agreen\n")
            result = panel.read_tail(str(log), maximum_lines=2)
            self.assertEqual("red\ngreen", result)

    def test_dashboard_contains_only_same_origin_endpoints(self):
        page = panel.dashboard_html("test-nonce")
        self.assertIn('nonce="test-nonce"', page)
        self.assertIn("/api/control", page)
        self.assertIn("/api/command", page)
        self.assertNotIn("http://", page)
        self.assertNotIn("https://", page)


if __name__ == "__main__":
    unittest.main()
