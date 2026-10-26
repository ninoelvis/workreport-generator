package com.workreport.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键盘 / 导航栏避让的**端到端布局模型**测试。
 *
 * 把整条链路的数学完整走一遍，锁死两个关键结果：
 *  1. 键盘弹出时，内容视口的底边 == 键盘上沿（一条空白都不留）
 *  2. 键盘隐藏时，底部导航栏的位置完整保留（三个 Tab 不被压）
 *
 * 链路：
 *   屏幕 → AppRoot 的 max(导航栏, IME) 避让 → NavHost 高度
 *        → 页面 LazyColumn（不再叠 imePadding）→ 内容视口
 */
class KeyboardLayoutModelTest {

    private val SCREEN = 2000
    private val NAV_BAR = 180   // 底部导航栏 + 系统导航条
    private val IME = 900      // 输入法高度

    /** 复刻 AppRoot 的计算 */
    private fun navHostHeight(navBarPx: Int, imePx: Int): Int =
        SCREEN - BottomAvoidance.compute(navBarPx, imePx)

    @Test
    fun `键盘弹出时内容视口底边正好等于键盘上沿`() {
        val navHost = navHostHeight(NAV_BAR, IME)
        // 页面里不再叠 imePadding，所以视口高度 == NavHost 高度
        val viewportBottom = navHost
        val keyboardTop = SCREEN - IME
        assertEquals(
            "键盘上方不应该留任何空白（视口底边必须等于键盘上沿）",
            keyboardTop,
            viewportBottom,
        )
    }

    @Test
    fun `键盘隐藏时底部导航栏的位置完整保留`() {
        val navHost = navHostHeight(NAV_BAR, 0)
        assertEquals("底部三个 Tab 不能被压住", SCREEN - NAV_BAR, navHost)
    }

    @Test
    fun `不管键盘多高都不会多减一次导航栏高度`() {
        for (ime in listOf(200, 500, 900, 1400, 1800)) {
            val navHost = navHostHeight(NAV_BAR, ime)
            val keyboardTop = SCREEN - ime
            assertEquals(
                "ime=$ime 时出现了空白带",
                keyboardTop,
                navHost,
            )
        }
    }

    @Test
    fun `键盘比导航栏还矮时以导航栏为准`() {
        // 极端情况：分屏 / 悬浮键盘
        val navHost = navHostHeight(NAV_BAR, 100)
        assertEquals(SCREEN - NAV_BAR, navHost)
    }

    @Test
    fun `内容视口高度始终为正`() {
        // 真实取值范围：输入法最高也就占满屏幕（状态栏那一条总还留着）
        for (ime in listOf(0, 200, 900, 1400, 1900)) {
            assertTrue(
                "ime=$ime 时视口高度为 ${navHostHeight(NAV_BAR, ime)}，必须 > 0",
                navHostHeight(NAV_BAR, ime) > 0,
            )
        }
    }
}
