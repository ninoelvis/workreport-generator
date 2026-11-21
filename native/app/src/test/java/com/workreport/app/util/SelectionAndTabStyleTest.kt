package com.workreport.app.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v2.0.1「多选返回手势 + 顶部 Tab 样式」的防回归测试。
 *
 * 两个坑：
 *
 * 1. **多选状态下按返回手势，底部导航栏会永久消失**。
 *    根因：多选标志是全局的，之前只有「取消」按钮和批量操作完成会清它，
 *    系统返回手势 / 切换 Tab 都不走那条路 → 标志卡在 true → 底栏永久没了。
 *    修法（方案 D）：① 历史页用 `BackHandler` 拦返回手势 → 只退出多选；
 *    ② AppRoot 兜底 —— 只要不在历史报告页，多选状态一律清掉。
 *    另外**真相源必须只有一份**（`ServiceLocator.selectionMode`），
 *    之前 `_uiState.selectionMode` 和全局标志是两份，清一处另一处还留着。
 *
 * 2. 顶部「列表 / 统计」的样式必须和 App 已确立的视觉语言一致
 *    （v1.8.0 底栏的指示条平移），不能是 App 里唯一的异类。
 */
class SelectionAndTabStyleTest {

    private val srcRoot: String =
        File("src/main/java/com/workreport/app").absolutePath

    private fun read(path: String): String = File(srcRoot, path).readText()

    // ---------------- 1. 返回手势 ----------------

    @Test
    fun `多选时返回手势必须只退出多选`() {
        val screen = read("ui/history/HistoryScreen.kt")
        assertTrue(
            "多选时要用 BackHandler 拦返回手势",
            screen.contains("BackHandler"),
        )
        assertTrue(
            "BackHandler 只在多选时生效",
            screen.contains("BackHandler(enabled = selectionMode)"),
        )
        assertTrue(
            "返回时应退出多选而不是离开页面",
            screen.contains("viewModel.exitSelection()"),
        )
    }

    @Test
    fun `离开历史报告页必须自动清掉多选状态`() {
        val appRoot = read("ui/AppRoot.kt")
        assertTrue(
            "AppRoot 必须监听路由变化做兜底清理",
            appRoot.contains("LaunchedEffect(currentRoute)"),
        )
        assertTrue(
            "不在历史报告页时要把多选状态清掉",
            appRoot.contains("ServiceLocator.setSelectionMode(false)"),
        )
        assertTrue(
            "底栏的可见性要同时考虑当前路由与多选状态",
            appRoot.contains("showBottomBar && (!selectionMode)"),
        )
    }

    @Test
    fun `多选状态只能有一个真相源`() {
        val vm = read("ui/history/HistoryViewModel.kt")
        assertTrue(
            "ViewModel 应直接暴露全局的那一份多选状态",
            vm.contains("val selectionMode: StateFlow<Boolean> = ServiceLocator.selectionMode"),
        )
        assertTrue(
            "UiState 里不能再存一份 selectionMode（否则会不一致）",
            !vm.contains("val selectionMode: Boolean = false"),
        )
        // ServiceLocator 是唯一的真相源
        val locator = read("ServiceLocator.kt")
        assertTrue(
            "ServiceLocator 持有唯一的多选状态",
            locator.contains("val selectionMode: StateFlow<Boolean>"),
        )
    }

    @Test
    fun `退出多选必须同时清全局标志和选中项`() {
        val vm = read("ui/history/HistoryViewModel.kt")
        val exit = vm.substringAfter("fun exitSelection()").substringBefore("\n    }")
        assertTrue(
            "退出多选要清掉全局标志",
            exit.contains("ServiceLocator.setSelectionMode(false)"),
        )
        assertTrue(
            "退出多选要清掉已选中的 id",
            exit.contains("selectedIds = emptySet()"),
        )
    }

    // ---------------- 2. 顶部 Tab 样式 ----------------

    @Test
    fun `顶部Tab必须是下划线加指示条平移`() {
        val tab = read("ui/common/UnderlineTabBar.kt")
        assertTrue(
            "Tab 要有指示条",
            tab.contains("TAB_INDICATOR_WIDTH"),
        )
        assertTrue(
            "指示条位置要用动画平移（和底栏同一套）",
            tab.contains("animateDpAsState") && tab.contains("indicatorX"),
        )
        assertTrue(
            "平移要用弹簧动画",
            tab.contains("Spring.DampingRatioMediumBouncy"),
        )
        assertTrue(
            "文字颜色要过渡",
            tab.contains("animateColorAsState"),
        )
    }

    @Test
    fun `顶部Tab不能是App里唯一的异类样式`() {
        val tab = read("ui/common/UnderlineTabBar.kt")
        // 旧的「灰底槽 + 白块」分段控件要去掉
        assertTrue(
            "不应再用 surfaceVariant 底槽那种分段控件",
            !tab.contains("surfaceVariant"),
        )
        assertTrue(
            "不应再有选中块背景（改成下划线）",
            !tab.contains("if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent"),
        )
    }

    @Test
    fun `指示条宽度要和底栏一致`() {
        val tab = read("ui/common/UnderlineTabBar.kt")
        val bar = read("ui/AnimatedBottomBar.kt")
        assertTrue(
            "顶部指示条宽度常量应为 16.dp",
            tab.contains("val TAB_INDICATOR_WIDTH = 16.dp"),
        )
        assertTrue(
            "底栏指示条宽度也是 16.dp（两者用同一套视觉语言）",
            bar.contains("INDICATOR_WIDTH = 16.dp"),
        )
    }
}
