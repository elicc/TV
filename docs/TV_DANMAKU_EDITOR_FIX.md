# 弹幕搜索接口弹窗裁切修复

## 范围与原因

用户截图显示检测区与二维码被裁切。原先 `MaterialAlertDialogBuilder.setView()` 的内部 custom panel 与加权滚动区共同参与测量，仅设置外层窗口高度不能保证自定义表单获得完整高度。模拟器复现了内容或底栏被裁切的问题。

## 实现

- `DanmakuApiDialog.java`：改用现有 AppCompatDialog 直接承载表单，窗口使用当前 Activity 尺寸的 88% 宽、82% 高；不修改公共 BaseAlertDialog 或手机界面。
- `dialog_danmaku_api.xml`：标题、滚动表单、固定底栏；片名、集数、检测按钮同排，二维码独立右列，结果位于输入下方。默认、取消、保存始终留在底栏。
- 复用 `tv_setting_panel`、现有文本和焦点 selector，不新增依赖、图标或主题体系。
- 配置只在保存时生效；检测、扫码及默认动作仍是草稿操作。
- 为输入框提供向下经过片名、集数、检测、保存的焦点链接，避免依赖编辑光标占用的左右键。
- `test_danmaku_ui_contracts.py` 增加直接内容承载和焦点链接静态契约。

## 验证

- 两个 flavor 的 assembleDebug 和 lint 均通过：`/tmp/tv-danmaku-editor-final-build.log`。
- UI 契约、Probe、API、Manifest 合计 17 个 unittest 通过；Probe 内含 181 检查，API 内含 32 检查：`/tmp/tv-danmaku-editor-final-tests.log`。
- 1920×1080 Android TV 模拟器截图确认默认字号下地址、检测区、完整二维码、结果及底部三个按钮全部可见；输入框聚焦描边正常。
- 默认及焦点截图：`.omx/artifacts/danmaku-ui/api-fixed-{default,focus}.png`。截图之后仅补充默认焦点及向下导航链接，未更改尺寸和视觉样式。
- 最终 APK 已安装模拟器。最终向下导航链路与大字号尚未完成设备验收；后续模拟器前台切换到 Google 登录错误页，未继续操作该页面，也未清除数据。
- 按用户要求，未在 `192.168.1.22` 安装、启动或测试。本修复不代表全屏播放、扫码、真实服务检测链路全部验收完成。
