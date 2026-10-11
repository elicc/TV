"""Offline TV layout/wiring contracts; not a substitute for device focus/render tests."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
TV = ROOT / "app/src/leanback"
A = "{http://schemas.android.com/apk/res/android}"


def views(name):
    root = ET.parse(TV / "res/layout" / name).getroot()
    return {node.get(A + "id"): node for node in root.iter() if node.get(A + "id")}


class DoubanDiscoveryLayoutTest(unittest.TestCase):
    def test_compact_heading_keeps_room_for_first_card_row(self):
        nodes = views("activity_search.xml")
        self.assertEqual("6dp", nodes["@+id/recordLayout"].get(A + "layout_marginBottom"))
        self.assertEqual("4dp", nodes["@+id/word"].get(A + "layout_marginBottom"))
        self.assertEqual("false", nodes["@+id/word"].get(A + "includeFontPadding"))
        self.assertEqual("6dp", nodes["@+id/doubanTabs"].get(A + "layout_marginBottom"))
        # Reclaim whitespace, not poster space or the TV tab focus target.
        self.assertEqual("44dp", nodes["@+id/doubanTabs"].get(A + "layout_height"))
        card = ET.parse(TV / "res/layout/adapter_hot_movie.xml").getroot()
        self.assertEqual("172dp", card.find("FrameLayout").get(A + "layout_height"))

    def test_only_three_seed_free_tabs(self):
        tabs = views("activity_search.xml")["@+id/doubanTabs"]
        self.assertEqual(["@+id/tabHotMovies", "@+id/tabHotTv", "@+id/tabShowing"],
                         [node.get(A + "id") for node in tabs])
        source = (TV / "java/com/fongmi/android/tv/ui/activity/SearchActivity.java").read_text()
        self.assertIn("loadCollection(collection", source)
        self.assertNotIn("loadHotMovies", source)
        self.assertNotIn("showHotFallback", source)

    def test_related_row_is_last_and_uses_source_search(self):
        nodes = views("activity_video.xml")
        self.assertEqual("@+id/recommendationsRow", list(nodes["@+id/scroll"][0])[-1].get(A + "id"))
        self.assertEqual("androidx.leanback.widget.HorizontalGridView", nodes["@+id/recommendations"].tag)
        source = (TV / "java/com/fongmi/android/tv/ui/activity/VideoActivity.java").read_text()
        self.assertIn("R.id.recommendations, R.id.recommendationStatus}", source)
        self.assertIn("CollectActivity.start(this, item.getTitle())", source)

    def test_related_grid_measures_children_inside_vertical_scroll(self):
        source = (TV / "java/com/fongmi/android/tv/ui/activity/VideoActivity.java").read_text()
        self.assertIn("mBinding.recommendations.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)", source)

    def test_related_empty_and_error_have_reachable_status(self):
        nodes = views("activity_video.xml")
        self.assertIn("@+id/recommendationStatus", nodes)
        self.assertEqual("true", nodes["@+id/recommendationStatus"].get(A + "focusable"))
        source = (TV / "java/com/fongmi/android/tv/ui/activity/VideoActivity.java").read_text()
        self.assertIn("R.id.recommendationStatus}", source)
        self.assertIn("R.string.tv_douban_no_recommendations", source)
        self.assertIn("R.string.tv_douban_error", source)
        self.assertIn("mViewModel.retryRecommendations()", source)

    def test_shared_card_has_focus_and_recycle_marquee_reset(self):
        source = (TV / "java/com/fongmi/android/tv/ui/adapter/DoubanMovieAdapter.java").read_text()
        self.assertIn("setOnFocusChangeListener", source)
        self.assertIn("setMarqueeRepeatLimit(-1)", source)
        self.assertIn("text.setSelected(focused)", source)
        self.assertIn("onViewRecycled", source)
        self.assertIn("onViewDetachedFromWindow", source)
        self.assertIn("horizontal ? View.GONE : View.VISIBLE", source)
