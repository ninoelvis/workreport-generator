# 工作报告生成器 · 原生版 —— 导入 Android Studio / 调试 / 打包指南

## 〇、环境要求

| 项 | 版本 | 说明 |
|---|---|---|
| JDK | **17**（Temurin / Oracle / OpenJDK 都行） | AGP 8.7 要求 JDK 17，JDK 21 也可 |
| Android Studio | **Koala (2024.1) 及以上** | 需要支持 AGP 8.7 |
| Android SDK | Platform **35** + Build-Tools **34.0.0** | `compileSdk 35 / targetSdk 34 / minSdk 26` |
| Gradle | 8.9（工程自带 Wrapper，无需本地安装） | |

> ⚠️ **JDK 21+ 也能跑**，但如果遇到 `Unsupported class file major version`，把 Gradle JDK 切回 17。

---

## 一、导入 Android Studio

1. 解压 `工作报告生成器_原生版_工程源码_v1.3.0-beta1.zip` 到任意目录（路径**不要有中文和空格**）。
2. Android Studio → **File → Open** → 选中解压出来的 `native` 目录（不是 `app` 子目录）→ OK。
3. 首次打开会提示 **Gradle Sync**，等待右下角进度条走完。
   - 如果提示缺少 SDK，按提示安装 **Android SDK Platform 35** 和 **Build-Tools 34.0.0**。
4. Sync 完成后，顶部工具栏会出现设备下拉框 + ▶ 运行按钮。

### 国内网络加速（可选）

工程里 `settings.gradle.kts` 已经把 `google()` 放在第一位，阿里云镜像作为加速/兜底。
如果你的网络访问 Google 仓库很慢，可以自行把 `maven.aliyun.com` 那一行挪到 `google()` 前面。

---

## 二、本地跑起来（调试）

1. 手机上打开 **设置 → 关于手机 → 连续点 7 次「版本号」** 打开开发者模式。
2. 开发者选项里打开 **USB 调试**。
3. USB 连接电脑，手机上弹出「允许 USB 调试吗？」→ 勾选「始终允许」→ 确定。
4. Android Studio 顶部设备下拉框选中你的手机 → 点 ▶ **Run 'app'**。
5. 应用会装到手机上并自动启动。

### 断点调试

- 在任意 Kotlin 文件左侧行号点一下设置断点 → 点 🐞 **Debug 'app'**。
- 常用断点位置：
  - `export/XlsxWriter.kt` → `buildSheetXml()`：看 Excel XML 怎么拼的
  - `export/ReportNaming.kt` → `plan()`：看文件名和密码怎么算的
  - `util/SensitiveWordEngine.kt` → `replace()`：看脱敏逻辑
- **Logcat** 过滤：`adb logcat -s WorkReport:*` 或在 IDE 里搜包名 `com.workreport.app`。

### 命令行跑

```bash
cd native
./gradlew installDebug
adb shell am start -n com.workreport.app/.MainActivity
```

---

## 三、跑单元测试

```bash
./gradlew testDebugUnitTest
```

测试覆盖：
- `XlsxWriterTest` —— xlsx 的 10 个部件、样式索引、合并区域、列宽行高、XML 转义
- `DateUtilsTest` —— 日期解析、周范围、周四密码、月内周数
- `SensitiveWordEngineTest` —— 脱敏替换与工作内容格式化
- `ReportNamingTest` —— 文件名格式与解压密码规则
- `EncryptedZipWriterTest` —— AES-256 / AE-1(HMAC-SHA1) / Deflate、错误密码必须失败

报告在 `app/build/reports/tests/testDebugUnitTest/index.html`。

---

## 四、签名打包（生成可安装的正式 APK）

### 4.1 准备签名证书

Android Studio 菜单：**Build → Generate Signed Bundle / APK… → APK → next**

1. **Key store path**：点 `Create new…` 选一个位置（例如 `~/keys/workreport.jks`），密码自己设。
2. **Alias**：填 `workreport`，密码自己设。
3. 有效期 25 年以上，勾选全部。
4. 完成后会生成 `.jks` 文件。

