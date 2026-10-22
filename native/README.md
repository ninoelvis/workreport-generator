# 工作报告生成器 · 原生版（Native）

完全重写的 **纯原生 Android** 版本：Kotlin + Jetpack Compose，**不包含任何 WebView、HTML、JS 网页文件**。

> ⚠️ 这是**测试版**（`1.3.0-beta1`），用于替换验证。确认无误后再决定是否上传 GitHub / 发布正式版。
> 包名与签名与旧版一致（`com.workreport.app`），因此**可以直接覆盖安装**，本地数据不会丢。

---

## 一、整体结构

```
native/
├── settings.gradle.kts          # 仓库源（国内镜像优先 + 官方源兜底）
├── build.gradle.kts             # 顶层插件声明
├── gradle.properties
├── gradle/libs.versions.toml    # 版本目录（依赖版本集中管理）
├── gradle/wrapper/              # Gradle Wrapper
├── local.properties             # 本地 SDK 路径（不入库）
├── scripts/                     # 辅助脚本
│   ├── dump_xlsx_templates.py   # 从旧版 xlsx 提取 XML 模板
│   ├── gen_app_logo_vd.py       # 生成 App 内顶栏 VectorDrawable
│   └── svg2vd.py                # 通用 SVG → VectorDrawable 转换
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/workreport/app/
        │   │   ├── WorkReportApp.kt          # Application
        │   │   ├── MainActivity.kt           # 唯一 Activity
        │   │   ├── ServiceLocator.kt         # 轻量依赖容器
        │   │   ├── data/
        │   │   │   ├── local/
        │   │   │   │   ├── ReportDatabase.kt # Room 数据库
        │   │   │   │   ├── ReportDao.kt      # DAO
        │   │   │   │   ├── ReportEntity.kt   # 历史报告实体
        │   │   │   │   └── SettingsStore.kt  # SharedPreferences 设置
        │   │   │   ├── remote/
        │   │   │   │   └── ApiClient.kt      # OkHttp（后续在线功能预留）
        │   │   │   └── repository/
        │   │   │       └── ReportRepository.kt
        │   │   ├── domain/model/SensitiveWord.kt
        │   │   ├── export/
        │   │   │   ├── XlsxTemplates.kt      # xlsx 各部件 XML 模板
        │   │   │   ├── XlsxWriter.kt         # xlsx 生成器
        │   │   │   ├── EncryptedZipWriter.kt # AES-256 加密 ZIP
        │   │   │   └── ReportExporter.kt     # 导出编排
        │   │   ├── ui/
        │   │   │   ├── AppRoot.kt            # 底部导航 + NavHost
        │   │   │   ├── common/Components.kt  # 公共组件
        │   │   │   ├── home/                 # 首页 Tab
        │   │   │   ├── history/              # 历史报告 Tab
        │ │   │   │   └── profile/             # 我的 Tab
        │   │   ├── theme/                    # 配色 / 主题
        │   │   └── util/
        │   │       ├── DateUtils.kt          # 日期算法（与旧版逐字一致）
        │   │       ├── PinyinTable.kt        # 汉字→拼音首字母表（自动生成）
        │   │       ├── SensitiveWordEngine.kt # 敏感词替换
        │   │       └── MailHelper.kt          # 邮箱应用探测与带附件分享
        │   └── res/                          # 图标、颜色、主题、FileProvider 路径
        └── test/java/com/workreport/app/export/XlsxWriterTest.kt
```

## 二、页面流程

```
                    ┌──────────────────────────────┐
                    │      启动 MainActivity       │
                    │  WorkReportTheme(themeMode)  │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │   AppRoot (Scaffold + 底部导航) │
                    └──────────────┬───────────────┘                                   │
        ┌──────────────────────────┼──────────────────────────┐
        │                          │                          │
┌───────▼────────┐      ┌──────────▼─────────┐      ┌─────────▼────────┐
│  首页 Home      │      │ 历史报告 History    │      │  我的 Profile     │
│                │      │                    │      │                  │
│ 姓名/时间/标题  │      │ 搜索框(按文件名)     │      │ 姓名设置          │
│ 工作记录(增删)  │      │ 列表卡片            │      │ 主题模式          │
│ 已完成/计划     │      │  ├ 再次下载         │      │ 敏感词库(增删改)  │
│ 实时预览        │      │  ├ 发送邮件(带附件)  │      │ 清除数据          │
│ [生成加密ZIP]   │      │  └ 删除             │      │ 关于              │
└───────┬────────┘      └──────────┬─────────┘      └─────────┬────────┘
        │                          │                          │
        │ 生成成功                  │ 二次下载 → MediaStore     │ 保存词库 → SharedPreferences
        ▼                          │ 二次发邮件 → 邮件App       │ 切主题 → 立即重建
┌───────────────┐                  │                           │
│ 写入 ZIP      │                  └───────────────────────────┘
│ (AES-256)     │
│ ├ 应用私有目录 │
│ └ 系统「下载」  │
└───────┬───────┘
        │ 记入 Room → 历史报告列表
        ▼
```

## 三、与旧版的一致性保证

| 项目 | 规则 | 实现位置 |
|---|---|---|
| ZIP 文件名 | `{姓名}-{YYYY}年{M}月第{N}周工作报告.zip` | `ReportExporter.export()` |
| ZIP 内文件 | 同名 `.xlsx` | 同上 |
| 解压密码 | 本周周四 `yyyyMMdd` | `DateUtils.fmtDate8()` |
| 加密方式 | AES-256 + HMAC-SHA1（WinZip AE-2） | `EncryptedZipWriter` |
| Excel 工作表名 | `工作报告` | `XlsxTemplates.WORKBOOK_XML` |
| Excel 样式 | 宋体16标题 / 黑体10表头+B5C6EA填充 / 仿宋9-11数据，全部细黑边框 | `XlsxTemplates.STYLES_XML` |
| Excel 合并 | A1:F1、A3:F4 六列 | `XlsxWriter.buildSheetXml()` |
| Excel 列宽 | 20.71484375 / 29.8125 / 26.36328125 / 19.97265625 / 21.46484375 / 13.83203125 | 同上 |
| Excel 行高 | 51 / 28 / 333 / 82 | 同上 |
| 正文格式 | `完成共N项工作内容：` / `下周预计共N项工作任务：` | `SensitiveWordEngine` |
| 敏感词脱敏 | 替换为小写拼音首字母 | `SensitiveWordEngine` |
| 月内周数 | 从当月第一个周日起数 | `DateUtils.getWeekOfMonth()` |

`app/src/test/java/.../XlsxWriterTest.kt` 会对上述结构做断言。

## 四、权限

| 权限 | 用途 | 备注 |
|---|---|---|
| `INTERNET` | **仅预留** | 当前版本代码不会发起任何网络请求 |
| `WRITE_EXTERNAL_STORAGE` (maxSdkVersion=28) | Android 8/9 写「下载」目录 | Android 10+ 走 MediaStore，免权限 |

未申请相机、位置、通讯录、麦克风等任何无关权限。

## 五、数据存储

| 内容 | 位置 |
|---|---|
| 历史报告 | Room 数据库 `workreport.db` |
| 姓名 / 主题 / 敏感词库 / 默认邮件应用 | SharedPreferences `workreport_settings` |
| 生成的加密 ZIP | `filesDir/reports/`（FileProvider 分享用） |
| 生成的加密 ZIP 副本 | 系统「下载」目录 |

## 六、构建

```bash
# 需要 JDK 17 + Android SDK（compileSdk 35 / build-tools 34）
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 正式包（用 keystore/release.keystore 签名）
```
