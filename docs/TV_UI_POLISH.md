# TV UI 收尾优化记录（A2.1）

日期：2026-09-09。范围严格限定 Leanback TV UI，不涉及网络安全、Manifest、手机端、依赖或播放器内核。

## 已完成

- 首页将配置加载、内容加载、无推荐三种状态分开；配置失败提供“重新加载配置”和“设置”，避免重试误触发内容请求。错误正文改为可本地化的恢复指引，技术异常仍保留在既有通知日志。
- Hero 前景图统一使用 `FIT_CENTER`，保留影片主体；影片图不足以铺满时由同片低分辨率模糊氛围层填充。图片槽固定高度，避免 drawable 固有尺寸撑高首屏。
- 收藏页新增可见“管理收藏/完成”，删除需要确认；取消、删除中间项、末项及最后一项后均恢复到可预期焦点，空态聚焦“去找影片”。焦点缩放降至 1.04，并与 selected 状态分离。
- 首页在系统大字号（fontScale > 1.15）时切换两行工具栏、隐藏时钟，方向键从主导航可到片源/工具再到 Hero；Hero 文本与按钮自适应且按钮内边距统一。
- 播放设置、解码设置、配置及 MPV 弹窗的文字/输入提示补齐语义主题属性；主题仍通过集中 palette/attrs 扩展，现有三套暗色皮肤可继续换肤。

## 验证

- `:app:assembleLeanbackDebug`：通过，APK 已安装模拟器。
- 资源/源码回归：`python3 tools/tests/test_tv_theme_resources.py` 12 项通过；RecordAdapter 12 项通过；Blur 7 项、氛围契约 4 项通过；`git diff --check` 通过。
- 1080p Android TV 模拟器（`emulator-5554`）实机截图验证：默认首页横图完整主体与推荐卡片；收藏管理、确认删除、取消焦点恢复、中间/最后删除和空态；配置失败→“重新加载配置”→恢复；fontScale 1.3/1.5 两行导航与主要操作可见；设置页主题入口及不中断播放提示。
- 默认 TV lint 仍有基线 Manifest 4 errors 与 warnings；过滤已知基线 ID 的 lint 通过。未修改 Manifest 以隐藏问题。ARTEMIS 自主任务因 API_KEY_INVALID 失败，仅使用 ARTEMIS hierarchy/screenshot observer 和 ADB 做闭环验证。

## 后续风险

需要目标电视实测 720p/4K、低端设备模糊性能、TalkBack、动态壁纸、长标题及真实内容源；这些不在本轮 UI 收尾范围。

## 焦点交互微调（2026-09-09）

原先所有控件使用纯白焦点边框，与香槟金/冰川蓝/翡翠绿主题的强调色脱节。本次将焦点边框改为主题专属浅强调色：香槟 `#E7C995`、冰川 `#B9E1FA`、翡翠 `#B5E6D0`；仍保持至少 3:1 的边界对比度，且焦点与 selected 继续分离。

`Tv.HomeNav` / `Tv.HeroAction` 增加 Android `stateListAnimator`：获得焦点时 140ms 缓动至 1.025 倍，失焦 120ms 回落。缩放幅度保持克制，避免电视远距离观看时跳动、遮挡相邻控件；系统动画时长设置为 0 时由 Android 自动禁用动画。收藏卡片沿用既有 1.04 倍焦点动画，不叠加第二层缩放。

## 触摸滚动回弹修正（2026-09-09，替代上一轮方案）

### 根因与上一轮遗漏

上一轮将问题归因于 `NestedScrollView.requestChildFocus()` 并增加 `TvNestedScrollView`，但首页实际使用 Leanback `VerticalGridView`，点播分类页使用其子类 `CustomVerticalGridView`，没有被该补丁覆盖。设置页的原生滚动容器在本次拖动探查中也未稳定复现同样问题。因此撤回该自定义 NestedScrollView 及 9 处 XML 替换，不再把它作为已验证修复。

当前依赖为 Leanback 1.2.0，其网格模块为 `leanback-grid:1.0.0`。检查实际依赖的反编译代码：`GridLayoutManager.onLayoutChildren()` 在 `mFocusScrollStrategy == 0`（ALIGNED）时按旧 `mFocusPosition` 执行焦点对齐；触摸已滚离旧位置，但布局刷新仍可能将旧选中项拉回视口。修复前首页连续滑动确实出现回到 Hero/历史区域。异步图片布局是可能的触发条件，而不是已逐次追踪确认的唯一触发源。

### 本次调整

- 新增 `TvVerticalGridView.java`，集中处理输入方式切换，不逐页面添加延时或强制设置滚动坐标。
- 触摸按下/鼠标滚轮事件进入 pointer 模式：保存原策略，临时使用 `FOCUS_SCROLL_ITEM`，禁止子项焦点搜索，并由网格自身持有焦点。
- 抬手/取消后不立即恢复旧对齐，覆盖惯性滚动及后续布局刷新。
- 下一枚方向键/确认键先停止惯性滚动，从可见且可聚焦的子项恢复焦点，再恢复原始策略。首枚按键用于建立可见焦点，不直接打开影片；后续按键正常导航。
- `CustomVerticalGridView.java` 复用该基类；`activity_home.xml`、`activity_file.xml` 接入。`fragment_type.xml` 通过原有自定义网格继承获得修正。
- 无新增依赖，不修改手机端、主题、播放逻辑或 Manifest。

### 验证证据

设备：`emulator-5554`，1920×1080 Android TV；新 APK 已安装。使用 ARTEMIS hierarchy observer 定位并核对页面，ADB 注入手势/方向键及截图；ARTEMIS 自主任务之前因 API_KEY_INVALID 不可用，未将其任务完成状态作为证据。

- 首页深度连续滑动后停留 8 秒，内容截图区域逐像素一致（排除顶部时钟），未回到顶部；切回方向键，焦点落在当前可见影片。
- 点播分类页连续 6 次向上拖动以浏览下方内容，停留 10 秒，内容截图逐像素一致；切回方向键，焦点在当前可见行而非首行。
- 分类页 ADB mouse-source 拖动后停留 8 秒，内容位置保持；触摸点击可见影片确实进入 `VideoActivity`，但当时详情仍在加载，不据此宣称播放验证通过。
- `python3 tools/tests/test_tv_pointer_scroll.py`：12 项输入状态转换检查通过。测试编译真实生产类、替换 Android/Leanback 边界；不模拟真实布局，不能替代设备验证。
- 主题资源检查 13 项、RecordAdapter 检查 12 项通过；`:app:assembleLeanbackDebug` 和 `git diff --check` 通过。
- 默认 `:app:lintLeanbackDebug` 仍失败于既有 4 项 Manifest 错误：MissingLeanbackLauncher、ImpliedTouchscreenHardware、MissingIntentFilterForMediaSearch、MissingLeanbackSupport。未通过屏蔽规则掩盖它们。

本地截图与依赖诊断保存在 `.omx/state/tv-scroll-debug/`（不作为产品资源提交）。

### 剩余验证范围

实体电视、真实鼠标滚轮/触摸板、文件列表的长列表、空数据/换源及频繁前后台切换仍需补充设备回归；滚轮目前仅有状态转换测试，mouse-source 拖动不等同于滚轮验证。未宣称所有二级页面或所有输入设备均已验证。未来升级 Leanback 时需重新核对焦点/布局策略；不要在 ACTION_UP 立即恢复旧 ALIGNED 策略，否则可能重新引入回弹。
