# FongMi TV 设计稿 vs 实现 一致性审计

> **审查日期**：2026-09-10
> **设计稿目录**：`docs/design-stitch/stitch_fongmi_tv_ui_design/`
> **审查范围**：所有 11 个页面设计稿与 `app/src/leanback/`` 对应实现
> **设计规范核心**：`champagne_cinema_leanback/DESIGN.md`（Champagne Dark v1.x）

---

## 一、总结论（TL;DR）

- 设计系统**颜色令牌与实现存在系统性偏差**（5 处核心 surface/text 颜色不一致），与 `champagne_cinema_leanback/DESIGN.md` 标定的 Token 不对齐。
- 焦点态基础三要素（3.5dp stroke / 25dp glow / 1.05x scale）实现完整，但**导航 pill、卡片圆角、按钮尺寸等视觉单元与设计稿形态差异显著**。
- **字号阶梯与字号语义基本一致**（Hero/Title/Card/Body/Caption 与 sp 体系对齐）。
- 各页面布局与设计稿**结构差异巨大**：实现偏列表/网格堆叠，设计稿偏单屏 2D 平面分区 + HUD 浮层。
- **Player 控件与设计稿差异最大**：实现用 Media3 默认 seekbar + 单一 HorizontalScrollView 控制条，设计的渐变进度条 + 多分组按钮行未实现。
- **Tools、Live、Search、Detail 与设计稿结构性不匹配**最严重。
- 总体结论 — 不完全一致，存在多处需要修正以贴合设计稿的项。

---

## 二、Token 系统层差异

> 对比基准：`champagne_cinema_leanback/DESIGN.md` + 实际 `code.html` 中出现的设计值

| Token 角色 | 设计稿值 | 实现值 | 资源 ID | 状态 |
| --- | --- | --- | --- | --- |
| Canvas Base | `#121214` | `#0E0E10` | `tv_bg` | ❌ 偏差 |
| Surface Lower | `#18181C` | `#161619` | `tv_surface_low` | ❌ 偏差 |
| Surface Card | `#1C1C20` | `#202024` | `tv_surface` | ❌ 偏差 |
| Surface Elevated | `#26262C` | `#2A2A30` | `tv_surface_high` | ❌ 偏差 |
| Text Primary | `#F4F4F6` | `#F3F3F6` | `tv_text_primary` | ❌ 微差 |
| Text Secondary | `#B8B8C0` | `#9B9BA1` | `tv_text_secondary` | ❌ 偏差 |
| Text Muted | (设计未单独标) | `#66666D` | `tv_text_muted` | ⚠️ 参照未对齐 |
| On Accent | `#121214` | `#0E0E10` | `tv_on_accent` | ❌ 微差 |
| Accent | `#E5A958` | `#E5A958` | `tv_accent` | ✅ 一致 |
| Focus Ring | `#FCD58B` | `#FCD58B` | `tv_focus` | ✅ 一致 |
| Focus Bloom | `#FFE8A3` | （缺失） | — | ❌ 未定义 |
| Success | `#22C55E` | `#22C55E` | `tv_success` | ✅ 一致 |
| Danger | `#EF4444` | `#EF4444` | `tv_danger` | ✅ 一致 |

### 字号阶梯（实现 sp ≈ 设计 px @1080p）

| 语义 | 设计稿 (px) | 实现 (sp) | 资源 | 状态 |
| --- | --- | --- | --- | --- |
| Display / Hero | 64 / 52 | 38 / 48 | `tv_text_hero` / `tv_text_display` | ✅ 接近 |
| Section / Title | 26 | 26 | `tv_text_section` / `tv_text_title` | ✅ |
| Card Title | 20 | 20 | `tv_text_card_title` | ✅ |
| Body | 16 | 16 | `tv_text_body` | ✅ |
| Caption / Meta | 14 | 14 / 12 | `tv_text_caption` / `tv_text_meta` | ⚠️ meta 12sp 偏小 |
| Button | 16 | 16 | `tv_text_button` | ✅ |

### 圆角 / 焦点 / 间距

