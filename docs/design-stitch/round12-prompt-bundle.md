# Round 12 Stitch 迭代提示词包

适用项目：FongMi TV · Android TV Leanback · Champagne Dark v1.2 基线。
本轮一次性修复 11 张画板在归档走查中暴露的 A/B/C 三类问题。

---

## 全局硬约束（任何画板必须遵守）

- 色板：`#0E0E10` bg · `#202024` surface · `#E5A958` accent · `#FCD58B` focus（Ice 主题用 `#B8E0FF`，Jade 主题用 `#B8E8D2`）
- 焦点态：**outline + box-shadow 双保险**（不要只用 box-shadow）：
  ```
  outline: 3.5px solid #FCD58B !important;
  outline-offset: 2px;
  box-shadow: 0 0 25px rgba(252,213,139,0.45), 0 16px 40px rgba(0,0,0,0.7) !important;
  transform: scale(1.05);
  z-index: 40;
  ```
- 主标题 ≥ 20px · 卡片主名 ≥ 20px · 正文 ≥ 14px · 角标 11-12px
- 72px 上下左右 Overscan
- 纯线性 SVG 图标，禁止任何系统 emoji（📡 ⚙ 🎧 📍 等全部换 SVG）
- 中文文案准确，无错别字（**`厄rakis` → `厄拉科斯`**）
- minSdk 24 兼容：不使用 RenderEffect / 高 API 特性
- 屏幕宽 1920×1080，无真实剧照，全语义汉字水印海报

---

## 1️⃣ Detail 画板 — Round 12 修复指令

**继续设计 FongMi TV Leanback 1080p Detail 画板**

聚焦三处修复：

1. **【A 类】视频小窗右下 4 个操作按钮（选集 / 换源 / 倍速 / 收藏）**：当前完全无焦点态规范。修复方案 — 给它们全部加上焦点态描边：默认态用 `bg-[#202024] border border-white/10`；当前选中态（用 `选集` 作为示范焦点）切到 `bg-[#E5A958]/15 border-2 border-[#FCD58B] shadow-[0_0_16px_rgba(252,213,139,0.35)]`。
2. **【A 类】中文拼写**：所有出现的"沙丘 2：厄rakis 命运之战"统一改为"沙丘 2：厄拉科斯 命运之战"。面包屑、Hero 标题、状态矩阵文案、相关推荐卡（卡 5 沙丘第一部）全部同步。
3. **【B 类】元数据 5 行**（年代/类型/导演/主演/语言）：从 14px 提升到 16px，行高从 `leading-relaxed` 改为 `leading-loose`，3m 视距更易读。

画板右上角保持 Champagne Dark v1.2 标签，色彩系统与 overscan 沿用上轮。其他元素（顶部 Header、左侧视频小窗、下方 3 行 chip、底部相关推荐 5 卡、4 大状态矩阵）结构不变。

---

## 2️⃣ Player 画板 — Round 12 修复指令

**继续设计 FongMi TV Leanback 1080p Player 画板**

仅一处修改：

1. **【A 类】中文拼写**：所有"沙丘 2：厄rakis 命运之战"统一改为"沙丘 2：厄拉科斯 命运之战"。顶部片名 + 弹幕示例中提及片名处全部同步。

底部控制台、进度条、左侧快进 widget、右侧调优抽屉、scrim 上下渐变、弹幕示例（4 条全部用通用观影讨论，不依赖真实剧照）保持现状。

---

## 3️⃣ Live 画板 — Round 12 修复指令

**继续设计 FongMi TV Leanback 1080p Live & EPG 画板**

两处修复：

