#!/usr/bin/env python3
"""Render OmpChat launcher icons (PNG) from the same geometry as the old vector.

The adaptive-icon foreground used to be a vector; some Android 14/15 SystemUI
builds crash in SplashscreenIconDrawableFactory when drawing it (reproduced on
the API 35 emulator). Raster foregrounds avoid that path entirely.

    python tools/make_icons.py
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

BG = (0x12, 0x21, 0x1E, 255)
AMBER = (0xE6, 0xBE, 0x86, 255)
SCALE = 4  # supersampling for smooth edges


def draw_mark(draw: ImageDraw.ImageDraw, size: int) -> None:
    """Ring + tail + dot in a 108x108 viewport."""
    unit = size / 108.0

    def xy(x: float, y: float) -> tuple[float, float]:
        return (x * unit, y * unit)

    stroke = 6 * unit
    cx, cy = xy(54, 54)
    r = 23 * unit
    draw.ellipse(
        [cx - r, cy - r, cx + r, cy + r],
        outline=AMBER,
        width=max(1, round(stroke)),
    )
    draw.polygon([xy(68.5, 72.5), xy(78, 82), xy(66, 79)], fill=AMBER)
    dr = 5 * unit
    draw.ellipse([cx - dr, cy - dr, cx + dr, cy + dr], fill=AMBER)


def rounded_icon(size: int, round_icon: bool) -> Image.Image:
    big = size * SCALE
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if round_icon:
        draw.ellipse([0, 0, big - 1, big - 1], fill=BG)
    else:
        radius = int(big * 0.185)
        draw.rounded_rectangle([0, 0, big - 1, big - 1], radius=radius, fill=BG)
    draw_mark(draw, big)
    return image.resize((size, size), Image.LANCZOS)


def foreground(size: int) -> Image.Image:
    big = size * SCALE
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw_mark(ImageDraw.Draw(image), big)
    return image.resize((size, size), Image.LANCZOS)


DENSITIES = {
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}


def main() -> None:
    for density, factor in DENSITIES.items():
        target = RES / f"mipmap-{density}"
        target.mkdir(parents=True, exist_ok=True)
        size = round(48 * factor)
        rounded_icon(size, False).save(target / "ic_launcher.png")
        rounded_icon(size, True).save(target / "ic_launcher_round.png")
        # Adaptive foreground is 108dp; Android scales/crops it.
        foreground(round(108 * factor)).save(target / "ic_launcher_foreground.png")
        print(f"{target}: {size}px legacy, {round(108 * factor)}px foreground")

    # Splash icon is a plain bitmap (not an adaptive icon), so Android 12+
    # never builds an AdaptiveForegroundDrawable for the launch splash.
    splash_dir = RES / "drawable-nodpi"
    splash_dir.mkdir(parents=True, exist_ok=True)
    splash = Image.new("RGBA", (768, 768), (0, 0, 0, 0))
    draw_mark(ImageDraw.Draw(splash), 768)
    splash.save(splash_dir / "splash_icon.png")
    print(f"{splash_dir}: 768px splash icon")


if __name__ == "__main__":
    main()
