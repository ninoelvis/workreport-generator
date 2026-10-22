package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 敏感词引擎测试。
 *
 * 期望值与 WebView 旧版的 replaceSensitive / formatDoneWorks / formatPlanWorks 完全一致。
 */
class SensitiveWordEngineTest {

    private val words = listOf(
        SensitiveWord("纪委", "jw"),
        SensitiveWord("监委", "jw"),
        SensitiveWord("案管", "ag"),
        SensitiveWord("审理", "sl"),
        SensitiveWord("党风", "df"),
        SensitiveWord("一体化", "yth"),
        SensitiveWord("查询", "cx"),
        SensitiveWord("措施", "cs"),
        SensitiveWord("线索", "xs"),
        SensitiveWord("案件", "aj"),
        SensitiveWord("信访", "xf"),
        SensitiveWord("法规", "fg"),
        SensitiveWord("保密", "bm"),
    )

    private val engine = SensitiveWordEngine(words)

    @Test
    fun `敏感词被替换为拼音首字母`() {
        assertEquals("完成jw工作", engine.replace("完成纪委工作"))
        assertEquals("ag系统培训", engine.replace("案管系统培训"))
        assertEquals("yth平台cx", engine.replace("一体化平台查询"))
    }

    @Test
    fun `多个敏感词按词库顺序替换`() {
        // 旧版是 split/join 逐词替换，所以「纪委」被换成 jw 之后，
        // 后面再替换「监委」时不会受影响 —— 但「监委」本身也会被换成 jw，
        // 最终结果是 jwjwajsl（与旧版逐字一致）
        assertEquals("jwjwajsl", engine.replace("纪委监委案件审理"))
    }

    @Test
    fun `无敏感词时原样返回`() {
        assertEquals("普通文本", engine.replace("普通文本"))
        assertEquals("", engine.replace(""))
    }

    @Test
    fun `按词库顺序替换`() {
        // 「监委」在「纪委」之后，若先替换「纪委」不会影响「监委」
        val e = SensitiveWordEngine(listOf(SensitiveWord("纪委", "A"), SensitiveWord("监委", "B")))
        assertEquals("AB", e.replace("纪委监委"))
    }

    @Test
    fun `格式化本周已完成工作与旧版一致`() {
        val text = "完成共2项工作内容：\n1、配合纪委完成案件审理；\n2、组织案管系统培训。"
        assertEquals(
            "完成共3项工作内容：\n1、完成共2项工作内容：；\n2、1、配合jw完成ajsl；；\n3、2、组织ag系统培训。。",
            SensitiveWordEngine.formatDoneWorks(text, engine),
        )
    }

    @Test
    fun `格式化下周计划与旧版一致`() {
        val text = "1、推进线索核查。"
        assertEquals(
            "下周预计共1项工作任务：\n1、1、推进xs核查。。",
            SensitiveWordEngine.formatPlanWorks(text, engine),
        )
    }

    @Test
    fun `空行被过滤且序号连续`() {
        val text = "第一项\n\n\n第二项\n"
        assertEquals(
            "完成共2项工作内容：\n1、第一项；\n2、第二项。",
            SensitiveWordEngine.formatDoneWorks(text, engine),
        )
    }

    @Test
    fun `空文本返回空串`() {
        assertEquals("", SensitiveWordEngine.formatDoneWorks("", engine))
        assertEquals("", SensitiveWordEngine.formatDoneWorks("   \n  \n ", engine))
        assertEquals("", SensitiveWordEngine.formatPlanWorks("", engine))
    }
}
