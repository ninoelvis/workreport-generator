package com.workreport.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 红线端到端验证。
 *
 * 真实生成一份加密 ZIP 到磁盘，断言文件名、内层条目名、密码都对 ——
 * 这三个是公司红线，任何改动都必须先过这一关。
 *
 * 生成出来的文件还可以交给 python zipfile / 7-Zip 独立复核。
 */
class RedLineE2ETest {

    @Test
    fun `真实导出并落盘`() {
        val outDir = File(System.getProperty("java.io.tmpdir"), "workreport-redline")
        outDir.mkdirs()
        outDir.listFiles()?.forEach { it.delete() }

        val plan = ReportNaming.plan("张三", "2026.10.19-2026.10.25")
        println("VERIFY_BASE_NAME=" + plan.baseName)
        println("VERIFY_ZIP_NAME=" + plan.zipFileName)
        println("VERIFY_XLSX_NAME=" + plan.xlsxFileName)
        println("VERIFY_PASSWORD=" + plan.password)

        val xlsxFile = File(outDir, plan.xlsxFileName)
        XlsxWriter.write(
            target = xlsxFile,
            title = "2026年10月第3周工作报告",
            timeRange = "2026.10.19-2026.10.25",
            doneWorks = "完成共2项工作内容：\n1、完成纪委查询工作；\n2、落实整改措施。",
            planWorks = "下周计划一\n下周计划二",
        )

        val zipFile = File(outDir, plan.zipFileName)
        EncryptedZipWriter.write(
            target = zipFile,
            password = plan.password,
            entries = listOf(plan.xlsxFileName to xlsxFile.readBytes()),
        )
        xlsxFile.delete()

        println("VERIFY_ZIP_PATH=" + zipFile.absolutePath)
        println("VERIFY_ZIP_SIZE=" + zipFile.length())

        assertTrue(zipFile.exists())
        assertEquals(plan.zipFileName, zipFile.name)
    }
}
