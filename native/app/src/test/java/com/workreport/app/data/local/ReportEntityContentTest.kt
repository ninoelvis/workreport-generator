package com.workreport.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 历史报告内容快照。
 *
 * v1.3.9 起，生成报告时会把 Excel 里的内容一并存进数据库，
 * 这样点开历史报告就能直接预览，不必解压压缩包。
 * 旧版本生成的记录没有这些字段，需要优雅降级。
 */
class ReportEntityContentTest {

    private fun entity(
        title: String = "",
        timeRange: String = "",
        doneWorks: String = "",
        planWorks: String = "",
        contentPreview: String = "",
    ) = ReportEntity(
        fileName = "张三-2026年10月第3周工作报告.zip",
        displayName = "张三-2026年10月第3周工作报告",
        filePath = "/tmp/x.zip",
        password = "20261022",
        contentPreview = contentPreview,
        reportTitle = title,
        timeRange = timeRange,
        doneWorks = doneWorks,
        planWorks = planWorks,
    )

    @Test
    fun `新记录带完整内容可以预览`() {
        val e = entity(
            title = "2026年10月第3周工作报告",
            timeRange = "2026.10.19-2026.10.25",
            doneWorks = "参加部门例会\n整理台账",
            planWorks = "继续推进案件办理",
        )
        assertTrue(e.hasFullContent)
    }

    @Test
    fun `旧记录没有完整内容要降级处理`() {
        val e = entity(contentPreview = "旧版只存了前 120 字")
        assertFalse(e.hasFullContent)
    }

    @Test
    fun `只要有任意一个完整字段就算可预览`() {
        assertTrue(entity(title = "标题").hasFullContent)
        assertTrue(entity(timeRange = "2026.10.19-2026.10.25").hasFullContent)
        assertTrue(entity(doneWorks = "做了件事").hasFullContent)
        assertTrue(entity(planWorks = "下周做件事").hasFullContent)
    }

    @Test
    fun `默认构造的新字段都是空字符串`() {
        val e = ReportEntity(
            fileName = "a.zip",
            displayName = "a",
            filePath = "/a.zip",
            password = "20261022",
        )
        assertEquals("", e.reportTitle)
        assertEquals("", e.timeRange)
        assertEquals("", e.doneWorks)
        assertEquals("", e.planWorks)
        assertFalse(e.hasFullContent)
    }
}