| 项 | 设计稿 | 实现 | 资源 | 状态 |
| --- | --- | --- | --- | --- |
| Card Radius | 16px | 16dp | `tv_radius_card` | ✅ |
| Modal Radius | 24px | 24dp | `tv_radius_modal` | ✅ |
| Button Radius | 12px | 12dp | `tv_radius_button` | ✅ |
| Focus Stroke | 3.5dp | 3.5dp | `tv_focus_stroke` | ✅ |
| Focus Glow | 25px | 25dp | `tv_focus_glow` | ✅ |
| Focus Scale | 1.05x | 1.05x | `tv_focus_scale` | ✅ |
| Safe H | 72px | 36dp* | `tv_safe_horizontal` | ✅ (2x 密度换算) |
| Safe V | 48px | 24dp* | `tv_safe_vertical` | ✅ |
| Rail Gap | 32px | 32dp | `tv_rail_gap` | ✅ |
| Card Gap | 24px | 24dp | `tv_card_gap_x` | ✅ |

> 注：`tv_dimens.xml` 注释已说明 1080p 2x 密度换算，运行时表现与设计稿一致。

---

## 三、各页面布局 / 控件差异

### 3.1 Home（活动：`HomeActivity` / `activity_home.xml`）
设计稿：`home_focus_specs_1` / `_2` / `_ice_jade`（3 个变体，主要结构一致）

| 项 | 设计稿 | 实现 | 状态 |
| --- | --- | --- | --- |
| 顶栏 padding | px-72 pt-48 pb-6 | padding tv_safe h/v + 8dp top/bottom | ⚠️ 等价但来源不同 |
| Logo 容器 | 44x44 圆角 12 渐变金色背景 + 24x24 svg | 30x30 ImageView + FONGMI 文字 + TV 徽章 | ❌ 缺失金色徽章 |
| 导航形态 | pill（px-6 py-2.5 rounded-full 20px 字） | 矩形（`Tv.HomeNav` 8dp padding 16sp 字） | ❌ |
| 导航选中态 | pill bg white/10 + 1.5x24 E5A958 圆点 marker | `tv_home_nav` selector（焦点时 surface raised + 焦点 stroke） | ❌ 无左下 marker 圆点 |
| 行标题 | 26px font-bold + 1.5x24 E5A958 marker bar | `HeaderPresenter` 仅 `TextAppearance.Tv.Navigation` (16sp) 文字 | ❌ marker + 字号 |
| Hero 区标题 | 52px font-extrabold line-clamp-1 | `TextAppearance.Tv.Hero`（38sp） | ✅ |
| Hero 描述 | max-w-880 line-clamp-2 22px | `layout_marginEnd=324dp` 间接限定 2/3 宽度 16sp | ⚠️ 效果近似 |
| Hero Action 按钮 | bg #E5A958 text #0E0E10 22px | `tv_hero_primary` accent 填充 ✅ | ✅ |
| 卡片尺寸 | 2:3 海报 / 16:9 横卡（grid-cols-6 gap-6） | `Vod.Grid` 80dp 固定高 + `style.rect/oval/list` 三种 view type | ⚠️ 比例近似 |
| 卡片 metadata 颜色 | text-#8E8E93 14px | `tv_text_secondary`（#9B9BA1） | ❌ 颜色不符 |
| 底部遥控器提示 footer | 4 列 kbd pill + 状态指示 | 简单文字 `remoteHint` 24dp 高 | ❌ 缺失 kbd pill |

### 3.2 Settings（活动：`SettingActivity` / `activity_setting.xml`）
设计稿：`settings`

| 项 | 设计稿 | 实现 | 状态 |
| --- | --- | --- | --- |
| 布局 | 左导航 3 / 中详情 6 / 右数据 3 | 同结构 3:6:3 weight | ✅ |
| 顶栏 Back | 16px bg-surface + icon + "BACK 返回" + Breadcrumb | 仅 Back 文字按钮，无图标，无面包屑 | ❌ |
| 导航项 | rounded-xl p-4 + 40x40 icon 容器 + 标题 + 副标题 + accent 圆点 | `Tv.Setting.Nav` 58dp 高，padding 16/12，无图标 | ❌ |
| Section header | 18px font-bold + 1.5x16 E5A958 marker bar | `Tv.Setting.Section` 16sp font-bold accent uppercase（无 marker） | ❌ marker |
| 行内容 | 20px font-semibold + 副文本 | `Tv.Setting.Row` 58dp minHeight + 20sp bold title + 16sp value | ⚠️ 等价 |
| 解码器选择 | 3-way segmented radio（硬解/软解/智能） | 单一设置项，无 radio 分组 | ❌ |
| 开关 | TV 风格 toggle ON/OFF 圆形 thumb | 文字描述（On/Off/Off） | ❌ |
| 顶栏右侧 | 源状态 pill + 时钟 | 无 | ❌ |
| Surface 容器 | surfaceLow #151518 + 12/24 padding | `tv_setting_panel` (surface_low #161619) 22dp padding | ⚠️ 颜色偏差 + 接近 |

