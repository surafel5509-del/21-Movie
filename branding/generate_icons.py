#!/usr/bin/env python3
"""Generates the TorrentClaw Movies launcher icon + adaptive icon layers.

Run from the repository root:  python3 branding/generate_icons.py
Requires Pillow.  The script is deterministic so icons can be regenerated.
"""
from __future__ import annotations

import math
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
BRANDING = os.path.join(ROOT, "branding")

GOLD = (245, 179, 1, 255)
GOLD_DARK = (196, 138, 0, 255)
CREAM = (255, 248, 231, 255)
BG_CENTER = (26, 26, 36)
BG_EDGE = (7, 7, 11)

SS = 4  # supersampling factor


def radial_background(size: int) -> Image.Image:
    """Dark charcoal radial gradient used as the adaptive icon background."""
    small = 256
    img = Image.new("RGB", (small, small), BG_EDGE)
    px = img.load()
    cx = cy = small / 2
    max_d = math.hypot(cx, cy)
    for y in range(small):
        for x in range(small):
            d = math.hypot(x - cx, y - cy) / max_d
            t = min(1.0, d ** 1.15)
            px[x, y] = tuple(
                int(BG_CENTER[i] + (BG_EDGE[i] - BG_CENTER[i]) * t) for i in range(3)
            )
    return img.resize((size, size), Image.LANCZOS).convert("RGBA")


def rotate(points, angle_deg: float, cx: float, cy: float):
    """Rotate a list of (x, y) points around (cx, cy) by angle_deg."""
    a = math.radians(angle_deg)
    ca, sa = math.cos(a), math.sin(a)
    return [
        (cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca)
        for x, y in points
    ]


def draw_reel(d: ImageDraw.ImageDraw, cx: float, cy: float, r: float) -> None:
    """Solid gold reel: outer ring, perforated disc, hub and six spokes."""
    ring = r * 0.10
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=GOLD, width=int(max(2, ring)))

    # Solid gold disc that the holes are punched out of.
    disc = r * 0.80
    d.ellipse([cx - disc, cy - disc, cx + disc, cy + disc], fill=GOLD)

    # Six transparent film-windows.
    hole_r = r * 0.185
    orbit = r * 0.47
    for i in range(6):
        a = math.radians(-90 + i * 60)
        hx = cx + math.cos(a) * orbit
        hy = cy + math.sin(a) * orbit
        d.ellipse([hx - hole_r, hy - hole_r, hx + hole_r, hy + hole_r], fill=(0, 0, 0, 0))

    # Hub: transparent porthole with a gold pin, reading as a lens.
    hub = r * 0.30
    d.ellipse([cx - hub, cy - hub, cx + hub, cy + hub], fill=(0, 0, 0, 0))
    d.ellipse([cx - hub, cy - hub, cx + hub, cy + hub], outline=GOLD, width=int(max(2, r * 0.055)))
    pin = r * 0.10
    d.ellipse([cx - pin, cy - pin, cx + pin, cy + pin], fill=GOLD)


def draw_claw(d: ImageDraw.ImageDraw, cx: float, cy: float, r: float) -> None:
    """Three bold, tapered claw slashes raking across the reel (-38 degrees)."""
    angle = -36.0
    for offset, length, width in (
        (-0.62, 1.30, 0.165),
        (0.0, 1.72, 0.205),
        (0.58, 1.34, 0.17),
    ):
        ln = r * length
        wd = r * width
        # A tapered slash: wide at the head, needle point at the tail.
        shard = [
            (-ln * 0.46, -wd * 0.5),
            (ln * 0.54, -wd * 0.06),
            (ln * 0.60, wd * 0.02),
            (-ln * 0.16, wd * 0.5),
            (-ln * 0.52, wd * 0.22),
        ]
        # Shift perpendicular to the rake direction, then rotate into place.
        nx, ny = math.sin(math.radians(angle)), -math.cos(math.radians(angle))
        pts = [(x + nx * r * offset, y + ny * r * offset) for x, y in shard]
        pts = rotate(pts, angle, 0.0, 0.0)
        pts = [(cx + x, cy + y) for x, y in pts]
        d.polygon(pts, fill=CREAM)


def emblem(size: int, scale: float = 0.60) -> Image.Image:
    """Transparent emblem layer with the mark occupying `scale` of the canvas."""
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = big / 2
    r = big * scale / 2
    draw_reel(d, cx, cy, r)
    draw_claw(d, cx, cy, r)
    return img.resize((size, size), Image.LANCZOS)


def composed(size: int) -> Image.Image:
    bg = radial_background(size)
    bg.alpha_composite(emblem(size, 0.62))
    return bg


def write(path: str, image: Image.Image) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, optimize=True)
    print(f"  {os.path.relpath(path, ROOT)}  {image.size[0]}x{image.size[1]}")


def main() -> None:
    print("Legacy launcher icons (composed):")
    for density, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        icon = composed(px)
        write(os.path.join(RES, f"mipmap-{density}", "ic_launcher.png"), icon)
        # Round icons: same art, the launcher masks the shape.
        write(os.path.join(RES, f"mipmap-{density}", "ic_launcher_round.png"), icon)

    print("Adaptive icon foreground layers (108dp, emblem inside safe zone):")
    for density, px in (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)):
        write(os.path.join(RES, f"mipmap-{density}", "ic_launcher_foreground.png"), emblem(px, 0.58))

    print("Play Store / README artwork:")
    write(os.path.join(BRANDING, "app_icon_render.png"), composed(512))
    write(os.path.join(BRANDING, "feature_graphic.png"), feature_graphic())


def feature_graphic() -> Image.Image:
    w, h = 1024, 500
    img = radial_background(1).resize((w, h), Image.LANCZOS) if False else Image.new("RGB", (w, h), BG_EDGE)
    # Horizontal gradient background.
    px = img.load()
    for x in range(w):
        t = x / w
        col = tuple(int(BG_CENTER[i] + (BG_EDGE[i] - BG_CENTER[i]) * t) for i in range(3))
        for y in range(h):
            px[x, y] = col
    img = img.convert("RGBA")
    mark = emblem(420, 0.92)
    img.alpha_composite(mark, (60, (h - 420) // 2))
    # Subtle gold hairline for depth.
    d = ImageDraw.Draw(img)
    d.line([(520, h * 0.30), (960, h * 0.30)], fill=(245, 179, 1, 90), width=3)
    d.line([(520, h * 0.70), (880, h * 0.70)], fill=(255, 255, 255, 40), width=2)
    return img


if __name__ == "__main__":
    main()
