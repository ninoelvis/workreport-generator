package com.workreport.app.util

import java.util.Calendar
import java.util.Date

/**
 * 日期工具。
 *
 * ⚠️ 这里的所有算法都**逐字对齐** WebView 旧版（app/src/main/assets/index.html），
 * 任何改动都会导致导出的文件名、解压密码、周数与历史版本不一致，请勿随意修改。
 */
object DateUtils {

    private val WEEK_RANGE_REGEX =
        Regex("""^(\d{4})\.(\d{2})\.(\d{2})-(\d{4})\.(\d{2})\.(\d{2})$""")

    /**
     * 解析「yyyy.MM.dd-yyyy.MM.dd」，失败返回 null。
     * 对应旧版 parseDateRange()。
     */
    fun parseDateRange(text: String): Pair<Date, Date>? {
        val m = WEEK_RANGE_REGEX.matchEntire(text.trim()) ?: return null
        val (y1, m1, d1, y2, m2, d2) = m.destructured
        val start = Calendar.getInstance().apply {
            clear()
            set(y1.toInt(), m1.toInt() - 1, d1.toInt())
        }.time
        val end = Calendar.getInstance().apply {
            clear()
            set(y2.toInt(), m2.toInt() - 1, d2.toInt())
        }.time
        return start to end
    }

    /** 格式化为 yyyyMMdd，用于解压密码。对应旧版 fmtDate8()。 */
    fun fmtDate8(date: Date): String {
        val c = Calendar.getInstance().apply { time = date }
        return "%04d%02d%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
        )
    }

    /** 格式化为 yyyy.MM.dd。对应旧版 fmtDate()。 */
    fun fmtDate(date: Date): String {
        val c = Calendar.getInstance().apply { time = date }
        return "%04d.%02d.%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
        )
    }

    /**
     * 计算月内第几周（以周日为一周起点）。
     * 对应旧版 getWeekOfMonth()，逐行等价。
     */
    fun getWeekOfMonth(sunday: Date): Int {
        val c = Calendar.getInstance().apply { time = sunday }
        val month = c.get(Calendar.MONTH)
        val year = c.get(Calendar.YEAR)

        // 当月 1 号
        val firstDay = Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }
        // 当月第一个周日
        val firstSunday = (firstDay.clone() as Calendar).apply {
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        // 从首个周日开始，每逢周日计数（包含起始那个周日）
        var count = 0
        val cursor = (firstSunday.clone() as Calendar)
        while (!cursor.time.after(sunday)) {
            if (cursor.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) count++
            cursor.add(Calendar.DAY_OF_MONTH, 7)
        }
        return count
    }

    /** 本周周四（解压密码取周四）。对应旧版 getThursday()。 */
    fun getThursday(sunday: Date): Date = Calendar.getInstance().apply {
        time = sunday
        add(Calendar.DAY_OF_MONTH, 4)
    }.time

    /** 取某天所在自然周（周日~周六）。对应旧版 getWeekRange()。 */
    /**
     * 取某天所在「周」的起止：返回 **周日 ~ 周六**。
     *
     * ⚠️ 本 App 的一周是从**周日**开始的（和旧版 JS 的 `getDay() === 0` 一致），
     * 所以周日 ~ 周六，不是周一 ~ 周日。
     */
    fun getWeekRange(date: Date): Pair<Date, Date> {
        val c = Calendar.getInstance().apply { time = date }
        // ⚠️ Calendar.DAY_OF_WEEK 里周日 = 1，而 JS 的 getDay() 里周日 = 0，
        //    所以这里要减 (DAY_OF_WEEK - 1)，否则整周会偏移一天。
        val sunday = (c.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -(c.get(Calendar.DAY_OF_WEEK) - 1))
        }.time
        val saturday = Calendar.getInstance().apply {
            time = sunday
            add(Calendar.DAY_OF_MONTH, 6)
        }.time
        return sunday to saturday
    }

    /**
     * 本周时间范围文本，例如 2026.11.15-2026.11.21
     *
     * ⚠️ 本 App 的「一周」是**周日 ~ 周六**（见 [getWeekRange]），
     * 不是「周一 ~ 周日」。
     */
    fun currentWeekRangeText(today: Date = Date()): String {
        val (sunday, saturday) = getWeekRange(today)
        return "${fmtDate(sunday)}-${fmtDate(saturday)}"
    }

    /**
     * 上周的时间范围文本，格式与 [currentWeekRangeText] 完全一致。
     *
     * 给首页「时间范围」的快捷选择用（v2.0.0 起）。
     *
     * ⚠️ 必须复用 [getWeekRange] + [fmtDate]，
     * **不要自己拼字符串** —— 这个文本会写进 Excel 的 B2 格（红线），
     * 格式必须和用户手填时用的默认格式一模一样。
     */
    fun previousWeekRangeText(today: Date = Date()): String {
        val (sunday, saturday) = getWeekRange(today)
        val prevSunday = Calendar.getInstance().apply {
            time = sunday
            add(Calendar.DAY_OF_MONTH, -7)
        }.time
        val prevSaturday = Calendar.getInstance().apply {
            time = saturday
            add(Calendar.DAY_OF_MONTH, -7)
        }.time
        return "${fmtDate(prevSunday)}-${fmtDate(prevSaturday)}"
    }
}
