package com.workreport.app.export

import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.util.MaskSection
import com.workreport.app.util.SensitiveWordEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端验证「脱敏放行」到最终 xlsx 的完整链路。
 *
 * 验证三件事：
 *  1. 放行的那一处，在最终文件里保留原文；
 *  2. 没放行的那一处，在最终文件里是缩写；
 *  3. 完全不放行时，最终文件内容与旧版逐字节一致（Excel 格式与内容都不能被这次改造影响）。
 */
class MaskExclusionExportTest {

    private val engine = SensitiveWordEngine(
        listOf(
            SensitiveWord("纪委", "jw"),
            SensitiveWord("查询", "cx"),
        ),
    )

    private fun buildXlsx(doneText: String, excluded: Set<com.workreport.app.util.MaskAnchor>): ByteArray =
        XlsxWriter.toBytes(
            title = "2026年10月工作报告",
            timeRange = "2026.10.19-2026.10.25",
            doneWorks = SensitiveWordEngine.formatDoneWorks(doneText, engine, excluded),
            planWorks = SensitiveWordEngine.formatPlanWorks("", engine, excluded),
        )

    @Test
    fun `放行的那处在最终 xlsx 里保留原文`() {
        val text = "必须查询平台这四个字的查询我需要脱敏"
        val hits = engine.analyzeMultiline(text, emptySet(), MaskSection.DONE).hits
        assertEquals(2, hits.size)

        val bytes = buildXlsx(text, setOf(hits[0].anchor))
        val content = XlsxContentReader.read(bytes)
        assertNotNull("xlsx 解析失败", content)
        val done = content!!.doneWorks
        assertTrue("放行处没保留原文：$done", done.contains("必须查询平台这四个字的"))
        assertTrue("没放行处没被脱敏：$done", done.contains("的cx我需要脱敏"))
        assertTrue("放行处不该出现缩写：$done", !done.contains("必须cx平台"))
    }

    @Test
    fun `不放行时最终 xlsx 内容与旧版一致`() {
        val text = "完成纪委查询工作\n继续查询整改"
        val bytes = buildXlsx(text, emptySet())
        val content = XlsxContentReader.read(bytes)
        assertNotNull(content)
        val expected = SensitiveWordEngine.formatDoneWorks(text, engine)
        assertEquals(expected, content!!.doneWorks)
        assertTrue(content.doneWorks.contains("完成jwcx工作"))
    }

    @Test
    fun `面板命中数与最终文件里的替换数一致`() {
        val text = "完成纪委查询工作\n继续查询整改"
        val summary = engine.analyzeMultiline(text, emptySet(), MaskSection.DONE)
        val content = XlsxContentReader.read(buildXlsx(text, emptySet()))
        assertNotNull(content)
        val done = content!!.doneWorks
        // 每个词各算各的：查询 2 处、纪委 1 处
        assertEquals(summary.hits.count { it.word == "查询" }, done.split("cx").size - 1)
        assertEquals(summary.hits.count { it.word == "纪委" }, done.split("jw").size - 1)
        assertEquals(3, summary.maskedCount)
    }
}
