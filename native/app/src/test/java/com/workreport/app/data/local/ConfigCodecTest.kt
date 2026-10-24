package com.workreport.app.data.local

import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.domain.model.ShortcutSlot
import com.workreport.app.domain.model.ShortcutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配置快照的编解码往返。
 *
 * ⚠️ 这些是纯 Kotlin 逻辑，不依赖 org.json / android.util.Base64，
 * 所以能在 JVM 单元测试里真跑。
 */
class ConfigCodecTest {

    private val snapshot = ConfigSnapshot(
        userName = "张三",
        themeMode = "dark",
        sensitiveWords = listOf(
            SensitiveWord("纪委", "jw"),
            SensitiveWord("查询", "cx", auto = false),
        ),
        workShortcuts = listOf(ShortcutTemplate("id-1", "开发接口联调")),
        planShortcuts = listOf(ShortcutTemplate("id-2", "跟进纪委问题")),
        preferredMailPackage = "com.tencent.mm",
        autoSendMail = true,
        reminderEnabled = true,
        reminderWeekdays = setOf(4, 5),
        draft = ReportDraft(
            timeRange = "2026.10.19-2026.10.25",
            title = "2026年10月工作报告",
            titleManual = true,
            doneWorks = "完成查询功能开发\n处理了若干问题",
            planWorks = "下周继续跟进",
            records = listOf("记录一", "记录二"),
            recordInput = "正在输入的内容",
        ),
    )

    @Test
    fun `编解码往返后所有字段都不丢`() {
        val decoded = ConfigCodec.decode(ConfigCodec.encode(snapshot))
        assertNotNull(decoded)
        assertEquals(snapshot.userName, decoded!!.userName)
        assertEquals(snapshot.themeMode, decoded.themeMode)
        assertEquals(snapshot.sensitiveWords, decoded.sensitiveWords)
        assertEquals(snapshot.workShortcuts, decoded.workShortcuts)
        assertEquals(snapshot.planShortcuts, decoded.planShortcuts)
        assertEquals(snapshot.preferredMailPackage, decoded.preferredMailPackage)
        assertEquals(snapshot.autoSendMail, decoded.autoSendMail)
        assertEquals(snapshot.reminderEnabled, decoded.reminderEnabled)
        assertEquals(snapshot.reminderWeekdays, decoded.reminderWeekdays)
        assertEquals(snapshot.draft, decoded.draft)
    }

    @Test
    fun `正文里的换行和特殊字符不会被弄坏`() {
        val tricky = snapshot.copy(
            userName = "张\t三\n",
            sensitiveWords = listOf(SensitiveWord("a|b\tc", "x|y")),
            workShortcuts = listOf(ShortcutTemplate("id", "第一行\n第二行\t带制表符")),
        )
        val decoded = ConfigCodec.decode(ConfigCodec.encode(tricky))
        assertNotNull(decoded)
        assertEquals(tricky.userName, decoded!!.userName)
        assertEquals(tricky.sensitiveWords, decoded.sensitiveWords)
        assertEquals(tricky.workShortcuts, decoded.workShortcuts)
    }

    @Test
    fun `空内容也能往返`() {
        val empty = ConfigSnapshot()
        val decoded = ConfigCodec.decode(ConfigCodec.encode(empty))
        assertNotNull(decoded)
        assertEquals(empty.userName, decoded!!.userName)
        assertTrue(decoded.sensitiveWords.isEmpty())
        assertTrue(decoded.reminderWeekdays.isEmpty())
    }

    @Test
    fun `解不出来时返回 null 而不是抛异常`() {
        assertNull(ConfigCodec.decode(null))
        assertNull(ConfigCodec.decode(""))
        assertNull(ConfigCodec.decode("随便一段不是本格式的文本"))
    }

    @Test
    fun `提醒星期只保留 1 到 7`() {
        val bad = snapshot.copy(reminderWeekdays = setOf(0, 4, 9, 12))
        val decoded = ConfigCodec.decode(ConfigCodec.encode(bad))
        assertNotNull(decoded)
        assertEquals(setOf(4), decoded!!.reminderWeekdays)
    }
}
