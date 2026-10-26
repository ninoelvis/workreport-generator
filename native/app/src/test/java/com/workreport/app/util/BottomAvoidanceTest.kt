package com.workreport.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 底部避让高度的单元测试。
 *
 * 这个 bug 的本质：AppRoot 的 Scaffold 已经把内容区抬高了一个导航栏高度，
 * 页面里再叠 imePadding() 就等于多减了一次，键盘上方凭空多出一条空白带。
 */
class BottomAvoidanceTest {

    @Test
    fun `没弹键盘时让出导航栏高度`() {
        assertEquals(180, BottomAvoidance.compute(navBarPx = 180, imePx = 0))
    }

    @Test
    fun `弹键盘时让出键盘高度`() {
        assertEquals(900, BottomAvoidance.compute(navBarPx = 180, imePx = 900))
    }

    @Test
    fun `键盘比导航栏矮时取导航栏高度`() {
        // 分屏 / 悬浮键盘等极端情况下 IME 可能比导航栏矮
        assertEquals(180, BottomAvoidance.compute(navBarPx = 180, imePx = 120))
    }

    @Test
    fun `两者相等时就是那个值`() {
        assertEquals(180, BottomAvoidance.compute(navBarPx = 180, imePx = 180))
    }

    @Test
    fun `键盘弹出时不会多减一次导航栏高度`() {
        // 老逻辑：视口 = 屏幕 - 导航栏 - 键盘高度
        //          键盘顶 = 屏幕 - 键盘高度
        //          差 = 导航栏高度  ← 多出来的那条空白带
        // 新逻辑：避让 = max(导航栏, 键盘) = 键盘高度
        //          视口 = 屏幕 - 键盘高度 = 键盘顶 → 差 = 0
        val screen = 2000
        val navBar = 180
        val ime = 900

        val oldViewport = screen - navBar - ime
        val oldKeyboardTop = screen - ime
        val oldGap = oldKeyboardTop - oldViewport
        assertEquals("老逻辑会多出这么多空白", navBar, oldGap)

        val avoid = BottomAvoidance.compute(navBar, ime)
        val newViewport = screen - avoid
        assertEquals("新逻辑空白应为 0", 0, oldKeyboardTop - newViewport)
    }

    @Test
    fun `不弹键盘时底部导航栏的位置完整保留`() {
        val screen = 2000
        val navBar = 180
        val avoid = BottomAvoidance.compute(navBar, 0)
        assertEquals(screen - navBar, screen - avoid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `负的导航栏高度会报错`() {
        BottomAvoidance.compute(navBarPx = -1, imePx = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `负的键盘高度会报错`() {
        BottomAvoidance.compute(navBarPx = 0, imePx = -1)
    }
}
