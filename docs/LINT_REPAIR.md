# Debug flavor lint 修复说明

## 范围和取舍

- `app/lint.xml` 只豁免 `src/main/AndroidManifest.xml` 和 `src/mobile/AndroidManifest.xml` 上的三个 TV 专属误报：`MissingLeanbackLauncher`、`MissingLeanbackSupport`、`ImpliedTouchscreenHardware`。
- 依据为 Google Maven 发布的 `lint-checks:32.3.1` 源码：`AndroidTvDetector.beforeCheckFile()` 每个文件重置状态；`afterCheckFile()` 根据共享 Leanback 依赖认定为 TV 应用，而不合并 flavor 清单。TV 声明实际上均位于 `src/leanback/AndroidManifest.xml`，合并产物也包含它们。
- 不在 main/mobile 中添加虚假的 TV 启动入口或硬件支持声明，不全局禁用检查、不降低错误级别、不设置 `abortOnError=false`。
- `tools/tests/test_manifest_contracts.py` 额外校验两个 flavor 的合并产物，防止局部豁免掩盖 TV 启动声明丢失，并保证 mobile 不暴露 TV 启动入口。
- mobile 缺失的四个 MPV 文案补在 mobile flavor，包含默认英文、简体和繁体；不改变 MPV 配置处理逻辑。
- 共享 `Vod.Grid` / `Vod.Grid.Rounded` 样式恢复通用 `8dp` 默认，将 `tv_radius_card` 引用移到 leanback 同名样式覆盖，修复 mobile 资源链接失败；TV 圆角及焦点外观保持不变，不向 mobile 引入 TV 专属尺寸。
- mobile 播放页三个元数据标签误引用 TV 专有 `selector_item` drawable，改用 mobile 既有 `shape_item`（含触摸反馈及选中态颜色），不复制 TV 背景或新增资源。
- 将四个共享详情文案（`detail_line`、`detail_episode_range`、`detail_part`、`detail_sources`）现有简繁翻译从 leanback 移到 main，与默认英文资源作用域对齐，消除 mobile 的 `MissingTranslation`；不重复维护翻译。

## Intent 安全与行为对齐

**领域：** 外部媒体搜索入口及 Activity Intent 生命周期；优先级：中。

`MissingIntentFilterForMediaSearch` 是实际缺失，而非误报。两个 flavor 的现有 `HomeActivity` 增加 `MEDIA_PLAY_FROM_SEARCH` / `DEFAULT` filter，并复用已有搜索结果页处理非空 `SearchManager.QUERY`。只接收查询字符串，不转发外部嵌套 Intent、组件或 URI 授权，不增加 exported 组件和权限。

关键词匹配可能有歧义，因此展示既有结果选择页，不自动播放首个结果。此改动不等于实现完整的 Android Auto 无界面语音播放。

关键增量：

```diff
+ <intent-filter>
+     <action android:name="android.media.action.MEDIA_PLAY_FROM_SEARCH" />
+     <category android:name="android.intent.category.DEFAULT" />
+ </intent-filter>

  protected void onNewIntent(Intent intent) {
      super.onNewIntent(intent);
+     setIntent(intent);
      // 保留原有处理；冷启动与 singleTop 新 Intent 使用相同 checkAction。
  }

- } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
+ } else if (Intent.ACTION_SEARCH.equals(intent.getAction()) || MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(intent.getAction())) {
      String keyword = intent.getStringExtra(SearchManager.QUERY);
      // 保留非空检查、TV 搜索桥接校验及明确的 SearchActivity 导航。
```

涉及文件：两个 flavor 的 `AndroidManifest.xml` 和 `HomeActivity.java`；无新增依赖。

## 验证

```sh
./gradlew :app:lintLeanbackDebug :app:lintMobileDebug :app:assembleLeanbackDebug :app:assembleMobileDebug
python3 -m unittest tools.tests.test_manifest_contracts tools.tests.test_danmaku_api
```

Manifest 回归覆盖源文件与合并产物、flavor 隔离、限定的 lint 豁免及三语言 MPV 文案。测试不代替真实设备上的外部 Intent 分发或语音操作测试。

### 本轮结果

- 两个 flavor 的 lint 和 debug assemble 联合执行成功（`BUILD SUCCESSFUL`）。最新 XML 报告：leanback 为 0 errors / 311 warnings，mobile 为 0 errors / 173 warnings。
- `test_manifest_contracts`、`test_danmaku_api`、`test_home_navigation`、`test_tcl_search_bridge`、`test_tcl_search_bridge_accessibility` 合计 19 项通过，弹幕 harness 内部另验证 32 项检查；`git diff --check` 通过。
- 额外运行 `test_tv_theme_resources`：41 项中仍有 9 项失败，失败项与修复前基线一致，不属于本轮 lint 修复范围。不能据此声称整个仓库测试全绿。
- 未安装 APK、重启或操作电视设备；媒体搜索 Intent 真机分发及 mobile 标签触摸效果尚未进行设备验证。
- 原始执行日志：`/tmp/tv-lint-repair-final.log`、`/tmp/tv-lint-repair-tests.log`、`/tmp/tv-lint-repair-theme.log`（本机临时文件，非提交产物）。
