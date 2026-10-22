package com.workreport.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 命名与密码规则测试。
 *
 * 期望值来自 WebView 旧版（index.html）的命名与密码逻辑，
 * 这些规则**不允许改动**，改动会导致用户找不到旧文件、或者密码对不上。
 */
class ReportNamingTest {

    @Test
    fun `文件名格式与旧版一致`() {
        val p = ReportNaming.plan("张三", "2026.10.19-2026.10.25")
        assertEquals("张三-2026年10月第3周工作报告", p.baseName)
        assertEquals("张三-2026年10月第3周工作报告.zip", p.zipFileName)
        assertEquals("张三-2026年10月第3周工作报告.xlsx", p.xlsxFileName)
    }

    @Test
    fun `月份不补零`() {
        val p = ReportNaming.plan("李四", "2026.01.04-2026.01.10")
        assertEquals("李四-2026年1月第1周工作报告", p.baseName)
        assertEquals(1, p.month)
    }

    @Test
    fun `未填姓名时使用 姓名 占位`() {
        val p = ReportNaming.plan("   ", "2026.10.19-2026.10.25")
        assertEquals("姓名-2026年10月第3周工作报告.zip", p.zipFileName)
    }

    @Test
    fun `密码为起始日期加四天 yyyyMMdd`() {
        // ⚠️ 旧版（index.html 导出逻辑）的算法是：密码 = 用户填的「起始日期」+ 4 天，
        //    默认的时间范围是周日起始，所以正常情况下就等于「本周周四」。
        //    这里必须逐字照抄旧版行为，否则用户会拿到对不上的密码。
        assertEquals("20261023", ReportNaming.plan("张三", "2026.10.19-2026.10.25").password)
        assertEquals("20261029", ReportNaming.plan("张三", "2026.10.25-2026.10.31").password)
        // 跨年：2025.12.28 + 4 天 = 2026.01.01
        assertEquals("20260101", ReportNaming.plan("张三", "2025.12.28-2026.01.03").password)
        // 周日起始时恰好等于本周周四
        assertEquals("20261022", ReportNaming.plan("张三", "2026.10.18-2026.10.24").password)
    }

    @Test
    fun `月内周数与旧版一致`() {
        assertEquals(3, ReportNaming.plan("张三", "2026.10.19-2026.10.25").weekNum)
        assertEquals(4, ReportNaming.plan("张三", "2026.10.25-2026.10.31").weekNum)
        assertEquals(1, ReportNaming.plan("张三", "2026.10.04-2026.10.10").weekNum)
    }

    @Test
    fun `时间格式错误时抛异常`() {
        var threw = false
        try {
            ReportNaming.plan("张三", "2026.1.1-2026.1.7")
        } catch (e: IllegalArgumentException) {
            threw = true
            assertTrue(e.message!!.contains("yyyy.MM.dd"))
        }
        assertTrue("应当抛出 IllegalArgumentException", threw)
    }
}
