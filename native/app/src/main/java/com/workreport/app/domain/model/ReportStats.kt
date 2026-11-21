package com.workreport.app.domain.model

/**
 * 周报统计（v2.0.0 起）。
 *
 * ⚠️ 覆盖范围的诚实说明：
 * **统计只包含本 App 生成的报告**（数据库里的记录）。
 * 用户手动拷进存放目录的外部压缩包没有 `year / month / week_num` 这些结构化字段，
 * 也不在数据库里，所以**不在统计范围内**。
 * 界面上必须把这一点标出来，否则用户会以为漏了文件。
 *
 * 纯数据 + 纯计算，不依赖 Android，可在 JVM 单元测试里直接跑。
 */

/** 某个月的统计 */
data class MonthStat(
    /** 年，如 2026 */
    val year: Int,
    /** 月，1-12 */
    val month: Int,
    /** 这个月生成了几份报告 */
    val reportCount: Int,
    /** 这个月一共写了多少条工作记录 */
    val itemCount: Int,
) {
    /** 「2026年10月」 */
    val label: String get() = "${year}年${month}月"
}

/** 整体统计 */
data class ReportStats(
    /** 报告总份数 */
    val totalReports: Int,
    /** 全部报告的工作条目总数 */
    val totalItems: Int,
    /** 覆盖的月份数 */
    val monthCount: Int,
    /** 按月份升序排列的每月统计 */
    val months: List<MonthStat>,
    /** 最多条目的那个月（并列时取最近的） */
    val busiestMonth: MonthStat?,
) {
    companion object {
        val EMPTY = ReportStats(0, 0, 0, emptyList(), null)
    }
}

/**
 * 统计的计算。
 *
 * 条目数取自 `done_works`（**原始条目**，换行分隔），
 * 而不是 `done_works_exported` —— 后者是格式化并脱敏之后的文本，
 * 里面的「完成共 N 项」可能被用户自己输入的文本干扰，数不准。
 */
object ReportStatsCalculator {

    /**
     * @param entries 每份报告的 (year, month, 原始条目文本)
     */
    fun calculate(entries: List<StatsEntry>): ReportStats {
        if (entries.isEmpty()) return ReportStats.EMPTY

        val byMonth = LinkedHashMap<Int, MonthStat>()
        var totalItems = 0

        for (e in entries) {
            val items = countItems(e.doneWorks)
            totalItems += items
            val key = e.year * 100 + e.month
            val prev = byMonth[key]
            byMonth[key] = if (prev == null) {
                MonthStat(e.year, e.month, 1, items)
            } else {
                MonthStat(prev.year, prev.month, prev.reportCount + 1, prev.itemCount + items)
            }
        }

        val months = byMonth.values.sortedWith(
            compareBy<MonthStat> { it.year }.thenBy { it.month },
        )
        val busiest = months.maxByOrNull { it.itemCount }
            ?.takeIf { it.itemCount > 0 }

        return ReportStats(
            totalReports = entries.size,
            totalItems = totalItems,
            monthCount = months.size,
            months = months,
            busiestMonth = busiest,
        )
    }

    /**
     * 数一条报告里有多少条工作记录。
     *
     * 原始条目是**换行分隔**的（`SensitiveWordEngine.formatDoneWorks` 就是按行拆的），
     * 所以数非空行即可。
     */
    fun countItems(doneWorks: String): Int =
        doneWorks.split('\n').count { it.isNotBlank() }

    /** 统计用的输入条目 */
    data class StatsEntry(
        val year: Int,
        val month: Int,
        val doneWorks: String,
    )
}
