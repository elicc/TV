"""Resource contracts for the bounded TV header; rendering is checked on TV."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/leanback/res"
A = "{http://schemas.android.com/apk/res/android}"
APP = "{http://schemas.android.com/apk/res-auto}"


class DetailHeaderTests(unittest.TestCase):
    def setUp(self):
        self.layout = ET.parse(RES / "layout/activity_video.xml").getroot()
        self.views = {
            node.get(A + "id"): node for node in self.layout.iter()
            if node.get(A + "id")
        }

    def test_badges_cannot_expand_title_or_cover_rating(self):
        viewport = self.views["@+id/row1"]
        self.assertEqual("0dp", viewport.get(A + "layout_height"))
        self.assertEqual("@id/name", viewport.get(A + "layout_alignTop"))
        self.assertEqual("@id/name", viewport.get(A + "layout_alignBottom"))
        self.assertEqual("@id/name", viewport.get(A + "layout_toEndOf"))
        self.assertEqual("@id/rating", viewport.get(A + "layout_toStartOf"))
        self.assertEqual("true", viewport.get(A + "layout_alignWithParentIfMissing"))
        self.assertEqual("true", viewport.get(A + "clipChildren"))
        self.assertNotIn(self.views["@+id/rating"], list(viewport.iter()))

    def test_all_badges_including_remark_share_wrapping_clip(self):
        viewport = self.views["@+id/row1"]
        flow = viewport.find("com.google.android.flexbox.FlexboxLayout")
        self.assertIsNotNone(flow)
        self.assertEqual("wrap", flow.get(APP + "flexWrap"))
        self.assertEqual("center_vertical", flow.get(A + "layout_gravity"))
        self.assertEqual("true", flow.get(A + "clipChildren"))
        for name in ("site", "year", "area", "type", "remark"):
            self.assertIn(self.views["@+id/" + name], list(flow))

    def test_badges_balance_smaller_text_with_row_spacing(self):
        styles = ET.parse(RES / "values/styles.xml").getroot()
        compact = next(n for n in styles if n.get("name") == "DetailBadgeCompact")
        items = {n.get("name"): n.text for n in compact}
        self.assertEqual("false", items["android:includeFontPadding"])
        self.assertEqual("0dp", items["android:paddingTop"])
        self.assertEqual("0dp", items["android:paddingBottom"])
        self.assertEqual("0dp", items["android:layout_marginTop"])
        self.assertEqual("1dp", items["android:layout_marginBottom"])
        self.assertEqual("11sp", items["android:textSize"])
        for name in ("site", "year", "area", "type", "remark"):
            badge = self.views["@+id/" + name]
            self.assertEqual("@style/DetailBadgeCompact", badge.get("style"))
            self.assertIsNone(badge.get(A + "textSize"))
            self.assertEqual("wrap_content", badge.get(A + "layout_height"))

    def test_cast_spacing_has_no_duplicate_margins(self):
        card = ET.parse(RES / "layout/adapter_cast.xml").getroot()
        avatar = next(n for n in card if n.get(A + "id") == "@+id/avatar")
        width = lambda n: float(n.get(A + "layout_width").removesuffix("dp"))
        self.assertLessEqual(width(card) - width(avatar), 12)
        self.assertIsNone(card.get(A + "layout_marginEnd"))
        self.assertIsNone(card.get(A + "background"))
        self.assertEqual("@drawable/selector_metadata_card", avatar.get(A + "foreground"))
        self.assertEqual("true", avatar.get(A + "duplicateParentState"))

    def test_synopsis_keeps_its_two_line_budget(self):
        synopsis = self.views["@+id/description"]
        self.assertEqual("2", synopsis.get(A + "maxLines"))
        self.assertEqual("2dp", synopsis.get(A + "layout_marginBottom"))
        self.assertEqual("24dp", self.views["@+id/detailTabs"].get(A + "layout_height"))
        self.assertEqual("54dp", self.views["@+id/mediaRail"].get(A + "layout_height"))


if __name__ == "__main__":
    unittest.main()
