# 弹幕搜索接口

播放设置中的“搜索接口”接收 **FongMi 兼容搜索 API**，不是 TVBox / Spider 站点订阅地址，也不是某一集的 XML 文件地址。

## 地址格式

推荐显式 GET 模板，保留花括号，由客户端编码并替换片名与集名：

```text
https://你的服务域名/你的TOKEN/api/v2/fongmi/danmaku?name={name}&episode={episode}
```

服务不需要 Token 时去掉对应路径段。不要把示例中的中文域名或 TOKEN 原样填入。`danmu_api` 的 FongMi 兼容路由同时支持 GET 和表单 POST；不带 `{name}` / `{episode}` 的地址仍按原行为接收 `name`、`episode` 两个表单字段。不要向 GitHub 静态 JSON 地址发送搜索请求。

成功响应应为数组，例如：

```json
[{"name":"影片 - 第1集","url":"https://你的服务域名/api/v2/comment/集ID?format=xml"}]
```

空数组表示没有匹配结果；空响应、HTML、普通 JSON 对象、缺失或空白 URL 都不是有效搜索结果。兼容旧接口：`name` 可缺省或为 null，界面使用 URL 作为名称。

包含 `spider` / `sites` 的对象是站点配置，搜索入口不会加载其中的 JAR、提取 `ext.apiUrls` 或执行多源聚合。仅将这类地址强制改为 GET 无法完成适配。

## 检查步骤

1. 设置 → 播放 → 弹幕设置，开启加载和自动搜索，填写真实搜索 API。留空仍表示使用内容源提供的默认接口。
2. 播放影片 → 弹幕 → 搜索，确认返回候选而非 HTTP / 格式错误。
3. 核对剧名、季度、集数，再选择来源并观察弹幕是否上屏。自动搜索沿用取首个候选的行为，不保证模糊片名对应正确剧季。
4. 搜索成功但没有弹幕时，单独检查结果中的 XML URL、显示开关和视频时间；不能将搜索成功等同于渲染成功。

## 错误诊断与边界

- 输入框拒绝非 HTTP(S) 地址，但不会仅按 `.json` 后缀判定接口是否合法。
- 搜索响应区分 HTTP 状态、网络错误、空响应、站点配置和不支持的结果结构。
- HTTP 403 不自动回退 GET；检查地址、服务权限和网络代理。
- 日志使用 `DanmakuApi` 标签，仅记录方法、主机、状态或异常类型，不记录路径、查询参数、Token、片名或响应正文。
- 自动搜索仍不弹错误 Toast，但会留下脱敏诊断；手动搜索显示明确错误。
- 本次不改变弹幕文件下载器、播放引擎、匹配排序、主题或手机设置布局。

## 回归验证

```sh
python3 -m unittest tools.tests.test_danmaku_api
./gradlew :app:assembleLeanbackDebug :app:lintLeanbackDebug
./gradlew :app:assembleMobileDebug :app:lintMobileDebug
```

主机测试编译并执行生产 `DanmakuApi`、`Danmaku`、`ApiCallback`，只替换 Android 与网络边界；复用已缓存的 Gson / OkHttp / Okio / Kotlin 依赖，无需新增依赖或请求公共服务。覆盖 GET 编码、POST 表单、响应结构、错误分类、日志脱敏、主线程分发、取消及过期回调。
