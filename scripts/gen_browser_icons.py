#!/usr/bin/env python3
"""Generates the two browser filter icons NEU has no counterpart for:
zea_filter_general (the beacon — general goods) and zea_filter_earth
(the globe — the server's own items). Both are drawn in NEU's icon
language, sampled from NEU's own textures:

  inactive: transparent interior, white 1 px frame along the top and
            left, NEU's #21202c shadow along the bottom and right, the
            glyph in the frame gray #626264 with #151517 outlines and
            #48484a accents;
  active:   NEU's gold plate #e3b414 over the whole interior with the
            frame lighting inverted (white along the bottom and right,
            #21202c along the top and left), the glyph in #3b2c09 with
            #ab870f accents — the order_rarity_active precedent of
            keeping a hint of identity color applies to the globe
            (#36367c ocean, #5f7f37 land), matching the gem's palette.
"""
from PIL import Image
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/zea/textures/browser"

# NEU's palette, sampled from the order_* / sort_* textures.
WHITE = (255, 255, 255, 255)
SHADOW = (0x21, 0x20, 0x2C, 255)
OUTLINE = (0x15, 0x15, 0x17, 255)
BODY = (0x62, 0x62, 0x64, 255)
ACCENT = (0x48, 0x48, 0x4A, 255)
EYE = (0x9A, 0x9A, 0x9C, 255)
GOLD = (0xE3, 0xB4, 0x14, 255)
GOLD_DARK = (0x3B, 0x2C, 0x09, 255)
GOLD_ACCENT = (0xAB, 0x87, 0x0F, 255)
OCEAN_ACTIVE = (0x36, 0x36, 0x7C, 255)
LAND_ACTIVE = (0x5F, 0x7F, 0x37, 255)

# Glyph codes: '#' outline, 'g' body, 'a' accent, 'b' secondary body.
# '.' and ' ' are transparent.

BEACON = """
................
................
....#######.....
...#ggggggg#....
...#ggaAagg#....
...#ggaAagg#....
...#ggggggg#....
..##ggggggg##...
..#gggggggggg#..
..#gggggggggg#..
.#gggggggggggg#.
.#gggggggggggg#.
.#gggggggggggg#.
.##############.
................
................
"""

GLOBE = """
................
................
....########....
...#gggggggg#...
..#gaa#ggggab#..
..#gaa#ggggab#..
.#ggga#gggggab#.
.#gga#gggggaab#.
.#ggggggg##gaa#.
.#gggggg###aaa#.
..#gggga#agg#...
..#gg#gggggg#...
...#gggggggg#...
....########....
................
................
"""

# The globe's active variant keeps the earth identity colors, the
# order_rarity_active gem precedent: ocean blue, land green.
GLOBE_ACTIVE_COLORS = {
    "g": OCEAN_ACTIVE,
    "a": LAND_ACTIVE,
    "A": LAND_ACTIVE,
    "b": LAND_ACTIVE,
}

BEACON_ACTIVE_COLORS = {
    "g": GOLD_DARK,
    "a": GOLD_ACCENT,
    "b": GOLD_ACCENT,
}


def rows_of(map_text):
    rows = [r for r in map_text.split("\n") if r.strip()]
    assert len(rows) == 16, f"expected 16 rows, got {len(rows)}"
    for r in rows:
        assert len(r) == 16, f"expected 16 cols, got {len(r)}: {r!r}"
    return rows


def draw_frame(img, active):
    px = img.load()
    if not active:
        for x in range(16):
            px[x, 0] = WHITE
        for y in range(0, 16):
            px[0, y] = WHITE
        for x in range(1, 16):
            px[x, 15] = SHADOW
        for y in range(1, 15):
            px[15, y] = SHADOW
    else:
        for x in range(0, 15):
            px[x, 0] = SHADOW
        for y in range(1, 15):
            px[0, y] = SHADOW
        for y in range(0, 16):
            px[15, y] = WHITE
        for x in range(0, 16):
            px[x, 15] = WHITE


def emit(name, map_text, body_colors, active_colors):
    rows = rows_of(map_text)
    inactive = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = inactive.load()
    for y in range(16):
        for x in range(16):
            code = rows[y][x]
            if code in body_colors:
                px[x, y] = body_colors[code]
    draw_frame(inactive, active=False)
    inactive.save(OUT / f"{name}.png")

    active = Image.new("RGBA", (16, 16), GOLD)
    px = active.load()
    for y in range(16):
        for x in range(16):
            code = rows[y][x]
            if code in active_colors:
                px[x, y] = active_colors[code]
    draw_frame(active, active=True)
    active.save(OUT / f"{name}_active.png")
    print("wrote", name, "+ active")


BEACON_INACTIVE = {"#": OUTLINE, "g": BODY, "a": EYE, "A": EYE, "b": ACCENT}
BEACON_ACTIVE = {"#": GOLD_DARK, "g": GOLD_DARK, "a": GOLD_ACCENT, "A": GOLD_ACCENT, "b": GOLD_ACCENT}
GLOBE_INACTIVE = {"#": OUTLINE, "g": BODY, "a": ACCENT, "A": ACCENT, "b": ACCENT}

emit("zea_filter_general", BEACON, BEACON_INACTIVE, BEACON_ACTIVE)
emit("zea_filter_earth", GLOBE, GLOBE_INACTIVE, GLOBE_ACTIVE_COLORS)
