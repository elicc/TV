# TCL T7H 小T搜索桥接：部署与验收记录

日期：2026-10-11。状态：**通用结果字段版已安装；断开 ADB 的演员词与片名测试后，用户反馈可正常进入本应用搜索页；请求参数实验因字段过期已撤下，详见 [验证记录](tcl-t7h-query-bridge-validation.md)。重启及长期验收未完成。**

## 能做什么

保留「小T小T」及原厂识别：说「小T小T，帮我搜索让子弹飞」，原厂先处理并显示搜索结果，本应用读取指定标题中的关键词，再打开自己的搜索结果页。日常链路全部在电视内执行，不调用电脑 HTTP/ADB 转发，也不依赖电视固定 IP。仍需网络支持原厂识别和在线内容源。

这不是原始语音识别全文拦截，不支持任意自然语言任务，也不能阻止原厂先执行。原厂手动搜索也可能触发。不会自动播放、购买或执行任意 shell 命令。

## 安装包与设备

| 项目 | 本次值 |
| --- | --- |
| 设备 | 用户确认 TCL T7H，Android 11，armeabi-v7a |
| 固件指纹 | `TCL/tcl_mt5879_cn_db/tcl_mt5879_cn:11/RQ3A.210705.001/2025103118:user/release-keys` |
| 应用包 / 版本 | `com.elicc.android.tv` / 5.6.3 (563)，leanbackDebug |
| APK | `app/build/outputs/apk/leanback/debug/app-leanback-armeabi-v7a-debug.apk` |
| APK SHA-256 | `9ada6e957659ef6bd11597613f51195074a95daff8fe2c9a352345f41768d85f` |
| 签名证书 SHA-256 | `6f0cf46116bbcd9b4a2eeb324492b9ebe06afa395b45a999ba37e0b5279549c7` |
| 原安装包备份（部署电脑本地） | `/tmp/tcl-voice-inspect/release/installed-before-bridge.apk` |

覆盖安装返回 `Success`，保留应用数据和开关。临时原型 `com.elicc.voiceobserver` 已卸载。备份位于临时目录，需长期保存时应另行复制到私有备份目录；不要提交 APK、授权备份或个人配置到仓库。

## 构建、安装与启用

使用项目现有 SDK、播放器 AAR、签名配置；本次用本机 JDK 24 构建成功，不新增依赖。新环境按仓库 README 准备。

```bash
./gradlew :app:assembleLeanbackDebug --offline --console=plain
adb connect 192.168.1.22:5555
adb -s 192.168.1.22:5555 install -r app/build/outputs/apk/leanback/debug/app-leanback-armeabi-v7a-debug.apk
python3 tools/android/tcl_search_bridge_accessibility.py --serial 192.168.1.22:5555 enable
python3 tools/android/tcl_search_bridge_accessibility.py --serial 192.168.1.22:5555 status
```

1. 安装前核对签名；签名不符时停止，不能卸载以绕过检查。
2. 在本应用「设置 → 内容 → 小T搜索桥接」开启开关（新安装默认关闭），并授权无障碍服务。短按打开配置，长按查看/清除脱敏诊断。
3. TCL 设置页若不提供授权入口，使用上面的辅助工具。工具仅合并本服务授权并打开应用，不代替用户开启应用开关；备份原授权到权限受限的本地 JSON 文件。
4. `configured=true` 不等于运行成功：`bound_services_evidence` 必须包含「小T搜索桥接」，应用设置显示「已连接 · 通用结果字段模式」。
5. 覆盖更新后本机曾出现授权仍在、服务却未绑定。通过下面的仅本服务重新授权恢复，然后再次检查 `status`：

```bash
python3 tools/android/tcl_search_bridge_accessibility.py --serial 192.168.1.22:5555 disable
python3 tools/android/tcl_search_bridge_accessibility.py --serial 192.168.1.22:5555 enable
```

确认能正确跳转后可 `adb disconnect 192.168.1.22:5555`。不需要 USB、HDMI 或电脑常驻转发。ADB 调试与原有 HTTP 控制端口只用于可信内网，勿暴露公网。

## 解析边界及本轮故障

服务只观察 `com.tcl.wholenetsearch`，只读取 `com.tcl.wholenetsearch:id/ttvl_result_title`。

当前不再枚举类别，使用指定控件的统一标题结构提取查询词。已观察格式包括：

- `“流浪地球”的全部影视结果（24条）`
- `“让子弹飞”的游戏视频结果（1条）`
- `“张译的电影”的电影结果（51条）`

首个正式集成版只接受「全部影视」，因此用户说「让子弹飞」时停留在原厂页。ARTEMIS 实测原厂把它分到「游戏视频」，并非服务权限失败。新增该类别的回归用例先失败，扩展精确类别白名单后通过。修复版用户确认自动跳转，随后 ARTEMIS 确认本应用标题为 `“让子弹飞”的搜索结果`，前台为 `CollectActivity`。

