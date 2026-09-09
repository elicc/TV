# Design

## Source of truth
- Status: **Implemented — 用户已授权执行 A2.1，并补充主题色可扩展与换肤要求；已完成首轮 TV 实施，验证范围见实施报告。**
- UI-only refinement: [UI 收尾记录](docs/TV_UI_POLISH.md) 为本轮增量实现与验证依据；不扩大至网络、Manifest、手机端及播放内核。
- Last refreshed: 2026-09-09
- Primary product surfaces: Android TV `leanback`；本轮不改手机端、文档网站、内容接口或播放器内核。
- Evidence reviewed: README.md；TV 首页、详情、搜索、直播、设置 XML；HomeActivity、Product、VodPresenter、CustomRowPresenter；CustomWallView 与焦点 selector；当前模拟器首页截图。
- 完整证据、候选图与约束：[设计审查](design/tv-ui-2026-09/audit.md) / [方案展示](design/tv-ui-2026-09/index.html)。
- 当前方向：[A2主流TV调研](design/tv-ui-a2/research.md) / [A2新版方案](design/tv-ui-a2/index.html)。上一轮A/B/C保留为历史备选，原先B优先的建议不覆盖用户的新偏好。
- 本轮综合交接：[A2.1 综合调整与执行方案](docs/UI_UX_A2_EXECUTION_PLAN.md)，已评估 `docs/UI_UX_OPTIMIZATION.md` 并核验关键结论；详细取舍、模糊规则、任务与验收以该文件为准。A2 图片保留为概念布局参考；当前截图、偏差及未验证项见 [实施报告](docs/TV_UI_IMPLEMENTATION.md)。
- 当前 leanback Debug APK 已由工作区源码构建并安装验证；原始设计审查截图仍只代表当时基线。

## Brand
- Personality: 安静、清晰、可信赖的个人影视播放器，不是广告或会员售卖平台。
- Trust signals: 明示当前内容源、播放状态、失败原因；不伪造评分、热度、HDR/4K 或推荐能力。
- Avoid: 高亮壁纸压过内容、满屏品牌色、过多标签、实时全屏/视频玻璃模糊、强制自动预览；允许低分辨率静态影片图模糊氛围。

## Product goals
- Goals: 快速续播、容易找片、直播易达、高级播放能力可发现但不干扰常用操作。
- Non-goals: 内置内容、商业推荐算法、移除外部配置、重写播放引擎或切换 UI 框架。
- Success signals: 首屏至少完整展示一排内容标题；未配置/空/失败均有明确下一步；D-pad 不丢焦点；返回恢复上下文。

## Personas and jobs
- Primary personas: 遥控器为主的常规观众；需要换源、字幕、音轨的进阶用户；家庭共用场景。
- User jobs: 继续上次内容、查找影片、切换直播、管理收藏、配置内容源。
- Key contexts: 横屏 16:9、客厅约 2.5–3.5 米；观看距离与长辈使用比例为设计假设，需后续确认。

## Information architecture
- Primary navigation: A2采用顶部 首页 / 点播 / 直播 / 收藏 / 搜索；推送、本地入口及低频工具集中到更多，设置与当前片源保持可见。
- 顶栏横向键只移动焦点；片源入口通过确定键/菜单键打开选择，不使用原控件的左右键快速换源或闪烁焦点。
- Core routes: 首页、片库及筛选、搜索与结果、详情及选集、播放、直播频道/EPG、收藏历史、设置。
- Content hierarchy: 内容与续播优先；配置和解码参数次级；当前内容源以次级文字及显式切换入口表达，不代替应用品牌。
- 未配置时优先展示“添加配置 / 打开本地文件 / 接收推送”；缺少直播配置时不能跳入无解释的空页。

## Design principles
- 一屏一个明确焦点；导航选中、已收藏、播放中不等于遥控器焦点。
- 固定布局和可恢复上下文优先于视觉动效。
- 内容图承载氛围，稳定底色承载文字与交互。
- Tradeoffs: 用户倾向A，推荐继续A2而非切回B侧栏；A2通过横图/竖海报适配降低素材依赖。B/C作为历史备选，最终以用户选型为准。

## Visual language
- Color: A2.1当前建议背景 `#101114` / 表面 `#232429` / 强调 `#D7B889` / 主文字 `#F5F3EF` / 次文字 `#B7B4AE`；默认采用炭黑香槟金；另提供冰川蓝、翡翠绿两套暗色皮肤。语义角色与扩展方式见 [主题约定](docs/TV_THEMING.md)。
- Typography: 960×540 逻辑参考下，导航/常用控制建议 18–20sp，片名 18sp 左右，元信息 14–16sp，页面标题 24–32sp，Hero 32–40sp。C 关键标签 20–22sp。静态图使用像素值，不可直接当 Android sp 使用。
- Spacing/layout rhythm: 4/8dp 节奏；卡片间距 12–16dp，分区 24–32dp；关键内容安全区参考左右 48dp、上下 27dp，需同时容纳焦点扩张。
- Shape/radius/elevation: 卡片约 8dp、控件 6–8dp、面板 12–16dp；避免任意混用 4dp 与 28dp。非必需阴影禁用。
- Motion: 焦点 120–180ms，缩放 1.03–1.05 起步，尊重系统动画设置；不触发布局重排，不抢焦点。默认不自动播放预览。
- Imagery/iconography: 统一图标笔画与光学尺寸，关键动作有文字；正式海报保留来源比例，竖图 2:3 / 横图 16:9，可 letterbox，不横向拉伸竖海报。概念图中的插画/生成图片仅用于视觉比较。
- Home artwork: 首页清晰前景统一 `FIT_CENTER` 完整展示，横图也不做主体裁切；独立低分辨率模糊层填充氛围。图片槽固定最小高度，文本自适应，不让图片固有尺寸撑高首屏。
- Poster atmosphere: 缺少合适高清横图时，将当前影片图片低分辨率等比裁切、后台静态模糊后叠加暗色/渐变遮罩；前景海报保持清晰和原比例，文字与焦点不模糊。无图/失败回稳定暗底。参数与缓存/请求代次规则见 A2.1 交接文档第 3 节；不模糊实时视频、不改变全局强调色。

