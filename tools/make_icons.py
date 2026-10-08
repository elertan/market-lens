"""Generates the Market Lens icons in an OSRS-like pixel style: shaded colours and a dark outline.

Usage: python3 tools/make_icons.py [preview-dir]
Writes chart_icon.png (GE button), chart_icon_small.png (title bar), expand_icon.png and the chart type
icons line_icon.png and candle_icon.png to the plugin resources, and icon.png (the Plugin Hub icon: the chart icon at 2x) to the repository root.
With a preview directory, also writes every icon scaled 8x there.

The drawing helpers are shared with tools/icon_concepts.py, which keeps the other icon designs.
"""
import math
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / 'src/main/resources/com/marketlens/ui'
# The Plugin Hub shows icon.png from the repository root, at most 48x72.
HUB_ICON_SCALE = 2

# Colours: (light, base) pairs are shaded light on top and darker below, like OSRS icons.
OUTLINE = (0x14, 0x10, 0x0A)
GREEN = ((0x8C, 0xD8, 0x4A), (0x3A, 0x9A, 0x22))
RED = ((0xF0, 0x86, 0x5C), (0xB0, 0x2E, 0x1E))
STEEL = ((0xD8, 0xD8, 0xCC), (0x84, 0x84, 0x7A))
GOLD = ((0xFA, 0xD8, 0x4C), (0xC0, 0x88, 0x10))
WOOD = ((0xB0, 0x7C, 0x44), (0x6A, 0x44, 0x20))
GLASS = (0x2E, 0x4C, 0x50)
GLASS_SHINE = (0x6E, 0x96, 0x96)
# OSRS interface orange, as used by the timeframe tab labels.
ORANGE = (0xFF, 0x98, 0x1F)


