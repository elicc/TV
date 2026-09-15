#!/usr/bin/env python3
"""Derive the leanback cold-start splash backdrop from the master brand artwork.

Source of truth is docs/logo/01-底图.png (2048x2048). The splash only ever draws it
through a full-screen ImageView on a 16:9 panel, so we bake the crop *and* the
vignette here rather than at runtime:

  * center-crop to 16:9, then downscale to 1280x720. The artwork is a smooth
    gradient with no fine detail, so 720p re-upscaled to 1080p differs by at most
    5/255 per channel while costing 3.5 MB of texture instead of 16 MB.
  * a soft radial vignette, composited over the centre-weighted mark. Baking it
    keeps the splash to a single ImageView and makes the falloff identical on
    every device, instead of depending on a %p radial-gradient radius.

Usage:
    python3 tools/brand/make_splash_backdrop.py           # write the backdrop
    python3 tools/brand/make_splash_backdrop.py --check   # verify it is current
"""

from __future__ import annotations

import argparse
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs" / "logo" / "01-底图.png"
TARGET = (
    ROOT / "app" / "src" / "leanback" / "res" / "drawable-nodpi"
    / "ic_splash_backdrop.png"
)

WIDTH, HEIGHT = 1280, 720
ASPECT = WIDTH / HEIGHT

# Vignette tuning. The ellipse is sized to meet the frame corners exactly, the
# inner VIGNETTE_FREE of its radius is left completely untouched so the mark and
# wordmark sit on clean artwork, and the corners lose VIGNETTE_STRENGTH. Without
# that dead zone a centre-weighted ramp reads as a bright glow behind the mark
# rather than as depth around it.
VIGNETTE_CENTRE = (0.50, 0.48)
VIGNETTE_RADIUS = (0.7071, 0.7071)  # fractions of WIDTH / HEIGHT; 1/sqrt(2) meets the corners
VIGNETTE_FREE = 0.45
VIGNETTE_STRENGTH = 0.34
VIGNETTE_FALLOFF = 1.6
VIGNETTE_BLUR = 48
VIGNETTE_INK = (14, 14, 16)  # tv_bg #0E0E10, so the falloff reads as shadow not haze


def _crop_to_aspect(image: Image.Image) -> Image.Image:
    """Centre-crop the square master to 16:9, keeping the middle wave band."""
    width, height = image.size
    crop_height = round(width / ASPECT)
    if crop_height > height:
        crop_width = round(height * ASPECT)
        left = (width - crop_width) // 2
        return image.crop((left, 0, left + crop_width, height))
    top = (height - crop_height) // 2
    return image.crop((0, top, width, top + crop_height))


def _vignette() -> Image.Image:
    """Build the elliptical shadow mask as an 'L' image."""
    # Draw the ramp at low resolution and let the blur do the smoothing; this is a
    # pure gradient, so there is nothing to gain from shading 921_600 pixels.
    scale = 8
    small = Image.new("L", (WIDTH // scale, HEIGHT // scale), 0)
    draw = ImageDraw.Draw(small)
    cx = VIGNETTE_CENTRE[0] * small.width
    cy = VIGNETTE_CENTRE[1] * small.height
    rx = VIGNETTE_RADIUS[0] * small.width
    ry = VIGNETTE_RADIUS[1] * small.height
    # Largest ellipse first, then progressively smaller ones painted over it with
    # progressively lower alpha, so the final centre ends up fully clear. The fill
    # depends on the *radius fraction* the ellipse represents, not on the loop
    # index, which is what puts the dead zone at VIGNETTE_FREE.
    steps = 96
    for i in range(steps):
        s = i / (steps - 1)  # 0 = outermost
        radius = 1 - s
        ramp = max(0.0, (radius - VIGNETTE_FREE) / (1 - VIGNETTE_FREE))
        draw.ellipse(
            [cx - rx * radius, cy - ry * radius, cx + rx * radius, cy + ry * radius],
            fill=round(255 * ramp ** VIGNETTE_FALLOFF),
        )
    mask = small.resize((WIDTH, HEIGHT), Image.BILINEAR).filter(
        ImageFilter.GaussianBlur(VIGNETTE_BLUR / scale)
    )
    return mask.point(lambda p: round(p * VIGNETTE_STRENGTH))


def render() -> Image.Image:
    source = Image.open(SOURCE).convert("RGB")
    backdrop = _crop_to_aspect(source).resize((WIDTH, HEIGHT), Image.LANCZOS)
    shadow = Image.new("RGB", (WIDTH, HEIGHT), VIGNETTE_INK)
    return Image.composite(shadow, backdrop, _vignette())


def _pixels(image: Image.Image) -> bytes:
    return image.convert("RGB").tobytes()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="exit non-zero if the committed backdrop differs from a fresh render",
    )
    args = parser.parse_args()

    if not SOURCE.exists():
        print(f"error: master artwork missing: {SOURCE}", file=sys.stderr)
        return 2

    rendered = render()
    if args.check:
        if not TARGET.exists():
            print(f"error: {TARGET.relative_to(ROOT)} is missing", file=sys.stderr)
            return 1
        if _pixels(Image.open(TARGET)) != _pixels(rendered):
            print(
                f"error: {TARGET.relative_to(ROOT)} is stale; "
                "re-run tools/brand/make_splash_backdrop.py",
                file=sys.stderr,
            )
            return 1
        print(f"ok: {TARGET.relative_to(ROOT)} is up to date")
        return 0

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    rendered.save(TARGET, "PNG", optimize=True)
    print(f"wrote {TARGET.relative_to(ROOT)} ({TARGET.stat().st_size / 1024:.1f} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
