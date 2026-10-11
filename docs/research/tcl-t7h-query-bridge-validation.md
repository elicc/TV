# TCL T7H 查询参数方案：实测否决与降级

日期：2026-10-11。结论：**不能将 dumpsys 中的 Fragment arguments 当成当前语音请求。参数路径已撤下，改为不枚举类别的指定结果字段解析。**

## 1. 权限可行，不等于数据实时

本机 `android.permission.DUMP` 和 `android.permission.PACKAGE_USAGE_STATS` 都带有 `development` 标志。

实验版声明权限后，使用已授权 ADB 的 `pm grant` 成功授予。只给 DUMP 时，应用身份仍被拒绝，明确缺少 PACKAGE_USAGE_STATS；两项均授予后，下列命令能读取查询字段：

```bash
adb -s 192.168.1.22:5555 shell run-as com.elicc.android.tv \
  dumpsys activity com.tcl.wholenetsearch/.searchresult.ResultActivity
```

读取到的样本：

```text
mArguments=Bundle[{keyword=找一下张译的电影, is_suggestion=false,
search_type=0, isAI=true, requestId=72c73ba2-e912-4bf2-91f3-0a98d3d5e22e,
is_in_result_activity=true}]
```

实验版曾成功打开本应用 `“张译的电影”的搜索结果`，用户确认跳转，ARTEMIS 与前台 CollectActivity 双重核对。但后续两轮暴露了数据失效：

| 场景 | 用户反馈 / 参数证据 |
| --- | --- |
| 再说同一句“找一下张译的电影” | 未跳转，读取的 requestId 未变化 |
| 换成“帮我搜索星际穿越” | 仍未跳转，Fragment 参数连 keyword 都还是“找一下张译的电影” |

因此首轮成功不能证明这条路径可重复使用。若放松去重直接使用参数，还可能转发旧搜索词。

## 2. 静态代码解释了原因

从本机原厂 APK 只读检查 `com.tcl.wholenetsearch.searchresult.ResultActivity`：

- 首次创建 Fragment 时，`i()` 将 `keyword`、`requestId` 等写入 Bundle，再调用 `setArguments(bundle)`。
- `onNewIntent(Intent)` 调用 `a(intent)` 更新 Activity 字段，然后 `this.l.a(this.i, this.m)` 更新已有 Fragment 的业务状态。
- **这条更新路径没有再次 setArguments。** Android 的 Fragment 参数诊断仍可打印第一次的值；`mResumed=true` 只能证明页面在前台，不能证明 arguments 新鲜。

本机诊断参考（临时目录可能被系统清理，不作为交付依赖）：

- `/tmp/tcl-voice-inspect/ResultActivity.java`，`onNewIntent` 与 `i()`。
- `/tmp/tcl-voice-inspect/wholenetsearch.apk`，设备原厂搜索 APK 的只读副本。

**未来不得仅因 dumpsys 能读出关键词，就重新启用该数据源。必须覆盖页面复用、不同词、相同词重复、返回旧页，证实字段更新。**

## 3. 日志探测未打通

原厂代码含 `onNewIntent mIsFromAI=… mRequestId=… mKeyWord=…` 的 Verbose 日志调用。本轮短时设置 `log.tag.ResultActivity=V`，用户说“帮我搜索流浪地球”，定向 `logcat -s ResultActivity:V` 仍无有效查询输出。

这只证明本次路径未取得实时数据，不推断其它固件或所有日志路径绝对不可用。临时属性已恢复为空；没有设置 persist 属性、没有授予 READ_LOGS、没有留下常驻日志转发。

## 4. 最终保留的实现

```text
小T搜索 → 原厂当前结果页面
 → 核对包名 + 精确资源 ID ttvl_result_title
 → 解析“查询词”的[任意类别]结果（数量条）
 → 稳定性、长度、控制字符、推荐描述与旧词去重检查
 → 显式 ACTION_SEARCH → 本应用搜索列表
```

- 不枚举片名、人名或结果类别。`“张译的电影”的电影结果（51条）` 以及新增类别都可提取查询词。
- 保留已验证的标题结构边界；并非扫描整屏所有文字，也不是原始 ASR 全文。
- 同结构的零条结果允许交给本应用搜索；这是逻辑测试覆盖，尚无本机零结果实测。
- 当无法证明同词是新请求时，仍抑制转发，不用延时猜测来绕过去重。用户可使用本应用内搜索重搜；此限制尚未解决。
- 本应用搜索仍调用内容源关键词接口，不新增演员/题材检索。实测“张译的电影”返回的部分内容为相关短视频，不能宣称结果等价于原厂演员作品列表。

## 5. 清理与安全边界

- 已撤销本应用实验授予的 DUMP / PACKAGE_USAGE_STATS，并从最终 manifest 移除声明。
- 已删除本轮未可靠工作的请求读取器、请求编号策略及其专用测试；不留下静默启用的试验分支。
- 不新增依赖，不扩大无障碍包名范围，不禁用原厂助手，不清空应用数据。
- IPC 仍为固定 HomeActivity 的显式 Intent + 一次性内部票据；关键词不拼入 shell 命令。
- UI 自动化会抑制普通无障碍服务。每次语音验收前必须释放 UiAutomation、核对服务实际绑定，不能用配置字符串替代运行证据。

最终 APK、操作步骤、测试状态见 [部署说明](tcl-t7h-search-bridge-deployment.md)。若要求任意自然语言、可靠重复命令或完全取消原厂执行，需要另选有正式回调的语音入口；当前固件的页面桥接不能保证这些能力。

## 6. 最终版设备验收

最终安装包 SHA-256：`9ada6e957659ef6bd11597613f51195074a95daff8fe2c9a352345f41768d85f`。安装后确认原厂与桥接服务均绑定，再断开 ADB。请求用户依次说“找一下张译的电影”“帮我搜索星际穿越”，用户回复：“已测试，可正常进入应用搜索页面”。这证明本轮离线于电脑的跳转体验；未重新连接逐一核对两个标题，未验证重启、24 小时存活或相同词重复转发。

最终源码专项 7 项通过（包括 100 个未枚举类别、演员词、零条结构、旧词不回放），TV assemble 成功。最终 TV lint 仍是此前四项清单错误，无新增桥接错误；全工作区另有其它页面测试失败，未越界修改。
