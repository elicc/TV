# Round 14 修复提示词包（2 张画板 / 2 类布局缺陷）

适用项目：FongMi TV · Android TV Leanback · Champagne Dark v1.2。
本轮修复归档走查中暴露的 2 个布局类缺陷（D14-1 / D14-2）。

---

## 1️⃣1️⃣ Collect 画板 — Round 14 修复指令

**继续设计 FongMi TV Leanback 1080p Collect & History 画板**

仅一处修改（卡片网格对齐根因）：

1. **【D14-1】卡片网格"底部对齐"修复**：
   当前 `<main class="flex-1 flex gap-6 relative overflow-visible">` 内部第一个子 div 是
   `<div class="flex-1 flex flex-col justify-between">`（line 172 附近），导致卡片网格在垂直方向被 `justify-between` 拉散——顶部一排贴上沿、底部一排贴下沿，中间留大片空白。

   修复方案：把这个外层 div 改为 `<div class="flex-1 flex flex-col gap-3">`，去掉 `justify-between`。
   - 第 1 个直接子元素（"近期活跃与在看"日期分组行）保留原样
   - 第 2 个直接子元素（`<div class="grid grid-cols-5 gap-5 w-full">`）保留原样
   - 在两元素之间加 `gap-3`（12px）保持视觉分组
   - 卡片网格内部不再被两端拉开，**自然从顶部向下排**，第 2 行卡片紧跟在第 1 行下方，底部留白由 body 的 `justify-between` 自然分配给 footer

2. **【A/B/C 类合规保持】**：拼写「厄拉科斯」、卡片主名 20px、焦点态 `tv-focus-primary` 双保险、续播进度条 1.5px 全部维持 Round 12 状态。

---

## 1️⃣2️⃣ Detail 画板 — Round 14 修复指令

**继续设计 FongMi TV Leanback 1080p Detail 画板**

一处修改（视频小窗与下方"播放线路"重叠修复）：

1. **【D14-2】视频小窗底部的"焦点提示"和"快捷键提示"行**（line 181 附近）：
   当前是单行 `flex items-center justify-between` 结构，把三段长文字压在一行里，挤在视频小窗与"播放线路" chip 行之间，发生视觉重叠。

   修复方案：把这个 div 拆为**两行独立结构**，增加呼吸感：
   ```html
   <!-- 第一行：焦点状态提示（独占一行，左对齐） -->
   <div class="mt-4 flex items-center gap-2 text-[13px] text-[#8E8E93]">
     <span class="w-2 h-2 rounded-full bg-[#FCD58B]"></span>
     <span class="text-[#FCD58B] font-medium">当前焦点：视频小窗</span>
     <span class="text-white/40">· 3.5dp #FCD58B 描边 + 25px 柔光 + 1.04x 放大</span>
   </div>
   <!-- 第二行：快捷键提示（独占一行，右对齐） -->
   <div class="mt-1.5 flex items-center justify-end gap-2 text-[12px] text-[#8E8E93]">
     <span class="px-1.5 py-0.5 rounded bg-white/10 text-white font-mono text-[11px]">长按 OK</span>
     <span>换源弹出菜单</span>
   </div>
   ```
   - 第一行 `mt-4` (16px) 替代原 `mt-2.5` (10px)
   - 删掉原"3.5dp #FCD58B 描边 + 25px 柔光 + 1.04x 放大"那段冗长文字——视频小窗本身已经有焦点态可视表达，无需文字二次重复描述；改为简短"当前焦点：视频小窗"即可
   - 快捷键提示行 `mt-1.5` (6px) 紧贴焦点行下方

2. **【D14-2 续】"播放线路" section 顶部间距**：line 234 `<section class="mt-6 space-y-4">` 改为 `<section class="mt-8 space-y-5">`（24→32px 行间距、16→20px section 间距），确保线路 chip 行与上方焦点提示之间有明显呼吸空间，不再视觉粘连。

3. **【D14-3】视频小窗底部渐变**：line 169 那个 `bg-gradient-to-t from-black/95 to-transparent` 改为 `bg-gradient-to-t from-black/85 to-transparent`，让小窗边界更清晰，避免黑色渐变与提示文字背景色混淆。

4. **【保持项】**：4 个操作按钮焦点态（选集描边 + 换源/倍速/收藏默认）、拉科斯拼写、16px 元数据、Overscan 72px 全部维持。

---

## 执行顺序

| 步骤 | 操作 |
|---|---|
| 1 | Stitch 中选中 Collect 画板 → 粘贴 1️⃣1️⃣ 段 → 等待生成 |
| 2 | Stitch 中选中 Detail 画板 → 粘贴 1️⃣2️⃣ 段 → 等待生成 |
| 3 | 2 张画板按 Shift+D 下载替换到 `docs/design-stitch/stitch_fongmi_tv_ui_design/` |
| 4 | 通知我"Round 14 已完成"，我跑最终验收：① Collect 检查 line 172 不再有 `flex-col justify-between`；② Detail 检查 line 181/234 间距已改 |