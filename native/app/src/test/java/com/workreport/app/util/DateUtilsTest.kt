package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 日期算法测试。
 *
 * 所有期望值都由 **WebView 旧版**（index.html 的 fmtDate / fmtDate8 / getWeekRange /
 * getThursday / getWeekOfMonth / parseDateRange）在同一台机器上用同一份输入跑出来，
 * 保证原生版与旧版完全一致（文件名、解压密码、周数都依赖这些算法）。
 */
class DateUtilsTest {

    private fun date(y: Int, m: Int, d: Int) = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d)
    }.time

    @Test
    fun `解析日期范围`() {
        val r = DateUtils.parseDateRange("2026.10.19-2026.10.25")
        assertEquals(date(2026, 10, 19), r!!.first)
        assertEquals(date(2026, 10, 25), r.second)
    }

    @Test
    fun `非法日期范围返回 null`() {
        assertNull(DateUtils.parseDateRange("bad-input"))
        assertNull(DateUtils.parseDateRange("2026.1.1-2026.1.7"))
        assertNull(DateUtils.parseDateRange(""))
    }

    @Test
    fun `周范围为周日到周六`() {
        // 2026-10-22 是周四，属于 10.18(周日) - 10.24(周六)
        val (sunday, saturday) = DateUtils.getWeekRange(date(2026, 10, 22))
        assertEquals("2026.10.18", DateUtils.fmtDate(sunday))
        assertEquals("2026.10.24", DateUtils.fmtDate(saturday))
    }

    @Test
    fun `跨月周范围正确`() {
        val (sunday, saturday) = DateUtils.getWeekRange(date(2026, 1, 1))
        assertEquals("2025.12.28", DateUtils.fmtDate(sunday))
        assertEquals("2026.01.03", DateUtils.fmtDate(saturday))
    }

    @Test
    fun `周四即解压密码日期`() {
        val (sunday, _) = DateUtils.getWeekRange(date(2026, 10, 22))
        assertEquals("20261022", DateUtils.fmtDate8(DateUtils.getThursday(sunday)))
    }

    @Test
    fun `月内周数与旧版一致`() {
        val cases = listOf(
            Triple("2026.10.18", 3, "10月第3周"),
            Triple("2026.10.25", 4, "10月第4周"),
            Triple("2026.01.04", 1, "1月第1周"),
            Triple("2026.03.01", 1, "3月第1周"),
            Triple("2026.03.29", 5, "3月第5周"),
            Triple("2026.04.05", 1, "4月第1周"),
            Triple("2026.12.06", 1, "12月第1周"),
        )
        cases.forEach { (ymd, expected, label) ->
            val parts = ymd.split(".")
            val d = date(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            val (sunday, _) = DateUtils.getWeekRange(d)
            assertEquals(label, expected, DateUtils.getWeekOfMonth(sunday))
        }
    }

    @Test
    fun `格式化日期`() {
        assertEquals("2026.10.25", DateUtils.fmtDate(date(2026, 10, 25)))
        assertEquals("20261025", DateUtils.fmtDate8(date(2026, 10, 25)))
        assertEquals("2026.01.05", DateUtils.fmtDate(date(2026, 1, 5)))
    }

    @Test
    fun `本周时间范围文本`() {
        assertEquals(
            "2026.10.18-2026.10.24",
            DateUtils.currentWeekRangeText(date(2026, 10, 22)),
        )
    }
}
