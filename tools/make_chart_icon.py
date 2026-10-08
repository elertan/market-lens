"""Generates the Market Lens button icon (a green and a red price line).

Usage: python3 tools/make_chart_icon.py src/main/resources/com/marketlens/ui/chart_icon.png [preview.png]
The optional preview is the same icon scaled 8x.
"""
import sys, zlib, struct

W, H = 26, 24
GREEN = (0x1E, 0xD6, 0x1E, 255)
RED = (0xF0, 0x40, 0x40, 255)
BLACK = (0, 0, 0, 255)

def line(px, x0, y0, x1, y1, color):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        for tx in (0, 1):  # 2x2 pen: thick in every direction
            for ty in (0, 1):
                if 0 <= x0 + tx < W and 0 <= y0 + ty < H:
                    px[y0 + ty][x0 + tx] = color
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy; x0 += sx
        if e2 <= dx:
            err += dx; y0 += sy

def polyline(px, pts, color):
    for (a, b) in zip(pts, pts[1:]):
        line(px, a[0], a[1], b[0], b[1], color)

def outline(px):
    out = [row[:] for row in px]
    for y in range(H):
        for x in range(W):
            if px[y][x][3] == 0 and any(
                0 <= x + dx < W and 0 <= y + dy < H and px[y + dy][x + dx][3] and px[y + dy][x + dx] != BLACK
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = BLACK
    return out

def png(path, px, scale=1):
    rows = b''
    for row in px:
        for _ in range(scale):
            rows += b'\x00' + b''.join(bytes(c) * 1 for c in row for _ in range(scale))
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    w, h = len(px[0]) * scale, len(px) * scale
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    data += chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)

px = [[(0, 0, 0, 0)] * W for _ in range(H)]
# Zigzag like a price chart; red follows green 8px lower, leaving a small gap.
SHAPE = [(1, 11), (5, 5), (9, 13), (14, 1), (18, 9), (23, 4)]
polyline(px, [(x, y + 8) for x, y in SHAPE], RED)
polyline(px, SHAPE, GREEN)
px = outline(px)
png(sys.argv[1], px)
if len(sys.argv) > 2:
    png(sys.argv[2], px, 8)