1. **【A 类】系统 emoji 替换**：右侧"实时直播源与解码设置"面板标题前的 `📡` emoji（line 330 附近），替换为线性 SVG 图标 — 用 `<svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M5 12.55a11 11 0 0114 0M8.5 16.5a6 6 0 017 0M12 20h.01M2 8.82a15 15 0 0120 0"/></svg>`（卫星信号线性图）。颜色与标题一致用 `text-accent` (#E5A958)。
2. **【D 类】EPG 文案精简**：当前频道项 1 / 4 / 5 出现 `21:00-21:35 (75%)` 这种文字 + 百分比混排，3m 视距读不清。修复方案：保留 `21:00-21:35` 时间段，副行只显示节目名 + 状态徽章，百分比用进度条代替；只在当前播放项的副行加一行 `font-mono text-[12px] text-amber-300/90` 的 `进度 75%` 短标签。

频道项 3（CCTV-5+ 4K 焦点态，含 EPG 子节目单展开）保持原样，其他频道项按 D 类调整。

---

## 4️⃣ Search 画板 — Round 12 修复指令

**继续设计 FongMi TV Leanback 1080p Search & Keyboard 画板**

两处修复：

1. **【A 类】中文拼写**：所有"沙丘 2：厄rakis 命运之战"统一改为"沙丘 2：厄拉科斯 命运之战"。右侧 4 张结果卡的卡 1 标题与"智能首字母联想"chip 1 文字同步。
2. **【B 类】4 张结果卡主标题统一从 16px 上调至 20px**：当前 `text-base font-bold text-white` 改成 `text-[20px] font-bold text-white tracking-tight leading-tight`；副行（年份·类型·首字母）从 12px 上调至 14px，保留 `text-[#8E8E93]`。

T9/QWERTY 切换、5 行键盘（数字/字母/清空/Backspace/Space）、`E` 键焦点态、首字母联想 chip 流、4 个筛选 tab（全部/电影/剧集/动漫）保持现状。

---

## 5️⃣ Settings 画板 — Round 12 修复指令

**继续设计 FongMi TV Leanback 1080p Settings 画板**

三处修复：

1. **【C 类】焦点态双保险**：当前 `focus-ring` class 只用 `box-shadow: 0 0 0 3.5px #FCD58B, 0 0 25px rgba(252,213,139,0.45)`，缺 outline。修复 — 改为：
   ```
   .focus-ring {
     outline: 3.5px solid #FCD58B !important;
     outline-offset: 0px;
     box-shadow: 0 0 25px rgba(252,213,139,0.45), 0 16px 40px rgba(0,0,0,0.7) !important;
     transform: scale(1.02);
     border-color: #FCD58B !important;
   }
   ```
2. **【B 类】左侧 6 个分组项主名**（配置接口/解码与渲染/网络嗅探/直播源/WebDAV/关于）：从 18px 提升至 20px。当前 `text-[18px] font-medium` 改为 `text-[20px] font-semibold`；副行说明从 13px 提升至 14px。
3. **【B 类】右侧核心交互项主名**：解码 3 选项（MediaCodec/FFmpeg/智能自适应）从 16px 提升至 20px；音频透传 2 项（eARC 源码输出/夜间人声增强）从 16px 提升至 20px；SurfaceView/TextureView 2 选项从 16px 提升至 20px。

3 列布局（左 3 + 中 6 + 右 3）、右上"局域网手机扫码推送"二维码区、底部 4 项极简提示条保持原样。

---

## 6️⃣ Collect 画板 — Round 12 修复指令

**继续设计 FongMi TV Leanback 1080p Collect & History 画板**

三处修复：

1. **【A 类】中文拼写**：所有"沙丘 2：厄rakis 命运之战"统一改为"沙丘 2：厄拉科斯 命运之战"。焦点卡 1 + 右侧浮层"快捷操作面板"里的"当前指向影片 (FOCUSED)"行标题同步。
2. **【B 类】左侧 10 张卡片主标题**统一从 18px 提升至 20px。当前 `text-[18px] font-bold` 改为 `text-[20px] font-bold tracking-tight`；副行（类型·来源）从 13px 提升至 14px。
3. **【B 类】右侧"快捷操作面板"内的 4 个操作项主名**（从 01:14:28 继续播放/移出当前收藏/收藏到分类文件夹/清除该条历史记录）：从 `text-sm` 14px 提升至 20px（用 `text-[20px] font-semibold`）；"目标信息 Pill" 里的片名从 `text-sm` 提升至 20px。

顶部 3 个 Tab（我的收藏/观看历史/稍后再看）已使用焦点态 ✓；"焦点卡 1" 的 `tv-focus-primary` 焦点态（3.5dp + 25px glow + 1.045x scale）保持 ✓。其他卡片用 16:9 横版 + 续播进度条 1.5px 细线保持原状。

---

## 7️⃣ Tools 画板 — Round 12 修复指令

**继续设计 FongMi TV Leanback 1080p Tools 画板**

三处修复：

1. **【A 类】系统 emoji 替换**：顶部"工具箱 (Tools)"标题前的 `⚙` emoji（line 95 附近），替换为线性 SVG 图标 — 用 `<svg class="w-5 h-5 text-accent-gold" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/><circle cx="12" cy="12" r="3"/></svg>`。
2. **【C 类】焦点态双保险**：当前 `tv-card-focus` 只用 `border + box-shadow`（box-shadow 也是 `0 0 0 3.5px #FCD58B` 的 ring 模拟），缺 outline。修复 — 改为：
   ```
   .tv-card-focus {
     outline: 3.5px solid #FCD58B !important;
     outline-offset: 2px;
     border-color: transparent !important;
     box-shadow: 0 0 25px rgba(252,213,139,0.45), 0 16px 40px rgba(0,0,0,0.7) !important;
     transform: scale(1.025);
   }
   ```
3. **【D 类】闪退日志卡片路径精简**：底部 `[Native] libijkplayer.so SIGSEGV (0x000000)` 拆为两行 — 主行 `[Native] libijkplayer.so`，副行 `SIGSEGV (0x000000) · 2天前 23:14`，`text-[11px] text-text-dim font-mono`，避免主行字符拥挤。

6 大模块卡片（Cast / Push / File / Live Source / Crash / Profiler）布局 2 列 × 3 行保持；每张卡主标题 22px 已合规 ✓；描述 14px 已合规 ✓；底部"性能 Profiler"迷你柱状图保持。

---

## 8️⃣ Home / Ice / Jade 主题画板 — Round 12 修复指令（轻量）

**继续设计 FongMi TV Leanback 1080p Home Focus Specs 画板（Champagne / Ice / Jade 三主题合并）**

仅一处修改：

1. **【A 类】中文拼写**：所有"沙丘 2：厄rakis 命运之战"统一改为"沙丘 2：厄拉科斯 命运之战"。Hero 标题、卡片 1 标题、相关推荐其他卡片标题全部同步。

Champagne 焦点色 `#FCD58B`、Ice 焦点色 `#B8E0FF`、Jade 焦点色 `#B8E8D2` 维持现状。Hero 大字号 52px / 卡片主名 20px / 焦点态 3.5dp + 25px glow + 1.05x scale / 6 大状态矩阵 / 4 项 WCAG 自检 / 色板/字号阶梯/可访问性清单 全部保持。

---

## 执行顺序建议

| 步骤 | 操作 | 等待提示 |
|---|---|---|
| 1 | Stitch 中选中 Detail 画板 → 用上述 1️⃣ 提示词重新生成 | "1️⃣ 已生成" |
| 2 | Stitch 中选中 Player 画板 → 用 2️⃣ 提示词 | "2️⃣ 已生成" |
| 3 | Stitch 中选中 Live 画板 → 用 3️⃣ 提示词 | "3️⃣ 已生成" |
| 4 | Stitch 中选中 Search 画板 → 用 4️⃣ 提示词 | "4️⃣ 已生成" |
| 5 | Stitch 中选中 Settings 画板 → 用 5️⃣ 提示词 | "5️⃣ 已生成" |
| 6 | Stitch 中选中 Collect 画板 → 用 6️⃣ 提示词 | "6️⃣ 已生成" |
| 7 | Stitch 中选中 Tools 画板 → 用 7️⃣ 提示词 | "7️⃣ 已生成" |
| 8 | Stitch 中选中 Home/Ice/Jade 画板 → 用 8️⃣ 提示词 | "8️⃣ 已生成" |
| 9 | 全部画板按 Shift+D 下载到 ~/Downloads/stitch_fongmi_tv_ui_design/ | "9 个画板已下载" |
| 10 | 我执行归档：`cp -r ~/Downloads/stitch_fongmi_tv_ui_design/* docs/design-stitch/stitch_fongmi_tv_ui_design/` | 你回复"开始归档" |
| 11 | 我跑最终走查，更新 AI_DESIGN_REVIEW.md 的 Round 12 章节 + 把 Round 11.6 缺陷清单重标为 ✅ | 自动 |

---

## 给你节省时间的小贴士

- **逐张走**：不要一次性开 8 个画板，避免状态错乱
- **Stitch 自动建议按钮**：如果你看到画板右下角弹出"继续设计 X 画板"类快捷气泡，先试一下，看 Stitch 自动累积的上下文是否已经覆盖修复意图；如果不完全对，再手动粘贴完整提示词
- **逐张验收**：每张生成完，先看屏幕右下小预览图，确认修复点都到位，再按 Shift+D
