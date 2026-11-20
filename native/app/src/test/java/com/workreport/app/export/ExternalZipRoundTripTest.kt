package com.workreport.app.export

import com.workreport.app.util.ExternalZipPassword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 端到端验证：**App 自己生成的压缩包**能不能被「外部文件识别」这条链路正确处理。
 *
 * 这条链路是：
 *   1. App 按红线规则生成加密 ZIP（文件名 + 密码）
 *   2. 用户把它拷进「存放目录」
 *   3. App 扫描目录认出这个外部文件
 *   4. 从文件名反推密码
 *   5. 用推出来的密码解密、读出 Excel
 *
 * 这里把 1 → 4 → 5 完整跑一遍，确保外部识别不会因为任何偏差而读不出内容。
 *
 * ⚠️ 同时验证：**新增的存放目录功能没有动到命名和内容**。
 */
class ExternalZipRoundTripTest {

    private fun exportSample(dir: File, userName: String, timeRange: String): Triple<String, String, File> {
        val plan = ReportNaming.plan(userName, timeRange)
        val target = File(dir, plan.zipFileName)
        EncryptedZipWriter.write(
            target = target,
            password = plan.password,
            entries = listOf(plan.xlsxFileName to XlsxWriter.toBytes(
                title = "2026年10月工作报告",
                timeRange = timeRange,
                doneWorks = "本周已完成工作梳理样例\n第二行内容",
                planWorks = "下周计划一\n下周计划二",
            )),
        )
        return Triple(plan.zipFileName, plan.password, target)
    }

    @Test
    fun `从文件名反推的密码能解开 App 自己生成的压缩包`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "wr_ext_rt")
        dir.mkdirs()
        val timeRange = "2026.10.19-2026.10.25"
        val (fileName, realPassword, zipFile) = exportSample(dir, "张三", timeRange)

        // 真实用到的密码必须在候选列表里
        val candidates = ExternalZipPassword.candidates(fileName)
        assertNotNull("候选列表是空的：$fileName", candidates)
        assertTrue("候选里没有真实密码 $realPassword：$candidates", candidates.contains(realPassword))

        // 用候选密码依次尝试，必须能解开
        var content: XlsxContentReader.Content? = null
        for (c in candidates) {
            content = EncryptedZipReader.readContent(zipFile, c)
            if (content != null) break
        }
        assertNotNull("用候选密码都解不开", content)
        assertNotNull("用推断的密码解不开", content)
        assertEquals("2026.10.19-2026.10.25", content!!.timeRange)
        assertTrue(
            "读出来的正文不对：${content.doneWorks}",
            content.doneWorks.contains("本周已完成工作梳理样例"),
        )
        assertTrue(
            "读出来的计划不对：${content.planWorks}",
            content.planWorks.contains("下周计划一"),
        )

        zipFile.delete()
    }

    @Test
    fun `压缩包内部结构没有因为新功能发生变化`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "wr_ext_rt2")
        dir.mkdirs()
        val timeRange = "2026.10.19-2026.10.25"
        val (fileName, _, zipFile) = exportSample(dir, "张三", timeRange)

        // 外层文件名：{姓名}-{年}年{月}月第{周}周工作报告.zip
        assertEquals("张三-2026年10月第3周工作报告.zip", fileName)

        // 内层条目名：同名 .xlsx
        val names = net.lingala.zip4j.ZipFile(zipFile).fileHeaders.map { it.fileName }
        assertEquals(listOf("张三-2026年10月第3周工作报告.xlsx"), names)

        // 加密方式：WinZip AE-1（AES-256 + HMAC-SHA1）
        // zip4j 对 AES 条目的 compressionMethod 返回内部标记值，
        // 外部工具（7-Zip）看到的是 method 99 —— 那部分由 RedLineE2ETest 用外部工具核对。
        val header = net.lingala.zip4j.ZipFile(zipFile).fileHeaders.first()
        assertTrue("条目没有加密", header.isEncrypted)

        zipFile.delete()
    }

    @Test
    fun `换一个姓名的压缩包同样能被反推`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "wr_ext_rt3")
        dir.mkdirs()
        listOf("张三", "李四", "王小明", "欧阳娜娜").forEach { name ->
            val (fileName, realPassword, zipFile) = exportSample(dir, name, "2026.10.19-2026.10.25")
            assertTrue(
                "候选里没有真实密码：$fileName",
                ExternalZipPassword.candidates(fileName).contains(realPassword),
            )
            assertNotNull("解不开：$fileName", EncryptedZipReader.readContent(zipFile, realPassword))
            zipFile.delete()
        }
    }
}
