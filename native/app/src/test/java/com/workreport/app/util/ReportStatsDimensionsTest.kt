package com.workreport.app.util

import com.workreport.app.domain.model.ReportStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统计新维度（v2.0.1 起）的计算测试。
 *
 * 新增三个维度：
 *  · 按「月内第几周」归并（周维度柱状图）
 *  · 单份报告产出（平均 / 最细的几份）
 *  · 平均每份条目数
 */
class ReportStatsDimensionsTest {

    private fun entry(
        year: Int = 2026,
        month: Int = 11,
        week: Int = 1,
        done: String = "",
        name: String = "",
    ) = ReportStatsCalculator.StatsEntry(
        year = year,
        month = month,
        weekNum = week,
        doneWorks = done,
        displayName = name,
    )

    @Test
    fun `按周归并要合并同一个周号的报告`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(week = 1, done = "a\nb", name = "第1周甲"),
                entry(week = 1, done = "c", name = "第1周乙"),
                entry(week = 3, done = "d\ne\nf", name = "第3周丙"),
            ),
        )
        assertEquals(2, stats.weeks.size)
        val w1 = stats.weeks.first { it.weekNum == 1 }
        assertEquals(2, w1.reportCount)
        assertEquals(3, w1.itemCount)
        assertEquals("第1周", w1.label)
        val w3 = stats.weeks.first { it.weekNum == 3 }
        assertEquals(1, w3.reportCount)
        assertEquals(3, w3.itemCount)
    }

    @Test
    fun `周数为零的旧数据不参与按周统计`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(week = 0, done = "a\nb"),
                entry(week = 2, done = "c"),
            ),
        )
        assertEquals("周数为 0 的不能算出一个「第0周」", 1, stats.weeks.size)
        assertEquals(2, stats.weeks.first().weekNum)
    }

    @Test
    fun `周维度要按周号升序`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(week = 4, done = "a"),
                entry(week = 1, done = "b"),
                entry(week = 2, done = "c"),
            ),
        )
        assertEquals(listOf(1, 2, 4), stats.weeks.map { it.weekNum })
    }

    @Test
    fun `平均每份条目数要保留小数`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(done = "a\nb\nc"),   // 3 条
                entry(done = "d"),          // 1 条
                entry(done = "e"),          // 1 条
            ),
        )
        assertEquals(5, stats.totalItems)
        assertEquals(3, stats.totalReports)
        assertEquals(5.0 / 3.0, stats.avgItemsPerReport, 0.0001)
    }

    @Test
    fun `没有报告时平均值为零`() {
        val stats = ReportStatsCalculator.calculate(emptyList())
        assertEquals(0.0, stats.avgItemsPerReport, 0.0001)
        assertNull(stats.topReport)
        assertTrue(stats.weeks.isEmpty())
        assertTrue(stats.topReports.isEmpty())
    }

    @Test
    fun `写得最细的几份要按条目数降序且最多三份`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(done = "a", name = "甲"),
                entry(done = "a\nb\nc\nd", name = "乙"),
                entry(done = "a\nb", name = "丙"),
                entry(done = "a\nb\nc", name = "丁"),
                entry(done = "a", name = "戊"),
            ),
        )
        assertEquals(3, stats.topReports.size)
        assertEquals(listOf("乙", "丁", "丙"), stats.topReports.map { it.displayName })
        assertEquals(4, stats.topReport?.itemCount)
        assertEquals("乙", stats.topReport?.displayName)
    }

    @Test
    fun `空条目的报告不进入最细列表`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(done = "", name = "空白"),
                entry(done = "a", name = "有一条"),
            ),
        )
        assertEquals(1, stats.topReports.size)
        assertEquals("有一条", stats.topReports.first().displayName)
    }

    @Test
    fun `单份报告的统计要带上年月周`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(entry(year = 2026, month = 11, week = 3, done = "a\nb", name = "于壮壮-第3周")),
        )
        val top = stats.topReport!!
        assertEquals(2026, top.year)
        assertEquals(11, top.month)
        assertEquals(3, top.weekNum)
        assertEquals("2026年11月第3周", top.period)
    }

    @Test
    fun `按月归并不受新增字段影响`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(year = 2026, month = 10, week = 4, done = "a", name = "甲"),
                entry(year = 2026, month = 11, week = 1, done = "b\nc", name = "乙"),
            ),
        )
        assertEquals(2, stats.monthCount)
        assertEquals(listOf("2026年10月", "2026年11月"), stats.months.map { it.label })
        assertEquals("2026年11月", stats.busiestMonth?.label)
    }
}
