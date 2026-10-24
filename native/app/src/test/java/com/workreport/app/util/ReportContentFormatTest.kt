package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「载入首页继续编辑」的文本往返。
 *
 * Excel 里那两格是格式化后的文本（「完成共N项工作内容：\n1、xxx；」），
 * 载回首页必须拆回原始条目 —— 不拆的话重新生成会二次格式化，变成乱码。
 *
 * ⚠️ 拆分必须**无损**：拆完再格式化回去，生成的 Excel 内容要和原来一模一样。
 */
class ReportContentFormatTest {

    private val engine = SensitiveWordEngine(
        listOf(SensitiveWord("纪委", "jw"), SensitiveWord("查询", "cx")),
    )

    // ---------- 拆格式 ----------

    @Test
    fun `本周已完成工作梳理能拆回原始条目`() {
        val formatted = "完成共2项工作内容：\n1、配合纪委完成案件审理；\n2、组织案管系统培训。"
        assertEquals(
            "配合纪委完成案件审理\n组织案管系统培训",
            ReportContentFormat.unformatDoneWorks(formatted),
        )
    }

    @Test
    fun `下周工作计划能拆回原始条目`() {
        val formatted = "下周预计共1项工作任务：\n1、推进线索核查。"
        assertEquals("推进线索核查", ReportContentFormat.unformatPlanWorks(formatted))
    }

    @Test
    fun `不是格式化过的文本就原样返回`() {
        val raw = "配合纪委完成案件审理\n组织案管系统培训"
        assertEquals(raw, ReportContentFormat.unformatDoneWorks(raw))
    }

    @Test
    fun `空内容返回空串`() {
        assertEquals("", ReportContentFormat.unformatDoneWorks(""))
        assertEquals("", ReportContentFormat.unformatPlanWorks("   \n "))
    }

    @Test
    fun `多位数序号也能拆`() {
        val formatted = "完成共11项工作内容：\n10、第十条；\n11、第十一条。"
        assertEquals(
            "第十条\n第十一条",
            ReportContentFormat.unformatDoneWorks(formatted),
        )
    }

    // ---------- 往返（关键：导出结果必须逐字不变）----------

    @Test
    fun `拆完再格式化 导出的内容逐字相同`() {
        val original = "配合纪委完成案件审理\n组织案管系统培训"
        val formatted = SensitiveWordEngine.formatDoneWorks(original, engine)
        val restored = ReportContentFormat.unformatDoneWorks(formatted)
        // 再格式化一次，结果必须和第一次完全一样 —— 这才是用户拿到的周报
        assertEquals(formatted, SensitiveWordEngine.formatDoneWorks(restored, engine))
    }

    @Test
    fun `计划那边导出结果也逐字相同`() {
        val original = "推进线索核查\n继续查询整改"
        val formatted = SensitiveWordEngine.formatPlanWorks(original, engine)
        val restored = ReportContentFormat.unformatPlanWorks(formatted)
        assertEquals(formatted, SensitiveWordEngine.formatPlanWorks(restored, engine))
    }

    @Test
    fun `每行本来就带句号时导出结果也不变`() {
        val original = "第一步完成了备案。\n第二步完成了核查。"
        val formatted = SensitiveWordEngine.formatDoneWorks(original, engine)
        val restored = ReportContentFormat.unformatDoneWorks(formatted)
        // 最后一行结尾的「。」是格式化补的还是用户自己写的，从 Excel 里分不出来，
        // 拆的时候只能去掉 —— 但格式化那一步会补回来，所以**导出结果完全一致**。
        assertEquals(formatted, SensitiveWordEngine.formatDoneWorks(restored, engine))
        // 中间行的句号是用户自己写的，必须保住
        assertTrue(restored.contains("第一步完成了备案。"))
    }

    @Test
    fun `载入再生成 不会二次格式化`() {
        val original = "配合纪委完成案件审理\n组织案管系统培训"
        val first = SensitiveWordEngine.formatDoneWorks(original, engine)
        // 模拟：从 Excel 读出（格式化文本）→ 拆回 → 重新生成
        val restored = ReportContentFormat.unformatDoneWorks(first)
        val second = SensitiveWordEngine.formatDoneWorks(restored, engine)
        assertEquals("重新生成的内容必须和原来一致", first, second)
        assertTrue(second.startsWith("完成共2项工作内容："))
        assertTrue(!second.contains("完成共2项工作内容：\n1、完成共"))
    }
}
