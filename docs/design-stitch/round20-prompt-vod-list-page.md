# Round 20 v2 · 点播列表页画板提示词（修订版）

> **修订点**：
> 1. 卡片宽高严格按 FongMi TV 项目 `adapter_vod.xml` 实际规范（image 80dp 高 + name 在下方），而非 v1 的 340×560 2:3 竖图
> 2. 新增**二级筛选条件**（每个分类下都有 4-5 个 chip 筛选）
>
> 项目代码映射：`app/src/leanback/res/layout/activity_vod.xml` + `adapter_vod.xml` + `adapter_vod_rect.xml` + `adapter_vod_list.xml` + `adapter_vod_oval.xml`
> 适用项目：FongMi TV · Android TV Leanback · Champagne Dark v1.2。
>
> **图标约束（继承 CLAUDE.md Android Icon System）**：所有 UI 图标必须使用 Lucide 风格 SVG（24×24 viewport · 2-unit stroke · 圆头圆角 · 透明 fill）。

---

## 全局硬约束（继承 Round 1-19）

- 1920×1080 · Champagne Dark v1.2 · 72dp Overscan
- 字号：Hero 38sp / Section 26sp / Card 20sp / Body 16sp / Caption 14sp / Meta 12sp
- 焦点态：颜色 (#FCD58B) + 形状 (3.5dp 描边 + 25px glow) + 缩放 (1.05x) 三因素
- **纯 Lucide 风格 SVG 图标**，禁任何 emoji / 系统图标 / Material icon
- 严禁真实电影海报 / 剧照 / 演员肖像
- 顶部 Header：左 Back 返回 + 面包屑「首页 / 点播」+ 右 当前活跃源 + 时钟
- 底部 4 键：▲▼◀▶ 移动 / OK 进入详情 / Menu 排序与视图 / Back 返回首页

---

## 画板命名

`fongmi_tv_leanback_1080p_vod_list_full`

---

## 提示词正文（直接复制粘贴到 Stitch）

```
新建画板 fongmi_tv_leanback_1080p_vod_list_full

## 触发场景
从首页 OK 进入「点播」Tab → 加载全部点播内容（来自当前活跃 Spider API）。
本画板是点播内容的总入口，承担：
1. 5 个一级分类 Tab（电影/剧集/综艺/动漫/全部）
2. 每个分类下的二级筛选 chip（年份/地区/类型/状态/质量/排序）
3. 排序下拉
4. 视图切换 segmented (grid 网格 / list 列表 / oval 椭圆)
5. 影片网格展示

## 卡片宽高规范（严格遵循 FongMi TV 项目 adapter_vod.xml 实际规范）

### grid 模式（默认视图，对应 adapter_vod.xml）
- image 高度：80dp（注意：是 dp 不是 px；image 与 name 是分离的）
- image 宽度：match_parent（由 GridView 列宽决定）
- 因此 image 实际渲染为 **1:1 方块海报**（约 240dp × 240dp 在 1080p 上）
- image 圆角：tv_radius_card = 16dp（Vod.Grid style 配 cornerSizeTopLeft/TopRight = 16dp）
- 焦点态 scale 1.025x（项目 animator 实测值，详见 tv_focus_scale.xml）
- name 文字区：image 下方独立行
  - textSize: tv_text_card_title = 20sp（硬约束 ≥20sp）
  - 单行 + ellipsize=marquee
  - 居中对齐 + 6dp 内边距
  - 背景 shape_vod_name 圆角（bottomLeftRadius + bottomRightRadius = tv_radius_card = 16dp）
- 卡片整体高度 ≈ 80 + 30 (name) + padding = ~120dp
- 卡片之间间距：card_gap_x = 24dp

### list 模式（对应 adapter_vod_list.xml）
- 横向 16:9 横图布局
- image 高度 80dp，宽 match_parent（16:9 比例由宽自然算出）
- 整体高度约 80 + 30 = ~110dp
- 每行只显示 1 张卡（不是 grid）

### oval 模式（对应 adapter_vod_oval.xml）
- 椭圆海报 image（圆角 50% = 椭圆形）
- 高度 80dp，宽自适应
- 整体高度 ~120dp

## 布局结构（自顶向下 1080p）

### 1. 顶部 Header (72dp 高)
- 左：Back 返回 + 面包屑「首页 / 点播」
- 右：当前活跃源（饭太硬 极速4K）+ 时钟

### 2. 一级 Header (100dp 高)：5 个分类 Tab + 排序 + 视图切换

#### 左：5 个分类 Tab（一级分类）
- Tab 1：电影 (焦点默认，3.5dp #FCD58B 描边)
- Tab 2：剧集
- Tab 3：综艺
- Tab 4：动漫
- Tab 5：全部

每个 Tab：圆角 pill (16dp 圆角) + 32dp 高 + 16sp 文字 + 焦点态 3.5dp 描边 + scale 1.02
Tab 间间距 12dp

#### 中：排序 Dropdown 按钮
- 按钮 64dp 高，含 Lucide `chevron-down` 图标 + 「排序：最近添加」文字
- 点击后展开 4 选 1 segmented：最近添加 / 评分最高 / 年份最新 / 名称 A-Z

#### 右：视图切换 segmented (3 选 1)
- 选项 1：网格 grid (1:1 方块海报, 80dp) - Lucide `layout-grid` 图标，焦点默认
- 选项 2：列表 list (16:9 横图) - Lucide `list` 图标
- 选项 3：椭圆 oval (椭圆形 80dp) - Lucide `circle-ellipsis` 图标

### 3. 二级筛选区 (80dp 高，仅当某个分类 Tab 获焦时显示)

> **核心新增**：每个分类下都有 4-5 个 chip 筛选条件。当前选中「电影」Tab 时显示电影专属筛选；切到「剧集」时显示剧集专属筛选。

#### 当 Tab「电影」获焦时的二级筛选
横向滚动 chip 流（高度 56dp，间距 12dp）：

Chip 1：**全部** (焦点默认，「清空筛选」语义)
Chip 2：年代 (下拉菜单) — 含「2024 / 2023 / 2022 / 2021 / 2020 / 更早」
Chip 3：地区 (下拉菜单) — 含「全部 / 美国 / 韩国 / 日本 / 内地 / 港台 / 欧洲」
Chip 4：类型 (下拉菜单) — 含「全部 / 动作 / 喜剧 / 科幻 / 悬疑 / 爱情 / 战争 / 动画」
Chip 5：质量 (下拉菜单) — 含「全部 / 4K UHD / 1080P / 720P / 蓝光原盘」
Chip 6：评分 (下拉菜单) — 含「全部 / ≥9.0 / ≥8.0 / ≥7.0 / ≥6.0」
Chip 7：排序 (与一级 Header 排序联动)

#### 当 Tab「剧集」获焦时的二级筛选（差异说明）
Chip 1：全部
Chip 2：年代（同上）
Chip 3：地区（同上）
Chip 4：类型 — 含「全部 / 古装 / 都市 / 悬疑 / 校园 / 家庭 / 武侠 / 玄幻」
Chip 5：状态 — 含「全部 / 连载中 / 已完结 / 待更新」
Chip 6：集数 — 含「全部 / 短剧 (<10集) / 中等 (10-30集) / 长篇 (>30集)」
Chip 7：质量 / 排序

#### 当 Tab「综艺」获焦时的二级筛选
Chip 1：全部
Chip 2：年代
Chip 3：地区
Chip 4：类型 — 含「全部 / 真人秀 / 脱口秀 / 选秀 / 音乐 / 美食 / 游戏 / 相亲」
Chip 5：状态 — 含「全部 / 播出中 / 已完结 / 往期」
Chip 6：嘉宾（搜索框代替 chip）— 提示输入「按嘉宾姓名筛选」
Chip 7：质量 / 排序

#### 当 Tab「动漫」获焦时的二级筛选
Chip 1：全部
Chip 2：年代
Chip 3：地区 — 含「全部 / 日本 / 国产 / 美国 / 韩国 / 欧美」
Chip 4：类型 — 含「全部 / 热血 / 校园 / 恋爱 / 科幻 / 奇幻 / 搞笑 / 治愈」
Chip 5：状态 — 连载中 / 已完结 / 剧场版
Chip 6：原作 — 漫画改编 / 轻小说改编 / 游戏改编 / 原创
Chip 7：质量 / 排序

#### 当 Tab「全部」获焦时的二级筛选（聚合）
显示所有分类共有的筛选：年代 / 地区 / 类型 / 状态 / 质量 / 评分 / 排序

### 4. 结果统计行 (40dp 高)
- 左：「共 1,248 部影片 · 当前显示 1-24 · 已选 筛选：电影 / 2024 / 美国 / 4K UHD」
- 右：「清空筛选」按钮（Lucide `x-circle` 图标）

### 5. 主区域：影片网格 (Grid 模式，默认视图)

7 列 × 3-4 行 = 21-28 张方块海报卡片
卡片实际尺寸约 240dp × 270dp (image 240×240dp + name 30dp)

每张卡片结构（严格按 adapter_vod.xml）：
- image 区 (240×240dp)：抽象渐变背景 + 巨幅首字水印 (100px opacity 0.18) + 圆角 16dp
  - 顶左角：remark 徽章（14sp mono 「4K 原盘」或「1080P」）
  - 顶右角：year 徽章（14sp mono「2024」）
  - 底左角：site 来源徽章（14sp mono「饭太硬」）
  - 底右角：评分「9.2」或「豆瓣 8.5」(12sp)
- name 文字区 (240dp × 30dp, image 下方独立行)：
  - 主名 20sp 加粗 truncate 1 行（卡片主名硬约束 ≥20sp）
  - 6dp 内边距 + 居中对齐
  - 背景 shape_vod_name 圆角 16dp（与 image 圆角衔接成圆角矩形）

焦点态（焦点默认在第 1 张第 1 行第 1 列「沙丘 2」）：
- 3.5dp #FCD58B 描边 + 25px glow + scale 1.025x（与项目 animator 实测值一致）
- 焦点卡片右下角浮动显示「OK 键进入详情」提示徽章
- name 颜色从白色变为 #FCD58B 高亮色

### 6. 底部 4 键提示条 (48dp 高)
- ▲▼◀▶ 翻页 (24 张/页) / OK 进入详情 / Menu 排序与视图 / Back 返回首页

### 7. 状态条 (32dp 高)
- 右侧：「当前已加载 24/1248 · 下次滚动加载 25-48」+ 内存 86MB

## 默认焦点
二级筛选区 Chip 1「全部」（电影 Tab 获焦时）

## 验收清单
- [ ] 1920×1080 完整不溢出
- [ ] 卡片严格 1:1 方块（image 240×240dp + name 30dp 下方），与 adapter_vod.xml 实际一致
- [ ] 7 列 × 3-4 行网格（grid 模式）
- [ ] 5 个一级分类 Tab（电影/剧集/综艺/动漫/全部）
- [ ] **5 套二级筛选 chip（每分类 4-7 个 chip）**——核心新增
- [ ] 排序 Dropdown + 3 视图 segmented 完整
- [ ] 焦点态在二级筛选区 Chip「全部」
- [ ] 切 Tab 时二级筛选 chip 内容动态变化
- [ ] 8 个 Lucide 图标：chevron-down / layout-grid / list / circle-ellipsis / x-circle / play / info / heart
- [ ] 卡片主名 20sp（严格 = tv_text_card_title）
- [ ] remark/year/site 14sp mono
- [ ] image 圆角 16dp（= tv_radius_card）
- [ ] 焦点态 scale 1.025x（= tv_focus_scale animator 实测值）
- [ ] 无任何 emoji / Material icon / 系统图标
- [ ] 无任何真实电影海报 / 剧照
- [ ] Overscan 72dp 四边安全
- [ ] D-pad 可达：上下左右切换卡片、Tab、二级筛选 chip、视图切换

## 输出
生成 1920×1080 画板 + 配套 code.html
命名 fongmi_tv_leanback_1080p_vod_list_full
完成后回我「Round 20 v2 已生成」。
```

---

## 设计决策说明

### 修订点 1：卡片宽高 1:1 方块（不是 2:3 竖图）

**为什么改**：
- FongMi TV 项目 `adapter_vod.xml` / `adapter_vod_rect.xml` 都把 `image height="80dp"` 写死
- image 宽度由 GridView 列宽决定（match_parent）
- 实际渲染是 **1:1 方块海报**（1080p 上约 240×240dp）
- name 在 image **下方独立行**（不覆盖 image）

**与 DESIGN.md「2:3 竖图」规范的关系**：
- DESIGN.md 是 Round 1 设计规范，定义了视觉规范
- 项目工程实际用了 1:1 方块（考虑网格密度高 + 客厅 3m 视距下方块识别度更好）
- 这是**视觉优先 vs 工程现实**的冲突，按 Round 11.5 决策（视觉对齐 vs 工程让步）→ 本画板选工程
- Android 端实现时，coding agent 用 Round 18 H7 P0 规则：「硬约束优先于现有代码」—— 既然工程用 1:1 方块稳定运行，画板对齐工程即可

### 修订点 2：新增 5 套二级筛选

**为什么是 5 套（不是 1 套通用）**：
- 不同分类的筛选维度差异巨大：
  - 电影：年代 / 地区 / 类型 / 质量 / 评分
  - 剧集：年代 / 地区 / 类型 / **状态（连载中/完结）** / **集数** — 状态维度独有
  - 综艺：年代 / 地区 / 类型 / 状态 / **嘉宾（搜索）** — 嘉宾维度独有
  - 动漫：年代 / 地区 / 类型 / 状态 / **原作（漫画/轻小说）** — 原作维度独有
- 用 1 套通用筛选会丢失关键分类信息
- 5 套切换让用户**针对内容类型做精准筛选**

**为什么二级筛选高度 80dp 而不是更高**：
- 用户首要操作是「选分类 Tab → 浏览卡片」，筛选是次要
- 80dp = 56dp chip + 24dp padding，恰好容下 1 行 chip 不溢出
- 切分类时二级筛选 chip 内容动态变化，过渡 200ms

**为什么 chip 焦点默认在「全部」**：
- 「全部」= 清空筛选，让用户**第一眼看到结果**
- 想筛选的主动向右扫（Lucide 习惯）
- 与项目 `adapter_filter.xml` 现有逻辑一致

### 焦点态策略变更

| 元素 | Round 20 v1 | Round 20 v2 |
|---|---|---|
| 默认焦点 | Tab「电影」 | **二级筛选 Chip「全部」** |
| 焦点路径 | Tab → 卡片 | Tab → 二级筛选 → 卡片 |

**为什么焦点默认在二级筛选**：
- 5 个分类切换是高频操作（用户经常切来切去）
- 二级筛选紧随 Tab 之后是逻辑层级
- 用户**先选类型再选筛选条件再浏览**是自然浏览路径
- 避免焦点跳到卡片后用户还要返回 Tab 切换类型

### 卡片主名 20sp 严格遵循

- `tv_text_card_title` 实测值 = 20sp（Round 12 已修复）
- name background shape_vod_name 圆角 = tv_radius_card = 16dp（与 image 衔接）
- 单行 + ellipsize=marquee（项目实装滚动跑马灯）

### 5 套二级筛选的内容设计原则

每个分类的 chip 数量控制在 4-7 个：
- 5 个以下：分类特有维度不足，过滤不精细
- 7 个以上：横向滚动距离过长，D-pad 操作累
- **Chip 5 = 排序与视图联动**（与一级 Header 排序 Dropdown 同步）

## 验收脚本

```bash
ROOT=docs/design-stitch/stitch_fongmi_tv_ui_design/fongmi_tv_leanback_1080p_vod_list_full

cd "$ROOT" || { echo "MISSING"; exit 1; }

# 1. 拼写合规 (应 0)
echo "rakis: $(grep -c rakis code.html 2>/dev/null || echo 0)"

# 2. emoji (应 0)
echo "emoji: $(grep -cE '[📡⚙🎧📍🎬🎮🎵🎨🔥⭐▼▲⬇▶♥ⓘ]' code.html 2>/dev/null || echo 0)"

# 3. 焦点 3.5dp (应 ≥5)
echo "focus-3.5dp: $(grep -c '3.5px' code.html 2>/dev/null || echo 0)"

# 4. Lucide 图标 (应 ≥8)
echo "lucide-svg: $(grep -cE 'layout-grid|chevron-down|circle-ellipsis|x-circle|<svg' code.html 2>/dev/null || echo 0)"

# 5. 5 个分类 Tab (应 ≥5)
echo "category-tabs: $(grep -cE '电影|剧集|综艺|动漫|全部' code.html 2>/dev/null || echo 0)"

# 6. 5 套二级筛选 chip（核心新增，应包含 5 个分类的状态/集数/嘉宾/原作 等关键词）
echo "secondary-filter: $(grep -cE '连载中|已完结|集数|嘉宾|原作' code.html 2>/dev/null || echo 0)"

# 7. 卡片主名 20sp (应 ≥20)
echo "text-20px: $(grep -cE 'text-\[20px\]' code.html 2>/dev/null || echo 0)"

# 8. 视图切换 (应 ≥3)
echo "view-switcher: $(grep -cE '网格|列表|椭圆' code.html 2>/dev/null || echo 0)"

# 9. 卡片方块 1:1 标注 (应含 80dp / 240dp / 1:1 / 方块)
echo "card-ratio: $(grep -cE '80dp|240dp|1:1|方块' code.html 2>/dev/null || echo 0)"

# 10. 4 个角标 remark/year/site/score
echo "badges: $(grep -cE 'remark|year|site|score' code.html 2>/dev/null || echo 0)"

# 11. 端口信息 (应 0, 点播页不需要)
echo "port-9978: $(grep -c '9978' code.html 2>/dev/null || echo 0)"
```

全部通过告诉我「Round 20 v2 验收通过」，我跑 AI_DESIGN_REVIEW.md 加章节。
