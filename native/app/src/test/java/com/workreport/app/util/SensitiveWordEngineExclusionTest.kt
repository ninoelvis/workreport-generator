package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 脱敏引擎的「按处放行」能力。
 *
 * 核心场景：同一句话里出现两次「查询」时，用户只想放行其中一处，
 * 另一处照常脱敏。
 *
 * ⚠️ 兼容性红线：没有任何放行时，输出必须与旧版 WebView 逐字节一致，
 * 导出内容不能因为加了放行功能而发生变化。
 */
class SensitiveWordEngineExclusionTest {

    private val engine = SensitiveWordEngine(
        listOf(
            SensitiveWord("纪委", "jw"),
            SensitiveWord("查询", "cx"),
            SensitiveWord("措施", "cs"),
        ),
    )

    // ---------- 无放行时，行为与旧版完全一致 ----------

    @Test
    fun `无放行时 replace 与逐词 replace 结果一致`() {
        val text = "完成纪委查询工作，落实管控措施。"
        val expected = text.replace("纪委", "jw").replace("查询", "cx").replace("措施", "cs")
        assertEquals(expected, engine.replace(text))
    }

    @Test
    fun `无放行时 analyze 逐处统计的命中与实际替换一致`() {
        val text = "查询纪委查询"
        val summary = engine.analyze(text)
        assertEquals(3, summary.totalCount)
        assertEquals(2, summary.hits.count { it.word == "查询" })
        assertEquals(1, summary.hits.count { it.word == "纪委" })
        assertEquals(3, summary.maskedCount)
        assertEquals(0, summary.excludedCount)
    }

    @Test
    fun `无放行时 formatDoneWorks 与旧版逐字一致`() {
        val text = "完成查询工作\n落实纪委措施"
        val expected = "完成共2项工作内容：\n1、完成cx工作；\n2、落实jwcs。"
        assertEquals(expected, SensitiveWordEngine.formatDoneWorks(text, engine))
        assertEquals(
            expected,
            SensitiveWordEngine.formatDoneWorks(text, engine, emptySet()),
        )
    }

    @Test
    fun `无放行时 formatPlanWorks 与旧版逐字一致`() {
        val text = "推进查询工作\n落实措施"
        val expected = "下周预计共2项工作任务：\n1、推进cx工作；\n2、落实cs。"
        assertEquals(expected, SensitiveWordEngine.formatPlanWorks(text, engine))
    }

    // ---------- 按处放行 ----------

    @Test
    fun `同一句里两处查询只放行第一处`() {
        val text = "必须查询平台这四个字的查询我需要脱敏"
        val summary = engine.analyze(text)
        assertEquals(2, summary.totalCount)

        val first = summary.hits[0]
        val result = engine.replace(text, setOf(first.anchor))
        // 第一处保留原文，第二处照常脱敏
        assertEquals("必须查询平台这四个字的cx我需要脱敏", result)
    }

    @Test
    fun `同一句里两处查询只放行第二处`() {
        val text = "必须查询平台这四个字的查询我需要脱敏"
        val summary = engine.analyze(text)
        val second = summary.hits[1]
        val result = engine.replace(text, setOf(second.anchor))
        assertEquals("必须cx平台这四个字的查询我需要脱敏", result)
    }

    @Test
    fun `两处可以分别开关`() {
        val text = "必须查询平台这四个字的查询我需要脱敏"
        val summary = engine.analyze(text)
        val first = summary.hits[0]
        val second = summary.hits[1]

        val onlyFirst = engine.replace(text, setOf(first.anchor))
        assertTrue(onlyFirst.contains("必须查询平台"))
        assertTrue(onlyFirst.contains("的cx我需要脱敏"))

        val onlySecond = engine.replace(text, setOf(second.anchor))
        assertTrue(onlySecond.contains("必须cx平台"))
        assertTrue(onlySecond.contains("的查询我需要脱敏"))

        val both = engine.replace(text, setOf(first.anchor, second.anchor))
        assertEquals(text, both)
    }

    @Test
    fun `全部放行等于不脱敏`() {
        val text = "完成纪委查询工作，落实管控措施。"
        val all = engine.analyze(text).hits.map { it.anchor }.toSet()
        assertEquals(text, engine.replace(text, all))
    }

