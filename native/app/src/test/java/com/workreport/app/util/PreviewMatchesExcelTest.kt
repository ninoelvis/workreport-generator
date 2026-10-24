package com.workreport.app.util

import com.workreport.app.data.local.ReportEntity
import com.workreport.app.export.XlsxContentReader
import com.workreport.app.export.XlsxWriter
import com.workreport.app.domain.model.SensitiveWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「预览区显示的必须是实际导出的 Excel 里的内容」——
 * 这条是红线级的要求，用测试钉死。
 *
 * 背景：历史报告的预览区之前显示的是数据库里存的**原始条目**，
 * 而 Excel 里存的是**格式化（+脱敏）后**的文本，
 * 两边对不上，看起来就是「内容缺失严重」。
 */
class PreviewMatchesExcelTest {

    private fun engine(words: List<Pair<String, String>> = listOf("查询" to "cx", "纪委" to "jw")) =
        SensitiveWordEngine(words.map { SensitiveWord(it.first, it.second) })

    // ---------------- Excel 里那两格就是格式化后的文本 ----------------

    @Test
    fun `Excel 的 B3 就是格式化后的文本`() {
        val raw = "配合完成案件审理\n组织系统培训"
        // 这个引擎不含敏感词，格式化只做「加表头 + 加编号 + 加分隔符」
        val plain = SensitiveWordEngine(emptyList())
        val formatted = SensitiveWordEngine.formatDoneWorks(raw, plain)
        val bytes = XlsxWriter.toBytes(
            title = "2026年10月第3周工作报告",
            timeRange = "2026.10.19-2026.10.25",
            doneWorks = formatted,
            planWorks = "下周计划一\n下周计划二",
        )
        val content = XlsxContentReader.read(bytes)!!
        assertEquals(formatted, content.doneWorks)
        assertEquals("完成共2项工作内容：\n1、配合完成案件审理；\n2、组织系统培训。", content.doneWorks)
    }

    @Test
    fun `Excel 的 C3 就是格式化后的计划`() {
        val raw = "下周计划一\n下周计划二"
        val formatted = SensitiveWordEngine.formatPlanWorks(raw, engine())
        val bytes = XlsxWriter.toBytes(
            title = "t",
            timeRange = "2026.10.19-2026.10.25",
            doneWorks = "完成共1项工作内容：\n1、做了点事。",
            planWorks = formatted,
        )
        val content = XlsxContentReader.read(bytes)!!
        assertEquals(formatted, content.planWorks)
        assertEquals("下周预计共2项工作任务：\n1、下周计划一；\n2、下周计划二。", content.planWorks)
    }

    @Test
    fun `脱敏后的文本写进 Excel 读出来也是脱敏后的`() {
        val raw = "完成查询工作"
        val formatted = SensitiveWordEngine.formatDoneWorks(raw, engine())
        val bytes = XlsxWriter.toBytes("t", "2026.10.19-2026.10.25", formatted, "")
        val content = XlsxContentReader.read(bytes)!!
        assertEquals(formatted, content.doneWorks)
        assertTrue(content.doneWorks.contains("cx"))
        assertFalse(content.doneWorks.contains("查询"))
    }

    @Test
    fun `原始条目和 Excel 里的文本不是一回事（这就是之前预览对不上的原因）`() {
        val raw = "配合纪委完成案件审理\n组织案管系统培训"
        val formatted = SensitiveWordEngine.formatDoneWorks(raw, engine())
        assertFalse(raw == formatted)
    }

    // ---------------- 历史记录里存的「导出文本」 ----------------

    @Test
    fun `有导出文本的报告 预览直接用数据库 不用解压缩包`() {
        val entity = entity(
            doneWorks = "配合纪委完成案件审理",
            planWorks = "下周计划一",
            doneExported = "完成共1项工作内容：\n1、配合纪委完成案件审理。",
            planExported = "下周预计共1项工作任务：\n1、下周计划一。",
        )
        assertTrue(entity.hasExportedContent)
    }

    @Test
    fun `旧报告没有导出文本 退回解压缩包读 Excel`() {
        val entity = entity(doneWorks = "原始", planWorks = "原始")
        assertFalse(entity.hasExportedContent)
    }

    @Test
    fun `导出文本存在时 原始条目也还在（载入首页继续编辑要用）`() {
        val entity = entity(
            doneWorks = "配合纪委完成案件审理",
            planWorks = "下周计划一",
            doneExported = "完成共1项工作内容：\n1、配合纪委完成案件审理。",
            planExported = "下周预计共1项工作任务：\n1、下周计划一。",
        )
        assertTrue(entity.hasFullContent)
        assertEquals("配合纪委完成案件审理", entity.doneWorks)
    }

    private fun entity(
        doneWorks: String,
        planWorks: String,
        doneExported: String = "",
        planExported: String = "",
    ) = ReportEntity(
        id = 1L,
        fileName = "张三-2026年10月第3周工作报告.zip",
        displayName = "张三-2026年10月第3周工作报告",
        filePath = "/tmp/x.zip",
        password = "20261023",
        reportTitle = "2026年10月第3周工作报告",
        timeRange = "2026.10.19-2026.10.25",
        doneWorks = doneWorks,
        planWorks = planWorks,
        doneWorksExported = doneExported,
        planWorksExported = planExported,
    )
}
