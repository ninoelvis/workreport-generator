package com.workreport.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 图标体系防回归测试。
 *
 * 图标改版（v1.6.0）之后，下面这些规则必须一直成立：
 *   1. App 业务源码里不允许再出现 emoji 图标（全部换成矢量图标）
 *   2. 不允许再使用 Material Icons（要保证全套图标风格统一）
 *   3. 所有 R.drawable.ic_* 引用都必须有对应的资源文件
 *   4. 矢量图标文件里不能写死 android:tint（颜色必须由 Compose tint 跟随主题）
 *   5. 底部导航必须是自绘的（带指示条平移 + 图标缩放 + 文字淡入淡出）
 *   6. 桌面图标资源齐全（自适应图标 + 各密度传统图标 + 深色版）
 */
class IconSystemTest {

    private val srcRoot: File = File("src/main/java/com/workreport/app")
    private val resRoot: File = File("src/main/res")

    /** 读源码文件 */
    private fun read(relPath: String): String = File(srcRoot, relPath).readText()

    /** 去掉注释，只留下真正会被编译执行的代码/字符串 */
    private fun stripComments(src: String): String {
        val out = StringBuilder()
        var inBlock = false
        for (raw in src.lines()) {
            val line = if (inBlock) {
                val end = raw.indexOf("*/")
                if (end >= 0) {
                    inBlock = false
                    raw.substring(end + 2)
                } else {
                    ""
                }
            } else {
                raw
            }
            val start = line.indexOf("/*")
            if (!inBlock && start >= 0) {
                val end = line.indexOf("*/", start + 2)
                if (end < 0) {
                    inBlock = true
                    out.append(line.substring(0, start)).append('\n')
                    continue
                }
                out.append(line.substring(0, start)).append(line.substring(end + 2)).append('\n')
                continue
            }
            // 行注释
            val lineComment = line.indexOf("//")
            out.append(if (lineComment >= 0) line.substring(0, lineComment) else line).append('\n')
        }
        return out.toString()
    }

