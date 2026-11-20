package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.export.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import java.security.MessageDigest

/**
 * 「导出格式是红线」的**长期回归保护**。
 *
 * 用与基线完全相同的输入生成 xlsx，然后和基线文件
 * `/workspace/cross/web_ref.xlsx` 的 10 个 XML 部件**逐字节比对**。
 *
 * 基线由 `WebParityXlsxTest` 用同样的代码、同样的输入生成
 * （那份基线同时也是网页版 SheetJS 产物的对拍参照）。
 *
 * 只要下面任何一个文件被改动：
 *   XlsxWriter / styles 模板 / 主题 / 工作表结构 / 内容格式化
 * 这个测试就会红 —— 也就是「红线被破坏」会被立刻发现。
 *
 * 基线文件不在（比如别的机器上跑）就跳过，不让测试假红。
 */
class XlsxBaselineParityTest {

    private val baseline = File("/workspace/cross/web_ref.xlsx")

    private fun parts(bytes: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                out[e.name] = zis.readBytes()
            }
        }
        return out
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(b).joinToString("") { "%02x".format(it) }

    @Test
    fun `导出的 xlsx 与基线逐字节一致`() {
        assumeTrue("基线文件不存在，跳过：${baseline.absolutePath}", baseline.exists())

        val rules = listOf(
            SensitiveWord("查询", "cx"),
            SensitiveWord("纪委", "jw"),
        )
        val engine = SensitiveWordEngine(rules)
        val doneRaw = "完成共2项工作内容样例\n配合纪委完成案件审理"
        val planRaw = "下周计划样例一\n下周计划样例二"

        val bytes = XlsxWriter.toBytes(
            title = "2026年10月第3周工作报告",
            timeRange = "2026.10.19-2026.10.25",
            doneWorks = SensitiveWordEngine.formatDoneWorks(doneRaw, engine),
            planWorks = SensitiveWordEngine.formatPlanWorks(planRaw, engine),
        )

        val now = parts(bytes)
        val base = parts(baseline.readBytes())

        assertEquals("部件数量不一样", base.size, now.size)
        base.keys.sorted().forEach { name ->
            val b = base[name] ?: error("基线缺部件 $name")
            val n = now[name] ?: error("本次缺部件 $name")
            assertEquals("部件 $name 的字节变了（红线被破坏）", sha(b), sha(n))
        }
        assertTrue("部件数应该是 10", base.size == 10)
        println("✅ xlsx 10 个部件与基线逐字节一致")
    }
}
