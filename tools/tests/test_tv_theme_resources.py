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
        for token in ["tv_focus_ice", "tv_focus_jade", "#FCD58B", "#E5A958"]:
            self.assertIn(token, colors)
        styles = (RES / "values/tv_styles.xml").read_text()
        self.assertIn('tvColorFocus">@color/tv_focus_ice', styles)
        self.assertIn('tvColorFocus">@color/tv_focus_jade', styles)
        animator = (RES / "animator/tv_focus_scale.xml").read_text()
        self.assertIn("valueTo=\"1.05\"", animator)
        self.assertIn("duration=\"160\"", animator)
        home = (RES / "values/tv_home_styles.xml").read_text()
        self.assertIn("@animator/tv_focus_scale", home)

    def test_palette_contrast(self):
        colors = {n.get("name"): n.text for n in ET.parse(RES / "values/tv_colors.xml").iter("color")}
        aliases = {name: value.split("/")[-1] for name, value in colors.items() if value.startswith("@color/")}
        def luminance(name):
            while name in aliases:
                name = aliases[name]
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

    def test_stitch_h2_tokens(self):
        colors = {n.get("name"): n.text for n in ET.parse(RES / "values/tv_colors.xml").iter("color")}
        self.assertEqual("#0E0E10", colors["tv_bg"])
        self.assertEqual("#202024", colors["tv_surface"])
        self.assertEqual("#2A2A30", colors["tv_surface_high"])
        self.assertEqual("#F3F3F6", colors["tv_text_primary"])
        self.assertEqual("#E5A958", colors["tv_accent"])
        self.assertEqual("#FCD58B", colors["tv_focus"])
        dimens = {n.get("name"): n.text for n in ET.parse(RES / "values/tv_dimens.xml").iter("dimen")}
        for name, value in {
            "tv_overscan_h": "36dp",
            "tv_overscan_v": "24dp",
            "tv_focus_stroke": "3.5dp",
            "tv_radius_card": "16dp",
            "tv_radius_chip": "20dp",
            "tv_text_card_title": "20sp",
            "tv_text_hero": "38sp",
            "tv_text_display": "48sp",
        }.items():
            self.assertEqual(value, dimens[name])

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

    def test_home_consumes_stitch_safe_area_and_truthful_source_status(self):
        android = "{http://schemas.android.com/apk/res/android}"
        root = ET.parse(RES / "layout/activity_home.xml").getroot()
        toolbar = next(n for n in root.iter("LinearLayout") if n.get(android + "id") == "@+id/toolbar")
        recycler = next(n for n in root.iter() if n.get(android + "id") == "@+id/recycler")
        self.assertEqual("60dp", toolbar.get(android + "minHeight"))
        self.assertEqual("@dimen/tv_safe_horizontal", toolbar.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_horizontal", recycler.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_vertical", recycler.get(android + "paddingBottom"))
        source = next(n for n in root.iter("View") if n.get(android + "id") == "@+id/sourceStatus")
        self.assertEqual("8dp", source.get(android + "layout_width"))
        remote = next(n for n in root.iter("TextView") if n.get(android + "id") == "@+id/remoteHint")
        self.assertEqual("24dp", remote.get(android + "layout_height"))
        self.assertEqual("@string/tv_home_remote_hint", remote.get(android + "text"))
        code = (JAVA / "ui/activity/HomeActivity.java").read_text()
        self.assertIn("!TextUtils.isEmpty(getConfig().getUrl()) && !mConfigFailed", code)
        self.assertIn("R.color.tv_success : R.color.tv_danger", code)

    def test_vod_uses_stitch_safe_area_and_card_typography(self):
        android = "{http://schemas.android.com/apk/res/android}"
        activity = ET.parse(RES / "layout/activity_vod.xml").getroot()
        tabs = next(n for n in activity.iter() if n.get(android + "id") == "@+id/recycler")
        self.assertEqual("@dimen/tv_safe_horizontal", tabs.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_vertical", tabs.get(android + "paddingTop"))
        fragment = ET.parse(RES / "layout/fragment_type.xml").getroot()
        grid = next(n for n in fragment.iter() if n.get(android + "id") == "@+id/recycler")
        self.assertEqual("@dimen/tv_safe_horizontal", grid.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_vertical", grid.get(android + "paddingBottom"))
        for filename in ["adapter_vod.xml", "adapter_vod_list.xml", "adapter_vod_rect.xml", "adapter_vod_oval.xml"]:
            root = ET.parse(RES / "layout" / filename).getroot()
            title = next(n for n in root.iter() if n.get(android + "id") == "@+id/name")
            self.assertEqual("@dimen/tv_text_card_title", title.get(android + "textSize"), filename)

    def test_detail_uses_stitch_display_hierarchy_and_safe_chips(self):
        android = "{http://schemas.android.com/apk/res/android}"
        root = ET.parse(RES / "layout/activity_video.xml").getroot()
        video = next(n for n in root.iter() if n.get(android + "id") == "@+id/video")
        self.assertEqual("@dimen/tv_safe_horizontal", video.get(android + "layout_marginStart"))
        self.assertEqual("@dimen/tv_safe_vertical", video.get(android + "layout_marginTop"))
        name = next(n for n in root.iter() if n.get(android + "id") == "@+id/name")
        self.assertEqual("@dimen/tv_text_display", name.get(android + "textSize"))
        for chip_id in ["flag", "quality", "episode", "array", "part", "quick"]:
            chip = next(n for n in root.iter() if n.get(android + "id") == "@+id/" + chip_id)
            self.assertEqual("@dimen/tv_safe_horizontal", chip.get(android + "paddingStart"), chip_id)

    def test_live_drawer_and_osd_use_stitch_tokens(self):
        android = "{http://schemas.android.com/apk/res/android}"
        activity = ET.parse(RES / "layout/activity_live.xml").getroot()
        drawer = next(n for n in activity.iter() if n.get(android + "id") == "@+id/recycler")
        self.assertEqual("@dimen/tv_safe_horizontal", drawer.get(android + "layout_marginStart"))
        self.assertEqual("@dimen/tv_safe_vertical", drawer.get(android + "layout_marginTop"))
        self.assertEqual("@dimen/tv_safe_vertical", drawer.get(android + "layout_marginBottom"))
        self.assertEqual("@drawable/tv_setting_panel", drawer.get(android + "background"))

        for filename, title_id in [
            ("adapter_group.xml", "name"),
            ("adapter_channel.xml", "name"),
            ("adapter_epg_data.xml", "title"),
        ]:
            root = ET.parse(RES / "layout" / filename).getroot()
            title = next(n for n in root.iter() if n.get(android + "id") == "@+id/" + title_id)
            self.assertEqual("@dimen/tv_text_card_title", title.get(android + "textSize"), filename)
            self.assertEqual("@animator/tv_focus_scale", root.get(android + "stateListAnimator"), filename)

        widget = ET.parse(RES / "layout/view_widget_live.xml").getroot()
        top = next(n for n in widget.iter() if n.get(android + "id") == "@+id/top")
        bottom = next(n for n in widget.iter() if n.get(android + "id") == "@+id/bottom")
        self.assertEqual("@dimen/tv_safe_horizontal", top.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_vertical", top.get(android + "paddingTop"))
        self.assertEqual("@dimen/tv_safe_horizontal", bottom.get(android + "paddingStart"))

        control = ET.parse(RES / "layout/view_control_live.xml").getroot()
        self.assertEqual("@dimen/tv_safe_horizontal", control.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_horizontal", control.get(android + "paddingEnd"))
        self.assertEqual("@dimen/tv_safe_vertical", control.get(android + "paddingBottom"))

        code = (JAVA / "ui/activity/LiveActivity.java").read_text()
        self.assertEqual(1, code.count("ResUtil.getTextWidth(item.getName(), 20)"))
        self.assertIn("ResUtil.getTextWidth(item.getNumber() + item.getName(), 20)", code)
        self.assertIn("ResUtil.getTextWidth(epg.getList().get(0).getTime(), 16)", code)
        self.assertIn("ResUtil.getTextWidth(item.getTitle(), 20)", code)

    def test_vod_player_osd_uses_stitch_safe_area(self):
        android = "{http://schemas.android.com/apk/res/android}"
        control = ET.parse(RES / "layout/view_control_vod.xml").getroot()
        self.assertEqual("@dimen/tv_safe_horizontal", control.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_horizontal", control.get(android + "paddingEnd"))
        self.assertEqual("@dimen/tv_safe_vertical", control.get(android + "paddingBottom"))

        widget = ET.parse(RES / "layout/view_widget_vod.xml").getroot()
        top = next(n for n in widget.iter() if n.get(android + "id") == "@+id/top")
        self.assertEqual("@dimen/tv_safe_horizontal", top.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_vertical", top.get(android + "paddingTop"))
        self.assertEqual("@dimen/tv_safe_horizontal", top.get(android + "paddingEnd"))

    def test_secondary_tv_pages_use_stitch_safe_area_and_readable_text(self):
        android = "{http://schemas.android.com/apk/res/android}"
        for filename in ["activity_search.xml", "activity_setting.xml", "activity_crash.xml"]:
            root = ET.parse(RES / "layout" / filename).getroot()
            container = root if filename != "activity_setting.xml" else next(root.iter("androidx.appcompat.widget.LinearLayoutCompat"))
            self.assertEqual("@dimen/tv_safe_horizontal", container.get(android + "paddingStart"), filename)
            self.assertEqual("@dimen/tv_safe_vertical", container.get(android + "paddingTop"), filename)
        for filename in ["activity_collect.xml", "activity_file.xml", "activity_keep.xml"]:
            text = (RES / "layout" / filename).read_text()
            self.assertIn("@dimen/tv_safe_horizontal", text, filename)
            self.assertIn("@dimen/tv_safe_vertical", text, filename)
        search = ET.parse(RES / "layout/activity_search.xml")
        for node in search.iter():
            value = node.get(android + "textSize", "")
            if value.endswith("sp"):
                self.assertGreaterEqual(float(value[:-2]), 16, (node.get(android + "id"), value))

    def test_settings_uses_three_column_focusable_rail_and_preserves_actions(self):
        android = "{http://schemas.android.com/apk/res/android}"
        root = ET.parse(RES / "layout/activity_setting.xml").getroot()
        ids = {node.get(android + "id") for node in root.iter() if node.get(android + "id")}
        required = {
            "@+id/vod", "@+id/vodUrl", "@+id/vodHome", "@+id/vodHistory",
            "@+id/live", "@+id/liveUrl", "@+id/liveHome", "@+id/liveHistory",
            "@+id/doh", "@+id/dohText", "@+id/skin", "@+id/skinText",
            "@+id/atmosphere", "@+id/atmosphereText", "@+id/wall", "@+id/wallUrl",
            "@+id/wallDefault", "@+id/wallRefresh", "@+id/size", "@+id/sizeText",
            "@+id/player", "@+id/danmaku", "@+id/incognito", "@+id/incognitoText",
            "@+id/backup", "@+id/restore", "@+id/cache", "@+id/cacheText",
            "@+id/version", "@+id/versionText",
        }
        self.assertTrue(required <= ids)
        for nav in ["navContent", "navAppearance", "navPlayback", "navData", "navAbout"]:
            node = next(n for n in root.iter() if n.get(android + "id") == "@+id/" + nav)
            self.assertEqual("@style/Tv.Setting.Nav", node.get("style"), nav)
        self.assertIsNotNone(next(n for n in root.iter() if n.get(android + "id") == "@+id/settingsScroll"))
        self.assertIsNotNone(next(n for n in root.iter() if n.get(android + "id") == "@+id/utilityScroll"))
        code = (JAVA / "ui/activity/SettingActivity.java").read_text()
        for method in ["bindSectionNavigation", "bindSectionFocus", "selectNavigation", "scrollToSection"]:
            self.assertIn(method, code)
        self.assertIn("mBinding.settingBack.setOnClickListener", code)

    def test_push_and_crash_actions_have_dpad_focus_contract(self):
        android = "{http://schemas.android.com/apk/res/android}"
        for filename, ids in [("activity_push.xml", ["@+id/code", "@+id/clip"]), ("activity_crash.xml", ["@+id/restart", "@+id/details"])]:
            root = ET.parse(RES / "layout" / filename).getroot()
            nodes = {n.get(android + "id"): n for n in root.iter()}
            for view_id in ids:
                self.assertEqual("true", nodes[view_id].get(android + "focusable"), (filename, view_id))
                self.assertEqual("@animator/tv_focus_scale", nodes[view_id].get(android + "stateListAnimator"), (filename, view_id))
        push = (JAVA / "ui/activity/PushActivity.java").read_text()
        self.assertIn("mBinding.code.setContentDescription", push)
        self.assertIn("mBinding.code.requestFocus()", push)

if __name__ == "__main__":
    unittest.main(verbosity=2)