class Canvas:
    """A small pixel grid with drawing helpers. Call finish() last to add the dark outline."""

    def __init__(self, width, height):
        self.w, self.h = width, height
        self.px = [[None] * width for _ in range(height)]

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def set(self, x, y, colour):
        if self.inside(x, y):
            self.px[y][x] = colour

    def rect(self, x0, y0, x1, y1, shade):
        """Filled box: light top row, base colour below."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, shade[0] if y == y0 else shade[1])

    def disc(self, cx, cy, r, colour):
        for y in range(self.h):
            for x in range(self.w):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.set(x, y, colour)

    def ring(self, cx, cy, r, shade):
        """1-px circle, lit from the top-left."""
        for y in range(self.h):
            for x in range(self.w):
                if r - 1 < math.hypot(x - cx, y - cy) <= r + 0.5:
                    self.set(x, y, shade[0] if (x - cx) + (y - cy) < 0 else shade[1])

    def line(self, points, shade, pen=2, clip=None):
        """Polyline, {pen} px tall. With pen >= 2 each column's top pixel is light and the rest base."""
        mask = [[False] * self.w for _ in range(self.h)]
        for (x0, y0), (x1, y1) in zip(points, points[1:]):
            dx, dy = abs(x1 - x0), -abs(y1 - y0)
            sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
            err = dx + dy
            while True:
                for t in range(pen):
                    if self.inside(x0, y0 + t):
                        mask[y0 + t][x0] = True
                if (x0, y0) == (x1, y1):
                    break
                e2 = 2 * err
                if e2 >= dy:
                    err += dy
                    x0 += sx
                if e2 <= dx:
                    err += dx
                    y0 += sy
        for y in range(self.h):
            for x in range(self.w):
                if mask[y][x] and (clip is None or clip(x, y)):
                    top = y == 0 or not mask[y - 1][x]
                    self.set(x, y, shade[0] if (top or pen == 1) else shade[1])
        return mask

    def finish(self):
        """Adds a dark 1-px outline around every shape and returns the pixel rows."""
        out = [row[:] for row in self.px]
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x] is None and any(
                        self.inside(x + a, y + b) and self.px[y + b][x + a] not in (None, OUTLINE)
                        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out[y][x] = OUTLINE
        self.px = out
        return out


def magnifier(c, cx, cy, r, contents, handle_from, handle_to, handle_pen):
    """Glass with clipped contents, a shine, a steel rim, a steel collar and a wooden handle."""
    c.disc(cx, cy, r, GLASS)
    contents(lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= (r - 1) ** 2)
    c.set(cx - r + 2, cy - r + 2, GLASS_SHINE)
    if r > 4:
        c.set(cx - r + 3, cy - r + 2, GLASS_SHINE)
        c.set(cx - r + 2, cy - r + 3, GLASS_SHINE)
    c.ring(cx, cy, r, STEEL)
    # The collar joins the rim to the handle, so the magnifier reads as one piece.
    collar = (handle_from[0] - 1, handle_from[1] - 1)
    c.line([collar, handle_from], STEEL, pen=handle_pen)
    c.line([handle_from, handle_to], WOOD, pen=handle_pen)


def chart_icon():
    """The Market Lens icon: a magnifier over buy (green) and sell (red) price lines."""
    c = Canvas(20, 18)
    magnifier(c, 7, 7, 6, lambda clip: (
        c.line([(2, 8), (5, 5), (7, 7), (10, 3), (12, 5)], GREEN, pen=1, clip=clip),
        c.line([(2, 11), (5, 9), (7, 11), (10, 8), (12, 10)], RED, pen=1, clip=clip)),
        handle_from=(12, 12), handle_to=(17, 16), handle_pen=3)
    return c.finish()


def chart_icon_small():
    """Title-bar detail: the same magnifier at 12x12, with only the green line inside."""
    c = Canvas(12, 12)
    magnifier(c, 4, 4, 4, lambda clip: c.line([(1, 5), (3, 3), (4, 4), (7, 2)], GREEN, pen=1, clip=clip),
        handle_from=(8, 8), handle_to=(10, 10), handle_pen=2)
    return c.finish()


def expand_icon(size=13, arm=4):
    """Four L-shaped corners pointing outwards: the usual "expand to full screen" symbol."""
    c = Canvas(size, size)
    lo, hi = 1, size - 2  # leave a 1px margin for the outline
    for cx in (lo, hi):
        for cy in (lo, hi):
            step_x = 1 if cx == lo else -1
            step_y = 1 if cy == lo else -1
            for i in range(arm):
                c.set(cx + i * step_x, cy, ORANGE)  # horizontal arm
                c.set(cx, cy + i * step_y, ORANGE)  # vertical arm
    return c.finish()


def line_icon():
    """Chart type toggle: a line chart."""
    c = Canvas(13, 13)
    c.line([(1, 9), (3, 6), (5, 8), (8, 3), (11, 5)], GREEN)
    return c.finish()


def candle_icon():
    """Chart type toggle: candlesticks."""
    c = Canvas(13, 13)
    for x, top, bottom, wick_top, wick_bottom, shade in [
            (1, 6, 9, 4, 11, RED), (5, 3, 7, 1, 9, GREEN), (9, 4, 6, 2, 9, GREEN)]:
        c.line([(x + 1, wick_top), (x + 1, wick_bottom)], shade, pen=1)
        c.rect(x, top, x + 2, bottom, shade)
    return c.finish()


def write_png(path, pixels, scale=1):
    """RGBA PNG; empty pixels are transparent."""
    raw = b''
    for row in pixels:
        line = b''.join((bytes(c) + b'\xff' if c else b'\x00\x00\x00\x00') * scale for c in row)
        raw += (b'\x00' + line) * scale

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

    header = struct.pack('>IIBBBBB', len(pixels[0]) * scale, len(pixels) * scale, 8, 6, 0, 0, 0)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def main():
    icons = {'chart_icon': chart_icon(), 'chart_icon_small': chart_icon_small(), 'expand_icon': expand_icon(),
        'line_icon': line_icon(), 'candle_icon': candle_icon()}
    for name, pixels in icons.items():
        write_png(RESOURCES / f'{name}.png', pixels)
        if len(sys.argv) > 1:
            write_png(Path(sys.argv[1]) / f'{name}.png', pixels, 8)
    write_png(ROOT / 'icon.png', icons['chart_icon'], HUB_ICON_SCALE)


if __name__ == '__main__':
    main()
