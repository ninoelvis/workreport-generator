package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 交叉验证：把原生版的脱敏结果导出成 TSV，
 * 供 JS 版（网页版）逐字节对拍。
 *
 * 跑法： gradle :app:testDebugUnitTest --tests "*MaskParityExportTest*"
 * 输入： /workspace/cross/cases.tsv     （词库 \t 文本，文本已转义）
 * 产物： /workspace/cross/kotlin_expected.tsv
 *        /workspace/cross/kotlin_hits.tsv
 *
 * ⚠️ 单元测试里的 org.json 是空壳（stub），所以全程用 TSV，不碰 JSON。
 */
class MaskParityExportTest {

    private fun esc(s: String): String = buildString {
        for (ch in s) {
            when (ch) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> append("\\r")
                else -> append(ch)
            }
        }
    }

    private fun unesc(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { append('\n'); i += 2 }
                    't' -> { append('\t'); i += 2 }
                    'r' -> { append('\r'); i += 2 }
                    '\\' -> { append('\\'); i += 2 }
                    else -> { append(c); i++ }
                }
            } else {
                append(c)
                i++
            }
        }
    }

    @Test
    fun `导出原生版脱敏结果供 JS 对拍`() {
        val casesFile = File("/workspace/cross/cases.tsv")
        if (!casesFile.exists()) {
            println("没有 cases.tsv，跳过导出")
            return
        }
        val lines = casesFile.readLines().filter { it.isNotEmpty() }
        val expOut = StringBuilder()
        val hitsOut = StringBuilder()
        var count = 0

        for (line in lines) {
            val tab = line.indexOf('\t')
            if (tab < 0) continue
            val dictPart = line.substring(0, tab)
            val text = unesc(line.substring(tab + 1))
            val rules = dictPart.split(',').mapNotNull { entry ->
                val c = entry.indexOf(':')
                if (c < 0) null else entry.substring(0, c) to entry.substring(c + 1)
            }
            val engine = SensitiveWordEngine(rules.map { SensitiveWord(it.first, it.second) })

            // 1) 全部脱敏
            val masked = engine.replace(text)
            val done = SensitiveWordEngine.formatDoneWorks(text, engine)
            val plan = SensitiveWordEngine.formatPlanWorks(text, engine)

            // 2) 放行命中里的第 1、3、5… 处（验证「按处豁免」）
            val hits = engine.analyzeMultiline(text).hits
            val excluded = hits.filterIndexed { index, _ -> index % 2 == 0 }.map { h -> h.anchor }.toSet()
            val maskedEx = engine.replace(text, excluded)
            val doneEx = SensitiveWordEngine.formatDoneWorks(text, engine, excluded)
            val planEx = SensitiveWordEngine.formatPlanWorks(text, engine, excluded)

            expOut.append(esc(masked)).append('\t')
                .append(esc(maskedEx)).append('\t')
                .append(esc(done)).append('\t')
                .append(esc(doneEx)).append('\t')
                .append(esc(plan)).append('\t')
                .append(esc(planEx)).append('\n')

            for (h in hits) {
                hitsOut.append(count).append('\t')
                    .append(esc(h.anchor.section.name)).append('\t')
                    .append(esc(h.anchor.before)).append('\t')
                    .append(esc(h.anchor.word)).append('\t')
                    .append(esc(h.anchor.after)).append('\t')
                    .append(h.anchor.nth).append('\t')
                    .append(esc(h.replacement)).append('\t')
                    .append(h.lineIndex).append('\t')
                    .append(h.position).append('\n')
            }
            count++
        }

        File("/workspace/cross/kotlin_expected.tsv").writeText(expOut.toString())
        File("/workspace/cross/kotlin_hits.tsv").writeText(hitsOut.toString())
        println("已导出 $count 组原生版结果")
        assertTrue(count > 0)
    }
}
