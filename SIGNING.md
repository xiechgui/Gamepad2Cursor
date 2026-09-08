# 固定签名配置

PadCursor TV 使用 GitHub Actions Secrets 保存固定签名。JKS 私钥不会进入源码仓库；每次构建只在临时运行器中恢复，构建结束后删除。

## 1. 只生成一次 JKS

在装有 JDK 的电脑执行：

```bash
keytool -genkeypair -v -keystore padcursor-release.jks -alias padcursor -keyalg RSA -keysize 2048 -validity 10000
```

妥善备份 `padcursor-release.jks`、仓库密码、别名和密钥密码。丢失任意一项后，将无法再为已安装版本提供可覆盖升级的 APK。不要把 JKS、密码或 Base64 内容提交到 GitHub 仓库。

## 2. 将 JKS 转为 Base64

Windows PowerShell：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("padcursor-release.jks")) | Set-Clipboard
```

macOS / Linux：

```bash
base64 < padcursor-release.jks | tr -d '\n'
```

## 3. 配置 GitHub Actions Secrets

打开仓库：**Settings → Secrets and variables → Actions → New repository secret**，依次建立：

| Secret 名称 | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | 上一步得到的完整 Base64 文本 |
| `ANDROID_KEYSTORE_PASSWORD` | JKS 仓库密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名；按上面的命令应为 `padcursor` |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

四项缺少任何一项，工作流都会明确报错并停止，不会生成未签名或临时签名的 APK。

## 4. 构建与备份

运行 **Actions → Build PadCursorTV APK → Run workflow**。产物为：

```text
PadCursorTV-Android9-v0.3.3-release.apk
```

首次使用这套固定签名安装后，后续版本必须继续使用完全相同的 JKS、别名和密码。建议至少保留两份离线备份。

如果电视上现有 v0.1/v0.2 是其它 Debug 签名，第一次迁移到固定签名仍需卸载旧 APK；从固定签名版 v0.3.0 开始，后续版本即可直接覆盖升级。
