# 工作报告生成器 · 原生版（Native）

完全重写的 **纯原生 Android** 版本：Kotlin + Jetpack Compose，**不包含任何 WebView、HTML、JS 网页文件**。

> 当前版本 **v1.7.0**（正式版）。
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
| ~~`WRITE_EXTERNAL_STORAGE`~~ | ~~Android 8/9 写「下载」目录~~ | **v1.7.0 起已移除**：最低版本提到 Android 12 (API 31)，写「下载」目录一律走 MediaStore，全程免存储权限 |

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

## 六点五、液态玻璃底栏（v1.7.0 起）

底部导航从「占布局位置的一条实心色块」改成了**浮在内容之上的玻璃胶囊**。

| 文件 | 作用 |
|---|---|
| `ui/common/Glass.kt` | 玻璃核心：背景层录制、采样坐标、模糊层/清晰层拆分 |
| `ui/AnimatedBottomBar.kt` | 悬浮玻璃胶囊（含 v1.6.0 的指示条平移 / 图标缩放 / 文字淡入） |
| `ui/AppRoot.kt` | 去掉 `Scaffold`，底栏改为根 `Box` 叠加 |

### 为什么手写而不用第三方库
用 Compose 的 `GraphicsLayer` 把整屏内容录制成一层（`layer.record { drawContent() }`），
玻璃控件再从这层里按**坐标差平移采样**，最后用 Android 12 (API 31) 起的
`RenderEffect.createBlurEffect` 做高斯模糊。零第三方依赖，也不需要联网。

### 两个容易踩的坑
1. **`RenderEffect` 只能模糊自己这一层画的东西**。
   单纯给底栏挂 `renderEffect` 没用（模糊的是纯色 tint，等于没模糊），
   必须先把背景内容录进图层再采样。
2. **玻璃必须拆成「模糊层 + 清晰层」**。
   模糊作用在同一层里的所有内容上，边框和文字会被一起糊掉。

### 为什么底栏不再占布局位置
以前底栏占位置，一进二级页面底栏消失，内容区高度立刻变矮 64dp，
页面猛地往下跳一下。现在底栏浮在内容之上，**内容区高度全程不变**。
同样的坑还堵了两道防线：不再用 `Scaffold.bottomBar` 插槽、
不再用 `innerPadding.calculateBottomPadding()` 算底部避让（那个值会突变）。

`GlassBottomBarTest`（12 项）锁住以上全部约束。

## 七、图标体系（v1.6.0 起）

App 内**所有带图标的位置**（功能区、底部导航、操作按钮、顶栏 Logo、空状态、列表标签、
输入框标题）统一使用 `res/drawable/ic_*.xml` 里的 **VectorDrawable**，不再有 emoji，
也不再用 Material Icons。

| 规则 | 说明 |
|---|---|
| 单一色源 | 矢量里只写 `@color/icon_primary`，**不写 `android:tint`** —— 颜色由 Compose 的 `Icon(tint = MaterialTheme.colorScheme.primary)` 接管，自动跟随深浅色主题 |
| 层次感 | 设计稿的「深→浅」三档色阶改用**透明度**实现（`strokeAlpha` 1.0 / 0.66 / 0.40），既保住层次，又能整体换色 |
| 资源命名 | 只能用小写字母、数字、下划线（Android 限制），如 `ic_arrow_back_ios` |
| 状态栏图标 | `ic_notification.xml` 单独手写，必须是**白色实心剪影**（系统会做模板着色） |
| 桌面图标 | `mipmap-anydpi-v26/ic_launcher.xml`（自适应）+ 各密度传统 PNG + `mipmap-night-*` 深色版；因启动图标会被裁成圆形，采用**竖版堆叠**（标志在上、WorkReport 字样在下） |

图标源文件与生成脚本：

| 文件 | 作用 |
|---|---|
| `scripts/icons.py` | 全部图标的 SVG 定义（导航 / 功能区 / 操作 / 品牌） |
| `scripts/gen_vector_icons.py` | SVG → Android VectorDrawable（含色阶→透明度转换） |
| `scripts/gen_launcher_icons.py` | 桌面图标（自适应前景 + 各密度 PNG + 深色版） |
| `scripts/render_icon_set.py` | 渲染整套图标的实际观感（浅色/深色两套） |
| `scripts/check_drawable_refs.py` | 校验源码里的 `R.drawable.ic_*` 引用与资源文件一致 |

改完图标后跑 `./gradlew testDebugUnitTest` —— `IconSystemTest` 会拦住回归：
禁止再出现 emoji、禁止再使用 Material Icons、禁止在矢量里写死 tint、
要求全部引用都有资源、要求自绘底栏与页面过渡存在、要求桌面图标资源齐全。
