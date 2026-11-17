#!/usr/bin/env python3
"""Generates ZEA's enchant-indicator bitmap font.

Minecraft 26.3 dropped unifont, so the default font no longer covers the
special-enchantment codepoints ZEA's indicator row uses. This script
draws the missing glyphs as 8x8 monochrome white masks — the same cell
size, ascent and hand-drawn density as the emoji vanilla still ships in
nonlatin_european.png — and writes them as one texture strip plus the
bitmap font JSON that registers it.

Run from the repository root:
    python3 scripts/gen_enchant_font.py [--preview]
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image

# The glyphs, in the order the font texture lays them out. This order
# MUST stay in sync with the "chars" row written into the JSON below and
# with the FontSource.ZEA membership pinned by the tests.
GLYPHS: dict[str, list[str]] = {
    # Mending — the recycle triangle of three chasing arrows.
    "mending": [
        "...##...",
        "..#..#..",
        ".#....#.",
        ".#....##",
        "#.....##",
        "##......",
        ".######.",
        "........",
    ],
    # Silk Touch — a crystal ball on its stand, sparkle inside.
    "silk_touch": [
        "..####..",
        ".#.#..#.",
        "#..##..#",
        "#..##..#",
        ".#....#.",
        "..####..",
        "...##...",
        "..####..",
    ],
    # Flame — an impact starburst.
    "flame": [
        "#..#..#.",
        ".#.#.#..",
        "..###...",
        ".#####..",
        "..###...",
        ".#.#.#..",
        "#..#..#.",
        "........",
    ],
    # Piercing — a target: ring, center dot.
    "piercing": [
        "..####..",
        ".#....#.",
        "#..##..#",
        "#..##..#",
        "#..##..#",
        ".#....#.",
        "..####..",
        "........",
    ],
    # Wind Burst — the dash: one tapered streak flying left.
    "wind_burst": [
        ".....###",
        "....###.",
        "...###..",
        "..###...",
        ".###....",
        "###.....",
        "##......",
        "........",
    ],
    # Respiration — rising bubbles: big, medium, speck.
    "respiration": [
        "..##....",
        ".#..#...",
        "..##....",
        ".....##.",
        "....#..#",
        ".....##.",
        ".#......",
        "........",
    ],
    # Soul Speed — a runner at full stride.
    "soul_speed": [
        "....#...",
        "...##...",
        "..#####.",
        "...##...",
        "...#.#..",
        "..#...#.",
        ".#.....#",
        "........",
    ],
    # Swift Sneak — a pair of footprints, one behind the other.
    "swift_sneak": [
        "#.#.#...",
        ".###....",
        ".###....",
        ".###.#..",
        "..#..##.",
        "....##..",
        ".....#..",
        "........",
    ],
}

# Registry key paths, in the order of SpecialEnchantments' registry —
# only the ZEA-font members, since those are the ones this font draws.
ORDER = [
    "mending",
    "silk_touch",
    "flame",
    "piercing",
    "wind_burst",
    "respiration",
    "soul_speed",
    "swift_sneak",
]

CELL = 8
ASCENT = 7

ROOT = Path(__file__).resolve().parent.parent
TEXTURE = ROOT / "src/main/resources/assets/zea/textures/font/enchant_symbols.png"
FONT_JSON = ROOT / "src/main/resources/assets/zea/font/enchant_indicators.json"


def preview() -> None:
    for key in ORDER:
        print(f"== {key}")
        for row in GLYPHS[key]:
            print(row)


def build() -> None:
    width = CELL * len(ORDER)
    image = Image.new("RGBA", (width, CELL), (0, 0, 0, 0))
    for index, key in enumerate(ORDER):
        rows = GLYPHS[key]
        if len(rows) != CELL or any(len(row) != CELL for row in rows):
            raise SystemExit(f"glyph {key} is not exactly {CELL}x{CELL}")
        for y, row in enumerate(rows):
            for x, cell in enumerate(row):
                if cell == "#":
                    image.putpixel((index * CELL + x, y), (255, 255, 255, 255))
                elif cell != ".":
                    raise SystemExit(f"glyph {key} has unknown cell {cell!r}")
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    image.save(TEXTURE)
    print(f"wrote {TEXTURE} ({width}x{CELL})")

    # One row of astral-plane-friendly codepoints; the JSON is UTF-8 and
    # Minecraft's bitmap provider counts codepoints, not UTF-16 units.
    chars = "".join(
        {
            "mending": "\u267b",
            "silk_touch": "\U0001f52e",
            "flame": "\U0001f4a5",
            "piercing": "\U0001f3af",
            "wind_burst": "\U0001f4a8",
            "respiration": "\U0001fae7",
            "soul_speed": "\U0001f3c3",
            "swift_sneak": "\U0001f463",
        }[key]
        for key in ORDER
    )
    provider = {
        "type": "bitmap",
        "file": "zea:font/enchant_symbols.png",
        "ascent": ASCENT,
        "chars": [chars],
    }
    FONT_JSON.parent.mkdir(parents=True, exist_ok=True)
    FONT_JSON.write_text(
        json.dumps({"providers": [provider]}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {FONT_JSON} (chars: {chars})")


def main() -> None:
    if "--preview" in sys.argv:
        preview()
        return
    build()


if __name__ == "__main__":
    main()
