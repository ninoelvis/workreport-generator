package com.workreport.app.util

import com.workreport.app.export.ReportNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * 外部压缩包**候选密码**的测试。
 *
 * 核心认知（v1.5.0 开发时踩过）：**周数不能唯一确定起始日**。
 * `getWeekOfMonth` 算的是「落在当月第几个周日区间」，
 * 所以「第 3 周」对应 7 个可能的起始日 → 7 个可能的密码。
 *
 * 所以这里验证的是：**App 真实用到的那个密码，一定在候选列表里。**
 */
class ExternalZipPasswordTest {

    /** 用 App 自己的规则，构造「文件名 + 真实密码」的用例 */
    private fun cases(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val cal = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.JANUARY, 1)
        }
        for (i in 0 until 24) {
            // 每个月取 4 个不同的起始日，覆盖「周日」和「周中」两种
            for (offset in intArrayOf(0, 1, 5, 7)) {
                val start = (cal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_MONTH, offset)
                }.time
                val endCal = Calendar.getInstance().apply { time = start }
                endCal.add(Calendar.DAY_OF_MONTH, 6)
                val end = endCal.time
                val range = "${DateUtils.fmtDate(start)}-${DateUtils.fmtDate(end)}"
                val plan = ReportNaming.plan("张三", range)
                out += plan.zipFileName to plan.password
            }
            cal.add(Calendar.MONTH, 1)
        }
        return out
    }

    @Test
    fun `真实用到的密码一定在候选列表里`() {
        val all = cases()
        assertTrue("用例太少：${all.size}", all.size >= 90)
        all.forEach { (fileName, password) ->
            val candidates = ExternalZipPassword.candidates(fileName)
            assertTrue("候选列表是空的：$fileName", candidates.isNotEmpty())
            assertTrue(
                "候选密码里没有真实密码 $password（文件 $fileName，候选 $candidates）",
                candidates.contains(password),
            )
        }
    }

    @Test
    fun `每份文件的候选密码最多 7 个且都是 8 位数字`() {
        cases().forEach { (fileName, _) ->
            val c = ExternalZipPassword.candidates(fileName)
            assertTrue("候选太多：$c", c.size in 1..7)
            c.forEach { p ->
                assertTrue("密码格式不对：$p", p.matches(Regex("\\d{8}")))
            }
        }
    }

    @Test
    fun `候选密码按可能性排序：周日在最前面`() {
        // 2026 年 10 月：10.04 / 10.11 / 10.18 / 10.25 是周日
        val fileName = "张三-2026年10月第3周工作报告.zip"
        val candidates = ExternalZipPassword.candidates(fileName)
        assertEquals(7, candidates.size)
        // 第 3 个周日 = 10.18 → +4 天 = 10.22
        assertEquals("20261022", candidates.first())
        // 区间最后一天 10.24 → +4 天 = 10.28
        assertEquals("20261028", candidates.last())
    }

    @Test
    fun `认不出来的文件名返回空候选而不是瞎猜`() {
        assertTrue(ExternalZipPassword.candidates("周报.zip").isEmpty())
        assertTrue(ExternalZipPassword.candidates("张三-2026年10月工作报告.zip").isEmpty())
        assertTrue(ExternalZipPassword.candidates("张三-2026年13月第3周工作报告.zip").isEmpty())
        assertTrue(ExternalZipPassword.candidates("随便一个文件.txt").isEmpty())
        assertTrue(ExternalZipPassword.candidates("").isEmpty())
    }

    @Test
    fun `第 0 周也能推出候选密码`() {
        // 2025 年 1 月 1 号是周三，第一个周日是 1 月 5 号
        // 起始日早于第一个周日时，getWeekOfMonth 会算出 0
        val ref = ExternalZipPassword.parse("张三-2025年1月第0周工作报告.zip")
        assertNotNull(ref)
        assertEquals(0, ref!!.week)
        val candidates = ExternalZipPassword.candidates("张三-2025年1月第0周工作报告.zip")
        assertTrue("第 0 周应该也有候选：$candidates", candidates.isNotEmpty())
        candidates.forEach { assertTrue(it.matches(Regex("\\d{8}"))) }
    }

    @Test
    fun `解析出来的周信息是对的`() {
        val ref = ExternalZipPassword.parse("张三-2026年10月第3周工作报告.zip")
        assertNotNull(ref)
        assertEquals(2026, ref!!.year)
        assertEquals(10, ref.month)
        assertEquals(3, ref.week)
        assertNull(ExternalZipPassword.parse("乱七八糟.zip"))
    }

    @Test
    fun `第 3 周区间里的每一天都能被覆盖`() {
        val ref = ExternalZipPassword.parse("张三-2026年10月第3周工作报告.zip")!!
        val sundays = ExternalZipPassword.possibleSundays(ref)
        assertEquals(7, sundays.size)
        // 第 3 个周日是 2026.10.18
        val c = Calendar.getInstance().apply { time = sundays.first() }
        assertEquals(18, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SUNDAY, c.get(Calendar.DAY_OF_WEEK))
        // 区间最后一天是 10.24（周六）
        val c2 = Calendar.getInstance().apply { time = sundays.last() }
        assertEquals(24, c2.get(Calendar.DAY_OF_MONTH))
    }
}
