"""Icon concepts considered for Market Lens, kept for later use (1 = the icon in use).

Usage: python3 tools/icon_concepts.py
Writes each concept as a 20x18 PNG to tools/icon-concepts/, plus sheet.png with all of them at 8x
on the GE button colour, for comparing.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))  # find make_icons next to this script
from make_icons import (Canvas, GLASS, GLASS_SHINE, GOLD, GREEN, RED, STEEL, chart_icon, magnifier, write_png)

OUT = Path(__file__).resolve().parent / 'icon-concepts'
W, H = 20, 18
DARK_GREEN = (0x1E, 0x4A, 0x16)
EYE_WHITE = (0xE8, 0xE0, 0xD0)
BUTTON = (0x5A, 0x4E, 0x3E)
BACKGROUND = (0x20, 0x1C, 0x16)


def candlesticks():
    c = Canvas(W, H)
    for x, top, bottom, wick_top, wick_bottom, shade in [
            (2, 9, 13, 7, 15, RED), (6, 7, 11, 5, 13, GREEN), (10, 8, 11, 6, 13, RED), (14, 3, 8, 1, 10, GREEN)]:
        c.line([(x + 1, wick_top), (x + 1, wick_bottom)], shade, pen=1)
        c.rect(x, top, x + 2, bottom, shade)
    return c.finish()


def volume_and_price():
    c = Canvas(W, H)
    for x, h, shade in [(2, 3, RED), (5, 5, GREEN), (8, 2, RED), (11, 6, GREEN), (14, 4, GREEN), (17, 7, GREEN)]:
        c.rect(x, 16 - h, x + 1, 16, shade)
    c.line([(1, 8), (4, 6), (7, 8), (10, 4), (13, 5), (17, 1)], GOLD)
    return c.finish()


def up_down_arrows():
    c = Canvas(W, H)
    c.rect(4, 7, 6, 15, GREEN)
    for i in range(5):
        c.line([(5 - i, 2 + i), (5 + i, 2 + i)], GREEN, pen=1)
    c.rect(12, 2, 14, 10, RED)
    for i in range(5):
        c.line([(13 - i, 15 - i), (13 + i, 15 - i)], RED, pen=1)
    return c.finish()


def coins_and_rise():
    c = Canvas(W, H)
    for y in (14, 12, 10):
        c.rect(1, y, 7, y + 1, GOLD)
    c.line([(5, 8), (9, 9), (12, 5), (14, 6), (17, 1)], GREEN)
    return c.finish()


def area_and_live_tag():
    c = Canvas(W, H)
    mask = c.line([(1, 10), (4, 7), (7, 9), (10, 5), (13, 6), (15, 3)], GREEN)
    for x in range(1, 16):
        ys = [y for y in range(H) if mask[y][x]]
        if ys:
            for y in range(max(ys) + 1, 16):
                c.set(x, y, DARK_GREEN)
    c.rect(16, 2, 18, 4, RED)
    return c.finish()


def lens_over_candles():
    c = Canvas(W, H)

    def candles(clip):
        for x, top, bottom, shade in [(4, 6, 10, RED), (7, 4, 8, GREEN), (10, 3, 6, GREEN)]:
            for y in range(top, bottom + 1):
                for xx in (x, x + 1):
                    if clip(xx, y):
                        c.set(xx, y, shade[0] if y == top else shade[1])

    magnifier(c, 7, 7, 6, candles, handle_from=(12, 12), handle_to=(17, 16), handle_pen=3)
    return c.finish()


def price_tags():
    c = Canvas(W, H)
    for y, shade in ((3, GREEN), (10, RED)):
        c.rect(8, y, 17, y + 4, shade)
        for i in range(3):
            c.line([(7 - i, y + 2 - i), (7 - i, y + 2 + i)], shade, pen=1)
        for x in range(1, 5, 2):
            c.set(x, y + 2, shade[1])
    return c.finish()


def insight_eye():
    c = Canvas(W, H)
    for x in range(1, 19):
        half = int(round(5 * math.sin(math.pi * (x - 0.5) / 18.5)))
        for y in range(9 - half, 9 + half + 1):
            c.set(x, y, EYE_WHITE)
    c.disc(10, 9, 4, GLASS)
    c.line([(6, 10), (8, 8), (10, 10), (12, 7), (14, 8)], GREEN, pen=1,
        clip=lambda x, y: (x - 10) ** 2 + (y - 9) ** 2 <= 16)
    c.set(8, 7, GLASS_SHINE)
    return c.finish()


def crosshair():
    c = Canvas(W, H)
    c.line([(1, 12), (4, 9), (7, 11), (11, 5), (14, 7), (18, 3)], GREEN)
    for i in range(-4, 5):
        if abs(i) > 1:
            c.set(11 + i, 6, STEEL[0])
            c.set(11, 6 + i, STEEL[0])
    c.ring(11, 6, 3, RED)
    return c.finish()


CONCEPTS = [
    ('01-lens-over-chart', chart_icon),
    ('02-candlesticks', candlesticks),
    ('03-volume-and-price', volume_and_price),
    ('04-up-down-arrows', up_down_arrows),
    ('05-coins-and-rise', coins_and_rise),
    ('06-area-and-live-tag', area_and_live_tag),
    ('07-lens-over-candles', lens_over_candles),
    ('08-price-tags', price_tags),
    ('09-insight-eye', insight_eye),
    ('10-crosshair', crosshair),
]


def sheet(icons, scale=8, pad=16, columns=5, gap=8):
    """All icons side by side on the GE button colour, scaled up."""
    tile_w, tile_h = W * scale + 2 * pad, H * scale + 2 * pad
    rows = (len(icons) + columns - 1) // columns
    out = [[BACKGROUND] * (columns * tile_w + (columns + 1) * gap) for _ in range(rows * tile_h + (rows + 1) * gap)]
    for i, pixels in enumerate(icons):
        ox = gap + (i % columns) * (tile_w + gap)
        oy = gap + (i // columns) * (tile_h + gap)
        for y in range(tile_h):
            for x in range(tile_w):
                out[oy + y][ox + x] = BUTTON
        for y, row in enumerate(pixels):
            for x, colour in enumerate(row):
                if colour:
                    for a in range(scale):
                        for b in range(scale):
                            out[oy + pad + y * scale + a][ox + pad + x * scale + b] = colour
    return out


def main():
    OUT.mkdir(exist_ok=True)
    icons = []
    for name, draw in CONCEPTS:
        pixels = draw()
        write_png(OUT / f'{name}.png', pixels)
        icons.append(pixels)
    write_png(OUT / 'sheet.png', sheet(icons))


if __name__ == '__main__':
    main()
