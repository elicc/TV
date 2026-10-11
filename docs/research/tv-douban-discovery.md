# TV 豆瓣榜单与相关推荐

## 行为与边界

- 搜索空状态仅有热门电影、热门电视剧、影院热映三个 Tab，首次按需加载前 40 项，保留 API 顺序。
- 详情页使用已匹配 `providers[DOUBAN]` 的 `externalType + externalId` 加载最多 20 项相关推荐。候选预览不会触发；切换信息源 Tab 不清空推荐。
- 推荐位于详情底部横向卡片行，点击按片名进入现有聚合搜索，不把豆瓣 ID 当作片源 ID，也不自动播放搜索结果。
- 推荐是非关键内容：加载中显示提示，成功空列表显示“暂无推荐”，失败显示可聚焦的重试提示，不影响详情/播放；尚无可信豆瓣匹配时隐藏。新影片或重新确认匹配时清空旧列表；页面销毁、切片后的迟到响应无效。
- 搜索榜单有独立骨架、空状态和可遥控器点击的失败重试。不会把其他榜单或搜索词列表冒充当前榜单。
- 共享卡片在获得焦点时对片名、年份、类型启用无限 marquee；失焦、回收、离屏重置。详情相关推荐不显示排名角标。
- 搜索标题上下留白减少：历史区末尾 6dp，标题下方 4dp，Tab 下方 6dp；去掉标题字体默认留白，保留 44dp Tab 和 172dp 海报高度。

## 接口契约

2026-10-11 使用仓库已有客户端配置分别实测成功。接口文档 `/Users/elicc/Downloads/douban.md` 把电影/电视剧推荐标题与路径写反，部分响应示例也不正确。

| 用途 | `/api/v2` 下路径 | 返回 |
| --- | --- | --- |
| 热门电影 | `/subject_collection/movie_hot_gaia/items` | 对象内 `subject_collection_items` |
| 热门电视剧 | `/subject_collection/tv_hot/items` | 同上 |
| 影院热映 | `/subject_collection/movie_showing/items` | 同上 |
| 电影相关推荐 | `/movie/{id}/recommendations` | 顶层数组 |
| 电视剧相关推荐 | `/tv/{id}/recommendations` | 顶层数组 |

热门电影/热映海报为 `cover.url`；热门电视剧和相关推荐为 `pic.large/normal`。相关推荐样本无独立年份字段，从 `card_subtitle` 补充。结构化导演、演员优先；`info` 的导演与演员是不同字段。评分缺失保留条目，非法列表外层视为失败，合法空数组是成功空结果。不逐项请求详情。

`tools/tests/fixtures/douban_lists/` 是五个响应的最小字段投影，采样 `start=0,count=5`，推荐样本 ID 为电影 `35882838`、电视剧 `36097760`。不保存 API key、评论、用户资料等无关字段。

## 实现分工

- `DoubanCollection`：三个无种子榜单标识，不包含 UI 文案。
- `DoubanProvider` / `DoubanListParser`：请求边界和两种 JSON 结构的统一字段解析。
- `MetadataRepository`：12 小时缓存，同 key 请求合并，版本化缓存 `metadata_douban_lists_v2`，最多 32 个列表；失败只能回退相同 key 缓存。旧评分排序缓存不再读取。
- `RecommendationState` / `VideoViewModel`：可信匹配门槛、独立状态、影片代次与作品 key 双重校验。
- `SearchActivity` / `VideoActivity`：展示、Tab、逻辑焦点图及点击动作。
- `DoubanMovieAdapter`：替代原 `HotMovieAdapter`，复用网格与横向卡片、焦点文字行为，无新依赖或图标。

## 离线验证

使用 JDK 21，先构建以生成生产 class，再运行基于生产字节码的缓存与 ViewModel 测试：

```sh
./gradlew :app:assembleLeanbackDebug
python3 -m unittest \
  tools.tests.test_douban_lists \
  tools.tests.test_douban_list_cache \
  tools.tests.test_douban_recommendation_lifecycle \
  tools.tests.test_tv_douban_discovery \
  tools.tests.test_tv_search_keyboard \
  tools.tests.test_tv_search_history \
  tools.tests.test_tv_detail_fullscreen \
  tools.tests.test_tv_detail_header \
  tools.tests.test_tv_detail_choice_spacing \
  tools.tests.test_tv_detail_scroll
```

