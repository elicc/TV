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

    def test_player_grows_proportionally_without_narrowing_details(self):
        player = self.views["@+id/video"]
        dp = lambda attr: float(player.get(A + attr).removesuffix("dp"))
        self.assertEqual(432, dp("layout_width"))
        self.assertEqual(243, dp("layout_height"))
        self.assertAlmostEqual(16 / 9, dp("layout_width") / dp("layout_height"))
        self.assertEqual(12, dp("layout_marginEnd"))
        self.assertEqual(420 + 24, dp("layout_width") + dp("layout_marginEnd"))
        self.assertEqual("@+id/video", self.views["@+id/row2"].get(A + "layout_alignBottom"))

    def test_title_reserves_added_height_for_badge_row_spacing(self):
        title = self.views["@+id/name"]
        self.assertEqual("3dp", title.get(A + "paddingTop"))
        self.assertEqual("3dp", title.get(A + "paddingBottom"))
        self.assertEqual("center_vertical", title.get(A + "gravity"))
        self.assertEqual("@dimen/tv_text_section", title.get(A + "textSize"))

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
        self.assertEqual("3dp", items["android:layout_marginBottom"])
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

    def test_cast_click_searches_person_name_not_character(self):
        java = ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui"
        adapter = (java / "adapter/CastAdapter.java").read_text()
        click = adapter.split("setOnClickListener(view -> {", 1)[1].split("});", 1)[0]
        self.assertIn("holder.getBindingAdapterPosition()", click)
        self.assertIn("current == RecyclerView.NO_POSITION", click)
        self.assertIn("current >= items.size()", click)
        self.assertIn("person != null && !person.getName().isEmpty()", click)
        self.assertIn("listener.onItemClick(person.getName())", click)
        self.assertNotIn("getCharacter()", click)
        activity = (java / "activity/VideoActivity.java").read_text()
        self.assertIn("new CastAdapter(name -> CollectActivity.start(this, name))", activity)
        self.assertNotIn("new CastAdapter(name -> SearchActivity.start(this, name))", activity)

    def test_badge_text_uses_optical_centering_with_symmetric_padding(self):
        styles = ET.parse(RES / "values/styles.xml").getroot()
        compact = next(n for n in styles if n.get("name") == "DetailBadgeCompact")
        items = {n.get("name"): n.text for n in compact}
        self.assertIn("center_vertical", items["android:gravity"].split("|"))
        self.assertEqual(items["android:paddingTop"], items["android:paddingBottom"])
        self.assertEqual("false", items["android:includeFontPadding"])
        for name in ("site", "year", "area", "type", "remark"):
            badge = self.views["@+id/" + name]
            self.assertEqual("com.fongmi.android.tv.ui.custom.DetailBadgeTextView", badge.tag)
            self.assertEqual("@style/DetailBadgeCompact", badge.get("style"))
            for attr in ("gravity", "padding", "paddingVertical", "paddingTop", "paddingBottom",
                         "includeFontPadding", "translationY"):
                self.assertIsNone(badge.get(A + attr), f"{name} overrides {attr}")

    def test_optical_alignment_moves_only_text_drawing_not_badge_geometry(self):
        view = ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui/custom/DetailBadgeTextView.java"
        code = view.read_text()
        self.assertIn("extends MaterialTextView", code)
        self.assertIn("layout.getLineCount() != 1", code)
        self.assertIn("layout.getText().toString()", code)
        self.assertIn("getTextBounds", code)
        self.assertIn("getBaseline() + textBounds.exactCenterY()", code)
        self.assertIn("canvas.translate(0,", code)
        self.assertIn("canvas.restoreToCount", code)
        for mutation in ("setTranslationY(", "setPadding(", "setLayoutParams("):
            self.assertNotIn(mutation, code)

    def test_synopsis_keeps_its_two_line_budget(self):
        synopsis = self.views["@+id/description"]
        self.assertEqual("2", synopsis.get(A + "maxLines"))
        self.assertEqual("2dp", synopsis.get(A + "layout_marginBottom"))
        self.assertEqual("24dp", self.views["@+id/detailTabs"].get(A + "layout_height"))
        self.assertEqual("54dp", self.views["@+id/mediaRail"].get(A + "layout_height"))

    def test_badges_render_field_values_without_client_labels(self):
        activity = ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"
        code = activity.read_text()
        for signature, type_getter in (("private void setMetadataText(MovieMetadata item)", "getType"),
                                       ("private void setText(Vod item)", "getTypeName")):
            start = code.index(signature)
            method = code[start:code.index("\n    }", start)]
            self.assertIn("setText(mBinding.site, 0, getSite().getName());", method)
            for field, getter in (("year", "getYear"), ("area", "getArea"), ("type", type_getter)):
                self.assertIn(f"setText(mBinding.{field}, 0, item.{getter}());", method)
            self.assertIn("setText(mBinding.director, R.string.detail_director,", method)
            self.assertIn("setText(mBinding.actor, R.string.detail_actor,", method)
        self.assertIn("setText(mBinding.remark, 0, item.getRemarks());", code)


if __name__ == "__main__":
    unittest.main()
