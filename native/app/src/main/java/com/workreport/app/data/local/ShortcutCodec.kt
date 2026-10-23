package com.workreport.app.data.local

import com.workreport.app.domain.model.ShortcutTemplate
import java.util.Base64

/**
 * 快捷记录模板的本地编解码。
 *
 * 存 SharedPreferences 的是一段纯文本：每个模板一行，
 * 每行 = Base64(id + 分隔符 + 正文)。
 *
 * 刻意不依赖 `org.json` —— Android JVM 单元测试里 org.json 是空壳，
 * 用它写出来的编解码没法在本地跑单测。纯 Kotlin 实现可以直接断言往返不丢字段。
 */
object ShortcutCodec {

    /** 字段分隔符，正文里不会出现这个字符 */
    private const val SEP = '\u0001'

    /** 行分隔符 */
    private const val LINE = '\n'

    fun encode(templates: List<ShortcutTemplate>): String =
        templates.joinToString(LINE.toString()) { t ->
            base64(t.id + SEP + t.text)
        }

    fun decode(raw: String?): List<ShortcutTemplate> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(LINE)
            .asSequence()
            .mapNotNull { line ->
                val text = unbase64(line.trim())
                if (text.isEmpty()) return@mapNotNull null
                val idx = text.indexOf(SEP)
                if (idx <= 0) return@mapNotNull null
                val id = text.substring(0, idx)
                val body = text.substring(idx + 1)
                if (body.isBlank()) return@mapNotNull null
                ShortcutTemplate(id = id, text = body)
            }
            .toList()
    }

    private fun base64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun unbase64(value: String): String =
        runCatching {
            String(Base64.getDecoder().decode(value), Charsets.UTF_8)
        }.getOrDefault("")
}
