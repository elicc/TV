import unittest
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class HomeNavigationTests(unittest.TestCase):

    def test_vod_left_wraps_to_search(self):
        layout = ElementTree.parse(ROOT / "app/src/leanback/res/layout/activity_home.xml")
        nav_vod = next(
            view for view in layout.iter()
            if view.get(ANDROID + "id") == "@+id/navVod"
        )
        self.assertEqual("@id/navSearch", nav_vod.get(ANDROID + "nextFocusLeft"))

    def test_menu_key_opens_the_source_dialog_on_key_up(self):
        code = (ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java").read_text()
        self.assertIn("if (KeyUtil.isMenuKey(event))", code)
        self.assertNotIn("KeyUtil.isActionDown(event) && KeyUtil.isMenuKey(event)", code)
        self.assertIn("mBinding.sourceRow.setOnClickListener(v -> showDialog())", code)
        self.assertIn("SiteDialog.create().show(this)", code)


if __name__ == "__main__":
    unittest.main()
