package com.workreport.app.data.local

import java.util.Base64

/**
 * 草稿的编解码。
 *
 * 抽成纯函数（不依赖 Context、也不依赖 org.json），方便单测验证「存取往返不丢字段」。
 *
 * 格式：每行一个字段，值统一用 Base64 编码 —— 这样正文里的换行、
 * 冒号、换行符都不用担心，解析时按行切就行。
 */
object DraftCodec {

    private const val VERSION = "1"

    fun encode(draft: ReportDraft): String {
        val lines = mutableListOf<String>()
        lines += VERSION
        lines += b64(draft.timeRange)
        lines += b64(draft.title)
        lines += if (draft.titleManual) "1" else "0"
        lines += b64(draft.doneWorks)
        lines += b64(draft.planWorks)
        lines += b64(draft.recordInput)
        lines += draft.records.size.toString()
        draft.records.forEach { lines += b64(it) }
        return lines.joinToString("\n")
    }

    /** 解不出来就返回空草稿，绝不抛异常（脏数据不该让 App 起不来） */
    fun decode(raw: String?): ReportDraft {
        if (raw.isNullOrEmpty()) return ReportDraft()
        return try {
            val lines = raw.split("\n")
            if (lines.isEmpty() || lines[0] != VERSION) return ReportDraft()
            val timeRange = unb64(lines.getOrNull(1))
            val title = unb64(lines.getOrNull(2))
            val titleManual = lines.getOrNull(3) == "1"
            val doneWorks = unb64(lines.getOrNull(4))
            val planWorks = unb64(lines.getOrNull(5))
            val recordInput = unb64(lines.getOrNull(6))
            val count = lines.getOrNull(7)?.toIntOrNull() ?: 0
            val records = mutableListOf<String>()
            for (i in 0 until count) {
                val item = unb64(lines.getOrNull(8 + i))
                if (item.isNotEmpty()) records.add(item)
            }
            ReportDraft(
                timeRange = timeRange,
                title = title,
                titleManual = titleManual,
                doneWorks = doneWorks,
                planWorks = planWorks,
                records = records,
                recordInput = recordInput,
            )
        } catch (e: Exception) {
            ReportDraft()
        }
    }

    private fun b64(value: String): String =
        if (value.isEmpty()) "" else Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun unb64(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return try {
            String(Base64.getDecoder().decode(value), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