    @Test
    fun `放行的那处会被算作命中并标记为已放行`() {
        val text = "必须查询平台这四个字的查询我需要脱敏"
        val first = engine.analyze(text).hits[0]
        val summary = engine.analyze(text, setOf(first.anchor))
        assertEquals(2, summary.totalCount)
        assertEquals(1, summary.maskedCount)
        assertEquals(1, summary.excludedCount)
        assertTrue(summary.hits.any { it.excluded && it.anchor == first.anchor })
    }

    @Test
    fun `放行的词不会被后续规则拆开`() {
        // 词库里「纪委」和「委」有包含关系：放行「纪委」后，
        // 「委」不能再单独被替换，否则结果会变成「纪w」而不是原文。
        val e = SensitiveWordEngine(
            listOf(SensitiveWord("纪委", "jw"), SensitiveWord("委", "w")),
        )
        val text = "完成纪委工作"
        val hit = e.analyze(text).hits.first()
        assertEquals("完成jw工作", e.replace(text))
        assertEquals(text, e.replace(text, setOf(hit.anchor)))
    }

    @Test
    fun `内容改了之后旧的放行会安全失效`() {
        val text = "必须查询平台这四个字的查询我需要脱敏"
        val first = engine.analyze(text).hits[0]
        val excluded = setOf(first.anchor)

        // 在句首插入了新内容，原来那处的前后文变了，放行自动失效
        val edited = "今天" + text
        val expected = edited.replace("查询", "cx")
        assertEquals(expected, engine.replace(edited, excluded))
    }

    @Test
    fun `放行不会影响别处的命中`() {
        val text = "第一行查询工作\n第二行查询工作"
        val summary = engine.analyzeMultiline(text)
        assertEquals(2, summary.totalCount)
        val first = summary.hits[0]
        val masked = SensitiveWordEngine.formatDoneWorks(text, engine, setOf(first.anchor))
        assertTrue(masked.contains("1、第一行查询工作；"))
        assertTrue(masked.contains("2、第二行cx工作。"))
    }

    @Test
    fun `analyzeMultiline 与逐行脱敏口径一致`() {
        val text = "查询工作\n查询工作\n纪委措施"
        val summary = engine.analyzeMultiline(text)
        assertEquals(2, summary.hits.count { it.word == "查询" })
        assertEquals(1, summary.hits.count { it.word == "纪委" })
        assertEquals(1, summary.hits.count { it.word == "措施" })
    }

    @Test
    fun `analyzeMultiline 放行后该处不再被替换`() {
        val text = "查询工作\n查询工作"
        val summary = engine.analyzeMultiline(text)
        val masked = SensitiveWordEngine.formatDoneWorks(
            text,
            engine,
            setOf(summary.hits[0].anchor),
        )
        assertTrue(masked.contains("1、查询工作；"))
        assertTrue(masked.contains("2、cx工作。"))
    }

    @Test
    fun `面板统计的命中数与导出内容里的替换数一致`() {
        val text = "完成纪委查询工作，落实措施\n继续查询整改"
        val summary = engine.analyzeMultiline(text)
        val formatted = SensitiveWordEngine.formatDoneWorks(text, engine, emptySet())
        assertEquals(4, summary.maskedCount)
        assertEquals(2, formatted.split("cx").size - 1)
        assertEquals(1, formatted.split("jw").size - 1)
        assertEquals(1, formatted.split("cs").size - 1)
        // 摘要里每一条命中，在导出内容里都必须已经变成缩写
        for (hit in summary.hits) {
            assertTrue(
                "「${hit.word}」在摘要里，但导出内容里没被替换",
                !formatted.contains(hit.word),
            )
        }
    }

    @Test
    fun `放行集合在不同内容区之间不会串`() {
        val doneText = "完成查询工作"
        val planText = "推进查询工作"
        val doneHit = engine.analyzeMultiline(doneText, emptySet(), MaskSection.DONE).hits.first()
        val planText2 = SensitiveWordEngine.formatPlanWorks(planText, engine, setOf(doneHit.anchor))
        // 用「本周已完成」的锚点去放行「下周计划」，不该生效
        assertTrue(planText2.contains("cx"))
    }

    @Test
    fun `空内容不会产生命中`() {
        assertEquals(0, engine.analyze("").totalCount)
        assertEquals(0, engine.analyzeMultiline("   \n  ").totalCount)
    }
}
