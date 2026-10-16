"""Resource/lifecycle contracts complement the emulator UI checks; no device mutations."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
TV = ROOT / "app/src/leanback"
MAIN = ROOT / "app/src/main"
A = "{http://schemas.android.com/apk/res/android}"
APP = "{http://schemas.android.com/apk/res-auto}"


class DanmakuUiContracts(unittest.TestCase):
    def test_fixed_rows_and_inherited_endpoint_not_saved_implicitly(self):
        activity = (TV / "java/com/fongmi/android/tv/ui/activity/SettingDanmakuActivity.java").read_text()
        dialog = (TV / "java/com/fongmi/android/tv/ui/dialog/DanmakuApiDialog.java").read_text()
        self.assertNotIn("View.GONE", activity)
        self.assertIn("String text = DanmakuSetting.getApiUrl();", dialog)
        remote = dialog.split("public void onServerEvent", 1)[1].split("@Override", 1)[0]
        self.assertNotIn("performClick", remote)
        self.assertNotIn("putApiUrl", remote)
        self.assertIn("binding.positive.requestFocus()", remote)

    def test_detector_lifecycle_and_result_retention(self):
        dialog = (TV / "java/com/fongmi/android/tv/ui/dialog/DanmakuApiDialog.java").read_text()
        self.assertIn("private final DanmakuProbe probe", dialog)
        stop = dialog.split("public void onStop()", 1)[1].split("@Override", 1)[0]
        self.assertIn("probe.cancel()", stop)
        self.assertIn("if (testing && binding != null)", stop)
        self.assertIn("public void onDestroyView()", dialog)
        self.assertNotIn("DanmakuApi.cancel()", dialog)

    def test_editor_save_actions_are_outside_scroll(self):
        root = ET.parse(TV / "res/layout/dialog_danmaku_api.xml").getroot()
        self.assertEqual(root.tag, "LinearLayout")
        scroll = next(x for x in root if x.tag.endswith("NestedScrollView"))
        ids = {x.get(A + "id") for x in scroll.iter()}
        self.assertNotIn("@+id/positive", ids)
        self.assertNotIn("@+id/negative", ids)
        self.assertIn("@+id/test", ids)
        self.assertTrue(any(x.get(A + "id") == "@+id/positive" for x in root.iter()))

    def test_editor_uses_one_window_bound_and_explicit_focus_links(self):
        dialog = (TV / "java/com/fongmi/android/tv/ui/dialog/DanmakuApiDialog.java").read_text()
        self.assertIn("extends AppCompatDialogFragment", dialog)
        self.assertIn("dialog.setContentView(binding.getRoot())", dialog)
        self.assertNotIn("MaterialAlertDialogBuilder", dialog)
        root = ET.parse(TV / "res/layout/dialog_danmaku_api.xml").getroot()
        views = {x.get(A + "id"): x for x in root.iter() if x.get(A + "id")}
        self.assertEqual(views["@+id/text"].get(A + "nextFocusDown"), "@id/testName")
        self.assertEqual(views["@+id/testName"].get(A + "nextFocusDown"), "@id/testEpisode")
        self.assertEqual(views["@+id/testEpisode"].get(A + "nextFocusDown"), "@id/test")
        self.assertEqual(views["@+id/test"].get(A + "nextFocusDown"), "@id/positive")
        self.assertEqual(views["@+id/positive"].get(A + "nextFocusUp"), "@id/test")

    def test_tv_overrides_preserve_binding_and_slider_contracts(self):
        for name in ["dialog_danmaku_setting", "view_danmaku_appearance", "view_danmaku_timing", "view_danmaku_density", "view_danmaku_display"]:
            source = ET.parse(MAIN / "res/layout" / (name + ".xml")).getroot()
            target = ET.parse(TV / "res/layout" / (name + ".xml")).getroot()
            original = {x.get(A + "id"): x for x in source.iter() if x.get(A + "id")}
            current = {x.get(A + "id"): x for x in target.iter() if x.get(A + "id")}
            for key, item in original.items():
                with self.subTest(layout=name, id=key):
                    self.assertIn(key, current)
                    self.assertEqual(item.tag, current[key].tag)
                    if item.tag.endswith("Slider"):
                        for attr in [A + "valueFrom", A + "valueTo", A + "stepSize"]:
                            self.assertEqual(item.get(attr), current[key].get(attr))

    def test_tabs_do_not_change_on_focus(self):
        panel = (MAIN / "java/com/fongmi/android/tv/ui/dialog/DanmakuSettingPanel.java").read_text()
        self.assertNotIn("checkOnFocus", panel)
        self.assertIn("addOnButtonCheckedListener", panel)

    def test_all_new_strings_are_localized(self):
        for filename in ["tv_danmaku_strings.xml", "tv_danmaku_playback_strings.xml"]:
            keys = None
            for locale in ["values", "values-zh-rCN", "values-zh-rTW"]:
                root = ET.parse(TV / "res" / locale / filename).getroot()
                current = {x.get("name") for x in root}
                if keys is None: keys = current
                self.assertEqual(keys, current)


if __name__ == "__main__":
    unittest.main()