> 🔒 **`.jks` / `.keystore` 属于私钥，绝对不要提交到 GitHub，也不要打进交付压缩包。**
> 本工程源码包里已经排除了 `keystore/` 目录，你把自己的证书放进去即可。

### 4.2 配置工程读取签名

编辑 `app/build.gradle.kts`，把 `signingConfigs` 里的占位改成你的证书信息：

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../keystore/release.keystore")  // 换成你的路径
            storePassword = "你的store密码"
            keyAlias = "workreport"
            keyPassword = "你的key密码"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false      // 先关掉，方便排查问题
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

> 官方更推荐的做法：不把密码写进 `build.gradle.kts`，改用 `local.properties` 或环境变量：
> ```kotlin
> storeFile = file(providers.gradleProperty("WR_KEYSTORE").get())
> storePassword = providers.gradleProperty("WR_KEYSTORE_PASS").get()
> keyAlias = providers.gradleProperty("WR_KEY_ALIAS").get())
> keyPassword = providers.gradleProperty("WR_KEY_PASS").get())
> ```
> 然后在 `~/.gradle/gradle.properties` 里写：
> ```properties
> WR_KEYSTORE=/绝对路径/release.keystore
> WR_KEYSTORE_PASS=你的store密码
> WR_KEY_ALIAS=workreport
> WR_KEY_PASS=你的key密码
> ```

### 4.3 打包

```bash
./gradlew clean assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

### 4.4 校验签名

```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

应显示与旧版 WebView 版**相同的证书 SHA-256**（这样才能覆盖安装、不丢数据）：

```
05453ccc8e8ddb0395f0e0b1bbcdd95dddaafc8a71a5215ed4605f35f72a07bf
```

### 4.5 覆盖安装到手机

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 五、发布到应用市场 / 内部测试

### Google Play（内部测试轨道）

1. AAB 格式：`./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
2. Play Console → 新建应用 → 上传 AAB → 内部测试 → 收集测试链接。
3. 版本号规则：每次上传必须 `versionCode` +1（当前 `versionCode = 7`）。

### 国内市场

- 各市场基本都要求 **64 位**（本工程已支持，`abiFilters` 未限制，arm64-v8a + armeabi-v7a + x86_64 都有）。
- 需要提供**软件著作权证书**和**隐私合规说明**。

---

## 六、常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| `SDK location not found` | 没配 SDK 路径 | 在 `local.properties` 写 `sdk.dir=/path/to/Android/Sdk` |
| `Unsupported class file major version` | Gradle 用了 JDK 21+ | `gradle.properties` 里加 `org.gradle.java.home=/path/to/jdk17` |
| `Failed to install... INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 装的 APK 签名和手机里已有的不一致 | 先卸载旧版再装；或沿用旧证书签名 |
| 首次生成后下载目录找不到文件 | Android 10+ 走 MediaStore | 到「文件管理 → 下载」查看，Android 8/9 需要存储权限 |
| Excel 打开显示「已损坏」 | 极端情况下的 XML 转义 | 跑 `./gradlew testDebugUnitTest` 验证 XlsxWriterTest |

---

## 七、代码结构速查

```
app/src/main/java/com/workreport/app/
├── MainActivity.kt          唯一 Activity
├── WorkReportApp.kt         Application
├── ServiceLocator.kt        轻量依赖容器
├── data/
│   ├── local/               Room（历史报告）+ SharedPreferences（设置）
│   ├── remote/ApiClient.kt  OkHttp（预留，当前不联网）
│   └── repository/          仓库层
├── domain/model/            数据模型
├── export/                  ★ Excel / 加密 ZIP 导出（核心）
│   ├── XlsxTemplates.kt     xlsx 各部件 XML 模板（逐字对齐旧版）
│   ├── XlsxWriter.kt        xlsx 生成
│   ├── EncryptedZipWriter.kt AES-256 加密 ZIP
│   ├── ReportNaming.kt      文件名 / 密码规则
│   └── ReportExporter.kt    导出编排 + 落盘
├── ui/                      Compose 界面（home / history / profile）
├── util/                    日期算法、拼音表、敏感词引擎、邮件应用探测
```
