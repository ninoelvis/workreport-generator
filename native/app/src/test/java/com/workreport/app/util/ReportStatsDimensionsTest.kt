package com.workreport.app.util

import com.workreport.app.domain.model.ReportStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `按周归并要合并同一个年月同一周号的报告`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(month = 10, week = 1, done = "a\nb", name = "第1周甲"),
                entry(month = 10, week = 1, done = "c", name = "第1周乙"),
                entry(month = 10, week = 3, done = "d\ne\nf", name = "第3周丙"),
            ),
        )
        assertEquals(2, stats.weeks.size)
        val w1 = stats.weeks.first { it.weekNum == 1 }
        assertEquals(2, w1.reportCount)
        assertEquals(3, w1.itemCount)
        // 标签必须带月份，否则不同月份的同周次看起来一样
        assertEquals("10月第1周", w1.label)
        val w3 = stats.weeks.first { it.weekNum == 3 }
        assertEquals(1, w3.reportCount)
        assertEquals(3, w3.itemCount)
    }

    /**
     * v2.1.2 修：**不同月份的同周次不能合并**。
     *
     * 坑：以前 byWeek 的 key 就是 weekNum，于是十月的第 4 周和十一月的第 4 周
     * 被合成一根柱子 —— 看着像"11月第4周还没写怎么就有数据"（那其实是十月的）。
     */
    @Test
    fun `不同月份的同一周号不能合并`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(year = 2026, month = 10, week = 4, done = "a", name = "十月第4周"),
                entry(year = 2026, month = 11, week = 4, done = "b\nc", name = "十一月第4周"),
            ),
        )
        assertEquals("两个不同月份的同周次必须是两根柱子", 2, stats.weeks.size)
        assertEquals(listOf("10月第4周", "11月第4周"), stats.weeks.map { it.label })
        assertEquals(1, stats.weeks.first { it.month == 10 }.itemCount)
        assertEquals(2, stats.weeks.first { it.month == 11 }.itemCount)
    }

    /** 不同年份的同月同周也不能合并 */
    @Test
    fun `不同年份的同月同周不能合并`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(year = 2025, month = 6, week = 2, done = "a", name = "去年"),
                entry(year = 2026, month = 6, week = 2, done = "b", name = "今年"),
            ),
        )
        assertEquals(2, stats.weeks.size)
        assertEquals(listOf(2025, 2026), stats.weeks.map { it.year })
    }

    /** 最忙的一周要带月份，不能只报「第N周」 */
    @Test
    fun `最忙的一周要带月份`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(month = 10, week = 2, done = "a", name = "十月"),
                entry(month = 11, week = 3, done = "b\nc\nd", name = "十一月"),
            ),
        )
        assertEquals("11月第3周", stats.busiestWeek?.label)
        assertEquals(3, stats.busiestWeek?.itemCount)
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
    fun `周维度要按时间升序`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(month = 10, week = 4, done = "a"),
                entry(month = 10, week = 1, done = "b"),
                entry(month = 11, week = 2, done = "c"),
            ),
        )
        // 先按月、再按周号
        assertEquals(listOf(1, 4, 2), stats.weeks.map { it.weekNum })
        assertEquals(listOf(10, 10, 11), stats.weeks.map { it.month })
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

    // v2.1.2：并列时统一取「最近」的那一个
    // （maxByOrNull 会保留第一个最大值，而列表是时间升序 → 会取到最早的）
    @Test
    fun `最忙的一周并列时要取最近的那一周`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(year = 2026, month = 10, week = 4, done = "a\nb", name = "甲"),
                entry(year = 2026, month = 11, week = 1, done = "c\nd", name = "乙"),
            ),
        )
        assertEquals(2, stats.busiestWeek?.itemCount)
        assertEquals("11月第1周", stats.busiestWeek?.label)
    }

    @Test
    fun `最忙的月份并列时要取最近的那个月`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(
                entry(year = 2026, month = 10, week = 4, done = "a\nb", name = "甲"),
                entry(year = 2026, month = 11, week = 1, done = "c\nd", name = "乙"),
            ),
        )
        assertEquals(2, stats.busiestMonth?.itemCount)
        assertEquals("2026年11月", stats.busiestMonth?.label)
    }

    // v2.1.2：跨年时轴标签必须能区分年份
    @Test
    fun `跨年的数据要能看出有多个年份`() {
        val same = ReportStatsCalculator.calculate(
            listOf(
                entry(year = 2025, month = 11, week = 3, done = "a", name = "甲"),
                entry(year = 2026, month = 11, week = 3, done = "b", name = "乙"),
            ),
        )
        assertTrue("跨年时 hasMultipleYears 必须为 true", same.hasMultipleYears)
        // 跨年的同月同周也必须分开算
        assertEquals(2, same.weeks.size)
        assertEquals(listOf(2025, 2026), same.weeks.map { it.year })

        val single = ReportStatsCalculator.calculate(
            listOf(entry(year = 2026, month = 11, week = 3, done = "a", name = "甲")),
        )
        assertFalse("同一年时不该标记为跨年", single.hasMultipleYears)
    }

    // v2.1.2：轴标签第二行用短形式「第3周」太宽，12 组时放不下会被整行跳过
    @Test
    fun `周的短标签要用不带第字的形式`() {
        val stats = ReportStatsCalculator.calculate(
            listOf(entry(year = 2026, month = 11, week = 3, done = "a", name = "甲")),
        )
        val week = stats.weeks.first()
        assertEquals("3周", week.shortLabel)
        assertEquals("11月第3周", week.label)
        assertEquals("2026年11月第3周", week.fullLabel)
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