### 3.3 Player（控制器：`view_control_vod.xml` / `view_widget_vod.xml`）
设计稿：`player`

| 项 | 设计稿 | 实现 | 状态 |
| --- | --- | --- | --- |
| 顶部 HUD | Back + 28px 标题 + 集数徽章 + 规格 chip + 源信息 + 时钟 | 仅标题 (20sp) + size + clock | ❌ |
| 中央 state widget | 左侧快进反馈卡片 + 缓冲胶囊 | 无（仅 error/center/message 三种） | ❌ |
| 右侧调节抽屉 | 380px panel：字幕/比例/时间轴 | 无 | ❌ |
| 底部进度条 | 6px 高 + 渐变 amber 填充 + 22px focus thumb + 章节标签 | Media3 `PlayerSeekView`（默认样式） | ❌ |
| 主操作行 | 4 按钮（暂停金色 / 上一集 / 下一集 / 选集） | HorizontalScrollView 一长串 16+ 按钮 | ❌ |
| 辅助操作 | 弹幕/倍速/画质/音轨 chip | 合并在同一行 | ⚠️ |
| 时间文本 | 20px gold 当前 + 18px total | Media3 默认 | ❌ |
| Scrim 渐变 | scrim-top / scrim-bottom 双层渐变 | `tvColorScrimStrong` 单色 | ❌ |

### 3.4 Detail（活动：`VideoActivity` / `activity_video.xml`）
设计稿：`detail`

| 项 | 设计稿 | 实现 | 状态 |
| --- | --- | --- | --- |
| 左视频小窗 | 672x378（grid-cols-7） | 420x236（左上角） | ⚠️ 比例固定非响应 |
| 顶 header | Back + 面包屑 + 源 pill + 时钟 | 无 | ❌ |
| Hero 标题 | 44px font-bold | `tv_text_display` 48sp | ⚠️ 接近 |
| 元数据 | 5 行 grid（左标签 / 右值）+ 16px leading-loose | 4 行水平排列 16sp | ❌ |
| 剧情描述 | 16sp line-clamp-3 | 缺失 | ❌ |
| 操作按钮 | 4 等宽 grid（选集/换源/倍速/收藏） | 4 等宽 weight=1 | ✅ 结构一致 |
| 底部线路/画质/集数 | 3 行 chip + left label | 3 个 HorizontalGridView（flag/quality/episode） | ✅ 类似 |
| Chip 圆角 | rounded-lg 8px | `selector_chip` 20dp | ❌ |

### 3.5 Collect / History（活动：`CollectActivity`/`KeepActivity`）
设计稿：`collect_history`

| 项 | 设计稿 | 实现 | 状态 |
| --- | --- | --- | --- |
| 顶 header | Back + 面包屑 + 标题 + 徽章 | 无 | ❌ |
| Tabs | 3 标签（收藏/历史/稍后再看）+ 筛选 + 排序 | 无 | ❌ |
| 主网格 | 5 cols × 2 rows 16:9 grid | `HorizontalGridView` 横滚 + Pager | ❌ |
| 卡片 | bg-surface 圆角 + quality badge + ambient blur | `adapter_vod` 2:3 海报（不同 aspect） | ❌ |
| 日期分组 | "近期活跃与在看" 标签 + 分割线 | 无 | ❌ |
| Keep 空态 | 标题 + 副文 + 主行动按钮 | `activity_keep.xml` 有相同结构 | ✅ |

### 3.6 Search（活动：`SearchActivity` / `activity_search.xml`）
设计稿：`search_keyboard`

