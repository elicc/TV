# 影視TV

適用於 Android TV 與手機的影音應用程式，整合媒體瀏覽與播放體驗，並支援外部配置與 [CatVod](https://github.com/CatVodTVOfficial/CatVodTVJarLoader) Spider 介面擴充。

**App 本身不內建或提供任何內容來源。** 外部內容需自行配置，也可開啟本地媒體檔案或推送媒體網址。

[使用與開發指南](https://fongmi.github.io/TV/) · [討論群組](https://t.me/fongmi_official)

## 開始使用

1. 安裝適合裝置的 APK：`leanback` 為電視版，`mobile` 為手機版；依 Android 系統支援的 ABI 選擇 `arm64-v8a` 或 `armeabi-v7a`。最低需求為 Android 7.0（API 24）。
2. 在設定中加入自己的配置，格式與欄位見[配置範例](https://fongmi.github.io/TV/config/#examples)。
3. 也可從系統檔案管理員開啟媒體檔案，或透過推送入口播放媒體網址。

## 主要功能

- **播放**：Media3／ExoPlayer、mpv、硬解與 FFmpeg 軟解；字幕、彈幕、音軌、倍速與片頭／片尾跳過。
- **瀏覽與管理**：分類篩選、搜尋、播放記錄、收藏與無痕模式。
- **播放清單**：M3U／TXT／JSON 格式、清單分組與 XMLTV 節目資訊。
- **操作**：電視遙控器、手機手勢、畫中畫與背景音訊。
- **互通**：DLNA 投放／接收、Android Auto、本地 HTTP 控制與裝置同步。

實際能力依配置、媒體、播放引擎與裝置而異；本地 HTTP API 僅供可信任區域網路使用，不要直接轉發到公網。

## 開發文件

| 文件 | 內容 |
| --- | --- |
| [App 功能](https://fongmi.github.io/TV/features/) | 操作與功能介紹 |
| [配置字典](https://fongmi.github.io/TV/config/) | 配置欄位、網路設定與 JSON 範例 |
| [擴充介接](https://fongmi.github.io/TV/spider/) | Java／Python／JavaScript 範例、方法與回傳格式 |
| [本地 API](https://fongmi.github.io/TV/local/) | 播放控制、推送、檔案與同步端點 |
| [網站維護](website/README.md) | 靜態網站建置與 GitHub Pages 發布 |

`app/src/main/` 為共用邏輯，`app/src/leanback/`、`app/src/mobile/` 為各自的 UI。模組清單見 [settings.gradle](settings.gradle)，SDK 與依賴版本見 [libs.versions.toml](gradle/libs.versions.toml)。

## Windows 建置

先準備以下環境與檔案：

- **JDK 21、Android SDK、Python 3.10**。SDK 平台版本依 `compileSdk` 設定；Python 可用 `py -3.10 --version` 確認，找不到時在 [chaquo/build.gradle](chaquo/build.gradle) 的 Python 區塊設定 `buildPython`。
- **配套 AAR**：放入 `app/libs/`。`lib-*.aar` 未納入 Git，單純 clone 不包含完整播放器依賴。
- **自己的簽章檔與 `local.properties`**：在儲存庫根目錄建立下列設定，將所有範例值替換成自己的資料。

```properties
sdk.dir=C:/Android/Sdk
storeFile=C:/keys/yingshi-tv.jks
keyAlias=your-key-alias
storePassword=your-keystore-password
```

金鑰密碼與 keystore 密碼共用 `storePassword`；不要提交簽章檔或真實密碼。

在儲存庫根目錄以 PowerShell 執行：

```powershell
# 電視版
.\gradlew.bat :app:assembleLeanbackRelease

# 手機版
.\gradlew.bat :app:assembleMobileRelease
```

APK 按 ABI 分包並輸出至 `Release/apk/`。簽章不同的 APK 不能直接覆蓋既有安裝。網站位於 `website/`，可獨立建置，不需編譯 Android App。

## macOS 建置（模擬器開發）

以 Apple Silicon Mac 為例。**症狀 → 真因**的對照見文末坑位表，建置失敗時先查表。

### 環境準備

- **JDK 21+**（temurin 即可，Gradle 自帶 toolchain 解析）。
- **Android SDK**：需 platform 36 與 build-tools 35 以上（media fork 的 `compileSdk` 為 36）。
- **Python 3.10**：Chaquopy 要求 `buildPython` 與 `chaquo/build.gradle` 的 `version = "3.10"` 主次版本一致，3.11/3.12 皆不可用。pyenv 安裝 `3.10.x` 後即可——`chaquo/build.gradle` 已內建 `~/.pyenv/versions/3.10.x` 的自動回退。

### 首次建置三步

**1. 建立 `local.properties`（倉庫根目錄）**

```properties
sdk.dir=/Users/<user>/Library/Android/sdk
# debug 建置用 debug keystore 佔位即可；release 請換成自己的簽章
storeFile=/Users/<user>/.android/debug.keystore
keyAlias=androiddebugkey
storePassword=android
```

**2. 自建配套 AAR**

`app/libs/lib-*.aar` 未納入 Git，需從 [FongMi/media](https://github.com/FongMi/media) 的 `release-1.11.0-fongmi` 分支建置（Danmaku／mpvplayer 等定制模組都在該 fork）：

```bash
git clone --depth 1 --branch release-1.11.0-fongmi https://github.com/FongMi/media.git ~/fongmi-media
echo "sdk.dir=$HOME/Library/Android/sdk" > ~/fongmi-media/local.properties   # fork 也要，否則報 SDK location not found
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
cp libraries/*/buildout/outputs/aar/lib-*-release.aar <本倉庫>/app/libs/    # 共 20 個 AAR
```

**3. 編譯**

```bash
./gradlew :app:assembleLeanbackDebug
```

### 模擬器安裝與啟動

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
ADB="$ANDROID_HOME/platform-tools/adb"

# Apple Silicon 的 TV 模擬器為 arm64-v8a（adb shell getprop ro.product.cpu.abilist 可查）
"$ANDROID_HOME/emulator/emulator" -avd Television_4K &

"$ADB" install -r app/build/outputs/apk/leanback/debug/app-leanback-arm64-v8a-debug.apk
"$ADB" shell monkey -p com.fongmi.android.tv -c android.intent.category.LAUNCHER 1   # 啟動

# 驗證模擬器運行的確是本次編譯產物（SHA256 應完全一致）
shasum -a 256 app/build/outputs/apk/leanback/debug/app-leanback-arm64-v8a-debug.apk
"$ADB" pull "$("$ADB" shell pm path com.fongmi.android.tv | head -1 | sed 's/package://;s/\r//')" /tmp/installed.apk
shasum -a 256 /tmp/installed.apk
```

模擬器上若殘留 `com.fongmi.android.tv_*` 等不同 applicationId 的舊變體，彼此獨立互不干擾，必要時 `adb uninstall` 清理。

### 已知坑位速查

| 症狀 | 真因與解法 |
| --- | --- |
| `does not specify compileSdk in build.gradle` | 根目錄缺 `local.properties`，腳本在讀取處即中斷（誤導性報錯）。建立後重試。 |
| `Couldn't find Python 3.10`（installDebugPythonRequirements） | PATH 上的 `python3` 是 3.11+。`pyenv install 3.10.x`；回退邏輯已寫入 `chaquo/build.gradle`，改版本時同步調整。注意 Gradle daemon 環境粘滯，必要時 `./gradlew --stop`。 |
| `程序包androidx.media3不存在` | `app/libs/` 缺 `lib-*.aar`。按上文自建 FongMi/media fork。 |
| `SDK location not found`（media fork 內） | fork 目錄也要 `local.properties`（sdk.dir）。 |
| Google TV 桌面「無法連接」 | 僅 Google 服務不可達，模擬器 NAT 網路本身正常（ICMP／DNS／HTTP 皆通），不影響 App 拉流。 |

### libass 依賴現況（2026-09）

上游 b9455fb1b（Integrate libass）及其後續提交依賴 **尚未推送至 FongMi/media 公開分支** 的 media3 狀態（`androidx.media3.*.libass`、`SecondaryTextTrackSelector`、`DanmakuPlayerViewController` 等類）。目前本倉庫以回退相關檔案 + 相容補丁（`SubtitleSetting` 補方法、`MpvUtil` 對齊 fork 公開 API）的方式維持可編譯，功能損失僅 libass 進階字幕／雙字幕／外部字體。fork 更新後可 `git checkout origin/main -- <檔案>` 還原。

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=FongMi/TV&type=Date)](https://www.star-history.com/#FongMi/TV&Date)
