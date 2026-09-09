# Round 13 修复提示词包（仅 2 张画板 / 5 处问题）

适用项目：FongMi TV · Android TV Leanback · Champagne Dark v1.2。
本轮修复 Round 12 验收中暴露的 4 个未达标项。

---

## 9️⃣ Player 画板 — Round 13 修复指令

**继续设计 FongMi TV Leanback 1080p Player 画板**

仅一处修改（弹幕示例区，纯 UI 文案，禁止 emoji）：

1. **弹幕示例 line 96**：`汉斯·季默配乐真的封神！低音炮在震了 🎧` → `汉斯·季默配乐真的封神！低音炮在震了` （去掉末尾的耳机 emoji）
2. **弹幕示例 line 107**：`遥控器按 [菜单] 键唤起音轨与外挂字幕设置 ⚙️` → `遥控器按 [菜单] 键唤起音轨与外挂字幕设置` （去掉末尾的齿轮 emoji）

如果去掉 emoji 后弹幕显得视觉单调，可在文字末尾追加一个**线性 SVG 喇叭小图标**（不要用 emoji），path: `M5 9V15H9L13 19V5L9 9H5ZM16.5 12C16.5 10.23 15.5 8.71 14 7.97V16.02C15.5 15.29 16.5 13.76 16.5 12Z`，stroke="#FCD58B"，stroke-width="1.5"，与弹幕金色文字协调。

其他元素（顶部片名"沙丘 2：厄拉科斯 命运之战"已正确 ✓、底部控制台、进度条、左侧快进 widget、右侧调优抽屉、scrim 上下渐变）保持现状。

---

## 🔟 Home 画板（home_focus_specs_1 / _2） — Round 13 修复指令

**继续设计 FongMi TV Leanback 1080p Home Focus Specs 画板**

仅一处修改（两处文字 → 已检查 _1 / _2 两张变体都漏修）：

1. **Hero 标题（line 168 in _1, line 170 in _2）**：`沙丘 2：厄rakis 命运之战` → `沙丘 2：厄拉科斯 命运之战`
2. **第 1 张卡片主名（line 238 in _2）**：`<h3>沙丘 2：厄rakis</h3>` → `<h3>沙丘 2：厄拉科斯</h3>`

字体（Plus Jakarta Sans + Noto Sans SC）、色板（Champagne Dark v1.2）、Hero 大字号 52px、5 张横版海报水印、6 大状态矩阵、4 项 WCAG 自检 全部保持现状。Ice / Jade 主题画板（home_focus_specs_ice_jade）已通过验收 ✓，无需改动。

---

## 执行顺序

| 步骤 | 操作 |
|---|---|
| 1 | Stitch 中选中 Player 画板 → 粘贴 9️⃣ 提示词 → 等待生成 |
| 2 | Stitch 中选中 home_focus_specs_1 画板 → 粘贴 🔟 提示词（明确说是该变体） |
| 3 | Stitch 中选中 home_focus_specs_2 画板 → 粘贴 🔟 提示词（明确说是该变体） |
| 4 | 3 张画板按 Shift+D 下载替换 |
| 5 | 通知我"Round 13 已完成"，我跑最终硬约束审计（应 0 emoji / 0 rakis） |
