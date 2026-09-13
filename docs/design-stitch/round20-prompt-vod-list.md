# Round 20 · 点播影片列表页（VOD List）完整提示词

> **目标**：补全 FongMi TV 点播影片列表页（**未在 Round 2-3 单独出画板**的关键中间页）。
> 触发链路：Home → 点播 Tab → 二级分类（如"科幻大片"）/ 搜索结果 / 演员作品集 → VOD List
> 项目代码映射：`app/src/leanback/res/layout/activity_vod.xml` + `adapter_vod.xml` + `adapter_vod_list.xml` + `adapter_vod_oval.xml`
> 适用项目：FongMi TV · Android TV Leanback · Champagne Dark v1.2。

---

## 全局硬约束（继承 Round 1-19）

- 1920×1080 · Champagne Dark v1.2 · 72dp Overscan
- 字号：Hero 38sp / Section 26sp / Card 20sp / Body 16sp / Caption 14sp / Meta 12sp
- 焦点态：颜色 (#FCD58B) + 形状 (3.5dp 描边 + 25px glow) + 缩放 (1.05x) 三因素
- 纯线性 SVG 图标，禁任何 emoji
- 严禁真实电影海报 / 剧照
- 海报必须用语义化抽象封面（渐变 + 巨幅首字 + 模糊氛围底），3 种卡片形态：
  - **2:3 竖版海报**（标准点播卡片）
  - **16:9 横图卡片**（横版海报，适配老电影/综艺）
  - **16:9 椭圆海报**（oval 风格，演员/导演作品集用）

---

## 画板命名

`fongmi_tv_leanback_1080p_vod_list_full`

---

## 提示词正文（直接复制粘贴到 Stitch）

```
新建画板 fongmi_tv_leanback_1080p_vod_list_full

## 场景与定位
FongMi TV 用户进入「点播 Tab」后，看到的二/三级列表页：
- 二级：分类浏览（科幻 / 动作 / 喜剧 / 剧集 / 动漫 / 综艺）
- 三级：单分类结果（如"科幻大片"列出所有科幻电影）
- 搜索结果：搜索关键词匹配的所有影片

页面承载 3 种卡片形态切换（grid 2:3 / rail 16:9 / oval），并显示过滤、排序、空态等完整功能。

## 布局结构（自顶向下 1080p）

### 1. 顶部 Header (72dp 高)
- 左侧：Back 返回 + 面包屑「首页 / 点播 / [当前分类]"
- 中央（不抢占左侧）：1 行简短分类描述（如「2024 年科幻动作片合集 · 共 142 部」），12sp 灰
- 右侧：
  - 视图切换 segmented（grid 2:3 / rail 16:9 / oval）—— **焦点默认在 grid**
  - 排序 chip（最近添加 ↓ / 评分 ↓ / 年份 ↓）
  - 设置图标（齿轮）

### 2. 左侧分类导航 (240dp 宽，垂直栏，仅 grid 模式下显示)
- 8-10 个一级分类 chip：
  - 全部 / 电影 / 剧集 / 动漫 / 综艺 / 科幻 / 动作 / 喜剧 / 悬疑 / 纪录片
- 默认焦点：当前激活分类（3.5dp 描边 + scale 1.025）
- 滚动支持：D-pad 上下移动，超出 10 个时滚动
- rail / oval 模式下隐藏左侧栏（节省空间）

### 3. 主内容区（grid 模式）

**grid 模式：5 列 × 3 行 = 15 张卡片**

每张卡片 320×480dp (2:3 竖版海报)：
- 上方海报（240×360dp）：
  - 抽象渐变背景（按影片主题色调，沙丘橙 / 奥本海默黑金 / 星际深蓝等）
  - 居中超大汉字首字 (font-heading 80px, opacity 0.85) + 模糊光晕
  - 左上角：徽章（4K / HDR / 杜比）
  - 右上角：评分（数字 + 星形 SVG）
  - 左下角：年份（如「2024」）
  - 右下角：线路数（如「6 路」圆形 chip）
- 卡片下方信息区（80dp）：
  - 主标题 20sp 加粗（最多 2 行）
  - 副信息 14sp 灰（导演 · 类型）
  - 焦点态卡片下方出现 1 行进度提示：「OK 直接播放 · Menu 选集 · 长按 收藏」

焦点态：3.5dp #FCD58B 描边 + 25px glow + scale 1.05 + z-index 40

### 4. 主内容区（rail 模式）

**rail 模式：3 列 × 5 行 = 15 张横版卡片**

每张卡片 580×260dp (16:9 横图 + 右侧元数据)：
- 左侧海报 320×180dp (16:9)：
  - 抽象渐变 + 汉字首字 + 4K/HDR 徽章
- 右侧元数据区（260dp 宽）：
  - 标题 26sp 加粗（最多 2 行）
  - 元信息行 14sp：导演 / 类型 / 年份 / 片长
  - 简介 14sp（最多 3 行，#C7C7CC）
  - 底部 metadata row：评分 + 线路数 + 已看进度（如已看）
  - 焦点态：在右侧加 4dp #FCD58B 左竖线

### 5. 主内容区（oval 模式）

**oval 模式：6 列 × 3 行 = 18 张椭圆海报卡片**

每张卡片 280×280dp 圆形/椭圆海报：
- 圆形抽象渐变背景（直径 240dp）
- 居中汉字首字 64sp
- 顶部弧形标签（年份）
- 卡片下方：主名 20sp（最多 8 字，超出 truncate）
- 副行：导演 14sp 灰
- 焦点态：3.5dp 描边（圆形）+ scale 1.08

### 6. 分页与滚动 (固定底部)
- 第 X / Y 页（Y = 总页数）
- D-pad ◀ ▶ 翻页（焦点态时左侧 ◀ / 右侧 ▶ 出现 3.5dp 描边按钮）
- 滚动条指示器（右侧 8dp 宽，#2A2A30 底 + 当前进度 #E5A958）

### 7. 底部遥控器 4 键提示条 (48dp 高)
- ▲▼◀▶ 浏览卡片
- OK 直接播放
- Menu 选集与排序
- ▶ 长按 收藏 / 添加稍后再看
- Back 返回上级

### 8. 默认焦点
第 1 张卡片（grid 模式第 1 行第 1 张 / rail 模式第 1 行第 1 张 / oval 模式第 1 张）

### 9. 状态栏 (32dp 高)
- 右侧：「共 142 部 · 当前显示 1-15 · 已看 23 部」+ 「网络 386 Mbps · 缓冲良好」

## 视觉变体说明

设计稿需要呈现 **grid 模式作为主视图**，但右栏放一个 240×600dp 的缩略示意展示 rail / oval 模式，
标注"按 ◀ ▶ 切换视图模式 / 顶部切换按钮"。

## 验收清单

- [ ] 1920×1080 完整不溢出
- [ ] 顶部 Header 含面包屑 / 视图切换 / 排序 / 齿轮
- [ ] grid 模式：5 列 × 3 行 = 15 张卡片 320×480dp
- [ ] rail 模式：3 列 × 5 行 = 15 张横版卡片 580×260dp（右侧缩略）
- [ ] oval 模式：6 列 × 3 行 = 18 张椭圆卡片 280×280dp（右侧缩略）
- [ ] 3 种卡片焦点态标准（3.5dp + 25px glow + scale）
- [ ] 海报含抽象渐变 + 巨幅汉字首字 + 徽章 + 评分 + 年份 + 线路数
- [ ] 主标题 ≥20sp（grid / oval）/ 26sp（rail）
- [ ] 左侧分类导航 8-10 个 chip（grid 模式专用）
- [ ] 分页与滚动指示器
- [ ] 底部 4 键提示条
- [ ] 状态栏显示总数 / 已看 / 网络
- [ ] 无任何 emoji
- [ ] 无任何真实电影海报 / 剧照
- [ ] Overscan 72dp 四边安全
- [ ] 单屏单焦点（grid 第 1 张默认）

## 输出

生成 1920×1080 画板 + 配套 code.html，命名 fongmi_tv_leanback_1080p_vod_list_full
完成后回我「Round 20 已生成」。
```

---

## 设计决策说明

### 为什么 3 种卡片形态在 1 张画板里

- 项目代码层确实有 3 个独立 adapter（`adapter_vod.xml` 竖版 / `adapter_vod_list.xml` 横图 / `adapter_vod_oval.xml` 椭圆）
- 但用户视角是「同一列表页切换视图」，不是「3 个不同页面」
- 设计稿必须呈现 3 种切换的视觉对比，否则开发做不出切换效果

### 为什么 grid 5×3 = 15 张可见

- 3m 视距下 1920×1080 屏能舒适容纳 5 列（每列 320dp 海报宽度合适）
- 3 行（避免滚动条过长，分页更舒服）
- 15 张是「一页」的合理上限（再多人眼记不住，焦点追踪困难）

### 为什么 rail 用 3×5 而不是 4×4

- rail 卡片宽 580dp，4 列要 2320dp 超 1920dp 屏宽
- 3 列宽度 1740dp + 2 间距 48dp = 1788dp，剩余 60dp 给左右边距（含 overscan 72dp）勉强够
- 5 行能容纳 15 个标题简介完整信息，符合「点开不滚动」原则

### 为什么 oval 用 6×3 = 18 张

- 椭圆卡片小（280dp），单屏可多放
- 18 张用满屏宽，3 行竖向移动聚焦焦点，更符合"快速浏览演员作品集"的心智
- 比 grid 多 3 张，鼓励用户用 oval 模式做"发现式浏览"

### 为什么不画"加载中"和"搜索无结果"等异常态

- Round 17 已经画了 4 种 Toast 通用状态（成功 / 失败 / 加载 / 信息）
- Round 19 vod_source_full 有"测试连接 → Toast"链路
- 本画板聚焦**正常态的完整布局**，异常态复用全局 Toast
- 如有需求，Round 21 可补一张「VOD List 异常态合集」画板

### 为什么顶部 Header 中央放分类描述

- 长面包屑 + 视图切换 + 排序 + 齿轮占满左右两端
- 中央是空档，放 1 行分类描述（"2024 年科幻动作片合集 · 共 142 部"）让用户知道当前在看什么
- 12sp 灰字小字，避免抢占视线

### 为什么卡片焦点态要写"OK / Menu / 长按"3 个提示

- D-pad 单焦点约束下，焦点卡是"全屏唯一受控对象"
- 提示用户 3 种快捷操作，避免进详情页才发现要返回
- 这一行 12sp 仅在焦点态出现，默认态隐藏避免视觉拥挤

### 焦点态左竖线（rail 模式）

- rail 卡片焦点态用「4dp #FCD58B 左竖线 + scale 1.025」比全描边更优雅
- 避免横版卡片（580×260dp）被全描边+scale 显得"鼓出来"
- 配合右下角"OK 播放"提示，整体节奏更像"选中态"而非"放大态"

## 与现有画板的协作

| 触发链路 | 现有画板 | 本画板 |
|---|---|---|
| Home 推荐 → 点播 Tab → 单分类 | 仅 Home，没有列表页 | **填补空缺** |
| 详情页相关推荐 → "更多" | Detail 已画 | 跳转本画板（按类型筛选） |
| 搜索结果 → 列表 | Search 已画 4 列 2:3 | 跳转本画板（grid 5×3 更密集） |
| 演员/导演作品集 | 无 | 本画板 oval 模式专门承载 |

## 验收脚本

```bash
ROOT=docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_vod_list_full

cd "$ROOT" || { echo "MISSING"; exit 1; }

# 1. 拼写合规 (应 0)
echo "rakis: $(grep -c rakis code.html 2>/dev/null || echo 0)"

# 2. emoji (应 0)
echo "emoji: $(grep -cE '[📡⚙🎧📍🎬🎮🎵🎨🔥⭐]' code.html 2>/dev/null || echo 0)"

# 3. 焦点 3.5dp (应 ≥10)
echo "focus-3.5dp: $(grep -c '3.5px' code.html 2>/dev/null || echo 0)"

# 4. 3 种视图模式齐全
echo "grid: $(grep -cE 'grid|5 列|3 行' code.html 2>/dev/null || echo 0)"
echo "rail: $(grep -cE 'rail|16:9|横图' code.html 2>/dev/null || echo 0)"
echo "oval: $(grep -cE 'oval|椭圆' code.html 2>/dev/null || echo 0)"

# 5. 字号 20sp
echo "text-20px: $(grep -cE 'text-\[20px\]' code.html 2>/dev/null || echo 0)"

# 6. Overscan 72px
echo "overscan-72: $(grep -c '72px' code.html 2>/dev/null || echo 0)"

# 7. 卡片数 ≥ 15 (grid 模式下)
echo "card-count: $(grep -cE 'aspect-\[2/3\]|aspect-video' code.html 2>/dev/null || echo 0)"

# 8. 面包屑
echo "breadcrumb: $(grep -cE '首页 / 点播' code.html 2>/dev/null || echo 0)"
```

全部通过告诉我「Round 20 验收通过」，我跑 AI_DESIGN_REVIEW.md 加 Round 20 章节。