解析测试编译真实 Java 源码、使用现有 Gson；缓存及 ViewModel 测试执行 Gradle 生成的生产字节码，仅替换平台、调度、存储和网络边界。滚动测试编译真实 `DetailScrollView`，以几何桩验证上下边距、双向整行显示、首行回顶、超高推荐区、重新布局和重复请求；另有返回键立即/延后聚焦分支的源码契约检查。这些测试不替代设备 UI 验证。

其他检查的已知阻碍：

- TV lint：4 项 Manifest 错误（MissingLeanbackLauncher、ImpliedTouchscreenHardware、MissingIntentFilterForMediaSearch、MissingLeanbackSupport），相关 Manifest 非本次修改。
- `test_tv_theme_resources`：41 项中 9 项失败；用本次改动前的工作区快照复跑，也是相同的 9 项失败，0 errors。
- mobile 编译及依赖该编译的 lint：`MpvConfDialog` 引用了缺失的 `dialog_import`、`player_mpv_conf_conflict_hint`、`player_mpv_conf_priority_hint`、`player_mpv_conf_import_failed`；未修改无关 mobile 文件来绕过。

## 运行时修复与交互验证（2026-10-11）

用户后续授权使用 TCL TV `192.168.1.22:5555`，已构建并安装更新包，未清除应用数据。

- **推荐空白根因**：狂飙 `tv:35465232` 的缓存和实时接口均有 20 项。`HorizontalGridView` 在纵向滚动容器中使用默认行高，实际高度为 0；显式 `setRowHeight(WRAP_CONTENT)` 后实机高度为 498px，卡片正常显示。空数据、失败重试及请求去重由离线测试覆盖，未伪造设备接口响应。
- **间距**：保留选择行末尾 12dp 间距，去除推荐区额外 18dp 上边距，避免叠加为 30dp。
- **焦点滚动**：AndroidX 1.16.0 的默认焦点滚动计算没有扣除裁剪用的顶部 padding，导致向上回到播放线路时被裁掉。TV 专用 `DetailScrollView` 在焦点、矩形请求和布局完成时统一按有效视口显示完整选择行。推荐区过高时优先完整显示卡片行，不强行缩小卡片或保证标题始终可见。用户已确认切换问题修复。
- **返回键回顶**：`VideoActivity.focusPlayerFromDetail()` 先把焦点移出滚动区，再 `scrollTo(0, 0)`；立即聚焦和原有下一帧重试路径一致，避免旧焦点触发重新滚到底部。保持全屏/控制面板原有返回顺序。
- **播放器与选择区双向导航**：`DETAIL_ROW_IDS` 仅包含下方选择区，不再包含固定头部的海报/演职人员；播放器向下进入第一个可见选择行，首行向上回到播放器。海报/演职人员由自身 Tab 进入，向上回到对应 Tab。删除重复的按数组下标查找函数，适配器与方向键共用按 View ID 查找的相邻行逻辑。抽取实际生产导航方法执行全部 256 种选择行可见性组合，验证头部海报可见时也不会混入选择区路径。
- **设备验证边界**：已用方向键进入推荐卡片并记录视图树/截图。最后一次返回键复验未执行成功（控制租约过期，随后设备被 `Badge optical alignment verification` 占用），不能视为实机返回回顶验证通过。证据保存于 `.omx/state/douban-detail-scroll/`。

后续取得设备租约后已安装播放器/选择区双向导航及卡片向上返回媒体 Tab 的最新 APK。导航回归执行 256 种选择行可见性组合，并验证海报/演职人员当前 Tab 及缺失 Tab 的回退；全套相关检查 42 项通过。实机验证途中前台切到了 TCL 全网搜索，已停止输入并释放自身租约；不能将这段测试当成媒体卡片返回 Tab 的实机通过证据。仍待设备补充验证：媒体卡片向上返回 Tab、底部返回回顶、跑马灯循环、推荐点击搜索后返回、大字体及播放生命周期组合场景。

### 媒体卡片向下导航补充

海报/演职人员卡片按下统一进入“换源”（`R.id.change`），按上仍返回当前媒体 Tab。方向键分发与两个网格的 `nextFocusDownId` 保持一致；播放器与下方选择区的双向路径不变。本轮修改了 `VideoActivity.java` 和焦点回归契约，未新增导航层。构建通过；设备由 `Search header alignment` 持锁，本轮暂未安装或实机验证。
