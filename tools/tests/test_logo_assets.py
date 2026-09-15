#!/usr/bin/env python3
"""Brand-asset resolution and freshness; APK/device tests remain separate.

The TV brand lives on three PNG sizes: 2048 px for the in-app brand mark and
its variants (home toolbar, toast, splash, launcher foreground, etc.), and
1280×720 for the TV launcher banner and its foreground/background layer.
Both come from tools/brand/make_logo_assets.py — the script is the source of
truth and the committed bytes must match a fresh render.

Mirrors tools/tests/test_tv_theme_resources.py, which already drives
tools/lucide/generate_android_vectors.py --check via subprocess.
"""
from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools" / "brand" / "make_logo_assets.py"

# Targets that must come out of make_logo_assets.py at exactly these sizes.
# Sizes match the design intent documented at the top of make_logo_assets.py.
EXPECTED: dict[Path, tuple[int, int]] = {
    ROOT / "app/src/main/res/drawable-nodpi/ic_logo_mark.png": (2048, 2048),
    ROOT / "app/src/main/res/drawable-nodpi/ic_splash_mark.png": (2048, 2048),
    ROOT / "app/src/main/res/drawable-nodpi/ic_logo_ribbon.png": (2048, 2048),
    ROOT / "app/src/main/res/drawable-nodpi/ic_logo_aurora_glass.png": (2048, 2048),
    ROOT / "app/src/main/res/drawable-nodpi/ic_logo_aurora_minimal.png": (2048, 2048),
    ROOT / "app/src/main/res/drawable-nodpi/ic_banner_image.png": (1280, 720),
    ROOT / "app/src/main/res/drawable-nodpi/ic_banner_bg.png": (1280, 720),
    ROOT / "app/src/leanback/res/drawable/ic_banner.png": (1280, 720),
}


class LogoAssetTests(unittest.TestCase):
    def test_every_target_has_expected_dimensions(self):
        missing = [p for p in EXPECTED if not p.exists()]
        self.assertFalse(missing, f"missing brand PNGs: {[p.name for p in missing]}")
        for path, expected_size in EXPECTED.items():
            with Image.open(path) as im:
                self.assertEqual(
                    expected_size,
                    im.size,
                    f"{path.relative_to(ROOT)} is {im.size}, expected {expected_size}",
                )

    def test_mark_and_splash_have_transparent_padding(self):
        # The mark and splash mark are derived from docs/logo/02-图层 1.png and
        # must keep clean transparent padding around the ribbon so they
        # composite over any toolbar / splash background without leaking.
        for name in ("ic_logo_mark.png", "ic_splash_mark.png"):
            path = ROOT / f"app/src/main/res/drawable-nodpi/{name}"
            im = Image.open(path).convert("RGBA")
            alpha = im.split()[-1]
            self.assertEqual(
                0,
                alpha.getpixel((0, 0)),
                f"{name} corner is not transparent",
            )
            self.assertEqual(
                0,
                alpha.getpixel((im.size[0] - 1, 0)),
                f"{name} top-right corner is not transparent",
            )
            self.assertEqual(
                0,
                alpha.getpixel((0, im.size[1] - 1)),
                f"{name} bottom-left corner is not transparent",
            )
            self.assertEqual(
                0,
                alpha.getpixel((im.size[0] - 1, im.size[1] - 1)),
                f"{name} bottom-right corner is not transparent",
            )

    def test_make_logo_assets_check_passes(self):
        # The generator must agree with the committed bytes; a contributor who
        # hand-edits one of these PNGs without re-running the script trips
        # this assertion.
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--check"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            0,
            result.returncode,
            f"make_logo_assets.py --check failed:\n{result.stdout}\n{result.stderr}",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)