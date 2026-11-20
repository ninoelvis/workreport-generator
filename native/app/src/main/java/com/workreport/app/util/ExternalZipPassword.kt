package com.workreport.app.util

import java.util.Calendar
import java.util.Date

/**
 * 外部压缩包的**解压密码候选**。
 *
 * App 自己生成的压缩包密码 = 「起始日 + 4 天」的 `yyyyMMdd`，
 * 而文件名里只有「年 / 月 / 月内第几周」。
 *
 * ⚠️ 关键：**周数不能唯一确定起始日**。
 *
 * `DateUtils.getWeekOfMonth()` 的语义是「这个日期落在当月第几个**周日区间**」，
 * 不是「这是当月第几个周日」。比如 2026 年 10 月：
 *
 * ```
 *   10.04 周日 → 10.11 周日 → 10.18 周日 → 10.25 周日
 *   第1周      第2周      第3周      第4周
 *
 *   第 3 周区间 = [10.18, 10.24]   ← 起始日可能是其中任何一天
 * ```
 *
 * 所以「第 3 周」对应 7 个可能的起始日 → 7 个可能的密码。
 * 解压时**依次尝试**，能解开那个就是它 —— App 自己生成的包必中。
 *
 * 文件名被改过（推不出周数）时，一个候选都没有，界面再让用户手输。
 *
 * ⚠️ 这只是**读外部文件**用的辅助，不参与导出 —— 导出的密码规则一个字都没动。
 */
object ExternalZipPassword {

    /** 「姓名-2026年10月第3周工作报告.zip」 */
    private val PATTERN =
        Regex("^(.+)-(\\d{4})年(\\d{1,2})月第(\\d+)周工作报告\\.zip$", RegexOption.IGNORE_CASE)

    /** 文件名里解析出的「年 / 月 / 月内第几周」 */
    data class WeekRef(val year: Int, val month: Int, val week: Int)

    /** 从文件名解析出年 / 月 / 周；不符合约定返回 null */
    fun parse(fileName: String): WeekRef? {
        val m = PATTERN.matchEntire(fileName.trim()) ?: return null
        val year = m.groupValues[2].toIntOrNull() ?: return null
        val month = m.groupValues[3].toIntOrNull() ?: return null
        val week = m.groupValues[4].toIntOrNull() ?: return null
        if (month !in 1..12 || week < 0) return null
        return WeekRef(year, month, week)
    }

    /**
     * 该周**所有可能的起始日**。
     *
     * 正常情况（第 N 周，N ≥ 1）：第 N 个周日，以及它之后的 6 天。
     *
     * ⚠️ 边界：**第 0 周**。
     * `getWeekOfMonth` 在起始日早于「当月第一个周日」时会算出 0
     * （比如 2025 年 1 月 1 号是周三，第一个周日是 1 月 5 号），
     * 这时候区间是「当月 1 号 → 当月第一个周日的前一天」。
     */
    fun possibleSundays(ref: WeekRef): List<Date> {
        val firstSunday = firstSundayOf(ref.year, ref.month) ?: return emptyList()
        val out = mutableListOf<Date>()

        if (ref.week == 0) {
            // 第 0 周：当月 1 号 → 第一个周日的前一天
            val c = Calendar.getInstance().apply {
                clear()
                set(ref.year, ref.month - 1, 1)
            }
            val firstSundayDay = firstSunday.get(Calendar.DAY_OF_MONTH)
            for (day in 1 until firstSundayDay) {
                c.set(Calendar.DAY_OF_MONTH, day)
                out += c.time
            }
            return out
        }

        val cursor = (firstSunday.clone() as Calendar)
        var count = 0
        while (count < ref.week && count < 8) {
            count++
            if (count == ref.week) {
                // 第 N 个周日，以及它之后的 6 天（第 N 周区间）
                val c = (cursor.clone() as Calendar)
                repeat(7) {
                    out += c.time
                    c.add(Calendar.DAY_OF_MONTH, 1)
                }
                return out
            }
            cursor.add(Calendar.DAY_OF_MONTH, 7)
        }
        return emptyList()
    }

    /**
     * 该压缩包**可能的解压密码**（按可能性从高到低）。
     *
     * 第一个是最可能的：起始日正好是那一周的周日（大多数用户填的起始日就是周日）。
     */
    fun candidates(fileName: String): List<String> {
        val ref = parse(fileName) ?: return emptyList()
        return possibleSundays(ref).map { sunday ->
            DateUtils.fmtDate8(DateUtils.getThursday(sunday))
        }.distinct()
    }

    /** 当月第一个周日（返回 Calendar 方便接着加天数） */
    private fun firstSundayOf(year: Int, month: Int): Calendar? {
        val c = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
        }
        var guard = 0
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY && guard < 40) {
            c.add(Calendar.DAY_OF_MONTH, 1)
            guard++
        }
        return if (guard < 40) c else null
    }
}
