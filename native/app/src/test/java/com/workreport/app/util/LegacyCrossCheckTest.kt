package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 临时交叉验证：拿旧版 WebView（JS）算法跑出来的结果，逐条对拍原生引擎。
 *
 * 用法：
 *   CROSS_DIR=/workspace/cross gradle :app:testDebugUnitTest --tests '*LegacyCrossCheckTest*'
 */
class LegacyCrossCheckTest {

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    @Test
    fun `原生引擎与旧版 JS 算法逐条一致`() {
        val dir = System.getenv("CROSS_DIR") ?: return
        val dirFile = File(dir)
        assumeTrue("交叉验证数据不在 $dir", dirFile.isDirectory)
        val casesFile = File(dirFile, "cases.tsv")
        val expectedFile = File(dirFile, "expected.tsv")
        assumeTrue(casesFile.exists() && expectedFile.exists())

        val caseLines = casesFile.readLines()
        val expectedLines = expectedFile.readLines()
        assertEquals("用例数与期望数不一致", caseLines.size, expectedLines.size)

        var checked = 0
        caseLines.forEachIndexed { index, caseLine ->
            val parts = caseLine.split('\t')
            assertEquals("第 $index 行用例格式不对", 2, parts.size)
            val words = parts[0].split(',').map { spec ->
                val kv = spec.split(':')
                SensitiveWord(kv[0], kv[1])
            }
            val text = unescape(parts[1])

            val exp = expectedLines[index].split('\t')
            assertEquals("第 $index 行期望格式不对", 3, exp.size)
            val expMasked = unescape(exp[0])
            val expDone = unescape(exp[1])
            val expPlan = unescape(exp[2])

            val engine = SensitiveWordEngine(words)
            assertEquals("第 $index 条 replace 不一致", expMasked, engine.replace(text))
            assertEquals(
                "第 $index 条 formatDoneWorks 不一致",
                expDone,
                SensitiveWordEngine.formatDoneWorks(text, engine),
            )
            assertEquals(
                "第 $index 条 formatPlanWorks 不一致",
                expPlan,
                SensitiveWordEngine.formatPlanWorks(text, engine),
            )
            checked++
        }
        println("交叉验证用例数: $checked")
        assertEquals(caseLines.size, checked)
    }
}
