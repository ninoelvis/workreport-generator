package com.workreport.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Date

/**
 * 导出产物的**文件名**锁死测试。
 *
 * ⚠️ 公司红线：用户拿到手的文件，名字一个字符都不能多、不能少：
 *
 * - ZIP 文件名：`{姓名}-{YYYY}年{M}月第{N}周工作报告.zip`
 * - ZIP 内的 Excel：同名 `.xlsx`
 * - 磁盘上的内部文件、复制到「下载」目录的那份、邮件附件、系统分享出去的，
 *   **全都必须是同一个名字**
 *
 * 背景：beta1 曾经给内部文件加过时间戳前缀（"1753..._张三-....zip"），
 * 以为「对外展示名没变就行」。结果邮件附件和系统分享里收件人看到的
 * 就是带时间戳的名字 —— 因为 FileProvider 的 DISPLAY_NAME 取的是
 * `File.getName()`，也就是磁盘上的真实文件名。
 * 这个测试就是为了防止同样的错再犯一次。
 */
class ReportFileNameTest {

    private val timeRange = "2026.10.19-2026.10.25"

    // ---------- 命名规则本身 ----------

    @Test
    fun `zip 文件名严格符合公司格式`() {
        val plan = ReportNaming.plan("张三", timeRange)
        assertEquals("张三-2026年10月第3周工作报告.zip", plan.zipFileName)
        assertTrue(
            "文件名必须匹配规定格式，实际：${plan.zipFileName}",
            Regex("^.+-\\d{4}年\\d{1,2}月第\\d+周工作报告\\.zip$").matches(plan.zipFileName),
        )
    }

    @Test
    fun `zip 内的 excel 与 zip 同名`() {
        val plan = ReportNaming.plan("张三", timeRange)
        assertEquals("张三-2026年10月第3周工作报告.xlsx", plan.xlsxFileName)
        assertEquals(plan.baseName + ".xlsx", plan.xlsxFileName)
        assertEquals(plan.baseName + ".zip", plan.zipFileName)
    }

    @Test
    fun `没填姓名时用「姓名」两个字`() {
        val plan = ReportNaming.plan("   ", timeRange)
        assertEquals("姓名-2026年10月第3周工作报告.zip", plan.zipFileName)
    }

    // ---------- 内部文件：名字必须一模一样，唯一性靠目录 ----------

    @Test
    fun `内部文件名就是公司规定的名字 没有任何前缀后缀`() {
        val plan = ReportNaming.plan("张三", timeRange)
        val target = ReportExporter.targetFileFor(
            outputDir = File("/tmp/wr-test-out"),
            now = Date(1_700_000_000_000L),
            zipFileName = plan.zipFileName,
        )
        assertEquals("张三-2026年10月第3周工作报告.zip", target.name)
        assertEquals(plan.zipFileName, target.name)
    }

    @Test
    fun `同一周生成多次 路径不同但名字相同`() {
        val plan = ReportNaming.plan("张三", timeRange)
        val dir = File("/tmp/wr-test-out2")
        val first = ReportExporter.targetFileFor(dir, Date(1_700_000_000_000L), plan.zipFileName)
        val second = ReportExporter.targetFileFor(dir, Date(1_700_000_060_000L), plan.zipFileName)
        assertNotEquals("两次生成不能写到同一个文件", first.path, second.path)
        assertEquals(first.name, second.name)
        assertEquals(plan.zipFileName, first.name)
    }

    // ---------- 真正写出来的 zip：外层名 + 内层名 ----------

    @Test
    fun `写出来的 zip 外层文件名和内层条目名都对`() {
        // 中文文件名要落盘，JVM 的文件系统编码必须是 UTF-8，
        // 否则 sun.jnu.encoding 是 ASCII 时 File→Path 会抛 InvalidPathException。
        // Android 一直是 UTF-8，这里只是防止在非 UTF-8 的开发机上误报。
        assumeTrue(
            "当前 JVM 文件系统编码不是 UTF-8，跳过落盘验证",
            (System.getProperty("sun.jnu.encoding") ?: "").contains("UTF", ignoreCase = true),
        )
        val plan = ReportNaming.plan("张三", timeRange)
        val xlsxBytes = XlsxWriter.toBytes(
            title = "2026年10月工作报告",
            timeRange = timeRange,
            doneWorks = "完成共1项工作内容：\n1、完成查询工作。",
            planWorks = "下周预计共1项工作任务：\n1、推进查询。",
        )
        // 走真实的落盘路径：外层文件名就是公司规定的那个
        val outDir = File("/tmp/wr-test-out3")
        val zip = ReportExporter.targetFileFor(outDir, Date(1_700_000_000_000L), plan.zipFileName)
        try {
            EncryptedZipWriter.write(
                target = zip,
                password = plan.password,
                entries = listOf(plan.xlsxFileName to xlsxBytes),
            )
            // 外层：文件本身的名字
            assertEquals("张三-2026年10月第3周工作报告.zip", zip.name)
            assertEquals(plan.zipFileName, zip.name)

            // 内层：zip 里的条目名（zip 是 AES 加密的，标准 ZipFile 读不了，用 zip4j）
            val entries = net.lingala.zip4j.ZipFile(zip, plan.password.toCharArray())
                .fileHeaders
                .map { it.fileName }
            assertEquals(
                "zip 里的条目名不对，实际：$entries",
                listOf("张三-2026年10月第3周工作报告.xlsx"),
                entries,
            )

            // 内层内容：解密后与 XlsxWriter 产出的字节完全一致
            val decrypted = EncryptedZipReader.readFirstXlsx(zip, plan.password)
            assertTrue("解密失败", decrypted != null)
            assertTrue(
                "xlsx 字节与生成的不一致",
                decrypted!!.contentEquals(xlsxBytes),
            )
        } finally {
            zip.delete()
            zip.parentFile?.delete()
        }
    }
}
