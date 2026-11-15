#!/usr/bin/env python3
"""Harta de paritate: fiecare clasa Java a modului -> unde ajunge in portul Horizon.

Un port "unu la unu" se tine cu o lista, nu din memorie: pentru fiecare fisier din
`src/main/java` stim in ce stare e. Starea vine din `horizon/mod/parity.json`
(scris de mana, pe masura ce se porteaza), iar scriptul genereaza tabelul.

Stari:
  ported    - clasa are echivalent Rust, cu aceeasi logica
  partial   - o parte din ea e in Rust (ex: doar constantele)
  data      - e continut (model, textura, reteta), importat in `.hmod`
  render    - e randare: acoperita de `native_mob_submit` + modelele importate
  todo      - neinceput

Rulare:
    python3 horizon/tools/parity.py
"""
from __future__ import annotations

import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "src/main/java"
STATE = ROOT / "horizon/mod/parity.json"
OUT = ROOT / "horizon/mod/PARITY.md"

# Ce e deja acoperit, pe pachete intregi (o clasa de randare se acopera prin
# randarea nativa + modelele importate, nu prin rescrierea ei in Rust).
PACKAGE_DEFAULTS = {
    "com/mrcrayfish/vehicle/client/render": "render",
    "com/mrcrayfish/vehicle/client/model": "render",
    "com/mrcrayfish/vehicle/client/raytrace": "todo",
    "com/mrcrayfish/vehicle/client/screen": "todo",
    "com/mrcrayfish/vehicle/world": "data",
}


def classify(path: Path, states: dict[str, str]) -> str:
    key = str(path.relative_to(SRC))
    if key in states:
        return states[key]
    package = str(path.parent.relative_to(SRC)).replace("\\", "/")
    for prefix, state in PACKAGE_DEFAULTS.items():
        if package.startswith(prefix):
            return state
    return "todo"


def main() -> None:
    states = json.loads(STATE.read_text()) if STATE.is_file() else {}
    files = sorted(p for p in SRC.rglob("*.java"))
    by_package: dict[str, list[tuple[str, str]]] = defaultdict(list)
    counts: Counter[str] = Counter()
    for path in files:
        package = str(path.parent.relative_to(SRC)).replace("/", ".")
        state = classify(path, states)
        by_package[package].append((path.stem, state))
        counts[state] += 1

    lines = [
        "# Paritate cu modul Java — generat de `horizon/tools/parity.py`",
        "",
        f"Clase Java in sursa: **{len(files)}**.",
        "",
        "| Stare | Clase | Inseamna |",
        "|---|---|---|",
        f"| `ported` | {counts['ported']} | logica e in Rust |",
        f"| `partial` | {counts['partial']} | o parte e in Rust |",
        f"| `render` | {counts['render']} | acoperita de `native_mob_submit` + modelele importate |",
        f"| `data` | {counts['data']} | continut importat in `.hmod` |",
        f"| `todo` | {counts['todo']} | neinceput |",
        "",
        "Starea se da in `horizon/mod/parity.json`; restul vine din `PACKAGE_DEFAULTS`",
        "din script. Nu modifica tabelul de mai jos de mana — ruleaza scriptul.",
        "",
    ]
    for package in sorted(by_package):
        lines.append(f"## `{package}`")
        lines.append("")
        lines.append("| Clasa | Stare |")
        lines.append("|---|---|")
        for name, state in by_package[package]:
            lines.append(f"| `{name}` | {state} |")
        lines.append("")
    OUT.write_text("\n".join(lines))
    print(f"{len(files)} clase -> {OUT.relative_to(ROOT)}")
    for state, count in counts.most_common():
        print(f"  {state:8} {count}")


if __name__ == "__main__":
    main()
