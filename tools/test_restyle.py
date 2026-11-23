"""Tests for the badge/PNG helpers in tools/restyle.py.

The resource pack must stay reproducible (its SHA-1 is pinned in config.yml and
the workflow), and the badge must really be ours: drawn from scratch, fully
deterministic, with transparency instead of a square background.
"""

import unittest

from tools import restyle


class EmblemTest(unittest.TestCase):
    def test_emblem_is_a_valid_reproducible_png(self):
        data = restyle.emblem_png(32)
        self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
        self.assertEqual(data, restyle.emblem_png(32))

    def test_emblem_has_transparent_background_and_drawn_ink(self):
        data = restyle.emblem_png(32)
        width, height, rgba = decode(data)
        self.assertEqual((width, height), (32, 32))
        opaque = sum(1 for i in range(3, len(rgba), 4) if rgba[i] > 0)
        self.assertGreater(opaque, 100)             # ring + chevron drawn
        self.assertLess(opaque, 32 * 32 - 50)       # not a filled square
        self.assertEqual(rgba[3], 0)                # top-left corner is empty

    def test_emblem_uses_the_requested_accent(self):
        data = restyle.emblem_png(32, "#12AB34")
        _, _, rgba = decode(data)
        reds = [i for i in range(0, len(rgba), 4)
                if rgba[i] == 0x12 and rgba[i + 1] == 0xAB and rgba[i + 2] == 0x34]
        self.assertTrue(reds)

    def test_png_round_trip(self):
        pixels = [(1, 2, 3, 4), (250, 251, 252, 255)]
        data = restyle.encode_png(2, 1, b"".join(bytes(p) for p in pixels))
        width, height, rgba = decode(data)
        self.assertEqual((width, height), (2, 1))
        self.assertEqual(bytes(rgba), b"\x01\x02\x03\x04\xfa\xfb\xfc\xff")

    def test_decoder_rejects_non_png(self):
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "bad.png"
            path.write_bytes(b"not a png at all")
            with self.assertRaises(RuntimeError):
                restyle.decode_png(path)


def decode(data):
    import tempfile
    from pathlib import Path

    with tempfile.TemporaryDirectory() as temporary:
        path = Path(temporary) / "probe.png"
        path.write_bytes(data)
        return restyle.decode_png(path)


if __name__ == "__main__":
    unittest.main()
