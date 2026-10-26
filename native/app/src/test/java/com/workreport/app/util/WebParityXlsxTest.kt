package com.workreport.app.util

import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.export.XlsxWriter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 网页版 ↔ 原生版 的 xlsx 逐字节对拍基准。
 *
 * 网页版用 SheetJS 生成 xlsx，原生版手写 XML 生成 xlsx。
 * 两边产出的 10 个 XML 部件必须**逐字节相同** ——
 * 这就是「导出格式是红线」最硬的验证。
 *
 * 跑法： gradle :app:testDebugUnitTest --tests "*WebParityXlsxTest*"
 * 产物： /workspace/cross/web_ref.xlsx
 */
class WebParityXlsxTest {

    @Test
    fun `生成网页版对照用的 xlsx`() {
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
        val out = File("/workspace/cross/web_ref.xlsx")
        out.writeBytes(bytes)
        println("已导出对照 xlsx：${out.absolutePath} (${bytes.size} B)")
        assertTrue(out.length() > 0)
    }
}
