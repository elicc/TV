#!/usr/bin/env python3
"""Brand-resource invariants for the small home and toast mark."""

from __future__ import annotations

import hashlib
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MASTER = ROOT / "docs/logo/02-图层 1.png"
VECTOR = ROOT / "app/src/main/res/drawable/ic_logo.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"


class LogoAssetTests(unittest.TestCase):
    def test_small_brand_vector_tracks_the_master(self):
        text = VECTOR.read_text()
        recorded = re.search(r"Source-SHA256: ([0-9a-f]{64})", text)
        self.assertIsNotNone(recorded)
        self.assertEqual(hashlib.sha256(MASTER.read_bytes()).hexdigest(), recorded.group(1))

        root = ET.fromstring(text)
        self.assertTrue(root.tag.endswith("vector"))
        self.assertEqual("24dp", root.get(ANDROID + "width"))
        self.assertEqual("24dp", root.get(ANDROID + "height"))
        self.assertEqual("24", root.get(ANDROID + "viewportWidth"))
        self.assertEqual("24", root.get(ANDROID + "viewportHeight"))
        self.assertEqual("VectorPath", root.get(TOOLS + "ignore"))

        path = next(node for node in root if node.tag.endswith("path"))
        data = path.get(ANDROID + "pathData")
        self.assertEqual("evenOdd", path.get(ANDROID + "fillType"))
        self.assertEqual(2, data.count("M"), "brand silhouette must contain outer edge and inner cutout")
        self.assertEqual(2, data.count("Z"), "each silhouette contour must close independently")
        # Bezier controls may sit slightly outside the rendered curve bounds,
        # but a wildly out-of-range value signals the old inverted canvas path.
        coordinates = [float(value) for value in re.findall(r"-?\d+(?:\.\d+)?", data)]
        self.assertGreaterEqual(min(coordinates), 0.8)
        self.assertLessEqual(max(coordinates), 23.2)

    def test_home_and_toast_use_the_small_vector(self):
        home = ET.parse(ROOT / "app/src/leanback/res/layout/activity_home.xml").getroot()
        logo = next(node for node in home.iter() if node.get(ANDROID + "id") == "@+id/logo")
        self.assertEqual("@drawable/ic_logo", logo.get(ANDROID + "src"))

        toast = ET.parse(ROOT / "app/src/main/res/layout/view_toast.xml").getroot()
        icon = next(node for node in toast.iter() if node.get(ANDROID + "id") == "@+id/toastIcon")
        self.assertEqual("@drawable/ic_logo", icon.get(ANDROID + "src"))

        activity = (ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java").read_text()
        self.assertIn("mBinding.logo.setImageResource(R.drawable.ic_logo);", activity)
        self.assertFalse((ROOT / "app/src/main/res/drawable/ic_logo_vector.xml").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