新类别无需加白名单；同结构零条结果也允许转交本应用（尚无零条结果设备实测）。未知标题结构、空值、超长/控制字符，以及含「没有找到」「未找到」「为你推荐」的描述仍拒绝转发。不能据此保证所有 TCL 固件兼容。

防抢屏规则：标题稳定 400 ms 后复核，候选最长 2 秒；记住进程内最近 32 个已消费词。返回旧结果不会因时间流逝自动重放；重复同片名只有观察到明确空标题周期才可能再次转发，因此可能安全漏转发。一次性内部票据 15 秒有效，连续三次启动失败自动关闭开关。不保证超慢初始化时补发。

## 验证证据与未测项

| 项目 | 结果 |
| --- | --- |
| 临时原型 G0 | 用户确认断开 ADB 后「星际穿越 / 流浪地球 / 阿凡达」三次均成功；这是原型证据，不能替代正式版验收 |
| 首个集成版「让子弹飞」 | 失败：已查明类别格式不匹配 |
| 此前类别白名单修复版「让子弹飞」 | 用户确认正常跳转；ARTEMIS 核对本应用正确片名及结果；ADB 核对前台 CollectActivity |
| 当前通用字段版断开 ADB 验收 | 请用户依次搜索“张译的电影”“星际穿越”，用户反馈“已测试，可正常进入应用搜索页面”；本轮未重新连接逐一回读标题，不沿用旧包证据 |
| 桥接专项测试 | 7 项通过；包含实际 Java 策略/票据执行、100 个合成新类别、演员词、零条结构、100 事件风暴、旧页/重复/过期、授权合并/移除 |
| TV assemble | 成功，见本机 `/tmp/tcl-voice-inspect/generic-bridge-build.log` |
| 全量 Python 测试 | 本轮实验阶段工作区 127 项（含随后撤下的实验用例）、11 失败/1 错误；涉及并行修改的 detail scroll、Douban discovery 及原有主题契约，非全绿。最终保留桥接专项 7 项通过；不覆盖或回退其它工作的文件 |
| TV lint | 前轮有既存 4 错误：MissingLeanbackLauncher / ImpliedTouchscreenHardware / MissingIntentFilterForMediaSearch / MissingLeanbackSupport；不能声称 lint 全绿 |
| Mobile 检查 | 既有缺失 tv_radius_card 与 MPV 对话框字符串阻断；桥接仅在 leanback 声明 |
| 重启 / 待机 / 24 小时稳定性 | 尚未验证，不承诺自动恢复或长期存活 |
| 正式版冷启动、旧页回流、关闭后语音、播放返回栈 | 尚缺完整设备验收；逻辑测试不能替代这些场景 |

专项命令：

```bash
python3 -m unittest tools.tests.test_tcl_search_bridge tools.tests.test_tcl_search_bridge_accessibility
git diff --check
```

ARTEMIS 使用 UiAutomation 时可能抑制普通无障碍服务，不能在说话/转发期间持续抓层级。完成检查后退出诊断进程，恢复授权、核对真实绑定，再测语音；不要杀掉不相关的 `app_process`。本机诊断曾恢复出旧 OEM 组件，应保留已有服务，并恢复先前确认的 `com.tcl.walleve/com.tcl.walleve.platform.accessibility.AiAccessibilityService`，不能盲目覆盖整个授权列表。

## 关闭、恢复与故障处理

- **立即关闭：** 应用设置关闭「小T搜索桥接」，待处理请求取消；原厂助手继续工作。
- **撤销授权：** 运行工具 `disable`，只移除桥接服务，不删除其它无障碍服务。
- **没跳转：** 先查应用开关和实际绑定，再查看长按诊断。如果停留在未知标题结构/推荐结果，这是保守拒绝，不要通过扩大到任意屏幕文字来修复。
- **被强停/撤权：** 手动打开应用，必要时重新授权；不承诺强停后自行复活。
- **回滚 APK：** 优先关开关或撤权；如需安装同签名旧包，先检查版本及数据兼容性。不能以清数据/卸载作为默认回滚办法。
- **日志：** 桥接仅存最多 50 条进程内阶段记录，不写搜索词到公共 logcat；系统/原厂自身日志不受此保证。无常驻录音或原始 ASR 保存。

源码入口：`TclSearchBridgeService`、`TclSearchBridgePolicy`、`TclSearchBridgeSettings`；复用现有 HomeActivity → SearchActivity / CollectActivity 链路。不替换搜索引擎，不引入后台电脑服务。完整设计和待验收矩阵见 `.omx/plans/tcl-t7h-search-bridge.md`。

## 本轮方案纠偏

DUMP + PACKAGE_USAGE_STATS 一次性授权本机可行，但原厂 Fragment 参数在 onNewIntent 后不更新，已被重复/不同关键词测试否决。最终版本已撤销这两项权限并移除声明；临时 ResultActivity 日志属性已恢复为空。不要再次将参数编号误当成可靠的新语音请求标识。详见 [完整证据](tcl-t7h-query-bridge-validation.md)。
