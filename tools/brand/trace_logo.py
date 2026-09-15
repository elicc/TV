#!/usr/bin/env python3
"""Trace the ribbon from the master artwork into an Android VectorDrawable.

Source of truth: docs/logo/02-图层 1.png. The master is already alpha-clean
(gray backdrop is RGBA ≈ (125, 125, 127, 0)), so the ribbon contour is just
the alpha channel thresholded to a binary mask. potrace converts that mask
into a smooth Bézier path; we then:

  * unwrap potrace's translate/scale/flip transform so the path lands back
    in raw master pixels with Y pointing down
  * fit the bbox onto a 1024×1024 viewport with 10 % canvas padding — same
    padding ratio as the raster ic_logo_mark.png the VectorDrawable replaces
  * preserve the play-button triangle that potrace carves as a sub-path,
    rendered as a transparent hole via the even-odd fill rule

The output is one Android VectorDrawable XML file. It renders the ribbon
crisply at any size — unlike the raster PNG which visibly aliases when the
home toolbar ImageView (24 dp) or toast ImageView (26 dp) downsample it to
≈50 px on a 1080p TV.

Usage:
    python3 tools/brand/trace_logo.py              # write the VectorDrawable
    python3 tools/brand/trace_logo.py --check      # verify it's up to date
"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys

import numpy as np
from PIL import Image
from svgpathtools import CubicBezier, Line, Path, QuadraticBezier, parse_path

ROOT = pathlib.Path(__file__).resolve().parents[2]
MASTER = ROOT / "docs" / "logo" / "02-图层 1.png"
TARGET = ROOT / "app/src/main/res/drawable/ic_logo_vector.xml"

VIEWBOX = 1024
PADDING = 0.10

# Three-stop gradient approximating the master's lavender → steel-blue → soft
# lavender ribbon shading. Stops are at fractions of the gradient line.
GRAD_STOPS: list[tuple[float, str]] = [
    (0.00, "#C9BFDC"),  # pale lavender top highlight
    (0.45, "#7E84BC"),  # main ribbon body
    (1.00, "#8FA1C6"),  # bottom reflection
]
GRAD_START = (0.35, 0.05)  # fraction of viewport
GRAD_END = (0.55, 1.00)


def _write_master_mask(out_path: pathlib.Path) -> None:
    master = Image.open(MASTER).convert("RGBA")
    alpha = np.array(master.split()[-1])
    mask = np.where(alpha >= 128, 255, 0).astype("uint8")
    Image.fromarray(mask, mode="L").save(out_path)


def _potrace(mask_path: pathlib.Path) -> tuple[Path, float, float, float, float]:
    out_svg = mask_path.with_suffix(".svg")
    subprocess.run(
        ["potrace", "--svg", "--output", str(out_svg), str(mask_path)],
        check=True, capture_output=True,
    )
    text = out_svg.read_text()
    m = re.search(
        r'transform="translate\(([-\d.]+),\s*([-\d.]+)\)\s*scale\(([-\d.]+),\s*([-\d.]+)\)"',
        text,
    )
    if not m:
        raise SystemExit("error: potrace output missing transform")
    tx, ty, sx, sy = (float(m.group(i)) for i in range(1, 5))
    d = re.search(r'<path d="([^"]+)"', text, re.DOTALL).group(1)
    return parse_path(d), sx, sy, tx, ty


def _remap_segments(path: Path, fn) -> Path:
    """Apply ``fn(complex) -> complex`` to every control / endpoint on the path."""
    out = []
    for seg in path:
        s = fn(seg.start)
        e = fn(seg.end)
        if isinstance(seg, CubicBezier):
            out.append(CubicBezier(s, fn(seg.control1), fn(seg.control2), e))
        elif isinstance(seg, QuadraticBezier):
            out.append(QuadraticBezier(s, fn(seg.control1), e))
        else:
            out.append(Line(s, e))
    return Path(*out)


def _unwarp(path: Path, sx: float, sy: float, tx: float, ty: float) -> Path:
    """Inverse of potrace's transform: raw master pixels, Y pointing down."""

    def fn(pt):
        x, y = pt.real, pt.imag
        return complex((x - tx) / sx, (ty - y) / abs(sy))

    return _remap_segments(path, fn)


