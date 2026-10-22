import csv
import io
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WHEELS_CSV = ROOT / "tools" / "wheels.csv"
WHEEL_TYPE_JAVA = ROOT / "paper-plugin/src/main/java/com/mrcrayfish/vehicle/paper/mechanic/WheelType.java"
CONFIG_YML = ROOT / "paper-plugin/src/main/resources/config.yml"


def load_preset_rows():
    """Citeste tools/wheels.csv ignorand comentariile (#) si liniile goale."""
    raw_lines = []
    with WHEELS_CSV.open(encoding="utf-8") as handle:
        for line in handle:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            raw_lines.append(line)
    reader = csv.DictReader(io.StringIO("".join(raw_lines)))
    rows = [row for row in reader if row.get("key")]
    return rows


def wheel_enum_models():
    """Extrage perechile NUME -> model din enum-ul WheelType (fizica modului)."""
    source = WHEEL_TYPE_JAVA.read_text(encoding="utf-8")
    enum_block = source.split("public enum WheelType")[1].split(";")[0]
    return {name.strip(): model
            for name, model in re.findall(r'(\w+)\("(\w+)"', enum_block)}


def config_wheel_prices():
    """Cheile wheel_* si preturile lor din config.yml (mechanic.parts.prices)."""
    text = CONFIG_YML.read_text(encoding="utf-8")
    return {match.group(1): int(match.group(2))
            for match in re.finditer(r'^\s+wheel_(\w+):\s*(\d+)\s*$', text, re.MULTILINE)}


class WheelsPresetTest(unittest.TestCase):
    def test_csv_keys_are_case_insensitive_unique(self):
        keys = [row["key"] for row in load_preset_rows()]
        lowered = [key.lower() for key in keys]
        self.assertEqual(len(lowered), len(set(lowered)),
                         f"chei duplicate (case-insensitive) in wheels.csv: {keys}")

    def test_every_enum_wheel_appears_in_preset_with_same_model(self):
        enum_models = wheel_enum_models()
        self.assertTrue(enum_models, "enum WheelType gol sau negasit")
        preset = {row["key"].lower(): row for row in load_preset_rows()}
        for enum_name, model in enum_models.items():
            key = enum_name.lower()
            self.assertIn(key, preset, f"{enum_name} lipseste din wheels.csv")
            self.assertEqual(preset[key]["model"], model,
                             f"model diferit pentru {enum_name}: csv={preset[key]['model']} java={model}")

    def test_preset_prices_match_config_and_are_positive(self):
        prices = config_wheel_prices()
        self.assertTrue(prices, "niciun wheel_* in config.yml mechanic.parts.prices")
        for row in load_preset_rows():
            key = row["key"].lower()
            self.assertIn(key, prices, f"wheel_{key} lipseste din config.yml")
            csv_price = int(row["pret_baza"])
            self.assertGreater(csv_price, 0)
            self.assertEqual(csv_price, prices[key],
                             f"pret diferit pentru wheel_{key}: csv={csv_price} config={prices[key]}")


if __name__ == "__main__":
    unittest.main()
