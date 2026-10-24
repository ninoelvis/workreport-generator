package com.workreport.app.data.local

import java.util.Base64
import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.domain.model.ShortcutTemplate

/**
 * 个人配置快照 —— 备份 / 恢复用的完整数据包。
 *
 * 只含「这台手机上属于我个人的东西」，不含历史报告与已生成的 ZIP：
 *  · 姓名、主题、邮箱偏好
 *  · 敏感词库
 *  · 两套快捷记录模板
 *  · 周报提醒设置
 *  · 首页未完成的草稿
 */
data class ConfigSnapshot(
    val userName: String = "",
    val themeMode: String = "system",
    val sensitiveWords: List<SensitiveWord> = emptyList(),
    val workShortcuts: List<ShortcutTemplate> = emptyList(),
    val planShortcuts: List<ShortcutTemplate> = emptyList(),
    val preferredMailPackage: String = "",
    val autoSendMail: Boolean = false,
    val reminderEnabled: Boolean = false,
    /** 星期几，1=周一 … 7=周日（与 java.util.Calendar 一致） */
    val reminderWeekdays: Set<Int> = emptySet(),
    val draft: ReportDraft = ReportDraft(),
)

/**
 * 配置快照的编解码。
 *
 * 刻意**不用 org.json**：Android 的 org.json 在 JVM 单元测试里只是 stub，
 * 跑不了真正的编解码往返。改成纯 Kotlin + Base64，任何环境都能测。
 *
 * 格式：每行 `键<TAB>值`，值按需 Base64；多行记录用 `键<TAB>序号<TAB>字段...`。
 */
object ConfigCodec {

    private const val VERSION = "workreport-config-v1"

    fun encode(snapshot: ConfigSnapshot): String {
        val sb = StringBuilder()
        sb.append(VERSION).append('\n')
        sb.append("name").append('\t').append(b64(snapshot.userName)).append('\n')
        sb.append("theme").append('\t').append(snapshot.themeMode).append('\n')
        sb.append("mail").append('\t').append(b64(snapshot.preferredMailPackage)).append('\n')
        sb.append("autosend").append('\t').append(snapshot.autoSendMail).append('\n')
        sb.append("reminden").append('\t').append(snapshot.reminderEnabled).append('\n')
        sb.append("remindwd").append('\t').append(snapshot.reminderWeekdays.sorted().joinToString(",")).append('\n')

        snapshot.sensitiveWords.forEachIndexed { i, w ->
            sb.append("sw").append('\t').append(i)
                .append('\t').append(b64(w.word))
                .append('\t').append(b64(w.pinyin))
                .append('\t').append(w.auto)
                .append('\n')
        }
        snapshot.workShortcuts.forEachIndexed { i, t ->
            sb.append("sww").append('\t').append(i)
                .append('\t').append(b64(t.id))
                .append('\t').append(b64(t.text))
                .append('\n')
        }
        snapshot.planShortcuts.forEachIndexed { i, t ->
            sb.append("swp").append('\t').append(i)
                .append('\t').append(b64(t.id))
                .append('\t').append(b64(t.text))
                .append('\n')
        }
        sb.append("draft").append('\t').append(b64(DraftCodec.encode(snapshot.draft))).append('\n')
        return sb.toString()
    }

    fun decode(raw: String?): ConfigSnapshot? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.isEmpty() || !lines[0].startsWith(VERSION)) return null

        var name = ""
        var theme = "system"
        var mail = ""
        var autoSend = false
        var remindEnabled = false
        var remindDays = emptySet<Int>()
        val words = mutableListOf<SensitiveWord>()
        val workTemplates = mutableListOf<ShortcutTemplate>()
        val planTemplates = mutableListOf<ShortcutTemplate>()
        var draft = ReportDraft()

        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            when (parts[0]) {
                "name" -> name = unb64(parts.getOrNull(1))
                "theme" -> theme = parts.getOrNull(1) ?: "system"
                "mail" -> mail = unb64(parts.getOrNull(1))
                "autosend" -> autoSend = parts.getOrNull(1) == "true"
                "reminden" -> remindEnabled = parts.getOrNull(1) == "true"
                "remindwd" -> remindDays = parts.getOrNull(1)
                    .orEmpty()
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }
                    .toSet()
                "sw" -> {
                    val w = unb64(parts.getOrNull(2))
                    if (w.isNotEmpty()) {
                        words += SensitiveWord(
                            word = w,
                            pinyin = unb64(parts.getOrNull(3)),
                            auto = parts.getOrNull(4) != "false",
                        )
                    }
                }
                "sww" -> {
                    val text = unb64(parts.getOrNull(3))
                    if (text.isNotEmpty()) {
                        workTemplates += ShortcutTemplate(id = unb64(parts.getOrNull(2)), text = text)
                    }
                }
                "swp" -> {
                    val text = unb64(parts.getOrNull(3))
                    if (text.isNotEmpty()) {
                        planTemplates += ShortcutTemplate(id = unb64(parts.getOrNull(2)), text = text)
                    }
                }
                "draft" -> {
                    val encoded = unb64(parts.getOrNull(1))
                    if (encoded.isNotEmpty()) draft = DraftCodec.decode(encoded)
                }
            }
        }

        return ConfigSnapshot(
            userName = name,
            themeMode = theme,
            sensitiveWords = words,
            workShortcuts = workTemplates,
            planShortcuts = planTemplates,
            preferredMailPackage = mail,
            autoSendMail = autoSend,
            reminderEnabled = remindEnabled,
            reminderWeekdays = remindDays,
            draft = draft,
        )
    }

    private fun b64(value: String): String =
        if (value.isEmpty()) "" else Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun unb64(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return runCatching {
            String(Base64.getDecoder().decode(value), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
