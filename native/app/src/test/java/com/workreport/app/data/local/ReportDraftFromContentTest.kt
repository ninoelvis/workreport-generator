package com.workreport.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 历史报告回填首页的草稿转换。
 *
 * 用户在历史报告里点「载入首页继续编辑」，内容要能正确地拆回首页的表单：
 * 「本周已完成工作梳理」是多行文本，应该拆成「工作记录」列表里的逐条记录。
 */
class ReportDraftFromContentTest {

    @Test
    fun `多行的本周完成会拆成工作记录列表`() {
        val draft = ReportDraft.fromContent(
            title = "2026年10月第3周工作报告",
            timeRange = "2026.10.19-2026.10.25",
            doneWorks = "参加部门例会\n整理案件台账\n完成三份材料",
            planWorks = "继续推进办理进度",
        )
        assertEquals(3, draft.records.size)
        assertEquals("参加部门例会", draft.records[0])
        assertEquals("整理案件台账", draft.records[1])
        assertEquals("完成三份材料", draft.records[2])
    }

    @Test
    fun `标题和时间原样带过去`() {
        val draft = ReportDraft.fromContent(
            title = "2026年10月第3周工作报告",
            timeRange = "2026.10.19-2026.10.25",
            doneWorks = "干活",
            planWorks = "继续干",
        )
        assertEquals("2026年10月第3周工作报告", draft.title)
        assertEquals("2026.10.19-2026.10.25", draft.timeRange)
        assertEquals("干活", draft.doneWorks)
        assertEquals("继续干", draft.planWorks)
    }

    @Test
    fun `有标题就标记为手动设过不会被自动标题覆盖`() {
        assertTrue(ReportDraft.fromContent("标题", "", "", "").titleManual)
        assertFalse(ReportDraft.fromContent("", "", "", "").titleManual)
    }

    @Test
    fun `空行和首尾空格会被清掉`() {
        val draft = ReportDraft.fromContent(
            title = "",
            timeRange = "",
            doneWorks = "  第一条  \n\n  \n第二条\n",
            planWorks = "",
        )
        assertEquals(2, draft.records.size)
        assertEquals("第一条", draft.records[0])
        assertEquals("第二条", draft.records[1])
    }

    @Test
    fun `内容全空时不会产生空记录`() {
        val draft = ReportDraft.fromContent("", "", "", "")
        assertTrue(draft.records.isEmpty())
        assertEquals("", draft.doneWorks)
        assertEquals("", draft.planWorks)
        assertEquals("", draft.recordInput)
    }

    @Test
    fun `单条内容也能正常工作`() {
        val draft = ReportDraft.fromContent("t", "r", "只有一条", "")
        assertEquals(1, draft.records.size)
        assertEquals("只有一条", draft.records[0])
    }
}
