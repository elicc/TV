# Web 设计稿到原生 Android TV 的翻译规则

## 各设计源的用途

| 来源 | 用于 | 不用于 |
| --- | --- | --- |
| 页面 `screen.png` | 构图、视觉比例、目标状态和像素对比 | 推断看不见的行为 |
| 页面 `code.html` | 页面局部尺寸、间距、颜色、层次、文案、显式状态 | 直接复制到应用或决定真实数据流 |
| `tv-ui-architect` React 组件 | 页面组成、交互意图、快捷键、状态切换 | 生产框架、mock 数据或 Android 生命周期 |
| 全局 `DESIGN.md` / v1.2 | token、10-foot 可读性、D-pad、动效、安全区的缺省规则 | 覆盖页面图中明确且有意的差异 |
| 当前 Android 实现 | 业务语义、数据模型、导航、播放、配置和生命周期 | 保留与设计稿冲突的旧视觉 |

## 翻译对照

- Tailwind/CSS flex/grid → 优先沿用现有 XML 容器；复杂约束可用项目已有布局体系，但不要只为视觉复刻引入新框架。
- CSS color/gradient/radius/border → `colors.xml`、theme attr、shape/selector drawable。共享语义色应走 `TvTheme`，页面私有装饰才使用局部资源。
- `:focus-visible` / `.focusable` → state-list drawable/color、stateListAnimator、`OnFocusChangeListener` 或现有 focus helper。
- CSS transform/transition → `TvMotion`、animator/animation 或 View render-property animator；避免每帧 layout 和软件模糊。
- 网页 backdrop blur → 优先使用现有半透明 surface、scrim、`AmbientGlowView`/`FilmAtmosphereView` 等轻量实现；不要假设低端 TV 可承受实时模糊。
- React state → 已有 Activity/Fragment/ViewModel/adapter 状态；跨配置变更或返回栈状态必须按 Android 生命周期恢复。
- 网页 click/keyboard handler → D-pad focus graph、`dispatchKeyEvent`/现有 `KeyUtil`、click/long-click；不要只验证鼠标或触摸。
- React mock collection → 现有 bean、config、database、ViewModel 和 API 结果；缺数据时显示设计稿对应空态。
- Lucide/网页图标 → 优先复用 `app/src/leanback/res/drawable` 中的矢量/位图；新增矢量应遵循现有尺寸与 tint 方式。

## 视觉校准重点

1. 先对齐大结构和安全区，再调卡片/行尺寸，最后调字号、图标与光晕。
2. 分别截取静态默认态和关键焦点态；焦点放大不能被父容器或相邻卡片裁切。
3. 对比 1920×1080 基准时记录设备实际分辨率、density 与系统 overscan；不要用未经说明的坐标截图宣称像素一致。
4. 设计文档中的 72px 和 90px/54px 安全区存在不同版本时，以目标页面实际边缘和 `code.html` 为准；没有页面证据时才采用较保守的边距。

## 功能保真约束

- 页面复刻是现有功能的视觉与交互升级，不是删减业务能力。
- 新设计未展示但原功能必须保留时，将其安置到合理的现有入口、Menu、抽屉或次级操作，而不是删除。
- 原型展示了项目尚不存在的功能时，只有能接入真实现有能力才实现；否则不要制作会误导用户的假按钮。
- 异步回调必须处理 Activity 销毁、重复提交、空结果和错误；adapter 更新后恢复到有效焦点位置。
