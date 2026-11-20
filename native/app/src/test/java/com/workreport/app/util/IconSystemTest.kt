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
        val drawable = File(resRoot, "drawable")
        val existing = drawable.listFiles { f -> f.isFile && f.name.endsWith(".xml") }
            ?.map { it.name.removeSuffix(".xml") }?.toSet() ?: emptySet()
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
        assertTrue("底栏应有文字淡入淡出", code.contains("labelAlpha"))
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
            "缺少深色自适应背景 drawable-night/ic_launcher_background.xml",
            File(resRoot, "drawable-night/ic_launcher_background.xml").exists()
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
                "缺少深色版 mipmap-night-$d/ic_launcher.png",
                File(resRoot, "mipmap-night-$d/ic_launcher.png").exists()
            )
        }
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
