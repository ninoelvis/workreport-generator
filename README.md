# 工作报告生成器

[![Release](https://img.shields.io/badge/下载-v1.3.1-blue)](https://github.com/ninoelvis/workreport-generator/releases/latest)
[![License](https://img.shields.io/badge/license-个人使用-lightgrey)](LICENSE)

## ⬇️ 下载

**原生版 Android App（v1.3.0，推荐）**：
[workreport-native-v1.3.1.apk](https://github.com/ninoelvis/workreport-generator/releases/download/v1.3.1/workreport-native-v1.3.1.apk)
（12 MB · Kotlin + Jetpack Compose 纯原生实现 · 无 WebView / 无网页资源 · 可直接覆盖安装旧版）

源码在 [`native/`](native/) 目录，用 Android Studio 直接打开即可构建。

**旧版 WebView App**：
[workreport-generator-v1.2.1.apk](https://github.com/ninoelvis/workreport-generator/releases/download/v1.2.1/workreport-generator-v1.2.1.apk)
（460 KB · 仍可正常下载使用，功能与原生版一致）

**网页版（免安装、离线可用）**：
[`web/workreport-web.htm`](web/workreport-web.htm) —— 下载后用浏览器打开即可，无需联网。

> 全部历史版本见 [Releases](https://github.com/ninoelvis/workreport-generator/releases)。

**更新记录**
- `v1.3.1` 两个内容输入框的清除按钮加上二次确认，防止误删；生成后的「是否拉起邮箱 App 发送」弹窗里新增收件人邮箱输入框，填完自动保存，下次生成直接带上（也可在「我的」页修改），确认后再选择邮箱 App
- `v1.3.0` **原生重写正式版**：整个 App 从 WebView 套壳改写为 Kotlin + Jetpack Compose 纯原生实现，不再加载任何网页资源。导出的 Excel 与加密 ZIP 与旧版**逐字节一致**（10/10 部件实测比对通过）。新增首次启动功能介绍页、收件人邮箱配置、生成后自动拉起邮箱 App 发送（可记住选择）、两个内容框的独立清除按钮；首页预览表格按 Excel 真实列宽重新对齐
- `v1.2.1` App 内顶栏图标换成新图标
- `v1.2.0` 全新应用图标（自适应图标）；状态栏与导航栏跟随主题
- `v1.1.0` 新增敏感词管理：可增删改敏感词库，长期保存，保存后立即生效
- `v1.0.2` 修复「报告标题」修改后预览区不实时刷新的问题
- `v1.0.1` 引导页新增源码仓库地址与一键复制
- `v1.0.0` 正式版发布

---

一个纯本地运行的 Android 小工具：填好本周工作内容，一键生成**加密 ZIP 周报**，
自动把文件名复制到剪贴板，并自动拉起你设定的邮箱 App 把压缩包带进附件。

**完全离线可用** —— 所有计算（Excel 生成 + AES-256 加密压缩）都在本机完成，数据不上传任何服务器。
原生版为后续在线功能预留了 `INTERNET` 权限，但当前版本**不会发起任何网络请求**。

> 📱 **v1.3.0 起为纯原生实现**：`native/` 目录是完整可构建的 Gradle 工程
> （Kotlin 2.0 + Compose BOM 2024.10 + Room + zip4j + OkHttp 预留），
> 仓库根目录的 `app/` 是旧版 WebView 工程，两者导出的文件格式完全一致。

---

## 功能

| | 功能 | 说明 |
|---|---|---|
| 📝 | 周报录入 | 填姓名与本周工作时间，自动算出周数、文件名与 ZIP 解压密码（默认取本周周四，格式 `yyyyMMdd`） |
| 📋 | 工作记录 | 做完一项随手记一条，上报前一键汇总进「本周已完成工作梳理」 |
| 📦 | 生成加密 ZIP | 导出 Excel 并用密码加密压缩，保存到系统「下载」目录 |
| 📋 | 自动复制文件名 | 生成成功的同时，文件名自动进入手机剪贴板 |
| 📨 | 自动发邮件 | 自动拉起你设定的邮箱 App，压缩包作为附件带入写邮件界面 |
| 👁️ | 实时预览 | 随时查看最终 Excel 效果，所见即所得 |
| 🛡️ | 敏感词管理 | 顶栏 🛡️ 打开面板，自行增删改敏感词库；词库长期保存（卸载软件才清除），保存后立即生效 |
| 🧹 | 分区清除 | 两个文本框各自可单独清空，「清除全部数据」一键重置 |
| ✨ | 首次引导 | 首次启动展示功能介绍与版本更新记录 |
| 🌗 | 深浅主题 | 跟随系统，可手动切换 |

### 隐私

- 不申请网络权限，不联网，不上传任何数据
- 所有输入只存在手机本地（WebView localStorage）
- 首次启动时姓名留空，填写后自动保存

---

## 目录结构

```
.
├── app/                          # Android 应用本体
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/index.html     # 全部前端逻辑 + 内联的 xlsx / zip 库
│   │   ├── java/com/workreport/app/
│   │   │   ├── MainActivity.java # WebView 壳 + JS bridge（剪贴板 / 邮件 / 存储）
│   │   │   └── AttachProvider.java # 把加密 ZIP 以 content:// 共享给邮件 App
│   │   └── res/                  # 图标与主题
│   └── ...
├── web/workreport-web.htm        # 网页版（离线，浏览器直接打开）
├── keystore/                     # 签名证书（★ 不提交到 git，见该目录 README）
├── docs/更新记录.md
├── build.sh                      # 一键构建脚本
└── README.md
```

---

## 构建

### 方式一：命令行（无需 Android Studio）

```bash
# 需要 JDK 17+、python3、curl、unzip
./build.sh
```

脚本会自动下载所需工具链（`aapt2` / `d8` / `zipalign` / `apksigner` / `android.jar`）
到 `tools/`，产物输出到 `dist/`。

只想要个未签名包调试：

```bash
./build.sh --unsigned
```

> **签名**：默认使用 `keystore/release.keystore`（alias `workreport`，密码 `workreport2026`）。
> 仓库里**不含** keystore，请自行放置，或用 `KEYSTORE_PATH` 等环境变量指定。

### 方式二：Android Studio

直接 `File → Open` 选本目录即可导入。工程用标准 Gradle 布局，
但仓库未附带 Gradle Wrapper，AS 首次打开会自动补齐。

---

## 发布

APK 不要直接提交进 git 历史（会让仓库永久膨胀），用 **GitHub Release** 分发：

1. 打 tag：`git tag v1.0.0 && git push origin v1.0.0`
2. 到仓库的 **Releases → Draft a new release**
3. 选好 tag，拖入 `dist/` 里的 APK，发布

这样得到的下载地址是稳定的，形如：

```
https://github.com/<你的用户名>/<仓库名>/releases/download/v1.0.0/xxx.apk
```

---

## 离线说明

| 场景 | 能否离线 |
|---|---|
| 生成 Excel、加密 ZIP、保存到下载目录 | ✅ |
| 复制文件名到剪贴板 | ✅ |
| 打开预览 / 管理工作记录 / 清除数据 | ✅ |
| 把附件带进邮箱 App 的写邮件界面 | ✅（拉起是本地行为） |
| **邮件真正发出去** | ❌ 需要邮箱 App 自己联网 |

---

## 更新记录

见 [`docs/更新记录.md`](docs/更新记录.md)。

---

## License

仅供个人使用。
