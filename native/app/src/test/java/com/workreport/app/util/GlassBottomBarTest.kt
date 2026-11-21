package com.workreport.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.7.0「液态玻璃底栏 + 二级页面交互重构」的防回归测试。
 *
 * 三个要守住的东西：
 *
 * 1. **玻璃是真的**：底栏必须用 `GraphicsLayer.record` + `RenderEffect` 做背景模糊，
 *    而不是只在纯色 tint 上挂个 renderEffect（那样模糊的是纯色，等于没模糊）。
 * 2. **进入二级页面不跳动**：底栏不再占布局位置，
 *    所以 AppRoot 里不能再用 Scaffold 的 bottomBar 插槽，
 *    也不能再用 `innerPadding.calculateBottomPadding()` 算底部避让（那个值会突变）。
 * 3. **玻璃采样坐标是对的**：采样偏移必须是「背景层原点 − 玻璃原点」，
 *    符号搞反了玻璃就会显示屏幕另一端的内容。
 */
class GlassBottomBarTest {

    private val srcRoot: String =
        File("src/main/java/com/workreport/app").absolutePath

    private fun read(path: String): String = File(srcRoot, path).readText()

    // ---------------- 1. 玻璃是真的 ----------------

    @Test
    fun `GlassSurface 必须用 GraphicsLayer 录制背景并做背景模糊`() {
        val code = read("ui/common/Glass.kt")

        assertTrue(
            "Glass.kt 应使用 rememberGraphicsLayer 录制背景层",
            code.contains("rememberGraphicsLayer"),
        )
        assertTrue(
            "Glass.kt 应调用 layer.record 录制内容",
            code.contains("layer.record"),
        )
        assertTrue(
            "GlassSurface 应通过 drawLayer 把背景层画进来",
            code.contains("drawLayer(backdrop.layer)"),
        )
        assertTrue(
            "玻璃必须用 RenderEffect 做真正的背景模糊",
            code.contains("RenderEffect.createBlurEffect"),
        )
        assertTrue(
            "玻璃的模糊层需要离屏合成，否则会糊到同层其它内容",
            code.contains("CompositingStrategy.Offscreen"),
        )
    }

    @Test
    fun `玻璃必须拆成模糊层和清晰层，否则边框和文字会被一起模糊`() {
        val code = read("ui/common/Glass.kt")
        assertTrue(
            "清晰层（边框/文字）不能和模糊层画在同一个 graphicsLayer 里",
            code.contains("清晰层"),
        )
        // 边框必须画在清晰层上
        assertTrue("玻璃清晰层应画边框", code.contains(".border(1.dp, palette.border, shape)"))
    }

    @Test
    fun `玻璃不应依赖第三方库`() {
        val code = read("ui/common/Glass.kt")
        val thirdParty = listOf(
            "chrisbanes.haze",
            "dev.chrisbanes",
            "com.github.sh.calvin.reorderable",
            "top.yukonga",
        )
        thirdParty.forEach { lib ->
            assertTrue("玻璃实现不应引入第三方库：$lib", !code.contains(lib))
        }
    }

