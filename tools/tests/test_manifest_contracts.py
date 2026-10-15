"""Static source/merged-manifest contracts, not device intent-dispatch tests.

Run both :app:processLeanbackDebugManifest and :app:processMobileDebugManifest
before this suite so it can check the actual flavor merge outputs.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
MEDIA_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH"


class ManifestContracts(unittest.TestCase):
    def assert_media_search(self, manifest):
        activity = next(x for x in manifest.findall("application/activity")
                        if x.get(ANDROID + "name").endswith(".HomeActivity"))
        filters = [x for x in activity.findall("intent-filter")
                   if any(a.get(ANDROID + "name") == MEDIA_SEARCH for a in x.findall("action"))]
        self.assertEqual(len(filters), 1)
        self.assertIn("android.intent.category.DEFAULT",
                      [x.get(ANDROID + "name") for x in filters[0].findall("category")])
        self.assertEqual(activity.get(ANDROID + "exported"), "true")

    def test_media_search_declared_and_handled_in_both_flavors(self):
        for flavor in ["leanback", "mobile"]:
            with self.subTest(flavor=flavor):
                src = ROOT / "app/src" / flavor
                self.assert_media_search(ET.parse(src / "AndroidManifest.xml").getroot())
                java = (src / "java/com/fongmi/android/tv/ui/activity/HomeActivity.java").read_text()
                self.assertIn("MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(intent.getAction())", java)
                self.assertIn("intent.getStringExtra(SearchManager.QUERY)", java)
                self.assertIn("if (!TextUtils.isEmpty(keyword))", java)
                warm = java.split("protected void onNewIntent(Intent intent)", 1)[1].split("\n    @Override", 1)[0]
                self.assertLess(warm.index("setIntent(intent)"), warm.index("checkAction(intent)"))

    def test_merged_flavors_keep_tv_features_isolated(self):
        for flavor in ["leanback", "mobile"]:
            manifests = list((ROOT / "app/build/intermediates/merged_manifests" / (flavor + "Debug")).glob("**/AndroidManifest.xml"))
            self.assertTrue(manifests, "Generate both debug manifests first")
            for path in manifests:
                with self.subTest(manifest=path):
                    manifest = ET.parse(path).getroot()
                    self.assert_media_search(manifest)
                    features = {x.get(ANDROID + "name"): x.get(ANDROID + "required") for x in manifest.findall("uses-feature")}
                    categories = {x.get(ANDROID + "name") for x in manifest.findall("application/activity/intent-filter/category")}
                    if flavor == "leanback":
                        self.assertEqual(features.get("android.hardware.touchscreen"), "false")
                        self.assertEqual(features.get("android.software.leanback"), "false")
                        self.assertIn("android.intent.category.LEANBACK_LAUNCHER", categories)
                        self.assertTrue(manifest.find("application").get(ANDROID + "banner"))
                    else:
                        self.assertNotIn("android.software.leanback", features)
                        self.assertNotIn("android.intent.category.LEANBACK_LAUNCHER", categories)

    def test_lint_exemptions_are_only_non_tv_source_files(self):
        config = ET.parse(ROOT / "app/lint.xml").getroot()
        self.assertEqual({x.get("id") for x in config}, {
            "MissingLeanbackLauncher", "MissingLeanbackSupport", "ImpliedTouchscreenHardware"})
        for issue in config:
            self.assertIsNone(issue.get("severity"))
            self.assertEqual({x.get("path") for x in issue}, {
                "src/main/AndroidManifest.xml", "src/mobile/AndroidManifest.xml"})

    def test_mobile_mpv_messages_exist_and_keep_format_argument(self):
        required = {"dialog_import", "player_mpv_conf_conflict_hint",
                    "player_mpv_conf_priority_hint", "player_mpv_conf_import_failed"}
        for locale in ["values", "values-zh-rCN", "values-zh-rTW"]:
            path = ROOT / "app/src/mobile/res" / locale / "mpv_config_strings.xml"
            strings = {x.get("name"): x.text for x in ET.parse(path).getroot()}
            self.assertEqual(set(strings), required)
            self.assertIn("%1$s", strings["player_mpv_conf_conflict_hint"])

    def test_poster_geometry_keeps_tv_tokens_in_tv_flavor(self):
        for flavor, expected in [("main", "8dp"), ("leanback", "@dimen/tv_radius_card")]:
            styles = ET.parse(ROOT / "app/src" / flavor / "res/values/styles.xml").getroot()
            for name, corners in [("Vod.Grid", {"cornerSizeTopLeft", "cornerSizeTopRight"}),
                                  ("Vod.Grid.Rounded", {"cornerSize"})]:
                with self.subTest(flavor=flavor, style=name):
                    style = next(x for x in styles.findall("style") if x.get("name") == name)
                    self.assertEqual({x.get("name"): x.text for x in style},
                                     {corner: expected for corner in corners})

    def test_mobile_metadata_tabs_use_mobile_selected_background(self):
        res = ROOT / "app/src/mobile/res"
        layout = ET.parse(res / "layout/activity_video.xml").getroot()
        tabs = [x for x in layout.iter() if x.get(ANDROID + "id") in {
            "@+id/tabSource", "@+id/tabDouban", "@+id/tabTmdb"}]
        self.assertEqual(len(tabs), 3)
        for tab in tabs:
            self.assertEqual(tab.get(ANDROID + "background"), "@drawable/shape_item")
        shape = ET.parse(res / "drawable/shape_item.xml").getroot()
        self.assertEqual(shape.find("item/shape/solid").get(ANDROID + "color"), "@color/selector_item")
        selector = ET.parse(res / "color/selector_item.xml").getroot()
        self.assertTrue(any(x.get(ANDROID + "state_selected") == "true" for x in selector))

    def test_shared_detail_labels_have_shared_chinese_translations(self):
        required = {"detail_line", "detail_episode_range", "detail_part", "detail_sources"}
        for locale in ["values-zh-rCN", "values-zh-rTW"]:
            strings = ET.parse(ROOT / "app/src/main/res" / locale / "strings.xml").getroot()
            translations = {x.get("name"): x.text for x in strings}
            for name in required:
                self.assertTrue(translations.get(name), (locale, name))


if __name__ == "__main__":
    unittest.main()
