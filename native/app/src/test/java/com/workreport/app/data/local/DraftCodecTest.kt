package com.workreport.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 草稿存取往返 —— 用户的核心诉求是「填的内容不能丢」，
 * 所以这里逐字段验证编解码不丢东西。
 */
class DraftCodecTest {

    @Test
    fun `空值解出空草稿`() {
        assertEquals(ReportDraft(), DraftCodec.decode(null))
        assertEquals(ReportDraft(), DraftCodec.decode(""))
    }

    @Test
    fun `脏数据不会抛异常`() {
        assertEquals(ReportDraft(), DraftCodec.decode("这不是 json"))
        assertEquals(ReportDraft(), DraftCodec.decode("{"))
    }

    @Test
    fun `所有字段往返后原样保留`() {
        val draft = ReportDraft(
            timeRange = "2026.10.19-2026.10.25",
            title = "2026年10月第4周工作报告",
            titleManual = true,
            doneWorks = "第一件事\n第二件事",
            planWorks = "下周要做的事",
            records = listOf("记录一", "记录二", "记录三"),
            recordInput = "还没提交的字",
        )
        val restored = DraftCodec.decode(DraftCodec.encode(draft))
        assertEquals(draft, restored)
    }

    @Test
    fun `多行内容不丢换行`() {
        val draft = ReportDraft(
            doneWorks = "第一行\n第二行\n第三行",
            planWorks = "计划一\n计划二",
        )
        val restored = DraftCodec.decode(DraftCodec.encode(draft))
        assertTrue(restored.doneWorks.contains("\n"))
        assertEquals(3, restored.doneWorks.lines().size)
    }

    @Test
    fun `空记录列表往返后仍是空列表`() {
        val restored = DraftCodec.decode(DraftCodec.encode(ReportDraft()))
        assertEquals(emptyList<String>(), restored.records)
    }

    @Test
    fun `记录里的空串会被丢掉`() {
        val restored = DraftCodec.decode(DraftCodec.encode(ReportDraft(records = listOf("a", "", "b"))))
        assertEquals(listOf("a", "b"), restored.records)
    }
}