    @Test
    fun `最低版本必须是 Android 12 以保证 RenderEffect 可用`() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(
            "minSdk 必须提到 31（Android 12），否则 RenderEffect 不可用",
            gradle.contains("minSdk = 31"),
        )
    }

    // ---------------- 2. 进入二级页面不跳动 ----------------

    @Test
    fun `AppRoot 不能再用 Scaffold 承载底栏`() {
        val appRoot = read("ui/AppRoot.kt")
        assertTrue(
            "AppRoot 不应再使用 Scaffold（它的 bottomBar 插槽会撑高内容区，导致切页跳动）",
            !appRoot.contains("Scaffold("),
        )
        assertTrue(
            "AppRoot 不能再用 innerPadding.calculateBottomPadding() 算底部避让（切页时这个值会突变）",
            !appRoot.contains("calculateBottomPadding"),
        )
        assertTrue(
            "AppRoot 应用根 Box 叠加悬浮底栏",
            appRoot.contains("AnimatedBottomBar("),
        )
    }

    @Test
    fun `底栏必须是悬浮的且不占布局位置`() {
        val code = read("ui/AnimatedBottomBar.kt")
        assertTrue(
            "底栏应使用 navigationBarsPadding 浮在系统导航条之上",
            code.contains("navigationBarsPadding"),
        )
        assertTrue(
            "底栏需要 visible 参数，用于二级页面淡出下沉",
            code.contains("visible: Boolean"),
        )
        assertTrue(
            "二级页面底栏应淡出",
            code.contains("barAlpha"),
        )
        assertTrue(
            "二级页面底栏应下沉",
            code.contains("barOffset"),
        )
    }

    @Test
    fun `底栏必须保留指示条平移与图标缩放动画`() {
        val code = read("ui/AnimatedBottomBar.kt")
        assertTrue("底栏应有指示条平移动画", code.contains("animateDpAsState"))
        assertTrue("底栏应有图标缩放动画", code.contains("animateFloatAsState"))
    }

    @Test
    fun `底栏去掉文字后必须保留无障碍标签`() {
        val code = read("ui/AnimatedBottomBar.kt")
        assertTrue(
            "底栏不再显示文字，但图标必须保留 contentDescription 供读屏朗读",
            code.contains("contentDescription = destination.label"),
        )
    }

    @Test
    fun `底栏点击必须有按下回弹动效`() {
        val code = read("ui/AnimatedBottomBar.kt")
        assertTrue(
            "底栏应监听按压状态（collectIsPressedAsState）",
            code.contains("collectIsPressedAsState"),
        )
        assertTrue(
            "底栏应有按下缩放动画（pressScale）",
            code.contains("pressScale"),
        )
        assertTrue(
            "底栏应提供 interactionSource 以接收按压反馈",
            code.contains("interactionSource"),
        )
    }

    @Test
    fun `底栏应该是窄胶囊并整体靠右对齐`() {
        val code = read("ui/AnimatedBottomBar.kt")
        assertTrue(
            "底栏宽度应由图标项宽累加得到（而不是通栏 fillMaxWidth）",
            code.contains("BAR_ITEM_WIDTH * destinations.size"),
        )
        assertTrue(
            "底栏应保留右侧边距常量",
            code.contains("BAR_END_MARGIN"),
        )

        val appRoot = read("ui/AppRoot.kt")
        assertTrue(
            "底栏在 AppRoot 里应靠右对齐（Alignment.BottomEnd）",
            appRoot.contains("Alignment.BottomEnd"),
        )
    }

    @Test
    fun `深色模式下底部不应露出浅色窗口底`() {
        // 坑（v1.8.0）：深色模式是 App 内手动切换的，themes.xml 里的 windowBackground
        // 写死浅色，NavHost 又因底部避让把内容顶起来，
        // 系统导航条那条没人绘制的区域就露出浅色 → 底部一条白杠。
        val appRoot = read("ui/AppRoot.kt")
        assertTrue(
            "AppRoot 根容器应铺满主题背景色，填掉底部没人绘制的区域",
            appRoot.contains("MaterialTheme.colorScheme.background"),
        )

        val theme = read("ui/theme/Theme.kt")
        assertTrue(
            "窗口底色应跟随 App 主题（不能写死浅色）",
            theme.contains("setBackgroundDrawable"),
        )
        assertTrue(
            "应关闭系统导航条的对比度遮罩",
            theme.contains("isNavigationBarContrastEnforced"),
        )

        val activity = read("MainActivity.kt")
        assertTrue(
            "enableEdgeToEdge 必须显式指定透明的系统栏样式（默认参数按系统深浅色套浅色 scrim）",
            activity.contains("SystemBarStyle.auto"),
        )
    }

    @Test
    fun `深色模式启动不应闪白`() {
        // 坑（v1.8.0 修）：App 的深色模式是「App 内手动切换」的，
        // 系统的 values-night 跟随的是**系统**深浅色，跟不到 App 里的选择。
        // 所以必须有一份运行时可切换的深色主题，启动时按 ThemeMode 选。
        val themes = File("src/main/res/values/themes.xml").readText()
        assertTrue(
            "应提供深色版窗口主题（窗口底色跟随深色，避免启动闪白）",
            themes.contains("Theme.WorkReport.Dark"),
        )
        assertTrue(
            "深色版窗口底色应指向深色底色资源",
            themes.contains("@color/page_bg_dark"),
        )
        assertTrue(
            "应提供深色版启动图主题",
            themes.contains("Theme.WorkReport.Splash.Dark"),
        )

        val activity = read("MainActivity.kt")
        assertTrue(
            "启动时应按当前主题模式切换启动图主题",
            activity.contains("Theme_WorkReport_Splash_Dark"),
        )
    }

    @Test
    fun `玻璃要够透才能看见下方内容`() {        val code = read("ui/AnimatedBottomBar.kt")
        assertTrue(
            "玻璃模糊半径应显著加大（>= 24dp）",
            code.contains("blurRadius = 26.dp"),
        )
        // 遮盖越少，下方内容越透。
        // 深色分支写在前面，所以第一个 tint 是深色值。
        val tints = Regex("tint = Color\\.White\\.copy\\(alpha = ([0-9.]+)f\\)")
            .findAll(code)
            .map { it.groupValues[1].toFloat() }
            .toList()
        assertTrue("应能解析出两套玻璃的 tint 值，实际：$tints", tints.size >= 2)
        assertTrue(
            "深色模式玻璃的遮盖不透明度应低于 0.1（否则是块灰板，看不见下方内容），实际：${tints[0]}",
            tints[0] < 0.1f,
        )
        assertTrue(
            "浅色模式玻璃的遮盖不透明度应低于 0.5（保留通透感），实际：${tints[1]}",
            tints[1] < 0.5f,
        )
    }

    @Test
    fun `一级页面必须给悬浮胶囊留出底部空间`() {
        val capsuleSpace = read("ui/AnimatedBottomBar.kt")
        assertTrue(
            "应定义胶囊占位高度常量",
            capsuleSpace.contains("CAPSULE_SPACE"),
        )
        listOf(
            "ui/home/HomeScreen.kt" to "首页",
            "ui/history/HistoryScreen.kt" to "历史报告",
            "ui/profile/ProfileScreen.kt" to "我的",
        ).forEach { (path, name) ->
            assertTrue(
                "$name 的列表底部应留出胶囊空间",
                read(path).contains("CAPSULE_SPACE"),
            )
        }
    }

    @Test
    fun `二级页面不应留胶囊空间`() {
        // 二级页面底栏是淡出下沉的，内容贴着底栏才不空
        val profilePages = read("ui/profile/ProfilePages.kt")
        assertTrue(
            "二级页面不应引入胶囊占位",
            !profilePages.contains("CAPSULE_SPACE"),
        )
    }

    @Test
    fun `键盘避让仍然只在 AppRoot 做一次`() {
        val appRoot = read("ui/AppRoot.kt")
        assertTrue("AppRoot 应统一处理输入法避让", appRoot.contains("BottomAvoidance.compute"))
        // 页面里不允许再叠 imePadding（只看真正的调用，注释里提到这个词不算）
        listOf(
            "ui/home/HomeScreen.kt",
            "ui/history/HistoryScreen.kt",
            "ui/profile/ProfileScreen.kt",
            "ui/profile/ProfilePages.kt",
        ).forEach { path ->
            val calls = read(path).lineSequence()
                .map { it.trim() }
                .filter { it.startsWith(".") && it.contains("imePadding") }
                .toList()
            assertTrue(
                "$path 不应再叠 imePadding()（会重复避让，键盘上方多出空白带），实际调用：$calls",
                calls.isEmpty(),
            )
        }
    }

    // ---------------- 3. 采样坐标 ----------------

    @Test
    fun `玻璃采样偏移方向不能反`() {
        // 背景层原点 - 玻璃原点 = 玻璃原点 - 背景原点（符号相反）
        // 背景层在 (0,0)，玻璃在 (0,200)：偏移应为 -200，把玻璃下方的内容对上来
        val offset = com.workreport.app.ui.common.GlassSampling.offset(
            backdropX = 0f,
            backdropY = 0f,
            glassX = 0f,
            glassY = 200f,
        )
        assertEquals("X 方向偏移", 0f, offset.x, 0.001f)
        assertEquals("Y 方向偏移应为背景层原点减玻璃原点", -200f, offset.y, 0.001f)
    }

    @Test
    fun `玻璃采样偏移在玻璃位于背景层上方时同样正确`() {
        val offset = com.workreport.app.ui.common.GlassSampling.offset(
            backdropX = 0f,
            backdropY = 500f,
            glassX = 0f,
            glassY = 200f,
        )
        assertEquals("Y 方向偏移应为 300", 300f, offset.y, 0.001f)
    }
}
