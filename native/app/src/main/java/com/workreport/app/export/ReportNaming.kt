package com.workreport.app.export

import com.workreport.app.util.DateUtils
import java.util.Calendar

/**
 * 导出命名与密码规则。
 *
 * ⚠️ 这里是**用户可见的产物规则**，与 WebView 旧版一字不差：
 *
 * - ZIP 文件名：`{姓名或"姓名"}-{YYYY}年{M}月第{N}周工作报告.zip`（月份不补零）
 * - ZIP 内文件：同名 `.xlsx`
 * - 解压密码：本周周四的 `yyyyMMdd`
 * - N = 该周周日落在当月的第几周（从当月第一个周日起数）
 *
 * 抽成纯函数是为了能直接跑单元测试，避免改动时悄悄破坏兼容性。
 */
object ReportNaming {

    /** 一次导出的命名结果 */
    data class Plan(
        /** 不含扩展名，例如「张三-2026年10月第3周工作报告」 */
        val baseName: String,
        /** 加密 zip 的文件名 */
        val zipFileName: String,
        /** zip 内部 xlsx 的文件名 */
        val xlsxFileName: String,
        /** 解压密码 */
        val password: String,
        val year: Int,
        val month: Int,
        val weekNum: Int,
    )

    /**
     * 根据姓名与本周工作时间算出命名与密码。
     *
     * @param timeRangeText 形如 `2026.10.19-2026.10.25`
     * @throws IllegalArgumentException 时间格式不对
     */
    fun plan(userName: String, timeRangeText: String): Plan {
        val parsed = DateUtils.parseDateRange(timeRangeText)
            ?: throw IllegalArgumentException("请输入正确格式的工作时间（yyyy.MM.dd-yyyy.MM.dd）")

        val sunday = parsed.first
        val thursday = DateUtils.getThursday(sunday)
        val password = DateUtils.fmtDate8(thursday)
        val weekNum = DateUtils.getWeekOfMonth(sunday)

        val calendar = Calendar.getInstance().apply { time = sunday }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1

        val name = userName.trim().ifEmpty { "姓名" }
        val baseName = "${name}-${year}年${month}月第${weekNum}周工作报告"

        return Plan(
            baseName = baseName,
            zipFileName = "$baseName.zip",
            xlsxFileName = "$baseName.xlsx",
            password = password,
            year = year,
            month = month,
            weekNum = weekNum,
        )
    }
}