| 项 | 设计稿 | 实现 | 状态 |
| --- | --- | --- | --- |
| 顶 header | Back + 面包屑 + Query Box（拼音/首字母） + 源状态 | 仅 keyword EditText + mic 图标 | ❌ |
| 键盘容器 | T9 / QWERTY 切换 + 26 字母 + 功能键 | `GridLayoutManager spanCount=7` + `adapter_keyboard_text` | ⚠️ 模式切换缺失 |
| 键盘按钮高度 | 52px (h-13) rounded-xl | 未确认（adapter_keyboard_text） | ⚠️ |
| 焦点态 | 3.5dp #FCD58B + scale 1.05 | `tv_focus_scale` + selector_item | ✅ 焦点基础元素一致 |
| 联想候选区 | "SE" → 沙丘/杀破狼/三体 | 无 | ❌ |
| 历史记录区 | 标题 + manage/clear 按钮 | recordLayout 包含相同结构 | ✅ |
| 热门词区 | 24px title | `word` TextView 24sp | ✅ |

### 3.7 Live（活动：`LiveActivity` / `activity_live.xml`）
设计稿：`live_epg`

| 项 | 设计稿 | 实现 | 状态 |
| --- | --- | --- | --- |
| 顶部 HUD | Back + 台号金色徽章 + 频道名 + 直播徽章 + 规格 chip + 比分 + 时钟 | 无 | ❌ |
| 左 EPG 抽屉 | 570px rounded-3xl scrim-panel + 频道分类 tab | `recycler` LinearLayoutCompat wrap_content，无固定宽度 | ❌ |
| 右 widget 抽屉 | 380px scrim-panel + 调节控件 | 无 | ❌ |
| 频道列表 | 16px font-semibold + 16px mono number + 13px EPG + resolution chip | `adapter_channel` 实现 | ⚠️ |
| EPG 数据 | 时间 + 节目名 + 进度条 | `adapter_epg_data` | ✅ 类似 |
| Surface 容器 | scrim-panel (rgba 20/20/24/0.88 + blur) | `tv_setting_panel` 实色 surface_low | ❌ |
| 比分浮层 | 右上角 backdrop floating | 无 | ❌ |

### 3.8 Tools（活动：`PushActivity` / `activity_push.xml`）
设计稿：`tools`

| 项 | 设计稿 | 实现 | 状态 |
| --- | --- | --- | --- |
| 布局 | 2 cols × 3 rows 6 大模块卡片 | 单 Activity 仅二维码 | ❌ |
| 顶 header | Back + 面包屑 + 开发入口指引 + 版本 chip | 无 | ❌ |
| 卡片规格 | 56x56 icon + 22px title + 副文本 + 当前焦点 badge | 无 | ❌ |
| 焦点态 | outline 3.5px + scale 1.025 + 双层 glow | `tv_focus_scale` 1.05x | ⚠️ 略强 |
| 表面颜色 | surface-card #1b1b1e + 圆角 16 | `tv_surface` #202024 | ❌ |

> 备注：实际项目里"工具箱"功能分散在 PushActivity、SettingActivity 子页、KeepActivity 等不同 Activity 中，**没有统一 Tools Hub**，与设计稿的 Tools 单页布局无法直接对应。

---

## 四、跨页面共性问题

1. **颜色 Token 未对齐**（影响所有页面背景/层级观感）
   - 5 个核心 surface/text 颜色与 `champagne_cinema_leanback/DESIGN.md` 标定值存在系统性偏差
   - 缺 `#FFE8A3` Focus Bloom Token
2. **导航 pill 形态未实现**
   - Home 顶栏、所有 Section header 缺 E5A958 marker bar
3. **Scrim 渐变与 Glassmorphism 缺位**
   - Player、Live 顶部/底部浮层仅使用实色 scrim；设计稿使用 `linear-gradient` + `backdrop-filter: blur`
4. **焦点态 Glow 参数接近但内 stroke 颜色不统一**
   - `selector_item` 使用 `?attr/tvColorSurfaceRaised` 作为填充；设计稿在 surface+border-focus 二态间切换
5. **顶 Back 按钮样式不统一**
   - 设计稿统一形态："rounded-lg bg-surface border + chevron icon + 'BACK 返回'"
   - 实现：每个 Activity 自行 `Tv.Setting.Back` 等不同样式
