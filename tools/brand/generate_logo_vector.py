#!/usr/bin/env python3
"""Generate the small Android brand mark from the committed raster master.

The master artwork is intentionally preserved for large surfaces.  Home and
toast icons are different: they render at roughly 48-52 physical pixels on the
2x TV canvas, so feeding Android a 2048 px ``drawable-nodpi`` bitmap makes the
GPU minify the artwork about forty times at draw time.  This generator traces
only the alpha silhouette and reapplies a compact gradient sampled from the
master.  The result keeps the recognizable ribbon shape and colour movement
while giving Android a native vector edge at its final size.

Requirements for regeneration (not for an Android build): Pillow, potrace and
svgpathtools. The generated XML records the source SHA-256 so ordinary unit
tests can detect a changed master without requiring those tools in CI.
"""

from __future__ import annotations

import argparse
import hashlib
import pathlib
import re
import subprocess
import tempfile

from PIL import Image
from svgpathtools import CubicBezier, Line, Path, QuadraticBezier, parse_path

ROOT = pathlib.Path(__file__).resolve().parents[2]
MASTER = ROOT / "docs/logo/02-图层 1.png"
TARGET = ROOT / "app/src/main/res/drawable/ic_logo.xml"

VIEWPORT = 24.0
PADDING = 1.0
ALPHA_THRESHOLD = 128

# Alpha-weighted vertical samples from the master.  At 24-26 dp the original
# micro-shading is below pixel scale, but these stops retain its lavender top,
# darker fold and steel-blue lower highlight.
GRADIENT_STOPS = (
    (0.000, "#BFB7E7"),
    (0.180, "#A8A7DB"),
    (0.360, "#8588C1"),
    (0.500, "#767DB9"),
    (0.660, "#8797D1"),
    (0.820, "#A0B2E7"),
    (1.000, "#9AAEE2"),
)


def source_hash() -> str:
    return hashlib.sha256(MASTER.read_bytes()).hexdigest()


def write_mask(path: pathlib.Path) -> None:
    alpha = Image.open(MASTER).convert("RGBA").getchannel("A")
    # Potrace traces black pixels by default: foreground must be 0, not 255.
    alpha.point(lambda value: 0 if value >= ALPHA_THRESHOLD else 255).save(path)


def trace() -> tuple[Path, float, float, float, float]:
    with tempfile.TemporaryDirectory(prefix="fongmi-logo-") as directory:
        directory = pathlib.Path(directory)
        mask = directory / "mask.pgm"
        svg = directory / "logo.svg"
        write_mask(mask)
        subprocess.run(
            ["potrace", "--svg", "--flat", "--tight", "--output", str(svg), str(mask)],
            check=True,
            capture_output=True,
        )
        text = svg.read_text()

    transform = re.search(
        r'transform="translate\(([-\d.]+),\s*([-\d.]+)\)\s*'
        r'scale\(([-\d.]+),\s*([-\d.]+)\)"',
        text,
    )
    path_data = re.search(r'<path d="([^"]+)"', text, re.DOTALL)
    if transform is None or path_data is None:
        raise RuntimeError("potrace returned an unsupported SVG structure")
    tx, ty, sx, sy = (float(transform.group(i)) for i in range(1, 5))
    return parse_path(path_data.group(1)), tx, ty, sx, sy


def transform_path(path: Path, tx: float, ty: float, sx: float, sy: float) -> list[Path]:
    def from_svg(point: complex) -> complex:
        return complex(tx + point.real * sx, ty + point.imag * sy)

    raw_subpaths = path.continuous_subpaths()
    path_min_x, path_max_x, path_min_y, path_max_y = path.bbox()
    transformed_x = (tx + path_min_x * sx, tx + path_max_x * sx)
    transformed_y = (ty + path_min_y * sy, ty + path_max_y * sy)
    min_x, max_x = min(transformed_x), max(transformed_x)
    min_y, max_y = min(transformed_y), max(transformed_y)
    scale = (VIEWPORT - 2 * PADDING) / max(max_x - min_x, max_y - min_y)
    offset_x = (VIEWPORT - (max_x - min_x) * scale) / 2
    offset_y = (VIEWPORT - (max_y - min_y) * scale) / 2

    def fit(point: complex) -> complex:
        point = from_svg(point)
        return complex(
            offset_x + (point.real - min_x) * scale,
            offset_y + (point.imag - min_y) * scale,
        )

    output = []
    for subpath in raw_subpaths:
        segments = []
        for segment in subpath:
            start, end = fit(segment.start), fit(segment.end)
            if isinstance(segment, CubicBezier):
                segments.append(CubicBezier(start, fit(segment.control1), fit(segment.control2), end))
            elif isinstance(segment, QuadraticBezier):
                segments.append(QuadraticBezier(start, fit(segment.control), end))
            else:
                segments.append(Line(start, end))
        output.append(Path(*segments))
    return output


def number(value: float) -> str:
    return f"{value:.3f}".rstrip("0").rstrip(".")


def serialize(subpaths: list[Path]) -> str:
    commands = []
    for subpath in subpaths:
        commands.append(f"M{number(subpath[0].start.real)},{number(subpath[0].start.imag)}")
        for segment in subpath:
            end = segment.end
            if isinstance(segment, CubicBezier):
                commands.append(
                    "C"
                    f"{number(segment.control1.real)},{number(segment.control1.imag)} "
                    f"{number(segment.control2.real)},{number(segment.control2.imag)} "
                    f"{number(end.real)},{number(end.imag)}"
                )
            elif isinstance(segment, QuadraticBezier):
                commands.append(
                    "Q"
                    f"{number(segment.control.real)},{number(segment.control.imag)} "
                    f"{number(end.real)},{number(end.imag)}"
                )
            else:
                commands.append(f"L{number(end.real)},{number(end.imag)}")
        commands.append("Z")
    return " ".join(commands)


def render() -> str:
    path, tx, ty, sx, sy = trace()
    path_data = serialize(transform_path(path, tx, ty, sx, sy))
    stops = "\n".join(
        f'                <item android:offset="{offset:.3f}" android:color="{color}" />'
        for offset, color in GRADIENT_STOPS
    )
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/brand/generate_logo_vector.py.
     Brand exception: traced from docs/logo/02-图层 1.png, not a Lucide icon.
     Source-SHA256: {source_hash()} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    xmlns:tools="http://schemas.android.com/tools"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    tools:ignore="VectorPath">
    <path
        android:fillType="evenOdd"
        android:pathData="{path_data}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="8"
                android:startY="0"
                android:endX="13"
                android:endY="24"
                android:type="linear">
{stops}
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    generated = render()
    if args.check:
        if not TARGET.exists() or TARGET.read_text() != generated:
            print(f"error: {TARGET.relative_to(ROOT)} is stale")
            return 1
        print(f"ok: {TARGET.relative_to(ROOT)} is up to date")
        return 0
    TARGET.write_text(generated)
    print(f"wrote {TARGET.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
