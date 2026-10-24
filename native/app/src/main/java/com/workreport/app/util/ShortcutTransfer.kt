package com.workreport.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.workreport.app.domain.model.ShortcutSlot
import com.workreport.app.domain.model.ShortcutTemplate
import java.util.Base64

/**
 * 快捷模板的复制 / 导入。
 *
 * 场景：驻场的同事各自装了这个 App，但模板是各人自己的。
 * 想把自己整理好的模板给同事，就复制成一段文本发过去，
 * 对方粘贴导入即可 —— 比让对方一条条手敲省事得多。
 *
 * ⚠️ 模板正文可能含**未脱敏**的敏感内容，所以导入时必须提醒用户自己确认。
 */
object ShortcutTransfer {

    private const val PREFIX = "WR-TPL:"
    private const val SEP = "|"

    /**
     * 把某个分区的模板序列化成一段可复制文本。
     */
    fun encode(slot: ShortcutSlot, templates: List<ShortcutTemplate>): String {
        val sb = StringBuilder(PREFIX)
        sb.append(slot.storageKey).append(SEP)
        templates.forEach { tpl ->
            // 正文用 Base64，防止里面有分隔符或换行把格式弄乱
            sb.append(Base64.getEncoder().encodeToString(tpl.text.toByteArray(Charsets.UTF_8)))
                .append(SEP)
        }
        return sb.toString()
    }

    /**
     * 解析一段文本，返回「分区 + 模板列表」。
     *
     * 解析不出来就返回 null（调用方给用户一句"这段文本不像模板"就行）。
     */
    fun decode(raw: String?): Pair<ShortcutSlot, List<ShortcutTemplate>>? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        val body = trimmed.removePrefix(PREFIX)
        val parts = body.split(SEP)
        if (parts.isEmpty()) return null
        val slot = ShortcutSlot.entries.firstOrNull { it.storageKey == parts[0] } ?: return null
        val templates = mutableListOf<ShortcutTemplate>()
        for (i in 1 until parts.size) {
            val encoded = parts[i].trim()
            if (encoded.isEmpty()) continue
            val text = runCatching {
                String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
            }.getOrNull() ?: continue
            if (text.isBlank()) continue
            templates += ShortcutTemplate(id = newId(), text = text)
        }
        if (templates.isEmpty()) return null
        return slot to templates
    }

    /** 把一段文本复制到剪贴板 */
    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("工作记录模板", text))
    }

    /** 读剪贴板里的文本 */
    fun readClipboard(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()
    }

    /** 复制成功后的提示 */
    fun toastCopied(context: Context, count: Int) {
        Toast.makeText(
            context,
            "已复制 $count 条模板，发给同事后让他粘贴导入",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun newId(): String =
        "${System.currentTimeMillis()}-${(0..0xFFFF).random().toString(16)}"
}