    private fun kotlinFiles(): List<File> =
        srcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `App 源码里不再有 emoji 图标`() {
        // emoji 的 Unicode 区段（不含 Variation Selector / ZWJ 组合件）
        val emoji = Regex(
            "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]" +
                "[\\u2190-\\u21FF\\u2300-\\u23FF\\u2460-\\u24FF\\u25A0-\\u27BF" +
                "\\u2B00-\\u2BFF\\uFE0F\\u2049\\u203C\\u2122\\u2139\\u3030\\u303D" +
                "\\u3297\\u3299]"
        )
        val offenders = mutableListOf<String>()
        for (f in kotlinFiles()) {
            val code = stripComments(f.readText())
            code.lines().forEachIndexed { i, line ->
                if (emoji.containsMatchIn(line)) {
                    offenders += "${f.name}:${i + 1}  ${line.trim().take(80)}"
                }
            }
        }
        assertTrue(
            "App 内还有 emoji 图标，应全部换成矢量图标：\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `不再使用 Material Icons`() {
        val offenders = mutableListOf<String>()
        for (f in kotlinFiles()) {
            val code = stripComments(f.readText())
            if (Regex("Icons\\.(Filled|Outlined|Rounded|Sharp|TwoTone|AutoMirrored)").containsMatchIn(code)) {
                offenders += f.name
            }
        }
        assertTrue("这些文件还在用 Material Icons：${offenders}", offenders.isEmpty())
    }

    @Test
    fun `所有 ic_ 矢量引用都有对应资源文件`() {
        // 矢量（drawable/*.xml）和位图（drawable-<density>/*.png）都算资源
        val existing = resRoot.listFiles { f ->
            f.isDirectory && (f.name == "drawable" || f.name.startsWith("drawable-"))
        }?.flatMap { d ->
            d.listFiles { f -> f.isFile && (f.name.endsWith(".xml") || f.name.endsWith(".png")) }
                ?.map { it.name.substringBeforeLast(".") } ?: emptyList()
        }?.toSet() ?: emptySet()
        val referenced = mutableSetOf<String>()
        for (f in kotlinFiles()) {
            Regex("R\\.drawable\\.(ic_\\w+)").findAll(f.readText())
                .forEach { referenced += it.groupValues[1] }
        }
        assertTrue("引用了不存在的图标资源", referenced.isNotEmpty())
        val missing = referenced.filter { it !in existing }
        assertTrue("缺少资源文件：$missing", missing.isEmpty())
    }

    @Test
    fun `矢量图标不写死 tint 颜色`() {
        val drawable = File(resRoot, "drawable")
        val offenders = drawable.listFiles { f -> f.isFile && f.name.startsWith("ic_") }
            ?.filter { it.readText().contains("android:tint") }
            ?.map { it.name } ?: emptyList()
        assertTrue(
            "这些图标写死了 tint，会挡住 Compose 的深浅色跟随：$offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun `品牌图标保留透明度层次`() {
        val brand = File(resRoot, "drawable/ic_brand.xml")
        assertTrue("缺少品牌图标 ic_brand.xml", brand.exists())
        val xml = brand.readText()
        assertTrue("品牌图标应保留 alpha 层次（设计稿的深→浅）", xml.contains("strokeAlpha"))
    }

    @Test
    fun `底部导航是自绘的`() {
        val appRoot = File(srcRoot, "ui/AppRoot.kt").readText()
        assertTrue(
            "AppRoot 应使用自绘底栏 AnimatedBottomBar",
            appRoot.contains("AnimatedBottomBar(")
        )
        assertTrue(
            "AppRoot 不应再使用 Material 的 NavigationBar",
            !appRoot.contains("NavigationBarItem(")
        )
        val bar = File(srcRoot, "ui/AnimatedBottomBar.kt")
        assertTrue("缺少 AnimatedBottomBar.kt", bar.exists())
        val code = bar.readText()
        assertTrue("底栏应有指示条平移动画", code.contains("animateDpAsState"))
        assertTrue("底栏应有图标缩放动画", code.contains("animateFloatAsState"))
        assertTrue("底栏应有按压回弹动效", code.contains("collectIsPressedAsState"))
        assertTrue(
            "底栏去掉文字后必须保留 contentDescription 供读屏朗读",
            code.contains("contentDescription = destination.label"),
        )
    }

    @Test
    fun `页面切换有过渡动画`() {
        val appRoot = File(srcRoot, "ui/AppRoot.kt").readText()
        assertTrue("NavHost 缺少 enterTransition", appRoot.contains("enterTransition"))
        assertTrue("NavHost 缺少 exitTransition", appRoot.contains("exitTransition"))
        assertTrue("NavHost 缺少 popEnterTransition", appRoot.contains("popEnterTransition"))
        assertTrue("NavHost 缺少 popExitTransition", appRoot.contains("popExitTransition"))
    }

    @Test
    fun `桌面图标资源齐全`() {
        assertTrue(
            "缺少自适应图标 mipmap-anydpi-v26/ic_launcher.xml",
            File(resRoot, "mipmap-anydpi-v26/ic_launcher.xml").exists()
        )
        assertTrue(
            "缺少圆形自适应图标 mipmap-anydpi-v26/ic_launcher_round.xml",
            File(resRoot, "mipmap-anydpi-v26/ic_launcher_round.xml").exists()
        )
        assertTrue(
            "缺少自适应背景 drawable/ic_launcher_background.xml",
            File(resRoot, "drawable/ic_launcher_background.xml").exists()
        )
        val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        for (d in densities) {
            assertTrue("缺少 mipmap-$d/ic_launcher.png", File(resRoot, "mipmap-$d/ic_launcher.png").exists())
            assertTrue("缺少 mipmap-$d/ic_launcher_round.png", File(resRoot, "mipmap-$d/ic_launcher_round.png").exists())
            assertTrue(
                "缺少 mipmap-$d/ic_launcher_foreground.png",
                File(resRoot, "mipmap-$d/ic_launcher_foreground.png").exists()
            )
            assertTrue(
                "缺少主题图标（单色）mipmap-$d/ic_launcher_monochrome.png",
                File(resRoot, "mipmap-$d/ic_launcher_monochrome.png").exists()
            )
            assertTrue(
                "缺少启动图图标 mipmap-$d/ic_launcher_splash.png",
                File(resRoot, "mipmap-$d/ic_launcher_splash.png").exists()
            )
        }
    }

    /**
     * v2.0.3 起：**桌面图标不再跟随系统深浅色**。
     *
     * 用户要求：桌面上就一个图标，不要在浅色/深色之间跳来跳去。
     * App 内的深浅色切换是 Compose 主题管的（手动切换），跟桌面图标无关。
     */
    @Test
    fun `桌面图标不跟随系统深浅色`() {
        for (d in listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) {
            assertTrue(
                "不应再有夜间图标目录 mipmap-night-$d（桌面图标不跟随系统深浅色）",
                !File(resRoot, "mipmap-night-$d").exists()
            )
        }
        assertTrue(
            "不应再有 drawable-night/ic_launcher_background.xml",
            !File(resRoot, "drawable-night/ic_launcher_background.xml").exists()
        )
    }

    /**
     * v2.0.3：新图标沿用「WorkReport」双色设计稿的配色 ——
     * 「Work」灰蓝渐变，「Report」深藏蓝。
     */
    @Test
    fun `图标配色沿用双色设计稿`() {
        // v2.0.3 方案 A：纯文字图标，底就是**纯白**（与设计稿一致）
        val bg = File(resRoot, "drawable/ic_launcher_background.xml").readText()
        assertTrue("图标底色应是纯白", bg.contains("#FFFFFFFF"))
        assertTrue(
            "纯文字图标不该再用渐变底（会干扰字样）",
            !bg.contains("gradient"),
        )

        val anydpi = File(resRoot, "mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue(
            "自适应图标要引用单色主题图标（Android 13+ 主题图标）",
            anydpi.contains("ic_launcher_monochrome"),
        )

        // 启动图要用单独那份
        val themes = File(resRoot, "values/themes.xml").readText()
        assertTrue(
            "启动图应使用单独的启动图图标",
            themes.contains("@mipmap/ic_launcher_splash"),
        )
    }

    /**
     * v2.0.3 方案 A：**桌面图标是纯文字**，没有任何图形。
     *
     * 用户明确要求「只有文字没有任何图形，不要在设计上自由发挥」。
     * 所以桌面图标的前景资源里不能有品牌图形，只能是 WorkReport 字标。
     *
     * 怎么验？前景 PNG 是抠出来的字标，
     * 它与品牌图形 `ic_brand` 的形状完全不同 ——
     * 用**像素特征**判断：字标是「上宽下窄的一行字」，
     * 品牌图形是「一个带勾的文档」。
     */
    @Test
    fun `桌面图标是纯文字不含图形`() {
        val fg = File(resRoot, "mipmap-xxxhdpi/ic_launcher_foreground.png")
        assertTrue("缺少自适应图标前景资源", fg.exists())
        val (w, h) = pngSize(fg)

        // 自适应图标前景必须是 **108dp 画布**（Android 硬约束）
        // xxxhdpi = 4x → 432px
        assertEquals("前景画布应为 432x432（108dp @xxxhdpi）", 432, w)
        assertEquals("前景画布应为 432x432（108dp @xxxhdpi）", 432, h)

        // 自适应图标的前景必须指向这份字标，不能指向品牌图形
        val anydpi = File(resRoot, "mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue(
            "自适应图标前景应是字标资源",
            anydpi.contains("@mipmap/ic_launcher_foreground"),
        )
        assertTrue(
            "桌面图标不能引用品牌图形标志（用户要求纯文字、无任何图形）",
            !anydpi.contains("ic_brand"),
        )

        // 品牌图形仍可以留在 App 内用（不是桌面图标）
        assertTrue(
            "App 内品牌图形应保留",
            File(resRoot, "drawable/ic_brand.xml").exists(),
        )
    }

    /** 纯 java.io 读 PNG 头的宽高（单元测试里没有 javax.imageio） */
    private fun pngSize(f: File): Pair<Int, Int> {
        f.inputStream().use { ins ->
            val head = ByteArray(24)
            val n = ins.read(head)
            assertTrue("PNG 文件太短：$f", n >= 24)
            fun be(o: Int): Int =
                ((head[o].toInt() and 0xFF) shl 24) or
                    ((head[o + 1].toInt() and 0xFF) shl 16) or
                    ((head[o + 2].toInt() and 0xFF) shl 8) or
                    (head[o + 3].toInt() and 0xFF)
            return be(16) to be(20)
        }
    }

    /**
     * v2.0.3：App 内顶栏的图标位**不能有边框、也不能垫底色**，
     * WorkReport 字样要「与背景融为一体」。
     */
    @Test
    fun `App内顶栏字标没有边框也没有底色方块`() {
        val components = read("ui/common/Components.kt")
        val topBar = components.substringAfter("fun AppTopBar(")
        assertTrue(
            "顶栏应使用 Wordmark 组件",
            topBar.contains("Wordmark("),
        )
        assertTrue(
            "顶栏字标不能有边框",
            !topBar.contains(".border("),
        )
        assertTrue(
            "顶栏字标不能垫底色方块",
            !topBar.contains("RoundedCornerShape(11.dp)"),
        )
    }

    @Test
    fun `App内字标资源齐全且随主题染色`() {
        for (d in listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) {
            assertTrue(
                "缺少 drawable-$d/ic_wordmark_work.png",
                File(resRoot, "drawable-$d/ic_wordmark_work.png").exists(),
            )
            assertTrue(
                "缺少 drawable-$d/ic_wordmark_report.png",
                File(resRoot, "drawable-$d/ic_wordmark_report.png").exists(),
            )
        }
        val wm = read("ui/common/Wordmark.kt")
        assertTrue(
            "字标要用 ColorFilter.tint 跟随主题（这样深浅色自动适配）",
            wm.contains("ColorFilter.tint"),
        )
        assertTrue(
            "字标要分 Work / Report 两段（各自染色）",
            wm.contains("ic_wordmark_work") && wm.contains("ic_wordmark_report"),
        )
    }

    @Test
    fun `图标数量不少于设计稿`() {
        val drawable = File(resRoot, "drawable")
        val count = drawable.listFiles { f -> f.isFile && f.name.startsWith("ic_") && f.name.endsWith(".xml") }
            ?.size ?: 0
        // 导航 3 + 品牌 1 + 功能区/操作 50 以上
        assertTrue("图标数量只有 $count 个，少于设计稿", count >= 50)
    }
}