6. **遥控器提示 footer 仅在 Home 实现**
   - 设计稿在 Player、Live、Search 等页面均有 kbd pill 提示，实现缺失
7. **空态 / 错误态浮层缺位**
   - 设计稿要求 `E5A958` "一键导入配置" / "自动切线" 等空/错态引导
   - 实现：仅 ProgressLayout + Notify.toast

---

## 五、修复建议优先级

| 优先级 | 项目 | 涉及文件 | 备注 |
| --- | --- | --- | --- |
| P0 | 颜色 Token 对齐 | `tv_colors.xml` | 与 `DESIGN.md` 校准 5 个核心色 |
| P0 | Scrim / Glassmorphism | `view_control_*.xml` + 新增 scrim drawable | 影响 Player/Live 氛围 |
| P1 | 顶 Back 按钮统一 | `tv_styles.xml` / 各 Activity toolbar | 统一形态 + 图标 |
| P1 | Home 导航 pill + Section marker | `tv_home_styles.xml` + `HeaderPresenter` | 视觉骨架 |
| P1 | Player 底部控制条重构 | `view_control_vod.xml` + `view_widget_vod.xml` | 4+6 分组按钮行 |
| P1 | Detail 左视频窗 + 右面板 12-grid | `activity_video.xml` | 响应式比例 |
| P2 | Live 顶部 HUD + 比分浮层 | `activity_live.xml` + `view_widget_live.xml` | 直播信息层 |
| P2 | Search 键盘模式切换 + 联想 | `activity_search.xml` + `adapter_keyboard_text` | 输入体验 |
| P2 | Tools Hub 落地页 | 新增 Activity + layout | 整合散落工具 |
| P3 | 遥控器 footer 提示 | 全局 string + 各 Activity 底部 | 可发现性 |
| P3 | 空/错态浮层 | 新增 layout 系列 | 容错体验 |

---

## 六、附录：审查依据文件

### 设计稿（设计端）
- `docs/design-stitch/stitch_fongmi_tv_ui_design/champagne_cinema_leanback/DESIGN.md` — 设计系统规范
- `docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_home_focus_specs_1/code.html`
- `docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_home_focus_specs_2/code.html`
- `docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_home_focus_specs_ice_jade/code.html`
- `docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_settings/code.html`
- `docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_player/code.html`
- `docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_detail/code.html`
- `docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_collect_history/code.html`
- `docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_search_keyboard/code.html`
- `docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_live_epg/code.html`
- `docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_tools/code.html`

### 实现（代码端）
- `app/src/leanback/res/values/tv_colors.xml`
- `app/src/leanback/res/values/tv_dimens.xml`
- `app/src/leanback/res/values/tv_styles.xml`
- `app/src/leanback/res/values/tv_home_styles.xml`
- `app/src/leanback/res/values/tv_setting_styles.xml`
- `app/src/leanback/res/values/styles.xml`（`Theme.Base`）
- `app/src/leanback/res/drawable/tv_home_nav.xml`、`shape_vod_focused.xml`、`shape_item_focused.xml`、`tv_hero_primary.xml`、`tv_setting_panel.xml`
- `app/src/leanback/res/animator/tv_focus_scale.xml`
- `app/src/leanback/res/layout/activity_home.xml`、`activity_setting.xml`、`activity_video.xml`、`activity_vod.xml`、`activity_collect.xml`、`activity_keep.xml`、`activity_search.xml`、`activity_live.xml`、`activity_push.xml`
- `app/src/leanback/res/layout/view_control_vod.xml`、`view_control_vod_action.xml`、`view_widget_vod.xml`、`view_control_live.xml`、`adapter_header.xml`、`adapter_hero.xml`、`adapter_vod.xml`、`adapter_vod_rect.xml`、`adapter_func.xml`
- `app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java`、`ui/presenter/HeaderPresenter.java`、`ui/presenter/HeroPresenter.java`、`ui/presenter/VodPresenter.java`

---

> 备注：因 git status 显示工作区已修改多个 leanback 资源，本次审计基于磁盘当前内容；若提交前尚有未保存改动，请以最终提交版本为准。