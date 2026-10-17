"""Resource/lifecycle contracts complement the emulator UI checks; no device mutations."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
TV = ROOT / "app/src/leanback"
MAIN = ROOT / "app/src/main"
A = "{http://schemas.android.com/apk/res/android}"
APP = "{http://schemas.android.com/apk/res-auto}"
TV_TABS = {"@+id/tabGroup", "@+id/tabAppearance", "@+id/tabTiming", "@+id/tabDensity", "@+id/tabDisplay"}


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
                    if key in TV_TABS: continue
                    self.assertEqual(item.tag, current[key].tag)
                    if item.tag.endswith("Slider"):
                        for attr in [A + "valueFrom", A + "valueTo", A + "stepSize"]:
                            self.assertEqual(item.get(attr), current[key].get(attr))

    def test_tv_tabs_follow_focus_and_mobile_keeps_toggle_group(self):
        panel = (MAIN / "java/com/fongmi/android/tv/ui/dialog/DanmakuSettingPanel.java").read_text()
        tabs = panel.split("private void bindTabs()", 1)[1].split("private View[] tabs()", 1)[0]
        self.assertIn("addOnButtonCheckedListener", tabs)
        self.assertIn("setOnFocusChangeListener", tabs)
        self.assertIn("setNextFocusUpId(tabs[index].getId())", panel)
        root = ET.parse(TV / "res/layout/dialog_danmaku_setting.xml").getroot()
        views = {x.get(A + "id"): x for x in root.iter() if x.get(A + "id")}
        for key in TV_TABS - {"@+id/tabGroup"}:
            self.assertEqual(views[key].get("style"), "@style/TvDanmakuTab")
        styles = (TV / "res/values/tv_danmaku_playback_styles.xml").read_text()
        self.assertIn("@drawable/selector_metadata_tab", styles)
        self.assertIn("@color/selector_metadata_tab_text", styles)

    def test_tv_setting_rows_are_single_line(self):
        for name in ["view_danmaku_appearance", "view_danmaku_timing", "view_danmaku_density"]:
            root = ET.parse(TV / "res/layout" / (name + ".xml")).getroot()
            for row in root:
                slider = [x for x in row if x.tag.endswith("Slider")]
                if not slider: continue
                with self.subTest(layout=name, slider=slider[0].get(A + "id")):
                    # Sub-options share the parent rows' start edge; only their color differs.
                    self.assertEqual(row.get("style"), "@style/TvDanmakuRow")
                    self.assertTrue(any(x.get(A + "id", "").endswith("Value") for x in row))

    def test_tv_playback_uses_full_width_picker(self):
        video = (TV / "java/com/fongmi/android/tv/ui/activity/VideoActivity.java").read_text()
        self.assertIn("DanmakuPickerDialog.create()", video)
        self.assertNotIn("DanmakuDialog.create()", video)
        self.assertIn("danmakuPickerVisible", video.split("private void setR1Callback()", 1)[1].split("}", 1)[0])
        picker = (TV / "java/com/fongmi/android/tv/ui/dialog/DanmakuPickerDialog.java").read_text()
        self.assertIn("setWidth(1f)", picker)
        self.assertIn("Gravity.BOTTOM", picker)
        root = ET.parse(TV / "res/layout/dialog_danmaku_picker.xml").getroot()
        ids = {x.get(A + "id") for x in root.iter()}
        for key in ["@+id/search", "@+id/choose", "@+id/setting", "@+id/grid", "@+id/empty"]:
            self.assertIn(key, ids)
        self.assertNotIn("@+id/title", ids)
        grid = next(x for x in root.iter() if x.get(A + "id") == "@+id/grid")
        self.assertEqual(grid.tag, "androidx.leanback.widget.VerticalGridView")
        self.assertIn("setNumColumns(columns)", picker)
        self.assertIn("count > 1 ? 2 : 1", picker)
        # The vertical grid traps UP unless focus-out is enabled toward the actions.
        self.assertEqual(grid.get(APP + "focusOutFront"), "true")
        self.assertIn("setNextFocusUpId(", picker)
        item = ET.parse(TV / "res/layout/adapter_danmaku_choice.xml").getroot()
        self.assertEqual(item.get(A + "foreground"), "@drawable/selector_detail_choice_indicator_overlay")
        # Chips wrap their name; the 1- or 2-column grid only caps the width.
        self.assertEqual(item.get(A + "layout_width"), "wrap_content")

    def test_tv_local_file_return_releases_picker(self):
        picker = (TV / "java/com/fongmi/android/tv/ui/dialog/DanmakuPickerDialog.java").read_text()
        chosen = picker.split("private void onChosen(", 1)[1].split("\n    }", 1)[0]
        # BACK from the file list must close the picker too, or it keeps OK away from playback.
        self.assertIn("close()", chosen)
        self.assertIn("player.play()", chosen)
        self.assertNotIn("isAdded()", picker.split("private static void setDanmaku(", 1)[1].split("\n    }", 1)[0])

    def test_tv_danmaku_search_is_remote_first(self):
        picker = (TV / "java/com/fongmi/android/tv/ui/dialog/DanmakuPickerDialog.java").read_text()
        self.assertIn("DanmakuFinderDialog.create()", picker)
        self.assertNotIn("getEffectiveApiUrl", picker)
        finder = (TV / "java/com/fongmi/android/tv/ui/dialog/DanmakuFinderDialog.java").read_text()
        init = finder.split("protected void initView()", 1)[1].split("\n    }", 1)[0]
        # Opening searches with the playing title; the keyboard only appears on request.
        self.assertIn("search();", init)
        self.assertNotIn("showKeyboard", finder)
        self.assertIn("DanmakuApiDialog.show(this, keyword", finder)
        root = ET.parse(TV / "res/layout/dialog_danmaku_finder.xml").getroot()
        ids = {x.get(A + "id") for x in root.iter()}
        for key in ["@+id/keywords", "@+id/keyword", "@+id/prev", "@+id/episode", "@+id/next", "@+id/anyEpisode", "@+id/retry", "@+id/configure", "@+id/recycler"]:
            self.assertIn(key, ids)
        api = (TV / "java/com/fongmi/android/tv/ui/dialog/DanmakuApiDialog.java").read_text()
        self.assertIn('binding.testName.setText(getArguments().getString("name", ""))', api)

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
