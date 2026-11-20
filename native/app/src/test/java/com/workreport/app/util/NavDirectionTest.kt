package com.workreport.app.util

import com.workreport.app.ui.TopLevelDestination
import com.workreport.app.ui.navDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 页面切换方向的单元测试。
 *
 * 这是 v1.6.0 的一个真机反馈：底栏三个 Tab 切换时动画方向是反的 ——
 * 从「我的」切到「历史报告」（往左退），新页面却从右边压过来。
 *
 * 根因：底栏 Tab 用 `navigate + popUpTo`，而 navigation-compose 的
 * `ComposeNavigator.navigate()` 在最后把 `isPop` 重置为 false，
 * 所以 Tab 切换永远走 enterTransition，方向被写死成「从右滑入」。
 */
class NavDirectionTest {

    private val home = TopLevelDestination.HOME.route      // 索引 0
    private val history = TopLevelDestination.HISTORY.route  // 索引 1
    private val profile = TopLevelDestination.PROFILE.route  // 索引 2
    private val subPage = "profile/name"                      // 非 Tab 页

    @Test
    fun `向右切是向前`() {
        // 首页 → 历史报告 → 我的
        assertEquals(1, navDirection(home, history))
        assertEquals(1, navDirection(history, profile))
        assertEquals(1, navDirection(home, profile))
    }

    @Test
    fun `向左切是向后`() {
        // ★ 用户反馈的场景：我的 → 历史报告，方向必须是「后退」
        assertEquals(-1, navDirection(profile, history))
        assertEquals(-1, navDirection(history, home))
        assertEquals(-1, navDirection(profile, home))
    }

    @Test
    fun `来回切换方向互为相反`() {
        assertEquals(-navDirection(profile, history), navDirection(history, profile))
        assertEquals(-navDirection(history, home), navDirection(home, history))
    }

    @Test
    fun `同一页面不做位移`() {
        assertEquals(0, navDirection(home, home))
        assertEquals(0, navDirection(history, history))
        assertEquals(0, navDirection(subPage, subPage))
    }

    @Test
    fun `首次进入没有位移`() {
        // 没有来源页 → 不做横向位移，只淡入
        assertEquals(0, navDirection(null, home))
        assertEquals(0, navDirection(null, history))
    }

    @Test
    fun `进子页面是向前`() {
        assertEquals(1, navDirection(profile, subPage))
        assertEquals(1, navDirection(home, subPage))
    }

    @Test
    fun `从子页面返回是向后`() {
        assertEquals(-1, navDirection(subPage, profile))
        assertEquals(-1, navDirection(subPage, home))
    }

    @Test
    fun `三个 Tab 的顺序是首页历史我的`() {
        assertEquals(0, TopLevelDestination.entries.indexOf(TopLevelDestination.HOME))
        assertEquals(1, TopLevelDestination.entries.indexOf(TopLevelDestination.HISTORY))
        assertEquals(2, TopLevelDestination.entries.indexOf(TopLevelDestination.PROFILE))
        // 「我的」在「历史报告」右边，所以从「我的」切到「历史报告」是后退
        assertTrue(
            TopLevelDestination.entries.indexOf(TopLevelDestination.PROFILE) >
                TopLevelDestination.entries.indexOf(TopLevelDestination.HISTORY)
        )
    }

    @Test
    fun `AppRoot 的过渡动画必须按方向计算不能写死`() {
        val src = File("src/main/java/com/workreport/app/ui/AppRoot.kt").readText()
        assertTrue(
            "AppRoot 必须用 pageDirection() 决定滑动方向，不能写死「从右滑入」",
            src.contains("pageDirection()")
        )
        assertTrue(
            "enter / exit / popEnter / popExit 四个过渡都要存在",
            src.contains("enterTransition") && src.contains("exitTransition") &&
                src.contains("popEnterTransition") && src.contains("popExitTransition")
        )
        // 写死的单向位移（只出现 +full/N 或只出现 -full/N）会让 Tab 来回切换方向反掉
        assertTrue(
            "滑动位移必须同时支持两个方向（不能只写死一个符号）",
            src.contains("full / 6") && src.contains("-full / 6")
        )
    }
}
