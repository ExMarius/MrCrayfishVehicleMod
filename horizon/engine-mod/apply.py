#!/usr/bin/env python3
"""Lipește modul nostru de mașini (`vehicle.rs`) în arborele sursă Horizon.

Horizon nu are un API de plugin runtime: modurile native se compilează în engine. Noi ne
compilăm oricum engine-ul de la zero pentru ARM64, așa că lipim modulul direct în sursă.

Scriptul e idempotent și pică zgomotos dacă ancorele nu mai există (adică upstream a
schimbat semnătura vreunui hook) — mai bine un build roșu decât un mod care nu rulează.

Utilizare:  python3 horizon/engine-mod/apply.py <cale-sursa-horizon>
"""

import sys
from pathlib import Path

MODULE = Path(__file__).with_name("vehicle.rs")

# Ancora 1: declarația de modul. O adăugăm la finalul `game.rs` (ordinea itemilor în Rust
# nu contează), ca să nu depindem de poziția unei linii din lista alfabetică.
GAME_MOD_MARK = "\n// [vehicle-mod] modul nostru, lipit de horizon/engine-mod/apply.py\nmod vehicle;\n"

# Ancora 2: hook-ul de input al jucătorului. Inserăm apelul după dispatch-ul mod_api.
INPUT_ANCHOR = "        mod_api::dispatch_player_input(self, mod_api::mods(), player, name, flags);\n"
INPUT_CALL = "        super::vehicle::on_player_input(self, player, name, flags);\n"

# Ancora 3: hook-ul de tick de server.
TICK_ANCHOR = "        mod_api::dispatch_server_tick(self, mod_api::mods(), context, phase);\n"
TICK_CALL = "        super::vehicle::on_server_tick(self, context, phase);\n"

# Ancora 4: comenzile modului (inregistrare + rulare).
REGISTER_ANCHOR = (
    "        mod_api::dispatch_register_commands(self, mod_api::mods(), commands);\n"
)
REGISTER_CALL = "        super::vehicle::register_commands(self, commands);\n"
PERFORM_ANCHOR = (
    "        mod_api::dispatch_perform_command(self, mod_api::mods(), player, command, ran);\n"
)
PERFORM_CALL = "        super::vehicle::on_perform_command(self, player, command, ran);\n"


def patch_once(text: str, anchor: str, call: str, where: str) -> str:
    if call in text:
        return text  # deja aplicat
    if anchor not in text:
        raise SystemExit(f"EROARE: ancora nu exista in {where}: {anchor.strip()}")
    if text.count(anchor) != 1:
        raise SystemExit(f"EROARE: ancora apare de {text.count(anchor)} ori in {where}")
    return text.replace(anchor, anchor + call)


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    root = Path(sys.argv[1])
    game = root / "crates/horizon-client/src/game.rs"
    hooks = root / "crates/horizon-client/src/game/native_hooks.rs"
    dest = root / "crates/horizon-client/src/game/vehicle.rs"
    for path in (game, hooks):
        if not path.is_file():
            raise SystemExit(f"EROARE: lipseste {path}")

    dest.write_text(MODULE.read_text(encoding="utf-8"), encoding="utf-8")

    game_text = game.read_text(encoding="utf-8")
    if "mod vehicle;" not in game_text:
        game.write_text(game_text + GAME_MOD_MARK, encoding="utf-8")

    hooks_text = hooks.read_text(encoding="utf-8")
    hooks_text = patch_once(hooks_text, INPUT_ANCHOR, INPUT_CALL, str(hooks))
    hooks_text = patch_once(hooks_text, TICK_ANCHOR, TICK_CALL, str(hooks))
    hooks_text = patch_once(hooks_text, REGISTER_ANCHOR, REGISTER_CALL, str(hooks))
    hooks_text = patch_once(hooks_text, PERFORM_ANCHOR, PERFORM_CALL, str(hooks))
    hooks.write_text(hooks_text, encoding="utf-8")

    print("[apply] modul vehicle a fost lipit in engine")
    print(f"[apply] {dest}")
    print(f"[apply] {hooks}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
