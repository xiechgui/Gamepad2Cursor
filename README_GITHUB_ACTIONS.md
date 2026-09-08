# PadCursor TV — GitHub Actions 在线编译

这是为 Vidda / Android 9 电视准备的手柄鼠标项目。

## 兼容范围

- 最低 Android：9（API 28）
- applicationId：`com.lantern.padcursor`
- 编译 SDK：34
- 构建工具：Android Gradle Plugin 8.5.2 + Gradle 8.7 + JDK 17

## 一次性在线编译

1. 在 GitHub 新建一个空仓库。
2. 把这个压缩包解压后的**全部文件和隐藏目录 `.github`** 上传到仓库根目录。
3. 打开仓库的 **Actions**。
4. 选择 **Build PadCursorTV APK**。
5. 点 **Run workflow**。
6. 等待构建完成。
7. 进入这次运行页面底部 **Artifacts**。
8. 下载 `PadCursorTV-Android9-APK`。
9. 解压后得到 `PadCursorTV-Android9-debug.apk`。

也可以直接 push 到 `main` 或 `master` 分支，工作流会自动构建。

## 安装

```bat
adb install -r PadCursorTV-Android9-debug.apk
```

安装后在电视：

`设置 → 辅助功能 → PadCursor TV 手柄鼠标 → 开启`

## 默认控制

- 左摇杆：移动光标（可切右摇杆）
- A：点击
- X：长按
- B：返回
- Y：主页
- LB / RB：滚动
- L3 / R3：光标居中
- START + SELECT：鼠标模式 / 手柄直通模式切换

> Debug APK 由 Android 调试证书自动签名，可直接侧载。它适合目前的测试阶段。等实机功能稳定后再做固定 release 签名更合适。
