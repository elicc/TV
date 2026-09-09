# TV 主题扩展约定

## 实施顺序与行为保护

1. 复用现有 selector 文件名与播放生命周期；顶栏重组后重新验证焦点顺序，片源入口使用普通按钮，避免旧快速换源左右键拦截导航。
2. 以 leanback 独有 attr/style 定义调色板，不重定义 main/mobile 的基础颜色。
3. 在 BaseActivity 创建 View 前应用已保存皮肤；返回页面仅允许非播放 Activity 重建。
4. 保留原壁纸配置；影片氛围独立开关，自定义壁纸默认关闭氛围。
5. 检查 XML/资源引用、三套调色板对比度并运行可用构建验证。

旧有状态基线：焦点 selector 顺序优先于 selected；播放页继承 PlaybackActivity；所有既有 padding 和控件导航保持不变。现有自动化 UI 测试不足，方向键/播放返回仍需设备验证。

## 当前资源与 API

- `app/src/leanback/res/values/tv_attrs.xml`：背景、面板、主次文字、禁用文字、强调、强调上文字、焦点、选中、边界、遮罩、错误与警告共 15 个独立语义角色。
- `tv_colors.xml`：只在调色板中声明具体色值，组件使用 `?attr/tvColor...`；不在页面复制 HEX，不让海报色更改全局强调。
- `styles.xml`：`Theme.Base` 提供完整默认角色，同时映射 Material 组件的 surface/container/primary 等属性，避免弹窗意外退回默认紫色。
- `tv_styles.xml`：`Theme.Tv.Champagne`、`Theme.Tv.Ice`、`Theme.Tv.Jade`，当前均为暗色皮肤；`TextAppearance.Tv.Meta/Body/Navigation/Title/Hero`。
- `tv_dimens.xml`：4/8dp 间距、卡片/控件/面板圆角、2dp 焦点宽度、文字层级。当前未强制所有遗留字号一次性替换。
- `color/text.xml`：disabled 优先，再 selected/checked 强调文字，普通主文字；焦点由形状白框表达，不用金色同时表示焦点和选中。
- 既有 `shape_*.xml` 复用文件名和 padding，收敛圆角与颜色。影片元信息不再按年份/来源任意分配蓝/红/绿状态颜色。

Java（仅 leanback）：

```java
int skin = TvTheme.getSkin(); // 0 香槟金，1 冰川蓝，2 翡翠绿
String[] labels = TvTheme.getSkinLabels(activity);
TvTheme.setSkin(skin);
int color = TvTheme.color(activity, R.attr.tvColorAccent);
boolean enabled = TvTheme.isAtmosphereEnabled();
TvTheme.setAtmosphereEnabled(enabled);
```

解析运行时颜色必须传**当前 Activity 或其 themed Context**，不能传全局 Application 来期望得到所选皮肤。新增 Drawable 必须从当前 Context 加载，否则会使用默认皮肤。

## 换肤生命周期与兼容

`BaseActivity.onCreate()` 在 `super.onCreate()` 和 View inflation 前 `setTheme()`。`tv_skin` 是独立偏好，不覆盖原 `theme_color`、壁纸文件或 mobile 资源。无效皮肤 ID 回退 0。

设置页保存后重建自身；返回仍在栈中的非播放页时，BaseActivity 检测皮肤或影片氛围偏好差异并重建。输入/列表焦点等页面状态由相应页面负责保存恢复。`PlaybackActivity` 及所有子类明确**不因换肤重建**，避免小窗、直播或全屏重播；这些已有播放页维持创建时的皮肤与氛围快照（`isFilmAtmosphereEnabled()`），正常重新打开时再应用新选择。这是有意的安全取舍，而非承诺无重建实时全量换肤。

`tv_film_atmosphere` 独立于皮肤。尚无显式选择时，内置壁纸默认允许氛围，自定义图片/GIF/视频默认不覆盖；保存开关后尊重用户值。关闭氛围不删除图片或清除壁纸偏好。Home/Video 仅在关闭影片氛围时创建原壁纸 View；片库、搜索、收藏、设置等使用稳定主题底色，不在其后播放不可见壁纸。背景组件负责生命周期和过期请求，不能通过换肤 API 改变播放或焦点。

启动 Splash 使用共同的曜石暗底；三种皮肤仅强调色不同，避免启动时显示不一致亮底。共享 `main` 代码与 `mobile` 皮肤没有被该基础层修改。

## 后续新增皮肤

1. 在 `tv_colors.xml` 增加必要颜色，在 `tv_styles.xml` 增加继承 `Theme.App` 的 style；仅覆盖需要变化的语义角色。
2. 在 `TvTheme.THEMES/LABELS` 末尾追加 style 与字符串资源。**持久化 ID 不得重排或复用**，移除皮肤时保留兼容映射。
3. 不新增 `values-theme-*` 等非法目录，不为每套皮肤复制页面 XML/selector。
4. 新增组件先复用现有角色；确需新角色时为默认皮肤及所有独立皮肤提供可解析值。
5. 当前承诺是暗色皮肤；若扩展浅色，需一并检查视频控制、遮罩、遗留白字/图标和全部状态，不能仅替换 background。
6. 完成对比度、弹窗、D-pad、长文本、播放返回与冷启动验收后发布。

## 基础验证结果与边界

- 完整 `:app:assembleLeanbackDebug` 已通过，并在 1080p 模拟器安装验证。具体证据及已知基线 lint/mobile 错误见 [实施报告](TV_UI_IMPLEMENTATION.md)。
- XML 解析通过；稳定面板主文字 13.98:1，次文字 7.49:1；抬升面板次文字 6.27:1。
- 香槟/冰蓝/翡翠强调文字对各自 selected 背景为 6.52/7.01/6.97:1，强调按钮黑字为 9.99/10.96/11.45:1；白色焦点对抬升面板为 12.96:1。
- 上述不是图片合成后对比度认证；图片、视频、亮壁纸上的焦点仍需截图及设备验证。
- 已验证：语义资源/三套静态调色板对比度，模拟器皮肤切换及返回首页重着色，小窗/全屏返回，横图/竖图/空态。真机、API 24、4K、大字号、TalkBack、自定义动态壁纸及播放中跨栈换肤仍需专项验证。

## UI 收尾补充

- 播放设置、解码设置、配置与 MPV 配置弹窗的主次文字和输入提示已接入现有语义属性；不按皮肤复制布局。
- 设置页明确告知：已有播放页面重新打开后应用新皮肤，不为立即换色打断播放。
- `Tv.HeroAction` 显式统一上下内边距，避免不同背景 drawable 改变大字号下的按钮高度。
- 本轮大字体及截图验证见 [TV_UI_POLISH.md](TV_UI_POLISH.md)；不表示全部遗留控件已支持浅色皮肤。