def _fit_to_viewbox(path: Path, viewbox: int, padding: float) -> Path:
    """Scale + translate so the bbox fits the viewbox with uniform padding."""
    xmin, xmax, ymin, ymax = path.bbox()
    bw, bh = xmax - xmin, ymax - ymin
    target = viewbox * (1 - 2 * padding)
    scale = target / max(bw, bh)
    ox = (viewbox - bw * scale) / 2 - xmin * scale
    oy = (viewbox - bh * scale) / 2 - ymin * scale

    def fn(pt):
        return complex(pt.real * scale + ox, pt.imag * scale + oy)

    return _remap_segments(path, fn)


def _to_android_d(path: Path) -> str:
    """Render as Android-compatible path data: M/L/C + Z, 2-decimal coords."""
    parts: list[str] = []
    for seg in path:
        s = seg.start
        e = seg.end
        if isinstance(seg, CubicBezier):
            c1, c2 = seg.control1, seg.control2
            parts.append(
                f"M{s.real:.2f},{s.imag:.2f}"
                f"C{c1.real:.2f},{c1.imag:.2f} "
                f"{c2.real:.2f},{c2.imag:.2f} "
                f"{e.real:.2f},{e.imag:.2f}"
            )
        elif isinstance(seg, QuadraticBezier):
            c1 = seg.control1
            parts.append(
                f"M{s.real:.2f},{s.imag:.2f}"
                f"Q{c1.real:.2f},{c1.imag:.2f} "
                f"{e.real:.2f},{e.imag:.2f}"
            )
        else:
            parts.append(f"M{s.real:.2f},{s.imag:.2f}L{e.real:.2f},{e.imag:.2f}")
    return " ".join(parts) + " Z"


def render() -> str:
    work = pathlib.Path("/tmp/logo_trace")
    work.mkdir(parents=True, exist_ok=True)
    mask = work / "ribbon_mask.pgm"
    _write_master_mask(mask)
    raw_path, sx, sy, tx, ty = _potrace(mask)
    fitted = _fit_to_viewbox(_unwarp(raw_path, sx, sy, tx, ty), VIEWBOX, PADDING)
    ribbon_d = _to_android_d(fitted)

    stops = " ".join(f"{o:.3f}" for o, _ in GRAD_STOPS)
    colors = " ".join(c for _, c in GRAD_STOPS)
    gx0, gy0 = GRAD_START
    gx1, gy1 = GRAD_END

    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Brand mark VectorDrawable. Generated from the master ribbon artwork\n'
        '     (docs/logo/02-图层 1.png) by tools/brand/trace_logo.py. Replaces the\n'
        '     raster ic_logo_mark.png so the 24 dp toolbar mark and the 26 dp toast\n'
        '     mark render crisply at any size instead of aliasing when downsampled\n'
        '     on a 1080p TV. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{VIEWBOX}dp"\n'
        f'    android:height="{VIEWBOX}dp"\n'
        f'    android:viewportWidth="{VIEWBOX}"\n'
        f'    android:viewportHeight="{VIEWBOX}">\n'
        '    <path\n'
        '        android:fillType="evenOdd"\n'
        f'        android:pathData="{ribbon_d}">\n'
        '        <aapt:attr name="android:fillColor" xmlns:aapt="http://aapt.android.com/aapt">\n'
        '            <gradient\n'
        f'                android:startX="{gx0 * VIEWBOX:.0f}"\n'
        f'                android:startY="{gy0 * VIEWBOX:.0f}"\n'
        f'                android:endX="{gx1 * VIEWBOX:.0f}"\n'
        f'                android:endY="{gy1 * VIEWBOX:.0f}"\n'
        '                android:type="linear">\n'
        f'                <item android:offset="{GRAD_STOPS[0][0]:.3f}" android:color="{GRAD_STOPS[0][1]}" />\n'
        f'                <item android:offset="{GRAD_STOPS[1][0]:.3f}" android:color="{GRAD_STOPS[1][1]}" />\n'
        f'                <item android:offset="{GRAD_STOPS[2][0]:.3f}" android:color="{GRAD_STOPS[2][1]}" />\n'
        '            </gradient>\n'
        '        </aapt:attr>\n'
        '    </path>\n'
        '</vector>\n'
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    rendered = render()
    if args.check:
        if not TARGET.exists():
            print(f"error: {TARGET.relative_to(ROOT)} is missing", file=sys.stderr)
            return 1
        if TARGET.read_text() != rendered:
            print(
                f"error: {TARGET.relative_to(ROOT)} is stale; "
                "re-run tools/brand/trace_logo.py",
                file=sys.stderr,
            )
            return 1
        print(f"ok: {TARGET.relative_to(ROOT)} is up to date")
        return 0
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(rendered)
    print(f"wrote {TARGET.relative_to(ROOT)} ({TARGET.stat().st_size / 1024:.1f} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())