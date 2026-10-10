"""Static contracts for spacing between the TV detail selection rows."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


RES = Path(__file__).resolve().parents[2] / "app/src/leanback/res"
A = "{http://schemas.android.com/apk/res/android}"


class DetailChoiceSpacingTests(unittest.TestCase):
    def test_all_selection_rows_use_the_same_increased_gap(self):
        root = ET.parse(RES / "layout/activity_video.xml").getroot()
        views = {node.get(A + "id"): node for node in root.iter()}
        for name in ("flagRow", "qualityRow", "episodeRow", "arrayRow", "partRow", "quickRow"):
            with self.subTest(row=name):
                row = views["@+id/" + name]
                self.assertEqual("@dimen/tv_gap_sm", row.get(A + "layout_marginBottom"))
                self.assertIsNone(row.get(A + "layout_marginTop"))
                self.assertEqual("center_vertical", row.get(A + "gravity"))
                self.assertEqual("gone", row.get(A + "visibility"))
        dims = ET.parse(RES / "values/tv_dimens.xml").getroot()
        gap = next(node.text for node in dims if node.get("name") == "tv_gap_sm")
        self.assertEqual("12dp", gap)

    def test_short_line_labels_do_not_shrink_below_other_choices(self):
        flag = ET.parse(RES / "layout/adapter_flag.xml").getroot()
        episode = ET.parse(RES / "layout/adapter_episode.xml").getroot()
        self.assertEqual(episode.get(A + "minHeight"), flag.get(A + "minHeight"))
        array = ET.parse(RES / "layout/adapter_array.xml").getroot()
        self.assertEqual(array.get(A + "layout_height"), flag.get(A + "minHeight"))


if __name__ == "__main__":
    unittest.main()
