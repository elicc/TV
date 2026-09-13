#!/usr/bin/env python3
"""TV-only resource/source invariants; APK/device tests remain separate."""
from pathlib import Path
import subprocess
import sys
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/leanback/res"
JAVA = ROOT / "app/src/leanback/java/com/fongmi/android/tv"
MAIN_JAVA = ROOT / "app/src/main/java/com/fongmi/android/tv"

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
        self.assertIn('tvColorFocusGlow">@color/tv_focus_glow_ice', styles)
        self.assertIn('tvColorFocusGlow">@color/tv_focus_glow_jade', styles)
        animator = (RES / "animator/tv_focus_scale.xml").read_text()
        self.assertIn("valueTo=\"1.05\"", animator)
        self.assertIn("valueTo=\"0.98\"", animator)
        self.assertIn("propertyName=\"translationZ\"", animator)
        self.assertIn("@integer/tv_motion_focus_on", animator)
        self.assertIn("@interpolator/tv_interp_emphasis", animator)
        home = (RES / "values/tv_home_styles.xml").read_text()
        self.assertIn("@animator/tv_focus_scale", home)
        # Every focus ring carries the halo band via a second glow stroke.
        for shape in (RES / "drawable").glob("shape_*_focused.xml"):
            text = shape.read_text()
            self.assertIn("tvColorFocusGlow", text, shape.name)
            self.assertIn("tv_glow_band", text, shape.name)
        # The hero primary action and the home nav also pick up the halo.
        for name in ["tv_hero_primary.xml", "tv_home_nav.xml"]:
            text = (RES / "drawable" / name).read_text()
            self.assertIn("tvColorFocusGlow", text, name)

    def test_home_nav_selected_state_matches_stitch_glass(self):
        colors = (RES / "values/tv_colors.xml").read_text()
        self.assertIn('<color name="tv_nav_selected">#3E3937</color>', colors)
        self.assertIn('<color name="tv_nav_selected_border">#0DFFFFFF</color>', colors)
        nav = (RES / "drawable/tv_nav_pill.xml").read_text()
        self.assertEqual(1, nav.count('@color/tv_nav_selected"'))
        self.assertEqual(2, nav.count('@color/tv_accent"'))
        self.assertIn('@color/tv_nav_selected_border', nav)
        text = (RES / "color/nav_text_color.xml").read_text()
        icons = (RES / "color/nav_icon_color.xml").read_text()
        self.assertIn('android:state_focused="true" android:color="?attr/tvColorOnAccent"', text)
        self.assertIn('android:state_focused="true" android:color="?attr/tvColorOnAccent"', icons)
        self.assertIn('android:state_selected="true" android:color="?attr/tvColorTextPrimary"', text)
        self.assertIn('android:state_selected="true" android:color="?attr/tvColorTextPrimary"', icons)

    def test_tv_page_icons_are_generated_from_lucide(self):
        sources = {
            "ic_nav_home": ("home.svg", 20),
            "ic_nav_vod": ("film.svg", 20),
            "ic_nav_live": ("tv.svg", 20),
            "ic_nav_keep": ("bookmark.svg", 20),
            "ic_nav_search": ("search.svg", 20),
            "ic_nav_more": ("more-horizontal.svg", 20),
            "ic_nav_settings": ("settings.svg", 20),
            "ic_empty_film": ("film.svg", 20),
            "ic_empty_broadcast": ("radio-tower.svg", 20),
            "ic_empty_cloud": ("cloud.svg", 20),
            "ic_setting_nav_content": ("database.svg", 24),
            "ic_setting_nav_appearance": ("palette.svg", 24),
            "ic_setting_nav_playback": ("play-circle.svg", 24),
            "ic_setting_nav_data": ("shield-check.svg", 24),
            "ic_setting_nav_about": ("info.svg", 24),
            "ic_setting_back": ("chevron-left.svg", 16),
        }
        for target, (source, size) in sources.items():
            vector = (RES / f"drawable/{target}.xml").read_text()
            self.assertIn(f"Generated from Lucide 0.344.0 {source}", vector)
            self.assertIn(f'android:width="{size}dp"', vector)
            self.assertIn(f'android:height="{size}dp"', vector)
            self.assertIn('android:viewportWidth="24"', vector)
            self.assertIn('android:strokeWidth="2"', vector)
            self.assertIn('android:strokeLineCap="round"', vector)
            self.assertIn('android:strokeLineJoin="round"', vector)
        subprocess.run(
            [sys.executable, ROOT / "tools/lucide/generate_android_vectors.py", "--check"],
            cwd=ROOT,
            check=True,
        )

    def test_setting_nav_uses_dedicated_lucide_icons(self):
        expected = {
            "navContent": "ic_setting_nav_content",
            "navAppearance": "ic_setting_nav_appearance",
            "navPlayback": "ic_setting_nav_playback",
            "navData": "ic_setting_nav_data",
            "navAbout": "ic_setting_nav_about",
        }
        root = ET.parse(RES / "layout/activity_setting.xml").getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        actual = set()
        for view_id, drawable in expected.items():
            view = next(n for n in root.iter() if n.get(android + "id") == "@+id/" + view_id)
            self.assertEqual("@drawable/" + drawable, view.get(android + "drawableStart"))
            actual.add(view.get(android + "drawableStart"))
        for legacy in ["ic_nav_vod", "ic_action_setting", "ic_widget_play", "ic_empty_cloud", "ic_action_debug"]:
            self.assertNotIn("@drawable/" + legacy, actual)

    def test_setting_back_uses_unclipped_lucide_icon(self):
        android = "{http://schemas.android.com/apk/res/android}"
        root = ET.parse(RES / "layout/activity_setting.xml").getroot()
        back = next(n for n in root.iter() if n.get(android + "id") == "@+id/settingBack")
        self.assertEqual("@drawable/ic_setting_back", back.get(android + "drawableStart"))

        style = next(s for s in ET.parse(RES / "values/tv_setting_styles.xml").iter("style") if s.get("name") == "Tv.Setting.Back")
        items = {item.get("name"): item.text for item in style}
        self.assertEqual("@drawable/tv_setting_back_selector", items["android:background"])
        self.assertEqual("4dp", items["android:drawablePadding"])
        self.assertEqual("24dp", items["android:layout_height"])

        for folder in ["values", "values-zh-rCN", "values-zh-rTW"]:
            strings = ET.parse(RES / folder / "tv_settings_strings.xml")
            label = next(n for n in strings.iter("string") if n.get("name") == "tv_setting_back")
            self.assertNotIn("‹", label.text)

        selector = (RES / "drawable/tv_setting_back_selector.xml").read_text()
        self.assertNotIn("<padding", selector)

    def test_leanback_source_confirmation_recovers_after_file_permission(self):
        dialog = (JAVA / "ui/dialog/ConfigDialog.java").read_text()
        layout = (RES / "layout/dialog_config.xml").read_text()
        self.assertIn("PermissionUtil.requestFile(this, allGranted ->", dialog)
        self.assertIn("if (!allGranted || !isAdded()) return", dialog)
        self.assertIn("SourceBootstrap.find(type", dialog)
        self.assertIn("FileChooser.from(launcher).show()", dialog)
        self.assertIn('ContentResolver.SCHEME_FILE.equalsIgnoreCase(UrlUtil.scheme(text))', dialog)
        self.assertIn("text.isEmpty() && isSourceType()", dialog)
        self.assertIn("firstSourceSetup ? View.GONE : View.VISIBLE", dialog)
        self.assertIn("updatePositiveText(s.toString())", dialog)
        self.assertIn("TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim())", dialog)
        self.assertIn("R.string.tv_config_continue", dialog)
        self.assertIn("R.string.tv_config_apply", dialog)
        self.assertIn('android:text="@string/tv_config_browse_local"', layout)
        self.assertIn('android:layout_weight="2"', layout)
        self.assertEqual(3, layout.count('android:paddingStart="8dp"'))
        self.assertEqual(3, layout.count('android:paddingEnd="8dp"'))

        setting = (JAVA / "ui/activity/SettingActivity.java").read_text()
        self.assertIn("if (allGranted) load(config)", setting)

    def test_source_bootstrap_is_versioned_atomic_and_source_only(self):
        bootstrap = (MAIN_JAVA / "db/SourceBootstrap.java").read_text()
        self.assertIn('FILE_NAME = "source-bootstrap.json"', bootstrap)
        self.assertIn("VERSION = 1", bootstrap)
        self.assertIn("FileUtil.writeAtomically", bootstrap)
        self.assertIn("snapshot.add(0)", bootstrap)
        self.assertIn("snapshot.add(1)", bootstrap)
        self.assertNotIn("Config.wall()", bootstrap)
        self.assertIn('Prefers.getString("config_" + type)', bootstrap)
        self.assertIn("getConfigDao().find(url, type)", bootstrap)
        self.assertIn("MAX_BYTES", bootstrap)

        base_config = (MAIN_JAVA / "api/config/BaseConfig.java").read_text()
        self.assertIn("SourceBootstrap.save()", base_config)
        for name in ["VodConfig.java", "LiveConfig.java"]:
            config = (MAIN_JAVA / "api/config" / name).read_text()
            self.assertIn("SourceBootstrap.save()", config, name)

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
        self.assertEqual("36dp", toolbar.get(android + "minHeight"))
        self.assertEqual("28dp", toolbar.get(android + "layout_marginTop"))
        self.assertEqual("@dimen/tv_safe_horizontal", toolbar.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_horizontal", recycler.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_vertical", recycler.get(android + "paddingBottom"))
        source = next(n for n in root.iter("View") if n.get(android + "id") == "@+id/sourceStatus")
        self.assertEqual("4dp", source.get(android + "layout_width"))
        keycaps = next(n for n in root.iter("com.fongmi.android.tv.ui.custom.TvKeycapsBar") if n.get(android + "id") == "@+id/keycaps")
        self.assertEqual("@dimen/tv_safe_horizontal", keycaps.get(android + "paddingStart"))
        self.assertEqual("@dimen/tv_safe_horizontal", keycaps.get(android + "paddingEnd"))
        self.assertEqual("24dp", keycaps.get(android + "layout_marginBottom"))
        code = (JAVA / "ui/activity/HomeActivity.java").read_text()
        self.assertIn("getString(R.string.tv_source_unconfigured)", code)
        self.assertIn("TvTheme.color(this, R.attr.tvColorAccent)", code)
        self.assertIn('.format("HH:mm")', code)
        self.assertIn("mBinding.clock.setVisibility(View.VISIBLE)", code)

    def test_home_empty_source_uses_reference_geometry_and_initial_focus(self):
        android = "{http://schemas.android.com/apk/res/android}"
        root = ET.parse(RES / "layout/view_empty_source.xml").getroot()
        self.assertEqual("428dp", root.get(android + "layout_height"))
        cards = next(n for n in root.iter("LinearLayout") if n.get(android + "id") == "@+id/cards")
        self.assertEqual("false", cards.get(android + "clipChildren"))
        includes = list(cards.iter("include"))
        self.assertEqual(["264dp"] * 3, [n.get(android + "layout_width") for n in includes])
        self.assertEqual(["112dp"] * 3, [n.get(android + "layout_height") for n in includes])

        card = ET.parse(RES / "layout/view_empty_source_card.xml").getroot()
        self.assertEqual("@drawable/selector_empty_card", card.get(android + "background"))
        self.assertEqual("false", card.get(android + "clipChildren"))
        icon_box = next(n for n in card.iter("FrameLayout") if n.get(android + "id") == "@+id/iconBox")
        self.assertEqual("40dp", icon_box.get(android + "layout_width"))

        home = (JAVA / "ui/activity/HomeActivity.java").read_text()
        presenter = (JAVA / "ui/presenter/EmptySourcePresenter.java").read_text()
        self.assertIn("findViewById(R.id.cardVod)", home)
        self.assertIn("return !hasConfiguredSource();", home)
        self.assertIn("if (!empty) mAdapter.add(R.string.home_recommend);", home)
        self.assertIn("if (empty && mAdapter.size() > 1) mAdapter.removeItems(1, mAdapter.size() - 1);", home)
        self.assertIn("card.iconBox.setBackgroundResource", presenter)
        self.assertIn("card.getRoot().setNextFocusDownId(card.getRoot().getId());", presenter)
        self.assertIn("Action.LIVE, false, true", presenter)

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
        self.assertEqual("false", root.get(android + "clipChildren"))
        self.assertEqual("false", root.get(android + "clipToPadding"))
        header = list(root)[0]
        self.assertEqual("false", header.get(android + "clipChildren"))
        self.assertEqual("false", header.get(android + "clipToPadding"))
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
        for scroll_id in ["settingsScroll", "utilityScroll"]:
            scroller = next(n for n in root.iter() if n.get(android + "id") == "@+id/" + scroll_id)
            self.assertEqual("false", scroller.get(android + "clipChildren"), scroll_id)
            self.assertEqual("false", scroller.get(android + "clipToPadding"), scroll_id)
            self.assertEqual("0dp", scroller.get(android + "paddingStart"), scroll_id)
            self.assertEqual("0dp", scroller.get(android + "paddingEnd"), scroll_id)
            self.assertEqual("11dp", scroller.get(android + "paddingTop"), scroll_id)
            self.assertEqual("11dp", scroller.get(android + "paddingBottom"), scroll_id)
            self.assertEqual("match_parent", scroller.get(android + "layout_width"), scroll_id)
            self.assertEqual("match_parent", scroller.get(android + "layout_height"), scroll_id)
            content = list(scroller)[0]
            self.assertEqual("false", content.get(android + "clipChildren"), scroll_id)
            self.assertEqual("false", content.get(android + "clipToPadding"), scroll_id)
            self.assertEqual("11dp", content.get(android + "paddingStart"), scroll_id)
            self.assertEqual("11dp", content.get(android + "paddingEnd"), scroll_id)
        for panel_id in ["settingsPanel", "utilityPanel"]:
            panel = next(n for n in root.iter("FrameLayout") if n.get(android + "id") == "@+id/" + panel_id)
            self.assertEqual("true", panel.get(android + "clipChildren"), panel_id)
            self.assertEqual("true", panel.get(android + "clipToPadding"), panel_id)
            self.assertEqual("@drawable/tv_setting_panel_frame", panel.get(android + "background"), panel_id)
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
