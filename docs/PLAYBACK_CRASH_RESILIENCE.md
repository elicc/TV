# 播放资源耗尽与缓存异常防护

针对 2026-10-30 TV 日志中的线程创建失败、CursorWindow 分配失败和
`SimpleCache.commitFile:416` 长度断言失败，宿主采取以下措施。

## 行为变化

- 本地回环地址及 `/proxy`、`/proxy/…` HTTP 路径不额外启动磁盘预读；当前或下一集
  是这类代理时，也不预加载下一集。不改用户保存的预加载开关、线程数及播放源。
  普通播放保留，但代理源可能不再获得提前缓存带来的缓冲收益。
- 普通直连源继续预读。每个预读会话使用独立缓存 key，并重新探测资源长度，避免同一
  URL 在不同影片/会话中共享旧长度信息。代价是不复用上一预读会话的磁盘缓存；旧块
  仍由既有缓存容量淘汰机制清理，不在写入期间主动删除。
- 每个预读 Range 请求要求准确的 `206`、起止范围、总长度及未压缩响应。短响应抛出
  IO 异常而不是正常 EOF，避免缓存把短读位置记录为文件总长度。服务端不符合要求时
  退回普通上游，不改变普通播放器对服务器的兼容策略。
- 会话在缓存状态异常、非法 Range 或连续三次下载失败后停止预读。调度、打开、读取、
  关闭均覆盖已知缓存异常；取消会话时取消活跃 OkHttp Call，而非只中断 worker。
  玩家读路径从已消费偏移继续访问原始上游，不再读写该异常会话的缓存。
- 历史保存按 key 合并，最多保留 128 个待写 key，只有一个串行 drain。
  进度和访问记录分别保留，避免访问记录吞掉尚未落盘的进度。CursorWindow 分配失败
  最多尝试三次，间隔 2 秒、4 秒；耗尽后保留内存 checkpoint，后续新保存至少等待
  30 秒冷却，不无限重试。退出刷新仅在成功落盘后发出。
  删除、清空、迁移和隐身模式会取消相应待写任务，避免延迟重试复活记录。
- CursorWindow 异常在 API 33 才成为公开 SDK 类型。为兼容实际抛出该隐藏类型的
  Android 11 TV，只精确匹配其类名；其他 RuntimeException 和 OOM 不被吞掉。

## 诊断与验证

VOD 的 Exo 播放期间每 60 秒记录一条 `PlaybackResources`，包含代理标记、FD 数、
Threads、VmSize、VmRSS；停止时取消采样。复用现有调度器，不做全线程/堆转储，
不记录播放 URL、HTTP 头或凭证。

本地回归：

```sh
python3 -m unittest tools.tests.test_exo_crash_resilience tools.tests.test_vod_resume tools.tests.test_diagnostics
./gradlew :app:assembleLeanbackDebug :app:compileMobileDebugJavaWithJavac :app:lintLeanbackDebug :app:lintMobileDebug
```

JVM 故障注入测试执行生产 Range 校验、播放器读路径及历史保存代码；网络、缓存与
Android 边界由确定性替身提供，不等同于真实播放器/插件/TV 集成验证。

设备验收应在不影响观看的时段进行：同一百度网盘源连续播放至少两小时，并覆盖
快进、切集、退出重进；对照线程、FD、虚拟内存趋势，确认旧请求及时回收。
另外验证直连源正常预读、不符合 Range 协议的源能够普通播放。
安装必须遵循仓库指定的 `scripts/deploy-leanback.sh`，不要使用 `adb install`。

## 尚未证实/解决的部分

现有混淆堆栈不能证明插件具体哪处泄漏。宿主停止额外代理预取可以降低放大效应，
但不能限制插件自行创建的线程，也不能保证消除 OOM；需要获得复现包、插件版本/
哈希及崩溃前资源曲线后继续定位。此次没有修改插件或增加全局 OOM 吞错机制。
跨会话取消请求不保证不响应取消的第三方代码立即退出。
