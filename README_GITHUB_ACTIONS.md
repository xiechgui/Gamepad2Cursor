# PadCursor TV — GitHub Actions 在线编译

这是为 Vidda / Android 9 电视准备的手柄鼠标项目。

## 兼容范围

- 最低 Android：9（API 28）
- 版本：v0.3.1
- applicationId：`com.lantern.padcursor`（与旧版一致；签名相同时可覆盖安装）
- 编译 SDK：34
- 构建工具：Android Gradle Plugin 8.5.2 + Gradle 8.7 + JDK 17

## 一次性在线编译

1. 在 GitHub 新建一个空仓库。
2. 把这个压缩包解压后的**全部文件和隐藏目录 `.github`** 上传到仓库根目录。
3. 按照根目录的 `SIGNING.md` 生成一次 JKS，并配置四项 GitHub Actions Secrets。
4. 打开仓库的 **Actions**。
5. 选择 **Build PadCursorTV APK**。
6. 点 **Run workflow**。
7. 等待构建和签名验证完成。
8. 进入这次运行页面底部 **Artifacts**。
9. 下载 `PadCursorTV-Android9-v0.3.1-APK`。
10. 解压后得到 `PadCursorTV-Android9-v0.3.1-release.apk`。

也可以直接 push 到 `main` 或 `master` 分支，工作流会自动构建。

## 安装

```bat
adb install -r PadCursorTV-Android9-v0.3.1-release.apk
```

GitHub Actions 每次都使用 Secrets 中保存的同一份 JKS，不会生成随机 Debug 签名。如果电视现有版本使用另一份签名，第一次迁移仍需先卸载旧版。

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
- 右摇杆 Y 轴：连续滚动（死区、灵敏度、速度可调）
- 七项鼠标动作与模式切换组合键均可自定义

> JKS 和四项 Secrets 是后续覆盖升级的唯一身份凭证，请至少保留两份离线备份，绝对不要把它们提交到源码仓库。
