package com.workreport.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.0.0 周报统计的纯逻辑测试。
 *
 * 统计要算对，靠的是三件事：
 *  1. 条目数从**原始条目**数非空行（不是从脱敏格式化后的文本里解析「完成共 N 项」——
 *     那段文本可能被用户自己输入的内容干扰）；
 *  2. 按「年 + 月」归并，而不是只按月（否则 2025年10月和2026年10月会混在一起）；
 *  3. 月份要按时间升序，不能按哈希顺序。
 */
class ReportStatsTest {

    private fun entry(year: Int, month: Int, items: List<String>) =
        ReportStatsCalculator.StatsEntry(year, month, items.joinToString("\n"))

    @Test
    fun `空列表返回空统计`() {
        val s = ReportStatsCalculator.calculate(emptyList())
        assertEquals(0, s.totalReports)
        assertEquals(0, s.totalItems)
        assertEquals(0, s.monthCount)
        assertNull(s.busiestMonth)
    }

    @Test
    fun `条目数按非空行计算`() {
        assertEquals(0, ReportStatsCalculator.countItems(""))
        assertEquals(0, ReportStatsCalculator.countItems("\n\n   \n"))
        assertEquals(1, ReportStatsCalculator.countItems("做了一件事"))
        assertEquals(3, ReportStatsCalculator.countItems("一\n二\n三"))
        // 中间夹空行不算
        assertEquals(2, ReportStatsCalculator.countItems("一\n\n二\n"))
    }

    @Test
    fun `按年月归并并按时间升序`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(2026, 3, listOf("a", "b")),
                entry(2026, 10, listOf("c")),
                entry(2025, 12, listOf("d", "e", "f")),
                entry(2026, 10, listOf("g", "h")),
            ),
        )
        assertEquals(4, stats.totalReports)
        assertEquals(8, stats.totalItems)
        assertEquals(3, stats.monthCount)

        assertEquals(
            listOf("2025年12月", "2026年3月", "2026年10月"),
            stats.months.map { it.label },
        )
        assertEquals(2, stats.months[2].reportCount)
        assertEquals(3, stats.months[2].itemCount)
    }

    @Test
    fun `同名的不同月份不会被混在一起`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(2025, 10, listOf("a")),
                entry(2026, 10, listOf("b", "c")),
            ),
        )
        assertEquals(2, stats.monthCount)
        assertEquals(1, stats.months[0].reportCount)
        assertEquals(1, stats.months[0].itemCount)
        assertEquals("2025年10月", stats.months[0].label)
        // 2026年10月是**一份**报告写了 2 条 —— 份数和条数是两回事
        assertEquals(1, stats.months[1].reportCount)
        assertEquals(2, stats.months[1].itemCount)
        assertEquals("2026年10月", stats.months[1].label)
    }

    @Test
    fun `产出最多的月份是条目最多的那个`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(2026, 1, listOf("a", "b", "c")),
                entry(2026, 2, listOf("d")),
            ),
        )
        assertEquals("2026年1月", stats.busiestMonth?.label)
        assertEquals(3, stats.busiestMonth?.itemCount)
    }

    @Test
    fun `全部报告都没有条目时不算产出最多的月份`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(entry(2026, 1, emptyList()), entry(2026, 2, emptyList())),
        )
        assertEquals(2, stats.totalReports)
        assertEquals(0, stats.totalItems)
        assertNull("没有条目就不该有「产出最多」的月份", stats.busiestMonth)
    }

    @Test
    fun `统计标签格式是 年份年月份月`() {
        val s = ReportStatsCalculator.calculate(listOf(entry(2026, 11, listOf("a"))))
        assertEquals("2026年11月", s.months[0].label)
    }
}
