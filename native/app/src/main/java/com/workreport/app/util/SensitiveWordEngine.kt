package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord

/**
 * 敏感词替换引擎。
 *
 * 规则与 WebView 旧版完全一致：按词库顺序，把敏感词替换成它的小写拼音首字母。
 * 例如「纪委」→「jw」、「一体化」→「yth」。
 */
class SensitiveWordEngine(words: List<SensitiveWord>) {

    /** 词库快照，构造后不再变化；需要更新请新建实例 */
    private val rules: List<Pair<String, String>> =
        words.filter { it.word.isNotBlank() }.map { it.word to it.pinyin }

    /**
     * 对文本做脱敏。
     *
     * 逐词 `replace`，等价于旧版的 `text.split(key).join(value)`。
     */
    fun replace(text: String): String {
        if (text.isEmpty() || rules.isEmpty()) return text
        var result = text
        for ((word, pinyin) in rules) {
            if (result.contains(word)) {
                result = result.replace(word, pinyin)
            }
        }
        return result
    }

    companion object {
        /** 格式化「本周已完成工作梳理」，与旧版 formatDoneWorks() 逐字一致 */
        fun formatDoneWorks(text: String, engine: SensitiveWordEngine): String {
            val lines = text.split('\n').filter { it.isNotBlank() }
            if (lines.isEmpty()) return ""
            val sb = StringBuilder()
            sb.append("完成共").append(lines.size).append("项工作内容：\n")
            lines.forEachIndexed { index, line ->
                val endChar = if (index == lines.size - 1) "。" else "；"
                sb.append(index + 1).append("、")
                    .append(engine.replace(line.trim()))
                    .append(endChar).append('\n')
            }
            return sb.toString().trim()
        }

        /** 格式化「下周工作计划」，与旧版 formatPlanWorks() 逐字一致 */
        fun formatPlanWorks(text: String, engine: SensitiveWordEngine): String {
            val lines = text.split('\n').filter { it.isNotBlank() }
            if (lines.isEmpty()) return ""
            val sb = StringBuilder()
            sb.append("下周预计共").append(lines.size).append("项工作任务：\n")
            lines.forEachIndexed { index, line ->
                val endChar = if (index == lines.size - 1) "。" else "；"
                sb.append(index + 1).append("、")
                    .append(engine.replace(line.trim()))
                    .append(endChar).append('\n')
            }
            return sb.toString().trim()
        }
    }
}
