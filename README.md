# PadCursor TV

一个面向 **Android TV / Android 9（API 28）** 的轻量手柄鼠标项目。

目标场景：

- 手柄通过蓝牙/USB连接 Vidda / 海信 Android 电视。
- 摇杆控制电视本机的屏幕光标。
- A 模拟点击，X 模拟长按，B 返回，Y 回主页。
- LB/RB 滚动。
- `START + SELECT` 在“电视鼠标模式”和“手柄直通模式”之间切换。
- 手柄直通模式用于 Moonlight：应用撤掉输入覆盖层，Moonlight/游戏直接收到手柄。

## 系统要求

- `minSdk 28`：Android 9
- 不需要 Google Play
- 不需要 Root
- 不需要“悬浮窗”权限
- 需要开启 Android **辅助功能（Accessibility Service）**

## 原理

Android 9 的 `AccessibilityService` 本身没有 Android 新版那种全局 `onMotionEvent()` API，无法直接在后台读取摇杆轴。

本项目采用：

1. Accessibility Service 创建 `TYPE_ACCESSIBILITY_OVERLAY` 全屏透明层；
2. 覆盖层保持键盘/手柄焦点，但设置 `FLAG_NOT_TOUCHABLE`，不会挡住模拟的触摸；
3. 覆盖层接收 `SOURCE_JOYSTICK` 的 `MotionEvent`；
4. 根据摇杆轴持续移动自绘光标；
5. 点击和滑动用 `AccessibilityService.dispatchGesture()` 注入到下层应用；
6. 按键用 `FLAG_REQUEST_FILTER_KEY_EVENTS` 全局处理；
7. 关闭鼠标模式时删除覆盖层，让 Moonlight/游戏重新直接接收手柄。

## 默认映射

| 手柄 | 操作 |
|---|---|
| 左摇杆 | 移动鼠标（设置里可切右摇杆） |
| A | 单击 |
| X | 长按 |
| B | 返回 |
| Y | Home |
| LB | 向上滚动 |
| RB | 向下滚动 |
| L3 / R3 | 光标居中 |
| START + SELECT | 鼠标模式 / 手柄直通切换 |

## 编译

推荐 Android Studio：

1. 用 Android Studio 打开本目录。
2. 安装 Android SDK Platform 34。
3. Gradle Sync。
4. `Build > Build APK(s)`。
5. Debug APK 默认位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以在项目根目录执行：

```bat
gradlew.bat assembleDebug
```

> 本项目没有依赖 AndroidX、Kotlin 或第三方库，只使用 Android 平台 API。

## 安装到 Vidda

如果电视已开启 ADB：

```bat
adb connect 电视IP:5555
adb install -r app-debug.apk
```

安装后：

1. 打开 PadCursor TV；
2. 进入“辅助功能设置”；
3. 找到 `PadCursor TV 手柄鼠标` 并启用；
4. 返回电视桌面；
5. 手柄建议使用 XInput/Xbox 模式；
6. 摇杆应出现并移动十字圆形光标。

## 与 Moonlight 共存

在电视桌面需要鼠标时保持 **鼠标模式 ON**。

准备进入 Moonlight 游戏时：

```text
START + SELECT
```

切换到：

```text
手柄直通 ON
```

此时透明输入层被删除，Moonlight可直接接收摇杆、扳机和全部手柄按键。

需要重新控制电视时再次按 `START + SELECT`。

## 已知兼容性风险

这个方案符合 Android 9 能使用的公开 API，但不同电视厂商可能修改 InputDispatcher / WindowManager 行为。

若出现：

- 光标显示正常；
- A/B/X/Y 可以工作；
- **摇杆完全不能移动光标**；

则通常意味着该 JUUI/固件没有把 `SOURCE_JOYSTICK` 的 Generic Motion 事件投递给可聚焦的 Accessibility Overlay。此时需要针对设备做下一版输入兼容，可能需要 ADB 权限、InputMethod 方案或厂商接口。

## 包名

```text
com.lantern.padcursor
```

---

## GitHub Actions 在线编译

仓库已包含 `.github/workflows/build-apk.yml`。把整个项目上传到 GitHub 后，在 **Actions → Build PadCursorTV APK → Run workflow** 即可云端编译。成功后从运行页面底部 **Artifacts** 下载 `PadCursorTV-Android9-APK`。

在线构建固定使用 JDK 17、Gradle 8.7、Android Gradle Plugin 8.5.2，APK 最低支持 Android 9 / API 28。
