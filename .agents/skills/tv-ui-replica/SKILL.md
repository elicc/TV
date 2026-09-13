---
name: tv-ui-replica
description: 基于 FongMi TV 仓库内的 Stitch 静态设计稿和 tv-ui-architect 交互原型，将用户指定页面完整复刻为 app/src/leanback 原生 Android TV 实现。用户要求“按某页面/设计稿复刻”、对齐 TV 页面布局样式、D-pad 焦点和现有业务功能时使用；不用于 mobile flavor 或仅做设计评审。
---

# TV UI Replica

把用户点名的设计页面落到现有原生 Android TV 应用中。交付物是可编译、可遥控操作、接入真实业务数据和状态的 `leanback` 实现，不是 React 演示、静态 XML 或只给建议。

## 设计源与冲突优先级

先确认仓库根目录同时存在：

- `docs/stitch_fongmi_tv_ui_design/`
- `docs/tv-ui-architect/`
- `app/src/leanback/`

按以下顺序裁决冲突：

1. 用户点名页面目录中的 `screen.png`：该状态的视觉基准。
2. 同目录 `code.html`：尺寸、间距、层次、颜色、文案和显式交互细节。
3. `docs/tv-ui-architect/src/components/`：页面流程、控件状态和跨页面交互意图。
4. `docs/stitch_fongmi_tv_ui_design/champagne_cinema_leanback/DESIGN.md` 与 `fongmi_tv_android_tv_v1.2.md`：页面未规定处的全局设计系统。
5. 现有 Android 代码：真实数据、导航、播放、配置、生命周期和错误恢复行为的权威来源。

视觉规范不能覆盖真实业务语义；现有旧样式也不能成为偏离明确设计稿的理由。不要把原型中的 mock 数据或伪功能带入生产实现。

需要了解网页原型到原生 Android 的翻译原则时，读取 [references/source-translation.md](references/source-translation.md)。

## 工作流

### 1. 解析用户所指页面

使用 `<skill-dir>/scripts/design_catalog.py --match "<用户页面描述>"` 查找候选设计稿；用 `--list` 查看完整目录。脚本应在仓库内运行，或显式传 `--root <repo>`。

- 唯一匹配时直接继续，不要求用户重复目录名。
- 多个候选属于同一页面的不同状态时，全部读取并将状态合并为一套实现。
- 只有候选代表实质不同页面且上下文无法消歧时才提问。
- 读取 [references/page-map.md](references/page-map.md)，确认原型组件和 Android 入口。不要仅凭 Activity 名称猜测职责。

至少用图像查看工具实际查看目标 `screen.png`，并读取 `code.html`、相关 React 组件，以及 Android 当前 Activity/layout/adapter/presenter/custom view；不得只凭目录名或 HTML 猜画面。先运行 `git status --short`，避免覆盖用户或其他 agent 的改动。

### 2. 建立实现清单

动手前写一个简短、可执行的清单，覆盖：

- 视觉层级：安全区、网格、对齐、比例、颜色、字体、圆角、图标、背景、动效。
- 页面状态：加载、已有内容、空态、错误态、选中态、弹层或抽屉。
- 遥控交互：初始焦点、上下左右路径、OK、Menu、Back、焦点恢复、边界行为。
- 真实功能：数据来源、adapter 更新、导航目标、播放/配置/收藏等副作用。
- 验证证据：构建、lint、遥控路径和与基准图的截图对比。

用户要求的是实现时，清单之后自动执行，不停在规划或等待批准。

### 3. 在原生 Leanback 架构内实现

- 默认只修改 `app/src/leanback/`；只有现有共享契约确实需要时才触及 `app/src/main/`。
- 复用 ViewBinding、现有 Activity/Fragment、adapter/presenter、`TvTheme`、`TvMotion`、焦点 selector、字体和自定义 View。先扩展既有 token/组件，不创建平行设计系统。
- 保留资源 ID、Intent extra、ViewModel/数据库/API/播放控制器契约。布局重排后同步 Java 绑定与焦点逻辑。
- 将 HTML/Tailwind/React 语义翻译为 Android XML/drawable/color selector/animator/Java；不得把网页原型嵌进 WebView，不得为复刻引入 React 或新依赖。
- 使用真实动态数据。原型文案仅可用于设计稿明确的静态标签、提示或空态；不得用它替代 API、配置、播放或收藏行为。
- 所有可操作控件必须可聚焦且具备清晰 focused/pressed/selected/disabled 表现；保证任一时刻只有一个主焦点。显式处理 `nextFocus*`、首焦点、返回后的焦点恢复，以及放大时的 `clipChildren`/层级遮挡。
- 保持 1080p 设计比例与 overscan 安全区，并检查长中文、缺图、慢加载和内容数量变化。不要把设计稿像素机械当成所有密度下的 dp；以设备实渲染结果校准。
- 动效只使用渲染属性并遵守现有 reduced-motion/低性能策略。播放器、直播和列表滚动不得因装饰动画阻塞。
- 不改变用户未指定页面，不做无关格式化，不添加依赖。

### 4. 验证并迭代

先运行最小编译闭环，再执行完整相关检查：

```bash
./gradlew :app:assembleLeanbackDebug
./gradlew :app:lintLeanbackDebug
```

如变更触及共享代码，再构建/检查 mobile flavor。若仓库已有相关单元或仪器测试，也必须运行。

在 Android TV 设备或模拟器上逐项验证初始焦点、D-pad 四向、OK、Menu、Back、焦点恢复，以及本次涉及的加载/空态/错误/真实内容路径。若编写测试代码，先按仓库的 ARTEMIS 规则探索真实设备流程，再写稳定定位与等待条件。

以目标 `screen.png` 为基准在相同或等比例 16:9 视口截图。每轮视觉修改后、下一次编辑前运行 `$visual-verdict`，根据结构、间距、字体、颜色、焦点态和溢出差异迭代；按仓库约定保存 verdict 状态。无法获得设备截图时不得声称像素对齐，只能报告已完成的静态/构建验证和剩余视觉风险。

## 完成条件

仅在以下条件同时满足时结束：

- 指定页面及其设计稿相关状态均已实现，不是占位 UI。
- 真实业务行为未退化，所有可操作元素可通过遥控器到达并能执行。
- 构建与相关 lint/test 通过，输出已阅读。
- 已完成设备视觉对比，或明确记录设备不可用造成的唯一验证缺口。
- 最终报告列出设计源、变更文件、功能/视觉调整、验证证据、简化点和剩余风险。

Task: {{ARGUMENTS}}
