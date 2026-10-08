"""Generates the Market Lens icons: a green and a red price line, shaded like OSRS icons.

Usage: python3 tools/make_chart_icon.py src/main/resources/com/marketlens/ui [preview-dir]
Writes chart_icon.png (button) and chart_icon_small.png (title bar). Previews are scaled 8x.
"""
import struct
import sys
import zlib

# Muted, shaded colours as used in OSRS icons: highlight on top, base below, dark outline.
GREEN = {'light': (0x8C, 0xD8, 0x4A), 'base': (0x3A, 0x9A, 0x22)}
RED = {'light': (0xF0, 0x86, 0x5C), 'base': (0xB0, 0x2E, 0x1E)}
OUTLINE = (0x14, 0x10, 0x0A)

# name -> (width, height, green zigzag like a price chart, how far lower the red line runs)
VARIANTS = {
    # Same size as the GE's own small icons (search, guide price).
    'chart_icon': (20, 18, [(1, 8), (5, 4), (8, 9), (12, 1), (15, 6), (18, 3)], 6),
    'chart_icon_small': (16, 16, [(1, 7), (4, 4), (7, 8), (10, 1), (12, 5), (14, 2)], 6),
}


def plot_line(mask, x0, y0, x1, y1):
    H, W = len(mask), len(mask[0])
    """Bresenham line, 2px tall: the top pixel is shaded light, the lower one dark."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        for ty in (0, 1):
            if 0 <= y0 + ty < H:
                mask[y0 + ty][x0] = True
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def line_mask(points, W, H):
    mask = [[False] * W for _ in range(H)]
    for a, b in zip(points, points[1:]):
        plot_line(mask, a[0], a[1], b[0], b[1])
    return mask


def paint(pixels, mask, colours):
    """Top pixel of each column run gets the highlight, the rest the base colour."""
    H, W = len(pixels), len(pixels[0])
    for y in range(H):
        for x in range(W):
            if mask[y][x]:
                top = y == 0 or not mask[y - 1][x]
                pixels[y][x] = colours['light'] if top else colours['base']


def add_outline(pixels):
    H, W = len(pixels), len(pixels[0])
    out = [row[:] for row in pixels]
    for y in range(H):
        for x in range(W):
            if pixels[y][x] is None and any(
                    0 <= x + dx < W and 0 <= y + dy < H and pixels[y + dy][x + dx] not in (None, OUTLINE)
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = OUTLINE
    return out


def write_png(path, pixels, scale=1):
    raw = b''
    for row in pixels:
        line = b''.join((bytes(c) + b'\xff' if c else b'\x00\x00\x00\x00') * scale for c in row)
        raw += (b'\x00' + line) * scale

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

    header = struct.pack('>IIBBBBB', len(pixels[0]) * scale, len(pixels) * scale, 8, 6, 0, 0, 0)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


for name, (width, height, shape, red_offset) in VARIANTS.items():
    pixels = [[None] * width for _ in range(height)]
    paint(pixels, line_mask([(x, y + red_offset) for x, y in shape], width, height), RED)
    paint(pixels, line_mask(shape, width, height), GREEN)
    pixels = add_outline(pixels)

    write_png(f'{sys.argv[1]}/{name}.png', pixels)
    if len(sys.argv) > 2:
        write_png(f'{sys.argv[2]}/{name}.png', pixels, 8)
