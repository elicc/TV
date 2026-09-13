#!/usr/bin/env python3
"""Generate TV UI VectorDrawables from vendored Lucide SVGs."""

from __future__ import annotations

import argparse
import difflib
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / "svg"
DRAWABLE = ROOT / "app/src/leanback/res/drawable"
ANDROID = "http://schemas.android.com/apk/res/android"
LUCIDE_VERSION = "0.344.0"

ICONS = {
    "ic_nav_home.xml": ("home.svg", 20),
    "ic_nav_vod.xml": ("film.svg", 20),
    "ic_nav_live.xml": ("tv.svg", 20),
    "ic_nav_keep.xml": ("bookmark.svg", 20),
    "ic_nav_search.xml": ("search.svg", 20),
    "ic_nav_more.xml": ("more-horizontal.svg", 20),
    "ic_nav_settings.xml": ("settings.svg", 20),
    "ic_empty_film.xml": ("film.svg", 20),
    "ic_empty_broadcast.xml": ("radio-tower.svg", 20),
    "ic_empty_cloud.xml": ("cloud.svg", 20),
    "ic_setting_nav_content.xml": ("database.svg", 24),
    "ic_setting_nav_appearance.xml": ("palette.svg", 24),
    "ic_setting_nav_playback.xml": ("play-circle.svg", 24),
    "ic_setting_nav_data.xml": ("shield-check.svg", 24),
    "ic_setting_nav_about.xml": ("info.svg", 24),
    "ic_setting_back.xml": ("chevron-left.svg", 16),
}


def number(value: str | None, default: float = 0) -> str:
    result = float(value) if value is not None else default
    return str(int(result)) if result.is_integer() else f"{result:g}"


def points(value: str) -> str:
    values = value.replace(",", " ").split()
    if len(values) % 2:
        raise ValueError(f"Odd number of SVG point coordinates: {value}")
    pairs = list(zip(values[::2], values[1::2]))
    return " ".join(("M" if index == 0 else "L") + ",".join(pair) for index, pair in enumerate(pairs))


def geometry(node: ET.Element) -> str:
    tag = node.tag.rsplit("}", 1)[-1]
    if tag == "path":
        return node.attrib["d"]
    if tag == "polyline":
        return points(node.attrib["points"])
    if tag == "polygon":
        return points(node.attrib["points"]) + " Z"
    if tag == "line":
        return f'M{number(node.get("x1"))},{number(node.get("y1"))} L{number(node.get("x2"))},{number(node.get("y2"))}'
    if tag == "circle":
        cx, cy, radius = map(float, (node.attrib["cx"], node.attrib["cy"], node.attrib["r"]))
        return f"M{cx - radius:g},{cy:g} A{radius:g},{radius:g} 0,1 0,{cx + radius:g},{cy:g} A{radius:g},{radius:g} 0,1 0,{cx - radius:g},{cy:g}"
    if tag == "ellipse":
        cx, cy, rx, ry = map(float, (node.attrib["cx"], node.attrib["cy"], node.attrib["rx"], node.attrib["ry"]))
        return f"M{cx - rx:g},{cy:g} A{rx:g},{ry:g} 0,1 0,{cx + rx:g},{cy:g} A{rx:g},{ry:g} 0,1 0,{cx - rx:g},{cy:g}"
    if tag == "rect":
        x = float(node.get("x", "0"))
        y = float(node.get("y", "0"))
        width = float(node.attrib["width"])
        height = float(node.attrib["height"])
        rx = float(node.get("rx", "0"))
        ry = float(node.get("ry", str(rx)))
        if not rx and not ry:
            return f"M{x:g},{y:g} H{x + width:g} V{y + height:g} H{x:g} Z"
        return (
            f"M{x + rx:g},{y:g} H{x + width - rx:g} "
            f"A{rx:g},{ry:g} 0,0 1,{x + width:g},{y + ry:g} "
            f"V{y + height - ry:g} A{rx:g},{ry:g} 0,0 1,{x + width - rx:g},{y + height:g} "
            f"H{x + rx:g} A{rx:g},{ry:g} 0,0 1,{x:g},{y + height - ry:g} "
            f"V{y + ry:g} A{rx:g},{ry:g} 0,0 1,{x + rx:g},{y:g} Z"
        )
    raise ValueError(f"Unsupported Lucide SVG element: {tag}")


def convert(source: Path, size: int) -> str:
    root = ET.parse(source).getroot()
    view_box = root.attrib["viewBox"].split()
    if view_box != ["0", "0", "24", "24"]:
        raise ValueError(f"{source.name}: expected a 24x24 Lucide viewport, got {root.attrib['viewBox']}")

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<!-- Generated from Lucide {LUCIDE_VERSION} {source.name}; do not edit pathData manually. -->',
        f'<vector xmlns:android="{ANDROID}"',
        f'    android:width="{size}dp"',
        f'    android:height="{size}dp"',
        '    android:viewportWidth="24"',
        '    android:viewportHeight="24">',
    ]
    for node in root:
        if node.tag.rsplit("}", 1)[-1] in {"defs", "title", "desc"}:
            continue
        fill = "#FFFFFF" if node.get("fill") == "currentColor" else "#00000000"
        lines.extend(
            [
                "    <path",
                f'        android:fillColor="{fill}"',
                '        android:strokeColor="#FFFFFF"',
                '        android:strokeWidth="2"',
                '        android:strokeLineCap="round"',
                '        android:strokeLineJoin="round"',
                f'        android:pathData="{geometry(node)}" />',
            ]
        )
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if committed vectors are stale")
    args = parser.parse_args()
    stale = False
    for output_name, (source_name, size) in ICONS.items():
        expected = convert(SOURCE / source_name, size)
        output = DRAWABLE / output_name
        actual = output.read_text() if output.exists() else ""
        if args.check:
            if actual != expected:
                stale = True
                sys.stderr.writelines(
                    difflib.unified_diff(
                        actual.splitlines(keepends=True),
                        expected.splitlines(keepends=True),
                        fromfile=str(output),
                        tofile=f"generated from {source_name}",
                    )
                )
        else:
            output.write_text(expected)
    return 1 if stale else 0


if __name__ == "__main__":
    raise SystemExit(main())
