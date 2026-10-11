"""Source-card measurement contracts; actual font metrics remain device checks.

TCL baseline (ARTEMIS hierarchy + screenshot): third yellow line is cropped;
62dp row height includes 16dp drawable padding and unrestricted text wrapping.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
TV = ROOT / "app/src/leanback"
A = "{http://schemas.android.com/apk/res/android}"


class SourceCardLayoutTests(unittest.TestCase):
    def test_source_rail_measures_text_instead_of_forcing_62dp(self):
        code = (TV / "java/com/fongmi/android/tv/ui/activity/VideoActivity.java").read_text()
        self.assertIn("mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);", code)
        self.assertNotRegex(code, r"mBinding\.quick\.setRowHeight\(ResUtil\.dp2px\(")
        root = ET.parse(TV / "res/layout/activity_video.xml").getroot()
        rail = next(v for v in root.iter() if v.get(A + "id") == "@+id/quick")
        self.assertEqual("wrap_content", rail.get(A + "layout_height"))

    def test_three_semantic_lines_fit_natural_card_height(self):
        card = ET.parse(TV / "res/layout/adapter_quick.xml").getroot()
        self.assertEqual("wrap_content", card.get(A + "layout_height"))
        self.assertEqual("62dp", card.get(A + "minHeight"))
        self.assertEqual("8dp", card.get(A + "paddingTop"))
        self.assertEqual("8dp", card.get(A + "paddingBottom"))
        fields = {node.get(A + "id"): node for node in card}
        for name in ("name", "site", "remark"):
            with self.subTest(name=name):
                text = fields["@+id/" + name]
                self.assertEqual("match_parent", text.get(A + "layout_width"))
                self.assertEqual("wrap_content", text.get(A + "layout_height"))
                self.assertEqual("true", text.get(A + "singleLine"))
                self.assertEqual("end", text.get(A + "ellipsize"))
                self.assertIsNone(text.get(A + "autoSizeTextType"))

    def test_marquee_is_restored_on_bind_and_cached_reattach(self):
        code = (TV / "java/com/fongmi/android/tv/ui/adapter/QuickAdapter.java").read_text()
        self.assertIn("setOnFocusChangeListener((view, focused) -> setMarquee(focused))", code)
        for method in ("onBindViewHolder", "onViewAttachedToWindow"):
            body = re.search(r"public void " + method + r"\([^)]*\) \{(.*?)\n    }", code, re.S).group(1)
            self.assertIn("holder.setMarquee(holder.itemView.hasFocus())", body)
        for method in ("onViewDetachedFromWindow", "onViewRecycled"):
            body = re.search(r"public void " + method + r"\([^)]*\) \{(.*?)\n    }", code, re.S).group(1)
            self.assertIn("holder.setMarquee(false)", body)
        self.assertIn("binding.name, binding.site, binding.remark", code)
        self.assertIn("text.setSelected(focused)", code)


if __name__ == "__main__":
    unittest.main()
