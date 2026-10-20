"""Generates the AI Kart body texture (64x64) — fully AI-authored artwork.

Simple blockchain of flat colour regions that the model's UV coordinates point
into. Run once to (re)create textures/vehicle/ai_kart/base.png.
"""
from __future__ import annotations

from PIL import Image

SIZE = 64

RED = (196, 33, 40, 255)        # primary body
RED_DARK = (148, 22, 27, 255)   # shading strip / pods undersides
BLACK = (18, 20, 24, 255)       # seat, crash bar, wing supports
WHITE = (240, 240, 240, 255)    # racing stripe
GRAY = (44, 47, 51, 255)        # floor pan
YELLOW = (242, 194, 48, 255)    # accent (nose badge)


def main() -> None:
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()

    def fill(x0: int, y0: int, x1: int, y1: int, colour: tuple[int, int, int, int]) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[x, y] = colour

    # Colour palette regions (coordinates are inclusive patches the model UVs sample).
    fill(0, 0, 31, 31, RED)          # body red
    fill(0, 40, 31, 47, RED_DARK)    # darker red shading strip
    fill(32, 0, 47, 15, BLACK)       # seat/crash bar black
    fill(48, 0, 63, 15, WHITE)       # racing-stripe white
    fill(32, 24, 47, 39, GRAY)       # floor pan dark grey
    fill(48, 16, 55, 23, YELLOW)     # small yellow accent

    # Give the red slab a subtle two-tone gradient so large faces don't render
    # perfectly flat: darker toward the bottom half of the region.
    for y in range(16, 32):
        shade = 1.0 - (y - 16) * 0.006
        r = int(RED[0] * shade)
        g = int(RED[1] * shade)
        b = int(RED[2] * shade)
        for x in range(0, 32):
            px[x, y] = (r, g, b, RED[3])

    import os
    out = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "src/main/resources/assets/vehicle/textures/vehicle/ai_kart/base.png",
    )
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
