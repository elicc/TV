# FongMi TV · UI/UX 优化清单（主题无关 · 可扩展）

> **目的**：本文档系统化地记录 FongMi TV 当前在 UI/UX 方面存在的不足，
> 并给出**主题无关**的优化方案与设计 Token 抽象，便于后续接入任意主题。
>
> **范围**：`app/src/main`、`app/src/leanback`、`app/src/mobile` 三个 source set。
> **不绑定特定主题色**：所有推荐项均以 **Token 变量** 描述，最终主题
> （如 Golden Cinema / Neo Noir / Midnight Neon / Warm Theatre / Ivory Editorial /
> Mist & Steel / Blush Cinema / Sand & Brass 等）由后续决策统一注入。

---

## 目录

- [0. 设计 Token 抽象层（主题无关）](#0-设计-token-抽象层主题无关)
- [1. 颜色系统 — 残缺、不成体系](#1-颜色系统--残缺不成体系)
- [2. 字体与排版 — 缺乏系统化](#2-字体与排版--缺乏系统化)
- [3. 圆角与卡片 — 散乱、不成层级](#3-圆角与卡片--散乱不成层级)
- [4. 主屏（Home）— 缺 Hero、缺氛围](#4-主屏home--缺-hero缺氛围)
- [5. 详情页（Video）— 信息堆叠无层级](#5-详情页video--信息堆叠无层级)
- [6. 直播页（Live）— 通道切换体验差](#6-直播页live--通道切换体验差)
- [7. 搜索页 — 缺乏引导](#7-搜索页--缺乏引导)
- [8. 设置页 — 完全扁平、无层次](#8-设置页--完全扁平无层次)
- [9. 焦点与遥控器导航 — 单焦点反馈不充分](#9-焦点与遥控器导航--单焦点反馈不充分)
- [10. 错误 / 空状态 / 加载 — 缺失](#10-错误-空状态-加载--缺失)
- [11. 品牌一致性与启动体验](#11-品牌一致性与启动体验)
- [12. 国际化 / 文字规范](#12-国际化--文字规范)
- [13. 修复优先级总览](#13-修复优先级总览)
- [14. 主题切换机制建议](#14-主题切换机制建议)

---

## 0. 设计 Token 抽象层（主题无关）

为支持后续**主题可切换**，在 `colors.xml` 之上引入抽象层。资源命名统一为
`xx_role_xx` 形式（**role-based**），不再使用具体颜色名。

### 0.1 Token 命名规范

| 类别 | 抽象 Token | 含义 |
|---|---|---|
| **Surface** | `surface_background` | 页面最底背景 |
| | `surface_elevated` | 卡片 / 浮层 / 弹窗 |
| | `surface_sunken` | 凹陷区域（输入框、Tab 选中态） |
| | `surface_inverse` | 反色块（亮色主题下的深色块，反之亦然） |
| **Text** | `text_primary` | 主要文字 |
| | `text_secondary` | 辅助文字 |
| | `text_tertiary` | 弱化文字 / 标签 |
| | `text_inverse` | 反色块上的文字 |
| | `text_link` | 链接 |
| **Action** | `action_primary` | 主 CTA 背景 |
| | `action_primary_text` | 主 CTA 文字 |
| | `action_secondary` | 次级操作背景 |
| | `action_secondary_text` | 次级操作文字 |
| **Accent** | `accent_brand` | 品牌强调色（焦点、Hero 高亮） |
| | `accent_brand_muted` | 品牌色淡化版（章节标题色） |
| **Status** | `status_live` | 直播 LIVE 标识 |
| | `status_success` | 成功 / 已收藏 |
| | `status_warning` | 警告 / 提示 |
| | `status_error` | 错误 / 删除 |
| **Focus** | `focus_border` | 焦点描边 |
| | `focus_glow` | 焦点外发光（深色主题更明显） |
| **Stroke** | `stroke_subtle` | 弱分割线 |
| | `stroke_default` | 普通分割线 |
| | `stroke_strong` | 强调分割线 |
| **Overlay** | `overlay_scrim` | 图片蒙层（hero 渐变蒙层） |
| | `overlay_modal` | 弹窗蒙层 |

### 0.2 实现方式（推荐）

- **白天 / 夜间 / 主题切换**：通过 `Theme.App.Light` 与 `Theme.App.Dark` 双套
  资源覆盖同一组 Token 名称。运行时由用户在设置中选择。
- **多主题切换**：在 `Product` 配置中支持 `theme_id` 字段，根据 ID 切换
  `Theme.App.GoldenCinema` / `Theme.App.NeoNoir` / `Theme.App.WarmTheatre`
  等。具体主题值可由后续决策注入。
- **类型化资源**：所有 Token 必须存在于 `:main` 模块；`leanback` / `mobile`
  只通过 `?attr/` 或 `@color/role_xx` 引用，禁止硬编码。

---

## 1. 颜色系统 — 残缺、不成体系

### 🔍 现状摘要

| 文件 | 问题 |
|---|---|
| `app/src/main/res/values/colors.xml` | **只有 black/white 及 alpha 变体**，缺主色 / 品牌色 / 语义色 / 灰色阶 |
| `app/src/leanback/res/color/text.xml` | 引用不存在的 `@color/yellow_500` |
| `app/src/leanback/res/layout/adapter_quick.xml` | 引用不存在的 `@color/yellow_500`、`@color/green_a_400` |
| `app/src/leanback/res/values/styles.xml` `Theme.Crash` | 引用不存在的 `@color/grey_800` |
| `app/src/leanback/res/color/bg_site.xml` | 硬编码 `#F44336`（Material Red）|
| `app/src/leanback/res/color/bg_year.xml` | 硬编码 `#2196F3`（Material Blue）|
| `app/src/leanback/res/color/bg_remark.xml` | 硬编码 `#177535`（森林绿）|
| `app/src/leanback/res/values/styles.xml` `Theme.Splash` | 硬编码 `#141218` |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | logo 用 `#2DD4AA / #4CF5CB / #68AEF4 / #FFD159`，**应用内完全未使用** |

### ✅ Token 化方案（无具体色值）

新增 Token：

| Token | 角色 |
|---|---|
| `surface_background` | 替代 `@color/black` 的页面底色 |
| `surface_elevated` | 替代 `@color/black_20` / `black_40` 的卡片背景 |
| `surface_inverse` | 替代 `@color/white` 的反色块 |
| `text_primary` | 替代 `@color/white` 的主文字 |
| `text_secondary` | 替代 `@color/white_70` 的次文字 |
| `text_tertiary` | 替代 `@color/white_50` 的弱文字 |
| `accent_brand` | 新增品牌主色（焦点 / Hero 强调 / 章节装饰条） |
| `accent_brand_muted` | 品牌色淡化版（章节标题、辅图标） |
| `status_live` | 替代 `#F44336` 直播态 |
| `status_info` | 替代 `#2196F3` 信息态 |
| `status_success` | 替代 `#177535` 成功态 |
| `status_warning` | 新增警告色 |
| `status_error` | 新增错误色 |
| `focus_border` | 焦点描边专用，**不能复用 `accent_brand`**（深浅主题可能冲突） |
| `stroke_subtle` / `stroke_default` / `stroke_strong` | 统一三档分割线 |
| `overlay_scrim` | 替代 `black_50` / `black_70` 的蒙层 |

**P0 修复**：
- 把 `text.xml` / `adapter_quick.xml` / `Theme.Crash` 中缺失的颜色资源补齐为对应 Token。
- 把 `bg_site / bg_year / bg_remark` 三个 hardcoded 色全部改为 Token 引用。
- 启动屏 `#141218` 改为 `surface_background` Token 引用。

---

## 2. 字体与排版 — 缺乏系统化

### 🔍 现状摘要

- 全应用只有 Material 默认字体。
- 字号跳变不连贯：24sp / 18sp / 16sp / 14sp，无明确语义。
- 所有文字颜色均为 `@color/white`，**无次级/辅助文字色阶**。
- 集数、年份、分辨率（1080p）未使用等宽字。
- 行高统一默认。

### ✅ 引入 type scale（Token 化）

| 抽象 Token | 推荐值 | 角色 |
|---|---|---|
| `text_display` | 36sp / 600 | 大数字（评分、字数）|
| `text_headline` | 24sp / 600 | Hero 标题 |
| `text_title` | 20sp / 500 | 章节标题、卡片标题 |
| `text_subtitle` | 18sp / 500 | 二级标题 |
| `text_body` | 16sp / 400 | 正文 |
| `text_caption` | 14sp / 400 | 辅助说明、按钮文字 |
| `text_overline` | 12sp / 500 + letterSpacing 0.12em | 章节标签、徽标 |

### ✅ 字体族（与主题绑定，但接口抽象）

- **Display 字体族**：`text_font_display`（中文：思源宋体 / 霞鹜文楷 / 系统衬线；英文：Playfair / Fraunces / Cormorant）
- **Body 字体族**：`text_font_body`（中文：思源黑体 / 系统；英文：Inter / Lato）
- **数字字体族（可选）**：`text_font_mono`（Roboto Mono / JetBrains Mono / DIN）—— 集数、年份、分辨率专用

字号与字重通过 `dimens.xml` / `styles.xml` 中的 `TextAppearance.*` 抽象，
禁止 layout 内直接写 `android:textSize`。

---

## 3. 圆角与卡片 — 散乱、不成层级

### 🔍 现状摘要

- 圆角值有 **4dp / 8dp / 16dp / 28dp** 多档，无明确语义。
- `shape_chip_round_focused` 28dp、`shape_vod_focused` 8dp、`shape_item_focused` 4dp 混用。
- **所有可聚焦元素**均为 `1.5dp 白色描边`，无尺寸 / 亮度 / 阴影变化。

### ✅ Radius 等级（Token 化）

| Token | 建议值 | 用途 |
|---|---|---|
| `radius_xs` | 4dp | 小徽标、chip、键盘按键 |
| `radius_sm` | 8dp | 卡片、输入框 |
| `radius_md` | 12dp | 主卡片、Hero 内嵌区 |
| `radius_lg` | 20dp | 大弹窗、浮层 |
| `radius_pill` | 高度 50% | 药丸按钮 |

### ✅ 焦点反馈分级（Token 化）

| 等级 | Token | 效果 |
|---|---|---|
| 普通 | `focus_border` 2dp + 轻微缩放 1.04 | 列表项、卡片 |
| 主要 | `focus_border_strong` 3dp + 阴影 + 缩放 1.06 | 主 CTA（播放/确认）|

---

## 4. 主屏（Home）— 缺 Hero、缺氛围

### 🔍 现状摘要

`app/src/leanback/res/layout/activity_home.xml`：
- **无 Hero Banner / Featured 轮播**。
- 功能入口（点播/直播/搜索/收藏/投屏/设置）以 6 个 100×100dp 的方块平铺于首行。
- 章节标题（"观看记录"/"推荐"）仅用 24sp 白字，无视觉强调。
- 顶部 logo + title + 时钟占位偏厚（24dp top + 8dp bottom）。

### ✅ 优化建议（结构层面，不绑颜色）

#### 4.1 新增 Hero 区
- 高度 ≈ 屏宽的 50–60%（TV 16:9 设计基准 960×540 下为 280–320dp）。
- 内含：大背景海报、Hero 标题、副标题、CTA 按钮组（播放 / 收藏 / 详情）。
- 蒙层使用 `overlay_scrim` Token（双向渐变：左下深 → 右上透明）。
- Hero 内容采用 `bias bottom-start`，宽度限制 40–50% 屏宽以保证海报图可见。

#### 4.2 章节标题（row header）改造
- 左侧 4dp 宽品牌色装饰条（用 `accent_brand` Token） + 24sp 中粗体 + 右侧 "查看全部 ›" 链接（用 `text_secondary`）。
- 装饰条的存在让章节与背景有清晰分层。

#### 4.3 功能入口改造为底部 Tab 栏
- 当前 `FuncPresenter` 6 个 100×100dp 方块改为 Bottom Navigation（图标 + 文案）。
- 数量 4–5 个为佳：点播 / 直播 / 搜索 / 收藏 / 我的。
- 选中态使用 `accent_brand` Token。

#### 4.4 滚动 toolbar
- 滚动时 toolbar 从可见渐变到半透明（用 `surface_background` + alpha 渐变），不要简单 visibility 切换。

#### 4.5 历史记录行强化
- 增加进度条 / 最近观看集数卡片。`History` Bean 已有 `progress`，需要在 UI 显式表达。

---

## 5. 详情页（Video）— 信息堆叠无层级

### 🔍 现状摘要

`app/src/leanback/res/layout/activity_video.xml`：
- 播放器 400×225dp，**TV 距离 2.5m+ 时明显偏小**（主流 TV 应用 16:9 播放器占屏宽 50%+）。
- 名称、备注、导演、演员等元数据**全部 16sp 白字平铺**，无视觉区分。
- 站源 / 年份 / 地区 / 类型是纯文本平铺，没有"标签"视觉。
- 选集 / 线路 / 画质 / 数组 / 快捷线路等多个 HorizontalGridView 堆叠。
- 按钮 "详情/收藏/换源" 用普通 selector_item 描边，无差异化。

### ✅ 优化建议

#### 5.1 播放器尺寸
- 放大到 **720×405dp**（屏宽 75%），hover/focus 时展示大尺寸海报替代。

#### 5.2 元数据"标签 + 值"双段式
- 站源：`[Source] 泥巴` 芯片样式（带 `surface_elevated` 背景 + 1px stroke）。
- 年份：单独高亮（大号数字字体 `text_font_mono`）。
- 评分：⭐ 8.5（大号数字 + 黄色五角星图标）。
- 类型：芯片样式。

#### 5.3 描述区展开/收起
- 描述允许 2~4 行展开 + 收起按钮（避免"演员：xxx,xxx,xxx,xxx,xxx"塞一行）。

#### 5.4 选集行多视图
- 支持"集数网格"和"时间线"两种视图切换（参考 Disney+）。
- 已选集用 `accent_brand` 填充；未选集用 `surface_elevated` + 1px `stroke_subtle`。

#### 5.5 线路/画质 chip
- 选中态使用 `accent_brand` 填充（不是白色描边），建立"已选"心智模型。

---

## 6. 直播页（Live）— 通道切换体验差

### 🔍 现状摘要

`app/src/leanback/res/layout/activity_live.xml`：
- 视频全屏覆盖，**左下角浮出 3 个 list**（分组/频道/EPG）。
- 列表宽度 `wrap_content`，未做宽度约束，3 列可能挤在一屏。
- EPG 数据用 `tools:itemCount="5"`，实际可能很长，**缺少日期切换、时间轴、节目预告卡片化**。
- 没有"正在播放/即将播放"视觉区分。

### ✅ 优化建议

#### 6.1 三栏分屏
- 左分组（窄 120dp） / 中频道（中等 200dp） / 右 EPG（宽 320dp），比例 1:1.5:2。
- 浮出列表用毛玻璃背景 `surface_elevated` + alpha + 模糊（参考 `shape_controller.xml` 已有 28dp 圆角可扩展）。

#### 6.2 EPG 改为时间轴
- 每列 30 分钟，当前节目用 `accent_brand` 高亮，可预加载下一节目。
- 顶部加"今天 / 明天 / 后天"日期切换 Tab。

#### 6.3 频道 logo + 名两列
- logo 用 56×56dp 圆形（参考 Apple TV Live）。
- 当前播放节目用 `status_live` 红点 + 文字 "直播中"。

---

## 7. 搜索页 — 缺乏引导

### 🔍 现状摘要

`app/src/leanback/res/layout/activity_search.xml`：
- 屏幕分两半：左输入 + 键盘，右搜索建议/热搜/历史。
- 热搜词、搜索记录都只是普通文本，**没有热搜排名、热度标识**。
- 键盘 7 列宽 × 5 行高 = 35 键，**没有"中文/英文/数字/符号"切换 Tab**。
- 历史记录没有"清空"操作。

### ✅ 优化建议

- 热搜加 🔥 排名数字 + 趋势色阶（1-3 名用 `accent_brand` 强调）。
- 键盘上方加 Tab 切换：**中 / 英 / 数字 / 符号**。
- 输入框 placeholder 用 `text_tertiary` 颜色。
- 历史记录上方加"清空全部"文字按钮（细描边、小尺寸）。
- 搜索结果加 loading skeleton（**不要用旋转菊花**，TV 上视觉干扰大）。

---

## 8. 设置页 — 完全扁平、无层次

### 🔍 现状摘要

`app/src/leanback/res/layout/activity_setting.xml`：
- 所有项都是 `LinearLayoutCompat + TextView` + 同样的 `selector_item` 描边。
- 没有图标（即使 `ic_setting_home` 等已存在）。
- 没有分组、标题、分隔线。
- 18sp 文字 + 24dp padding × 10+ 项堆叠，**视觉上像 Word 文档**。

### ✅ 优化建议

#### 8.1 Master-Detail 布局
- 左侧导航 + 右侧详情（参考 Android TV Leanback `SettingsFragment`）。

#### 8.2 至少 3 个分组
- `通用 / 播放 / 数据 / 关于`，每组标题用 `text_overline`（12sp / 500 / letterSpacing 0.12em）。

#### 8.3 每个选项配 36×36dp 图标
- 设置 / 播放器 / 弹幕 / 隐私 / DOH / 缓存 / 版本。

#### 8.4 开关项用 Material Switch
- 替代文字 "On/Off"。

#### 8.5 危险操作置底
- 清空缓存放最下方，配 `status_error` 提示。

---

## 9. 焦点与遥控器导航 — 单焦点反馈不充分

### 🔍 现状摘要

- 所有可聚焦元素都是 **1.5dp 白色描边**。
- 焦点移动无平滑滚动过渡（虽然有 `focusOutEnd/Front` 但缺缩放）。
- 长按删除（`onLongClick`）无预览提示。

### ✅ 优化建议

#### 9.1 焦点组合反馈
- 边框 `focus_border` 2dp + 缩放 1.06 + 阴影 Elevation 8 + 背景变亮 5%。
- **不要仅靠描边**，距离感不够。

#### 9.2 焦点移动的视觉引导
- 焦点进入新章节时，自动滚动到该区域。
- TV 设备可选用震动反馈 / 微提示音（可在设置中关闭）。

#### 9.3 长按防误操作
- 长按前显示 1.2s 延迟 + toast 提示 "再次长按删除"。

#### 9.4 边缘提示
- 焦点移动到屏幕边缘时显示"还有更多"渐变提示（参考 Netflix 卡片流）。

---

## 10. 错误 / 空状态 / 加载 — 缺失

### 🔍 现状摘要

- Crash 页只有默认 error image + 文字 + 2 个按钮。
- 加载只有 `ProgressLayout`（一个菊花）。
- 没有"无网络 / 无内容 / 无搜索结果 / 收藏为空"等空状态插画与文案。

### ✅ 优化建议

#### 10.1 空状态
- 插画 + 主色文案 + 1 个明确 CTA（如"去搜索 →"）。
- 复用一套插画位（`ic_empty_xxx.xml`），主题切换时仅换色（用 `tint` 引用 `text_tertiary`）。

#### 10.2 加载
- 骨架屏（Skeleton），TV 上比菊花更专业。
- 提供 `view_skeleton_card.xml` / `view_skeleton_row.xml` 模板。

#### 10.3 错误
- 内嵌 Toast + Retry 按钮。
- 错误色用 `status_error`，但不要刺眼红。

#### 10.4 首次启动
- 3~5 张 onboarding 引导，介绍 TV 遥控器操作（上下左右 / OK / 返回 / 菜单键）。

---

## 11. 品牌一致性与启动体验

### 🔍 现状摘要

- 启动屏 `#141218` 紫色背景，**与 logo 的青绿主色完全无关**。
- 启动图标是 `launcher_foreground` 矢量，但 splash 没用。
- 启动后第一帧直接进 home，**没有任何过渡动画**（`NoAnim` 显式关闭）。

### ✅ 优化建议

- 启动屏背景改为 `surface_background` Token 引用，配 logo 居中放大，0.6s 后渐出。
- App 内首次加载使用**主色品牌渐变**作为内容区背景。
- 关闭 `NoAnim` 后给页面切换一个 200ms 的 fade-in（不要花哨滑动，TV 设备切换要稳）。

---

## 12. 国际化 / 文字规范

### 🔍 现状摘要

- 字符串分布在 `app/src/main/res/values/strings.xml`、
  `app/src/leanback/res/values-{zh-rCN,zh-rTW}/strings.xml`。
- 部分文案在 layout 里**硬编码**（如 `android:text="设 置"`）而非引用 `@string/`。

### ✅ 优化建议

- 全量检查 layout 中硬编码的可见文字，统一迁移到 `strings.xml`。
- 中英文混排时**中英文之间留 1 个空格**（"庆余年 第二季"），符合中文出版规范。
- 集数、年份、分辨率优先使用 `text_font_mono`，避免对齐错位。

---

## 13. 修复优先级总览

| 优先级 | 主题 | 影响 | 涉及 Token |
|---|---|---|---|
| **P0** | 补齐缺失颜色资源 | 修复编译/视觉回退 | `accent_brand`、`surface_elevated` 等 |
| **P0** | `bg_site/year/remark` 改 Token | 避免视觉杂色 | `status_live/info/success` |
| **P0** | 统一焦点反馈（描边 + 缩放 + 阴影） | 遥控器可用性 | `focus_border`、`focus_glow` |
| **P0** | 全量 token 化（colors / dimens / styles） | 后续主题切换基础 | 全部 |
| **P1** | 引入 type scale + 文字色阶 | 信息层级 | `text_*` 全套 |
| **P1** | 新增 Home Hero 区 | 内容发现率 | `overlay_scrim` |
| **P1** | 详情页播放器放大 + 元数据标签化 | 沉浸感 | `surface_elevated`、`stroke_subtle` |
| **P2** | 设置页 Master-Detail 改造 | 专业感 | `text_overline` |
| **P2** | 直播 EPG 时间轴 | 直播核心体验 | `accent_brand` |
| **P2** | 空状态/错误状态/骨架屏 | 完整产品感 | `text_tertiary` |
| **P2** | 启动屏 + 过渡动画 | 品牌感 | `surface_background` |
| **P3** | Onboarding 引导 | 新用户体验 | — |

---

## 14. 主题切换机制建议

### 14.1 多主题支持的资源结构

```
res/
├── values/                     # 默认（占位 / 调试用）
│   ├── colors.xml              # Token 默认值
│   ├── dimens.xml              # Token 默认值
│   └── styles.xml              # 主题默认
├── values-light/               # 系统 Light 模式
├── values-dark/                # 系统 Dark 模式
└── values-theme-{id}/          # 用户主动选择的主题
    ├── colors.xml
    └── styles.xml
```

### 14.2 推荐主题 ID（与 `docs-design-directions*.html` 8 套对齐）

| Theme ID | 中文名 | 备注 |
|---|---|---|
| `golden_cinema` | 黄金年代影院 | 深色 + 暖金 |
| `neo_noir` | 极简暗夜 | 真黑 + 朱红 |
| `midnight_neon` | 午夜霓虹 | 午夜蓝 + 电气青 |
| `warm_theatre` | 暖光剧场 | 紫红 + 珊瑚 |
| `ivory_editorial` | 米白编辑 | 米白 + 黄铜 |
| `mist_steel` | 雾墨钢蓝 | 冷石灰 + 钢蓝 |
| `blush_cinema` | 粉调影院 | 灰粉 + 酒红 |
| `sand_brass` | 砂铜记忆 | 暖砂 + 古铜 |

**注意**：本清单**不锁定**其中任何一个主题。最终选择由后续决策决定，
所有 Token 设计均与具体主题解耦。

### 14.3 主题注入流程

1. 用户在设置中选择主题（首次启动时默认跟随系统）。
2. `Setting.theme` 持久化到 Preference / DataStore。
3. `App.onCreate()` 时根据 `Setting.theme` 调用 `setTheme(R.style.Theme_App_xxx)`。
4. 资源系统按当前主题解析所有 Token，无需重启 Activity。
5. 提供**主题预览**入口（设置 → 个性化 → 主题 → 实时切换预览）。

### 14.4 与 launcher icon 关系

- `ic_launcher_foreground` 当前使用多色 logo。
- 若启用主题切换，可考虑：主图标保留多色版本，**启动屏 icon** 跟随主题调色（monochrome tint）。

---

## 附录 A · 当前缺失/无效颜色资源列表

| 引用位置 | 缺失资源 | 应替换为 Token |
|---|---|---|
| `leanback/res/color/text.xml` | `@color/yellow_500` | `accent_brand` |
| `leanback/res/layout/adapter_quick.xml` | `@color/yellow_500` | `status_warning` 或 `accent_brand` |
| `leanback/res/layout/adapter_quick.xml` | `@color/green_a_400` | `status_success` |
| `leanback/res/values/styles.xml` `Theme.Crash` | `@color/grey_800` | `surface_elevated` |
| `leanback/res/values/styles.xml` `Theme.Splash` | `#141218` (硬编码) | `surface_background` |
| `leanback/res/color/bg_site.xml` | `#F44336` (硬编码) | `status_live` |
| `leanback/res/color/bg_year.xml` | `#2196F3` (硬编码) | `status_info` |
| `leanback/res/color/bg_remark.xml` | `#177535` (硬编码) | `status_success` |

---

## 附录 B · 当前硬编码/不一致的间距与圆角

| 类型 | 当前散乱值 | Token 化后 |
|---|---|---|
| 圆角 | 4 / 8 / 16 / 28 dp | `radius_xs / sm / md / lg / pill` |
| 焦点描边 | 全部 1.5dp 白 | `focus_border` 2dp + `accent_brand` |
| 卡片背景 | `black_20 / 40 / 50` 混用 | `surface_elevated`（含 alpha 0.05~0.15）|
| Section padding | 16 / 24 dp 混用 | `spacing_md` 16dp、`spacing_lg` 24dp |
| 字体大小 | 12 / 14 / 16 / 18 / 24 sp 跳变 | `text_overline/caption/body/subtitle/title/headline` |

---

## 附录 C · 关键文件路径速查

| 主题 | 路径 |
|---|---|
| 颜色 Token | `app/src/main/res/values/colors.xml` |
| 尺寸 Token | `app/src/main/res/values/dimens.xml`（需新建）|
| 样式 Token | `app/src/main/res/values/styles.xml` |
| 主题样式 | `app/src/leanback/res/values/styles.xml` |
| 启动主题 | `app/src/leanback/res/values/styles.xml` `Theme.Splash` |
| 启动图标 | `app/src/main/res/drawable/ic_launcher_foreground.xml` |
| TV 主屏布局 | `app/src/leanback/res/layout/activity_home.xml` |
| TV 详情页布局 | `app/src/leanback/res/layout/activity_video.xml` |
| TV 直播页布局 | `app/src/leanback/res/layout/activity_live.xml` |
| TV 搜索页布局 | `app/src/leanback/res/layout/activity_search.xml` |
| TV 设置页布局 | `app/src/leanback/res/layout/activity_setting.xml` |
| 焦点选择器 | `app/src/leanback/res/drawable/selector_item.xml` |
| 焦点形状 | `app/src/leanback/res/drawable/shape_*_focused.xml` |
| 主屏 Activity | `app/src/leanback/java/.../ui/activity/HomeActivity.java` |
| 详情页 Activity | `app/src/leanback/java/.../ui/activity/VideoActivity.java` |
| 直播页 Activity | `app/src/leanback/java/.../ui/activity/LiveActivity.java` |
| 设计方向参考 | `docs-design-directions.html` / `docs-design-directions-light.html` |

---

## 附录 D · 后续工作清单（按主题）

以下任务**主题无关**，可在主题确定后统一实施：

- [ ] **D1. Token 化基础设施**：建立完整 `colors.xml` / `dimens.xml` 抽象层。
- [ ] **D2. 修复 P0 资源引用**：补齐缺失颜色，把硬编码色改 Token。
- [ ] **D3. 焦点反馈系统**：统一 `focus_border` + 缩放 + 阴影。
- [ ] **D4. Type scale 落地**：建立 `TextAppearance.*` 体系。
- [ ] **D5. Home Hero 区**：新增 Hero 布局与渐变蒙层。
- [ ] **D6. 详情页元数据标签化**：站点/年份/评分/类型 chip 化。
- [ ] **D7. 设置页 Master-Detail**：分组 + 图标 + 开关。
- [ ] **D8. 直播 EPG 时间轴**：横向时间线 + 日期切换。
- [ ] **D9. 空/错/加载状态**：插画 + 骨架屏。
- [ ] **D10. 主题注入层**：实现 `setTheme(id)` 运行时切换。
- [ ] **D11. 启动屏 / 过渡动画**：跟随主题色调。
- [ ] **D12. 国际化文案检查**：硬编码文案迁 `strings.xml`。

---

> **文档版本**：v1.0 · 2026-09-09
> **不绑定具体主题色**：所有 Token 与建议均与具体色值解耦。
> **可扩展性**：新增主题仅需追加 `values-theme-{id}/` 资源，无需修改业务代码。