## Components
- Existing components to reuse: Leanback grids、CustomRowPresenter、VodPresenter 与 Holder、XML selectors、现有焦点返回处理、图片加载和主题资源机制。
- New/changed components: 优先扩展已有资源；必要的语义色、状态卡、分组设置、主次按钮不引入新 UI 框架或依赖。
- Variants and states: default / focused / selected / pressed / disabled / loading / error；选中使用勾选/短线及文字，焦点使用高对比边框及可选轻缩放。
- Ownership: 首轮资源覆盖限定 leanback；如涉及共享 CustomWallView，必须保护 mobile 并回归两个 flavor。

## Accessibility
- Target standard: 将 WCAG 对比度作为可测量设计目标，不宣称 Android 原生合规认证。
- Keyboard/focus: 全程五向键完成核心任务；抽屉关闭、返回、分页、换源及删除后焦点有明确落点；长按不是唯一操作入口。
- Contrast: 正文及元信息目标 ≥4.5:1，重要边界/焦点目标 ≥3:1；同时验证图片背景、视频、暗/亮壁纸和失焦状态。
- Screen reader: 图标按钮语义、内容标题、选中状态、错误说明和阅读顺序在实施时检查 TalkBack。
- Reduced motion: 提供减少动态背景选项；空态不依赖持续动画，颜色不是唯一状态线索。

## Responsive behavior
- Supported devices: 720p/1080p/4K TV，默认与大字号，低性能盒子；16:9 优先。
- Large text: 首页 `fontScale > 1.15` 时导航分两行，隐藏时钟；向下按键按主导航 → 片源/工具 → Hero 顺序。按钮高度随文字自适应，上下内边距统一。
- Layout adaptations: 依据逻辑尺寸、现有尺寸偏好与内容源 Style 适配列数；不把截图像素硬编码为 dp。
- Touch/hover: TV 不依赖 hover 或触摸；手机保持原 flavor 交互。

## Interaction states
- Loading: 内容区域骨架或明确进度；不抹掉可用导航，不反复重置焦点。
- Empty: 无历史隐藏整组或显示单行引导；无收藏提供“去找影片”。
- Error: 区分配置失败、无结果、海报失败、线路失败；对应重试/换源/编辑配置。
- Success: 收藏、配置、切换线路有简短结果反馈，不跳离上下文。
- Disabled: 无音轨/字幕等能力明确不可用或合理隐藏，同时保持焦点安全。
- Offline/slow network: 保留已加载内容、本地播放及最近记录；缺失海报使用稳定底色与片名，不展示虚假评分。

## Content voice
- Tone: 简短、具体、可行动；先说明发生了什么，再给下一步。
- Terminology: 点播、直播、内容源、播放线路分别命名；“上次观看”不能写成“播放中”。
- Microcopy: “继续 38:12”“无法连接内容源，重试或检查配置”；长技术描述移到说明页。

## Implementation constraints
- Framework: Java/XML + Leanback + Material；不因设计升级直接迁移 Compose。
- Tokens: 复用并收敛现有主题及 selector，先在 leanback 覆盖，不修改全局手机配色。
- Performance: 控制图片解码尺寸和缓存；静态同片模糊必须后台降采样、受限缓存、取消过期请求；动态壁纸、视频预览、模糊与阴影需要真实设备帧耗时验证。minSdk 24，不能仅依赖 API 31 RenderEffect。
- Compatibility: 不破坏 Spider/config Style、直播条件入口、列表/横竖卡片模式及历史定位。
- Data boundary: Vod未见独立backdrop、字标、预告片或评分字段；横图只在现有图片真正适合时采用，否则保留清晰海报比例并采用同片模糊背景，加载失败时暗底兜底。首页不为填满Hero批量请求详情，不编造简介、热度或画质信息。
- Playback boundary: A2默认保留现有详情小窗、全屏与返回机制，新增明确全屏入口；自动播放策略不因视觉改版被默默改变。
- Test/screenshot expectations: 批准实施后运行 TV 构建/lint、相关回归，必要时 mobile；真机验证 D-pad、长标题、图缺失、慢源、多线路、多集、字体放大及播放返回。

## Open questions
- [x] 用户偏向A，要求继续调研主流TV应用后深化。
- [x] 用户补充缺少高清横图时采用影片图模糊氛围，已纳入 A2.1。
- [x] 用户已确认按方案执行，并要求可扩展换肤；三套暗色皮肤已接入设置。
- [ ] 更偏点播发现、直播还是直接续播？用于微调默认焦点与导航排序。
- [x] 保留壁纸偏好，增加独立影片氛围开关；氛围启用时 Home/Video 不创建壁纸 View，浏览/工具页使用稳定底色。自定义 GIF/视频真机性能仍待验证。
- [x] 保留详情小窗及既有自动播放策略，提供明确全屏入口；纯详情再播放不属于本轮变更。
- [ ] 主要电视尺寸、距离、是否需要额外大字档；确定后校准卡片密度。
