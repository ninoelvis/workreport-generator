# 工作报告生成器

一个纯本地运行的 Android 小工具：填好本周工作内容，一键生成**加密 ZIP 周报**，
自动把文件名复制到剪贴板，并自动拉起你设定的邮箱 App 把压缩包带进附件。

**完全离线可用** —— APK 没有申请 `INTERNET` 权限，系统层面就禁止联网。
所有计算（Excel 生成 + AES 加密压缩）都在本机完成，数据不上传任何服务器。

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
