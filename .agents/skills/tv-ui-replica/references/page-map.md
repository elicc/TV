# 设计页面与 Android 实现映射

该表用于缩小检查范围，不替代代码搜索。页面职责以当前代码为准，发生改名时通过 ViewBinding 引用和 Manifest 重新定位。

| 用户常用称呼 / Stitch 目录关键词 | tv-ui-architect 参考 | 主要 Android 入口 | 常见关联资源 |
| --- | --- | --- | --- |
| 首页、Home、首页动效、首页焦点 | `App.tsx`, `Header.tsx`, `HeroSection.tsx`, `VodRails.tsx`, `FooterHint.tsx` | `HomeActivity`, home presenters | `activity_home.xml`, `adapter_hero.xml`, `adapter_history.xml`, `adapter_vod_*.xml`, `TvKeycapsBar`, `TvMotion`, `TvStagger` |
| 首页空源、点播/直播/WebDAV 未配置 | `SpecBoard.tsx` 及对应页面状态 | `HomeActivity`, `EmptySourcePresenter` | `view_empty_source.xml`, `selector_empty_card.xml`, `tv_home_strings.xml` |
| 点播分类、影视库、VOD | `VodView.tsx`, `VodRails.tsx` | `VodActivity`, `FolderFragment`, `TypeFragment` | `activity_vod.xml`, `fragment_folder.xml`, `adapter_type.xml`, `adapter_vod_*.xml` |
| 详情页、详情焦点状态 | `DetailView.tsx` | `VideoActivity` 的非全屏详情态 | `activity_video.xml`, `adapter_flag.xml`, `adapter_quality.xml`, `adapter_episode.xml`, `adapter_array.xml`, `adapter_part.xml`, `adapter_quick.xml` |
| 播放器、OSD、播放设置 | `PlayerModal.tsx` | `VideoActivity`, `PlaybackActivity`, VOD playback controller | `view_widget_vod.xml`, `view_control_vod*.xml`, `exo_player_seek_view.xml`, player dialogs/panels |
| 直播、频道、EPG | `LiveView.tsx`, `PlayerModal.tsx` | `LiveActivity` | `activity_live.xml`, `adapter_group.xml`, `adapter_channel.xml`, `adapter_epg_data.xml`, `dialog_live.xml` |
| 搜索、软键盘 | `SearchView.tsx` | `SearchActivity`; 聚合结果进入 `CollectActivity` | `activity_search.xml`, `adapter_keyboard_*.xml`, `adapter_word.xml`, `activity_collect.xml`, `CollectFragment` |
| 收藏与观看历史 | `FavoritesView.tsx`；首页 rail | `KeepActivity` 负责收藏，`HomeActivity` 负责历史 rail | `activity_keep.xml`, `adapter_vod.xml`（由 `KeepAdapter` 复用）, `adapter_history.xml`, `HistoryDialog` |
| 设置首页 | `SettingsView.tsx` | `SettingActivity` | `activity_setting.xml`, `tv_setting_styles.xml`, config/live/site dialogs |
| 设置 API/VOD 详情或完整配置 | `SettingsView.tsx` 的 API/VOD 状态 | `SettingActivity`, `ConfigDialog`, `SiteDialog` | `activity_setting.xml`, `dialog_config.xml`, `dialog_site.xml`, `adapter_config.xml`, `adapter_site.xml` |
| 设置 IPTV/直播源 | `SettingsView.tsx` 的 IPTV 状态 | `SettingActivity`, `LiveDialog` | `activity_setting.xml`, `dialog_live.xml`, `adapter_live.xml` |
| 嗅探设置 | `SettingsView.tsx` 的 sniffer 状态 | 先搜索现有 sniffer/parse 设置入口；不要假设独立 Activity | `Sniffer`, parse/player setting dialogs 和相关 string/layout |
| 工具、更多 | `SettingsView.tsx` 或 App 导航意图 | `HomeActivity` 的 More/`FuncPresenter` 及 Push/File/Cast 入口 | `adapter_func.xml`, `PushActivity`, `FileActivity`, `CastActivity` |
| 全局 Toast/通知状态 | 无单一页面组件 | `Notify` 及各 Activity 的错误/成功回调 | toast/widget/error drawable、strings；先检索调用方 |

## 容易误判的名称

- `CollectActivity` 是跨站搜索结果聚合页，不是“收藏”页；收藏由 `KeepActivity`/`Keep` 数据承担。
- `VideoActivity` 同时承载详情、选集和播放切换，详情设计不能只改播放器 overlay。
- Stitch 的多个 `focus_specs_*` 目录通常是同一页面的不同焦点状态，应合并验证，而不是创建多个页面。
- `settings_api_*`、`settings_iptv_*` 和 `settings_sniffer_*` 是设置子状态；先确认当前项目把能力放在主页面、Dialog 还是独立页面，再决定修改范围。
- `tv-ui-architect` 是交互参考应用，不是 Android 模块；不得修改它来代替产品实现。
