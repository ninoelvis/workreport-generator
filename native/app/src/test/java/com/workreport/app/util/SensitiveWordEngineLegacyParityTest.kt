package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * 脱敏引擎与**旧版 WebView 算法**的等价性验证。
 *
 * 旧版（index.html）的脱敏就一句：
 *
 * ```js
 * text = text.split(key).join(value)
 * ```
 *
 * 这里把这句话原样搬成 Kotlin 当作「参考答案」，
 * 然后用随机用例把新引擎和参考答案逐条对拍。
 *
 * ⚠️ 这是公司红线：没有任何放行时，导出的内容必须和旧版**逐字节一致**。
 * 引擎为了支持「按处放行」重写了替换过程，这个测试就是防止重写时跑偏的锁。
 *
 * 随机种子固定，失败可以复现。
 */
class SensitiveWordEngineLegacyParityTest {

    /**
     * 旧版算法：逐词替换。
     *
     * 旧版 JS 写的是 `text.split(key).join(value)`，
     * 对非空分隔串来说它与「逐词 replace」完全等价（两边都做过实测对拍：
     * 1500 组随机用例逐条一致，见 `LegacyCrossCheckTest`）。
     */
    private fun legacyMask(text: String, words: List<SensitiveWord>): String {
        var result = text
        for (w in words) {
            result = result.replace(w.word, w.pinyin)
        }
        return result
    }

    /** 旧版 formatDoneWorks */
    private fun legacyFormatDoneWorks(text: String, words: List<SensitiveWord>): String {
        val lines = text.split('\n').filter { it.trim().isNotEmpty() }
        if (lines.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("完成共").append(lines.size).append("项工作内容：\n")
        lines.forEachIndexed { index, line ->
            val endChar = if (index == lines.size - 1) "。" else "；"
            sb.append(index + 1).append("、")
                .append(legacyMask(line.trim(), words))
                .append(endChar).append('\n')
        }
        return sb.toString().trim()
    }

    /** 旧版 formatPlanWorks */
    private fun legacyFormatPlanWorks(text: String, words: List<SensitiveWord>): String {
        val lines = text.split('\n').filter { it.trim().isNotEmpty() }
        if (lines.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("下周预计共").append(lines.size).append("项工作任务：\n")
        lines.forEachIndexed { index, line ->
            val endChar = if (index == lines.size - 1) "。" else "；"
            sb.append(index + 1).append("、")
                .append(legacyMask(line.trim(), words))
                .append(endChar).append('\n')
        }
        return sb.toString().trim()
    }

    /** 容易互相干扰的敏感词：互相包含、前缀重叠 */
    private val wordPool = listOf(
        "纪委", "监委", "案管", "审理", "党风", "一体化", "查询", "措施",
        "线索", "案件", "信访", "法规", "保密", "委", "查", "询", "体",
        "案件查", "保密查", "一二",
    )

    private val filler = "今天完成工作并推进落实，。；、  123abcABC——\n\t"

    @Test
    fun `随机用例下与旧版算法逐字节一致`() {
        val random = Random(20261024)
        repeat(2000) { caseIndex ->
            val count = random.nextInt(1, 7)
            val picked = wordPool.shuffled(random).take(count)
            val words = picked.mapIndexed { index, w ->
                SensitiveWord(w, "p${index}x")
            }
            val sb = StringBuilder()
            repeat(random.nextInt(0, 15)) {
                if (random.nextInt(100) < 55) {
                    sb.append(picked.random(random))
                } else {
                    repeat(random.nextInt(1, 5)) {
                        sb.append(filler.random(random))
                    }
                }
            }
            var text = sb.toString()
            if (random.nextInt(100) < 25) text = "  $text"
            if (random.nextInt(100) < 25) text = "$text  \n"

            val engine = SensitiveWordEngine(words)
            assertEquals(
                "第 $caseIndex 条 replace 与旧版不一致",
                legacyMask(text, words),
                engine.replace(text),
            )
            assertEquals(
                "第 $caseIndex 条 formatDoneWorks 与旧版不一致",
                legacyFormatDoneWorks(text, words),
                SensitiveWordEngine.formatDoneWorks(text, engine),
            )
            assertEquals(
                "第 $caseIndex 条 formatPlanWorks 与旧版不一致",
                legacyFormatPlanWorks(text, words),
                SensitiveWordEngine.formatPlanWorks(text, engine),
            )
        }
    }
}
