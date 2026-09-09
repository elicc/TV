#!/usr/bin/env python3
"""TV-only resource/source invariants; APK/device tests remain separate."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/leanback/res"
JAVA = ROOT / "app/src/leanback/java/com/fongmi/android/tv"

class TvThemeTests(unittest.TestCase):
    def test_all_xml_parse(self):
        for path in RES.rglob("*.xml"):
            ET.parse(path)

    def test_semantic_attributes_defined(self):
        import re
        defined = {node.get("name") for node in ET.parse(RES / "values/tv_attrs.xml").iter("attr")}
        used = set()
        for path in RES.rglob("*.xml"):
            used.update(re.findall(r"\?attr/(tv\w+)", path.read_text()))
        self.assertTrue(used)
        self.assertFalse(used - defined)

    def test_hero_art_is_clipped(self):
        android = "{http://schemas.android.com/apk/res/android}"
        root = ET.parse(RES / "layout/adapter_hero.xml").getroot()
        self.assertEqual("true", root.get(android + "clipChildren"))
        poster = next(n for n in root.iter("ImageView") if n.get(android + "id") == "@+id/poster")
        self.assertEqual("true", poster.get(android + "cropToPadding"))
        self.assertEqual("152dp", poster.get(android + "layout_height"))

    def test_keep_empty_transition_has_focus_and_route(self):
        code = (JAVA / "ui/activity/KeepActivity.java").read_text()
        self.assertIn("mAdapter.remove(item.delete(), () ->", code)
        self.assertIn("setPositiveButton(R.string.tv_keep_remove", code)
        self.assertIn("cancel.setFocusableInTouchMode(true)", code)
        self.assertIn("cancel.post(cancel::requestFocus)", code)
        self.assertIn("focusCard(position)", code)
        self.assertIn("mBinding.findFilms.requestFocus()", code)
        self.assertIn("SearchActivity.start(this)", code)
        self.assertIn("mBinding.empty.setVisibility(empty ? View.VISIBLE : View.GONE)", code)

    def test_home_retry_keeps_config_failure_distinct(self):
        code = (JAVA / "ui/activity/HomeActivity.java").read_text()
        self.assertIn("if (mConfigLoading || mLoading) return", code)
        self.assertIn("if (!mOwnConfigEvent && !mConfigLoading && !getHome().isEmpty())", code)
        self.assertIn("else if (mConfigFailed) VodConfig.get().load(getCallback())", code)
        self.assertIn("if (mConfigLoading || mConfigFailed || TextUtils.isEmpty(getConfig().getUrl()))", code)
        hero = (JAVA / "ui/presenter/HeroPresenter.java").read_text()
        for state in ["tv_config_error_title", "tv_content_error_title", "tv_no_content_title", "tv_reload_config"]:
            self.assertIn(state, hero)
        self.assertIn("tv_config_error_body", hero)
        self.assertIn("tv_content_error_body", hero)
        self.assertNotIn("? item.error()", hero)

    def test_sharp_art_never_crops_subject(self):
        hero = (JAVA / "ui/presenter/HeroPresenter.java").read_text()
        self.assertNotIn("CENTER_CROP", hero)
        self.assertNotIn("RequestListener", hero)
        self.assertIn(".fitCenter().into(b.poster)", hero)
        self.assertIn("b.poster.getLayoutParams().height = heroHeight", hero)
        self.assertIn("b.atmosphere.setImage(item.sourceKey(), vod.getPic())", hero)

    def test_large_font_navigation_and_focus_states(self):
        home = (JAVA / "ui/activity/HomeActivity.java").read_text()
        self.assertIn("LinearLayout.VERTICAL : LinearLayout.HORIZONTAL", home)
        self.assertIn("!mBinding.utilities.hasFocus()", home)
        keep = (JAVA / "ui/adapter/KeepAdapter.java").read_text()
        self.assertNotIn("setSelected(true)", keep)
        self.assertNotIn("1.1f", keep)
        style = next(s for s in ET.parse(RES / "values/tv_home_styles.xml").iter("style") if s.get("name") == "Tv.HeroAction")
        items = {i.get("name"): i.text for i in style}
        self.assertEqual("8dp", items["android:paddingTop"])
        self.assertEqual("8dp", items["android:paddingBottom"])

    def test_targeted_legacy_pages_use_semantic_text(self):
        for name in ["activity_setting_player.xml", "activity_setting_decode.xml", "dialog_config.xml", "dialog_mpv_conf.xml"]:
            text = (RES / "layout" / name).read_text()
            self.assertNotIn('@color/white', text)
            self.assertIn('?attr/tvColorTextPrimary', text)

    def test_translations_match(self):
        for filename in ["tv_home_strings.xml", "tv_settings_strings.xml", "tv_theme_strings.xml", "tv_playback_strings.xml", "tv_keep_strings.xml"]:
            base = {n.get("name") for n in ET.parse(RES / "values" / filename).iter("string")}
            for locale in ["values-zh-rCN", "values-zh-rTW"]:
                self.assertEqual(base, {n.get("name") for n in ET.parse(RES / locale / filename).iter("string")})

    def test_focus_motion_and_skin_colors(self):
        colors = (RES / "values/tv_colors.xml").read_text()
        for token in ["tv_focus_ice", "tv_focus_jade", "#E7C995"]:
            self.assertIn(token, colors)
        styles = (RES / "values/tv_styles.xml").read_text()
        self.assertIn('tvColorFocus">@color/tv_focus_ice', styles)
        self.assertIn('tvColorFocus">@color/tv_focus_jade', styles)
        animator = (RES / "animator/tv_focus_scale.xml").read_text()
        self.assertIn("valueTo=\"1.025\"", animator)
        self.assertIn("duration=\"140\"", animator)
        home = (RES / "values/tv_home_styles.xml").read_text()
        self.assertIn("@animator/tv_focus_scale", home)

    def test_palette_contrast(self):
        colors = {n.get("name"): n.text for n in ET.parse(RES / "values/tv_colors.xml").iter("color")}
        def luminance(name):
            value = colors[name].lstrip("#")[-6:]
            rgb = [int(value[i:i+2], 16) / 255 for i in (0, 2, 4)]
            linear = [c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in rgb]
            return sum(a * b for a, b in zip(linear, (.2126, .7152, .0722)))
        def ratio(a, b):
            x, y = sorted([luminance(a), luminance(b)])
            return (y + .05) / (x + .05)
        for surface in ["tv_background", "tv_surface", "tv_surface_raised"]:
            self.assertGreaterEqual(ratio("tv_text_primary", surface), 4.5)
            self.assertGreaterEqual(ratio("tv_text_secondary", surface), 4.5)
            self.assertGreaterEqual(ratio("tv_focus", surface), 3)
        for skin in ["champagne", "ice", "jade"]:
            self.assertGreaterEqual(ratio("tv_on_accent", "tv_accent_" + skin), 4.5)
            self.assertGreaterEqual(ratio("tv_accent_" + skin, "tv_selected_" + skin), 4.5)

    def test_theme_before_inflation_and_player_exclusion(self):
        code = (JAVA / "ui/base/BaseActivity.java").read_text()
        self.assertLess(code.index("setTheme(TvTheme.getThemeRes())"), code.index("setContentView(getBinding()"))
        self.assertIn("!(this instanceof PlaybackActivity)", code)
        self.assertIn("appliedAtmosphere != TvTheme.isAtmosphereEnabled()", code)

    def test_home_route_and_lifecycle_guards(self):
        code = (JAVA / "ui/activity/HomeActivity.java").read_text()
        for route in ["VodActivity.start", "LiveActivity.start", "KeepActivity.start", "PushActivity.start", "SearchActivity.start", "SettingActivity.start", "SiteDialog.create", "FileChooser.from"]:
            self.assertIn(route, code)
        self.assertIn("getCurrentFocus() == mBinding.recycler", code)
        self.assertNotIn("CustomTitleView", (RES / "layout/activity_home.xml").read_text())
        self.assertIn("mBinding.title.setOnClickListener", code)
        self.assertIn("home.actionHandled", code)
        self.assertIn("if (!isChangingConfigurations())", code)
        self.assertIn("position != getHistoryIndex()", code)

if __name__ == "__main__":
    unittest.main(verbosity=2)
