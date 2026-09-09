# 影视TV

适用于 Android TV 与手机的音视频应用，整合媒体浏览与播放体验，并支持外部配置与 [CatVod](https://github.com/CatVodTVOfficial/CatVodTVJarLoader) Spider 接口扩展。

**App 本身不内置或提供任何内容来源。** 外部内容需自行配置，也可打开本地媒体文件或推送媒体地址。

[使用与开发指南](https://fongmi.github.io/TV/) · [讨论群组](https://t.me/fongmi_official)

## 开始使用

1. 安装适合设备的 APK：`leanback` 为电视版，`mobile` 为手机版；按 Android 系统支持的 ABI 选择 `arm64-v8a` 或 `armeabi-v7a`。最低要求为 Android 7.0（API 24）。
2. 在设置中加入自己的配置，格式与字段见[配置示例](https://fongmi.github.io/TV/config/#examples)。
3. 也可从系统文件管理器打开媒体文件，或通过推送入口播放媒体地址。

## 主要功能

- **播放**：Media3／ExoPlayer、mpv、硬解与 FFmpeg 软解；字幕、弹幕、音轨、倍速与片头／片尾跳过。
- **浏览与管理**：分类筛选、搜索、播放记录、收藏与无痕模式。
- **播放列表**：M3U／TXT／JSON 格式、列表分组与 XMLTV 节目信息。
- **操作**：电视遥控器、手机手势、画中画与后台音频。
- **互通**：DLNA 投屏／接收、Android Auto、本地 HTTP 控制与设备同步。

实际能力依配置、媒体、播放引擎与设备而异；本地 HTTP API 仅供可信局域网使用，不要直接转发到公网。

## 开发文档

| 文档 | 内容 |
| --- | --- |
| [App 功能](https://fongmi.github.io/TV/features/) | 操作与功能介绍 |
| [配置字典](https://fongmi.github.io/TV/config/) | 配置字段、网络设置与 JSON 示例 |
| [扩展对接](https://fongmi.github.io/TV/spider/) | Java／Python／JavaScript 示例、方法与返回格式 |
| [本地 API](https://fongmi.github.io/TV/local/) | 播放控制、推送、文件与同步端点 |
| [网站维护](website/README.md) | 静态网站构建与 GitHub Pages 发布 |

`app/src/main/` 为共用逻辑，`app/src/leanback/`、`app/src/mobile/` 为各自的 UI。模块列表见 [settings.gradle](settings.gradle)，SDK 与依赖版本见 [libs.versions.toml](gradle/libs.versions.toml)。

## Windows 构建

先准备以下环境与文件：

- **JDK 21、Android SDK、Python 3.10**。SDK 平台版本依 `compileSdk` 设置；Python 可用 `py -3.10 --version` 确认，找不到时在 [chaquo/build.gradle](chaquo/build.gradle) 的 Python 区块设置 `buildPython`。
- **配套 AAR**：放入 `app/libs/`。`lib-*.aar` 未纳入 Git，单纯 clone 不包含完整播放器依赖。
- **自己的签名文件与 `local.properties`**：在仓库根目录建立下列设置，将所有示例值替换成自己的资料。

```properties
sdk.dir=C:/Android/Sdk
storeFile=C:/keys/yingshi-tv.jks
keyAlias=your-key-alias
storePassword=your-keystore-password
```

密钥密码与 keystore 密码共用 `storePassword`；不要提交签名文件或真实密码。

在仓库根目录以 PowerShell 执行：

```powershell
# 电视版
.\gradlew.bat :app:assembleLeanbackRelease

# 手机版
.\gradlew.bat :app:assembleMobileRelease
```

APK 按 ABI 分包并输出至 `Release/apk/`。签名不同的 APK 不能直接覆盖既有安装。网站在 `website/`，可独立构建，不需编译 Android App。

## macOS 构建（模拟器开发）

以 Apple Silicon Mac 为例。**症状 → 真因**的对照见文末坑位表，构建失败时先查表。

### 环境准备

- **JDK 21+**（temurin 即可，Gradle 自带 toolchain 解析）。
- **Android SDK**：需 platform 36 与 build-tools 35 以上（media fork 的 `compileSdk` 为 36）。
- **Python 3.10**：Chaquopy 要求 `buildPython` 与 `chaquo/build.gradle` 的 `version = "3.10"` 主次版本一致，3.11/3.12 皆不可用。pyenv 安装 `3.10.x` 后即可——`chaquo/build.gradle` 已内置 `~/.pyenv/versions/3.10.x` 的自动回退。

### 首次构建三步

**1. 建立 `local.properties`（仓库根目录）**

```properties
sdk.dir=/Users/<user>/Library/Android/sdk
# debug 构建用 debug keystore 占位即可；release 请换成自己的签名
storeFile=/Users/<user>/.android/debug.keystore
keyAlias=androiddebugkey
storePassword=android
```

**2. 自建配套 AAR**

`app/libs/lib-*.aar` 未纳入 Git，需从 [FongMi/media](https://github.com/FongMi/media) 的 `release-1.11.0-fongmi` 分支构建（Danmaku／mpvplayer 等定制模块都在该 fork）：

```bash
git clone --depth 1 --branch release-1.11.0-fongmi https://github.com/FongMi/media.git ~/fongmi-media
echo "sdk.dir=$HOME/Library/Android/sdk" > ~/fongmi-media/local.properties   # fork 也要，否则报 SDK location not found
cd ~/fongmi-media
./gradlew :lib-common:assembleRelease :lib-common-ktx:assembleRelease \
  :lib-container:assembleRelease :lib-database:assembleRelease \
  :lib-datasource:assembleRelease :lib-datasource-okhttp:assembleRelease \
  :lib-decoder:assembleRelease :lib-decoder-ffmpeg:assembleRelease \
  :lib-extractor:assembleRelease :lib-exoplayer:assembleRelease \
  :lib-exoplayer-hls:assembleRelease :lib-exoplayer-dash:assembleRelease \
  :lib-exoplayer-smoothstreaming:assembleRelease :lib-exoplayer-rtsp:assembleRelease \
  :lib-effect:assembleRelease :lib-effect-ndk:assembleRelease \
  :lib-session:assembleRelease :lib-ui:assembleRelease \
  :lib-ui-danmaku:assembleRelease :lib-mpvplayer:assembleRelease
cp libraries/*/buildout/outputs/aar/lib-*-release.aar <本仓库>/app/libs/    # 共 20 个 AAR
```

**3. 编译**

```bash
./gradlew :app:assembleLeanbackDebug
```

### 模拟器安装与启动

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
ADB="$ANDROID_HOME/platform-tools/adb"

# Apple Silicon 的 TV 模拟器为 arm64-v8a（adb shell getprop ro.product.cpu.abilist 可查）
"$ANDROID_HOME/emulator/emulator" -avd Television_4K &

"$ADB" install -r app/build/outputs/apk/leanback/debug/app-leanback-arm64-v8a-debug.apk
"$ADB" shell monkey -p com.fongmi.android.tv -c android.intent.category.LAUNCHER 1   # 启动

# 验证模拟器运行的确实是本次编译产物（SHA256 应完全一致）
shasum -a 256 app/build/outputs/apk/leanback/debug/app-leanback-arm64-v8a-debug.apk
"$ADB" pull "$("$ADB" shell pm path com.fongmi.android.tv | head -1 | sed 's/package://;s/\r//')" /tmp/installed.apk
shasum -a 256 /tmp/installed.apk
```

模拟器上若残留 `com.fongmi.android.tv_*` 等不同 applicationId 的旧变体，彼此独立互不干扰，必要时 `adb uninstall` 清理。

### 已知坑位速查

| 症状 | 真因与解法 |
| --- | --- |
| `does not specify compileSdk in build.gradle` | 根目录缺 `local.properties`，脚本在读取处即中断（误导性报错）。建立后重试。 |
| `Couldn't find Python 3.10`（installDebugPythonRequirements） | PATH 上的 `python3` 是 3.11+。`pyenv install 3.10.x`；回退逻辑已写入 `chaquo/build.gradle`，改版本时同步调整。注意 Gradle daemon 环境粘滞，必要时 `./gradlew --stop`。 |
| `程序包androidx.media3不存在` | `app/libs/` 缺 `lib-*.aar`。按上文自建 FongMi/media fork。 |
| `SDK location not found`（media fork 内） | fork 目录也要 `local.properties`（sdk.dir）。 |
| Google TV 桌面「无法连接」 | 仅 Google 服务不可达，模拟器 NAT 网络本身正常（ICMP／DNS／HTTP 皆通），不影响 App 拉流。 |

### libass 依赖现状（2026-09）

上游 b9455fb1b（Integrate libass）及其后续提交依赖 **尚未推送至 FongMi/media 公开分支** 的 media3 状态（`androidx.media3.*.libass`、`SecondaryTextTrackSelector`、`DanmakuPlayerViewController` 等类）。目前本仓库以回退相关文件 + 兼容补丁（`SubtitleSetting` 补方法、`MpvUtil` 对齐 fork 公开 API）的方式维持可编译，功能损失仅 libass 进阶字幕／双字幕／外部字体。fork 更新后可 `git checkout origin/main -- <文件>` 还原。

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=FongMi/TV&type=Date)](https://www.star-history.com/#FongMi/TV&Date)
