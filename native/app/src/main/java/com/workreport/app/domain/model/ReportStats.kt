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

/**
 * 某一「第 N 周」的统计。
 *
 * ⚠️ v2.1.2 修：以前**只按周号归并**（key 就是 weekNum），
 *    于是十月的第 4 周和十一月的第 4 周被合成了同一根柱子 ——
 *    看着像"11月第4周还没写怎么就有数据"（那其实是十月的）。
 *
 *    现在 key 是 **年 + 月 + 周**，标签也带上月份，不再混淆。
 */
data class WeekStat(
    /** 年，如 2026 */
    val year: Int,
    /** 月，1-12 */
    val month: Int,
    /** 月内第几周，如 3 */
    val weekNum: Int,
    /** 这一周生成了几份报告 */
    val reportCount: Int,
    /** 这一周一共写了多少条工作记录 */
    val itemCount: Int,
) {
    /** 「11月第3周」—— 必须带月份，否则不同月份的同周次会混在一起 */
    val label: String get() = "${month}月第${weekNum}周"

    /**
     * 「3周」—— 轴标签的第二行。
     *
     * ⚠️ 为什么不用「第3周」：12 组周的时候每格只有 25dp 宽，
     *    「第3周」三个字放不下会被整行跳过，图表就变成一堆没标签的柱子。
     *    上面一行已经有月份了，「11月 / 3周」一样能看懂。
     */
    val shortLabel: String get() = "${weekNum}周"

    /** 「2026年11月第3周」 */
    val fullLabel: String get() = "${year}年${month}月第${weekNum}周"
}

/** 单份报告的产出（v2.0.1 起） */
data class ReportItemStat(
    /** 报告展示名 */
    val displayName: String,
    /** 写了几条工作记录 */
    val itemCount: Int,
    /** 年 */
    val year: Int,
    /** 月 */
    val month: Int,
    /** 第几周 */
    val weekNum: Int,
) {
    /** 「2026年11月第3周」 */
    val period: String get() = "${year}年${month}月第${weekNum}周"
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
    /** 按「年 + 月 + 月内第几周」归并的统计（v2.1.2 起），按时间升序 */
    val weeks: List<WeekStat> = emptyList(),
    /** 条目最多的那一周（v2.1.2 起，标签带月份，不会混淆） */
    val busiestWeek: WeekStat? = null,
    /** 写得最多的那一份报告（v2.0.1 起） */
    val topReport: ReportItemStat? = null,
    /** 写得最细的几份报告（v2.0.1 起），按条目数降序 */
    val topReports: List<ReportItemStat> = emptyList(),
    /**
     * 数据里是否跨了多个年份（v2.1.2 起）。
     *
     * 跨年时轴标签必须带年份 ——
     * 否则「2025年11月」和「2026年11月」在图上是两个一模一样的标签。
     */
    val hasMultipleYears: Boolean = false,
) {
    /**
     * 平均每份报告写了几条工作记录（v2.0.1 起）。
     *
     * 用 Double 是为了不丢小数（3 份 10 条 = 3.33），显示时再取一位。
     */
    val avgItemsPerReport: Double
        get() = if (totalReports == 0) 0.0 else totalItems.toDouble() / totalReports

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
        // ⚠️ key 必须是「年+月+周」，不能只用周号
        //    （只用周号会把不同月份的同周次合成一根柱子，看着像"未来周也有数据"）
        //    用整数编码：2026年11月第3周 → 20261103
        val byWeek = LinkedHashMap<Int, WeekStat>()
        val perReport = ArrayList<ReportItemStat>(entries.size)
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

            // 按「年 + 月 + 月内第几周」归并（v2.1.2 修）
            // 周号为 0 的（老数据/外部文件没有周数）不参与，避免算出一个「第0周」
            if (e.weekNum > 0) {
                val wKey = e.year * 10000 + e.month * 100 + e.weekNum
                val wPrev = byWeek[wKey]
                byWeek[wKey] = if (wPrev == null) {
                    WeekStat(e.year, e.month, e.weekNum, 1, items)
                } else {
                    WeekStat(
                        wPrev.year, wPrev.month, wPrev.weekNum,
                        wPrev.reportCount + 1, wPrev.itemCount + items,
                    )
                }
            }

            // 单份产出（v2.0.1 起）
            perReport += ReportItemStat(
                displayName = e.displayName,
                itemCount = items,
                year = e.year,
                month = e.month,
                weekNum = e.weekNum,
            )
        }

        val months = byMonth.values.sortedWith(
            compareBy<MonthStat> { it.year }.thenBy { it.month },
        )
        // ⚠️ 并列时取**最近**的那一个
        //    （maxByOrNull 会保留第一个最大值，而 months 是时间升序 → 会取到最早的）
        val busiest = months.maxWithOrNull(
            compareBy<MonthStat> { it.itemCount }
                .thenBy { it.year * 100 + it.month },
        )?.takeIf { it.itemCount > 0 }

        val weeks = byWeek.values.sortedWith(
            compareBy<WeekStat> { it.year }.thenBy { it.month }.thenBy { it.weekNum },
        )
        val ranked = perReport.sortedWith(
            compareByDescending<ReportItemStat> { it.itemCount }
                .thenByDescending { it.year * 100 + it.month }
                .thenByDescending { it.weekNum },
        )

        return ReportStats(
            totalReports = entries.size,
            totalItems = totalItems,
            monthCount = months.size,
            months = months,
            busiestMonth = busiest,
            weeks = weeks,
            busiestWeek = weeks.maxWithOrNull(
                compareBy<WeekStat> { it.itemCount }
                    .thenBy { it.year * 10000 + it.month * 100 + it.weekNum },
            )?.takeIf { it.itemCount > 0 },
            topReport = ranked.firstOrNull()?.takeIf { it.itemCount > 0 },
            topReports = ranked.filter { it.itemCount > 0 }.take(3),
            hasMultipleYears = entries.map { it.year }.distinct().size > 1,
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
        /** 月内第几周（v2.0.1 起；0 表示未知，不参与按周统计） */
        val weekNum: Int = 0,
        /** 报告展示名（v2.0.1 起，用于「写得最细的几份」） */
        val displayName: String = "",
    )
}
