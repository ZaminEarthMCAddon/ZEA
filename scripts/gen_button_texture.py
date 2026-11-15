#!/usr/bin/env python3
"""Generates ZEA's inventory-button background sheet and icon glyphs.

buttons.png: five 18x18 vanilla-styled button faces in a 90x18 strip.
Each face: a base fill, a one-pixel border, a top/left highlight and a
bottom/right shadow - the idiom vanilla's own widgets speak - over five
palettes: stone, dark stone, dirt, gold and obsidian.

icons/*.png: 16x16 glyphs for the custom icon set (cross, check marks,
question mark, settings cog, magnifier, chestplate, paw, book, ring).
All pixels are ZEA's own.
"""
from PIL import Image, ImageDraw
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/zea/textures"

PALETTES = [
    # (border, base, light, dark) - stone
    ((0x5A, 0x5A, 0x78), (0xC8, 0xC8, 0xD0), (0xFF, 0xFF, 0xFF), (0x3A, 0x3A, 0x50)),
    # dark stone
    ((0x28, 0x28, 0x3C), (0x6E, 0x6E, 0x84), (0x9E, 0x9E, 0xB4), (0x14, 0x14, 0x22)),
    # dirt
    ((0x56, 0x3D, 0x2A), (0x8A, 0x66, 0x47), (0xA5, 0x7F, 0x5C), (0x38, 0x26, 0x18)),
    # gold
    ((0x8A, 0x6A, 0x1E), (0xE8, 0xC0, 0x4A), (0xFF, 0xE3, 0x85), (0x5A, 0x42, 0x0E)),
    # obsidian
    ((0x14, 0x0F, 0x1E), (0x32, 0x27, 0x42), (0x4C, 0x3D, 0x62), (0x08, 0x05, 0x10)),
]

def button_face(draw, x0, palette):
    border, base, light, dark = palette
    s = 18
    # border ring
    draw.rectangle([x0, 0, x0 + s - 1, s - 1], fill=border)
    # base fill inside
    draw.rectangle([x0 + 1, 1, x0 + s - 2, s - 2], fill=base)
    # top/left highlight
    draw.rectangle([x0 + 1, 1, x0 + s - 2, 1], fill=light)
    draw.rectangle([x0 + 1, 1, x0 + 1, s - 2], fill=light)
    # bottom/right shadow
    draw.rectangle([x0 + 1, s - 2, x0 + s - 2, s - 2], fill=dark)
    draw.rectangle([x0 + s - 2, 1, x0 + s - 2, s - 2], fill=dark)
    # inner bevel softening: a one-pixel-inset mid line on top and left
    draw.point([x0 + 2, 2], fill=light)
    draw.point([x0 + 2, s - 3], fill=dark)

def gen_buttons():
    sheet = Image.new("RGBA", (90, 18), (0, 0, 0, 0))
    draw = ImageDraw.Draw(sheet)
    for i, palette in enumerate(PALETTES):
        button_face(draw, i * 18, palette)
    path = OUT / "gui" / "buttons.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    print("wrote", path)

WHITE = (255, 255, 255, 255)
GREEN = (85, 255, 85, 255)
GRAY = (200, 200, 200, 255)

def glyph_canvas():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)

def gen_cross():
    img, d = glyph_canvas()
    for i in range(3, 13):
        d.point([i, i], fill=WHITE)
        d.point([i, 15 - i], fill=WHITE)
    return img

def gen_check(color):
    img, d = glyph_canvas()
    for i in range(7):
        d.point([3 + i, 8 + i // 2], fill=color)
    for i in range(6):
        d.point([8 + i, 11 - i], fill=color)
    return img

def gen_question():
    img, d = glyph_canvas()
    d.rectangle([5, 2, 9, 3], fill=GRAY)
    d.rectangle([4, 4, 5, 5], fill=GRAY)
    d.rectangle([9, 4, 10, 7], fill=GRAY)
    d.rectangle([7, 8, 9, 9], fill=GRAY)
    d.rectangle([7, 11, 8, 12], fill=GRAY)
    return img

def gen_settings():
    img, d = glyph_canvas()
    d.rectangle([4, 3, 11, 12], outline=GRAY, width=2)
    d.rectangle([6, 6, 9, 9], fill=(60, 60, 70, 255))
    for t in range(4):
        d.rectangle([5 + t * 2, 1, 6 + t * 2, 2], fill=GRAY)
    return img

def gen_search():
    img, d = glyph_canvas()
    d.ellipse([2, 2, 9, 9], outline=GRAY, width=2)
    d.line([9, 9, 13, 13], fill=GRAY, width=2)
    return img

def gen_armor():
    img, d = glyph_canvas()
    d.rectangle([4, 2, 11, 3], fill=GRAY)
    d.rectangle([3, 4, 12, 9], fill=GRAY)
    d.rectangle([4, 10, 11, 12], fill=GRAY)
    d.rectangle([7, 4, 8, 6], fill=(30, 30, 40, 255))
    return img

def gen_pet():
    img, d = glyph_canvas()
    d.ellipse([4, 6, 11, 13], fill=(150, 110, 70, 255))
    d.ellipse([3, 2, 6, 5], fill=(150, 110, 70, 255))
    d.ellipse([9, 2, 12, 5], fill=(150, 110, 70, 255))
    d.point([6, 9], fill=(20, 20, 20, 255))
    d.point([9, 9], fill=(20, 20, 20, 255))
    return img

def gen_recipe():
    img, d = glyph_canvas()
    d.rectangle([3, 2, 12, 13], outline=GRAY, width=2)
    d.line([3, 7, 12, 7], fill=GRAY, width=1)
    d.line([7, 2, 7, 13], fill=GRAY, width=1)
    return img

def gen_baubles():
    img, d = glyph_canvas()
    d.ellipse([3, 6, 12, 13], outline=(230, 180, 60, 255), width=2)
    d.rectangle([7, 2, 8, 5], fill=(230, 180, 60, 255))
    return img

def main():
    gen_buttons()
    icons = {
        "cross": gen_cross(),
        "green_check": gen_check(GREEN),
        "white_check": gen_check(WHITE),
        "question": gen_question(),
        "settings": gen_settings(),
        "search": gen_search(),
        "armor": gen_armor(),
        "pet": gen_pet(),
        "recipe": gen_recipe(),
        "baubles": gen_baubles(),
    }
    icon_dir = OUT / "icons"
    icon_dir.mkdir(parents=True, exist_ok=True)
    for name, img in icons.items():
        path = icon_dir / f"{name}.png"
        img.save(path)
        print("wrote", path)

if __name__ == "__main__":
    main()
