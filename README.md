# 工作报告生成器

[![Release](https://img.shields.io/badge/下载-v1.3.10-blue)](https://github.com/ninoelvis/workreport-generator/releases/latest)
[![License](https://img.shields.io/badge/license-个人使用-lightgrey)](LICENSE)

## ⬇️ 下载

**原生版 Android App（v1.3.10，推荐）**：
[workreport-native-v1.3.10.apk](https://github.com/ninoelvis/workreport-generator/releases/download/v1.3.10/workreport-native-v1.3.10.apk)
（12 MB · Kotlin + Jetpack Compose 纯原生实现 · 无 WebView / 无网页资源 · 可直接覆盖安装旧版）

源码在 [`native/`](native/) 目录，用 Android Studio 直接打开即可构建。
[工程源码包 v1.3.10](https://github.com/ninoelvis/workreport-generator/releases/download/v1.3.10/workreport-native-source-v1.3.10.zip)

**旧版 WebView App**：
[workreport-generator-v1.2.1.apk](https://github.com/ninoelvis/workreport-generator/releases/download/v1.2.1/workreport-generator-v1.2.1.apk)
（460 KB · 仍可正常下载使用，功能与原生版一致）

**网页版（免安装，浏览器直接用）** —— 与原生版功能对齐，输出格式完全一致：

| 版本 | 入口 |
|---|---|
| 🌐 **在线版**（iPhone 推荐） | https://glzdekjuw015y.space.mcode.cn |
| 📄 **单文件版** | [`web/workreport-web.htm`](web/workreport-web.htm) ｜ [Releases 下载](https://github.com/ninoelvis/workreport-generator/releases/download/v1.3.10/workreport-web-v1.2.htm) |
| 🗜️ **离线包**（HTML + 使用说明） | [Releases 下载](https://github.com/ninoelvis/workreport-generator/releases/download/v1.3.10/workreport-web-offline-v1.2.zip) |

网页版已适配 **iPhone Safari / Edge / 电脑端浏览器**：iPhone 上生成后点「分享 / 发送」
可直接调起系统分享面板发到「邮件」App、存到「文件」或发微信；剪贴板、下载都有降级兜底。
单文件完全自包含（零外链资源），下载后双击用浏览器打开即可，无需联网。

> 全部历史版本见 [Releases](https://github.com/ninoelvis/workreport-generator/releases)。

### 📥 官方下载页

**https://st1s8u2h0kmcy.space.mcode.cn**

统一入口：主推原生版 App，同时提供网页在线版与单文件离线版三个下载入口，
并附各版本功能对照表、完整更新记录与免责声明。
页面内容由仓库内的 `docs/download-manifest.json` 驱动，
**后续发布新版本只需更新该清单并推送 main 分支，下载页会自动展示最新版本信息与下载地址，无需重写页面**。

**更新记录**

*原生版 App*

- `v1.3.10` 历史报告预览改成**真的把压缩包解开**（自动带上解压密码），**以前生成的报告也能看全表格**，不再只显示摘要
- `v1.3.9` 历史报告新增**「查看内容」**：点开就能看到压缩包里那份 Excel 长什么样（表格、合并单元格、列宽跟导出完全一致），不用手动解压；卡片上也会直接显示「本周完成」摘要
- `v1.3.8` 新增**快捷记录模板**：常写的重复内容存成模板，首页「工作记录」和「下周工作计划」两处各配一套、点一下就插入；「我的」页改成设置首页，每个配置项点进去单独编辑；引导页改为整页全屏（原先是弹窗，顶部会露出一条系统灰底），各分区可展开收起
- `v1.3.7` 邮件发送改回旧版验证过的**标准 `ACTION_SEND` 通道**（`EXTRA_STREAM` + `clipData` + 主题，邮件正文留空），撤销 v1.3.3 起为阿里邮箱做的专用通道 —— 专用通道在部分机型上会「点发送即闪退」或邮箱 App 根本拉不起来。现在各家邮箱 App 统一走同一条通道，附件一定带得上；主题与压缩包名完全一致
- `v1.3.6` 首页表单新增**草稿暂存**：填一半退出，下次打开自动恢复（生成成功后才清空）
- `v1.3.5` 按需求**彻底移除「自动带入收件人」功能**，邮件只带**主题 + 附件**，收件人由你在邮箱 App 里自己填（各家邮箱的收件人字段实现差异太大，逐个适配不可行）。同时修复首页文件名显示不全，「关于」页加入免责声明
- `v1.3.4` 修复 v1.3.3 引入的「点发送闪退 / 邮箱 App 拉不起来」
- `v1.3.3` 新增阿里邮箱专用通道（已于 v1.3.7 撤销）
- `v1.3.2` 修复填了收件人邮箱但拉起邮箱 App 后收件人栏是空的（收件人功能已于 v1.3.5 移除）
- `v1.3.1` 两个内容输入框的清除按钮加上二次确认，防止误删；生成后弹窗新增收件人邮箱输入框（已于 v1.3.5 移除）
- `v1.3.0` **原生重写正式版**：整个 App 从 WebView 套壳改写为 Kotlin + Jetpack Compose 纯原生实现，不再加载任何网页资源。导出的 Excel 与加密 ZIP 与旧版**逐字节一致**（10/10 部件实测比对通过）。新增首次启动功能介绍页、生成后自动拉起邮箱 App 发送、两个内容框的独立清除按钮；首页预览表格按 Excel 真实列宽重新对齐
- `v1.2.1` App 内顶栏图标换成新图标
- `v1.2.0` 全新应用图标（自适应图标）；状态栏与导航栏跟随主题
- `v1.1.0` 新增敏感词管理：可增删改敏感词库，长期保存，保存后立即生效
- `v1.0.2` 修复「报告标题」修改后预览区不实时刷新的问题
- `v1.0.1` 引导页新增源码仓库地址与一键复制
- `v1.0.0` 正式版发布

*网页版*

- `网页版 v1.2` 引导页大幅精简（更新记录 11 条 → 2 条）；新增**原生版 App 下载入口**与**免责声明**；修复「分享 / 发送」「复制密码」按钮点了没反应（新增的全局函数漏了导出到 `window`）
- `网页版 v1.1` 跨平台适配：**iPhone Safari / Edge / 电脑端浏览器**通用。新增 iOS 16px 输入框字号（避免聚焦时整页被放大）、`100dvh`、刘海屏安全区、触屏 hover 修正、弹窗可滚动；新增**系统分享**（iPhone 直接发到「邮件」App）；剪贴板与下载加降级兜底

---

一个纯本地运行的小工具：填好本周工作内容，一键生成**加密 ZIP 周报**，
自动把文件名复制到剪贴板，并可把压缩包作为附件带进邮箱 App。

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

- 不申请相机等无关权限；`INTERNET` 仅作预留，当前版本**不发起任何网络请求**
- 所有输入只存在本机（原生版用 Room 数据库，网页版用 localStorage）
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
├── web/workreport-web.htm        # 网页版单文件（离线，浏览器直接打开，自包含）
├── web/                           # 网页版在 Releases 里另附离线包
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
