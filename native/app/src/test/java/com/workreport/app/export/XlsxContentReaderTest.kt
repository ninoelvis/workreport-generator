package com.workreport.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「解压 → 解析 → 还原内容」的完整往返。
 *
 * 这是历史报告预览功能的核心：用户在历史页点开一份报告，
 * 要看到的就是压缩包里那个 xlsx 真正的内容。
 * 所以这里走的是和生产环境完全一样的链路：
 * XlsxWriter 写 → EncryptedZipWriter 加密 → EncryptedZipReader 解密 → XlsxContentReader 解析。
 */
class XlsxContentReaderTest {

    private val password = "20261023"

    private fun makeXlsx(): ByteArray {
        val file = File.createTempFile("wr_content", ".xlsx")
        try {
            XlsxWriter.write(
                target = file,
                title = "2026年10月第3周工作报告",
                timeRange = "2026.10.19-2026.10.25",
                doneWorks = "参加部门例会\n整理案件台账\n完成三份材料",
                planWorks = "继续推进办理进度\n归档卷宗",
            )
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    @Test
    fun `解析出四个单元格的内容`() {
        val content = XlsxContentReader.read(makeXlsx())
        assertNotNull(content)
        requireNotNull(content)
        assertEquals("2026年10月第3周工作报告", content.title)
        assertEquals("2026.10.19-2026.10.25", content.timeRange)
        assertEquals("参加部门例会\n整理案件台账\n完成三份材料", content.doneWorks)
        assertEquals("继续推进办理进度\n归档卷宗", content.planWorks)
    }

    @Test
    fun `单元格里的换行要原样保留`() {
        val content = XlsxContentReader.read(makeXlsx())
        requireNotNull(content)
        assertTrue("换行应保留", content.doneWorks.contains("\n"))
        assertEquals(3, content.doneWorks.lines().size)
    }

    @Test
    fun `特殊字符不会被 XML 转义破坏`() {
        val file = File.createTempFile("wr_special", ".xlsx")
        try {
            XlsxWriter.write(
                target = file,
                title = "标题 & <标签> \"引号\"",
                timeRange = "2026.10.19-2026.10.25",
                doneWorks = "核对《民法典》第37条；含符号 & < > \" '",
                planWorks = "正常内容",
            )
            val content = XlsxContentReader.read(file.readBytes())
            requireNotNull(content)
            assertEquals("标题 & <标签> \"引号\"", content.title)
            assertEquals("核对《民法典》第37条；含符号 & < > \" '", content.doneWorks)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `空内容解析成空字符串而不是报错`() {
        val file = File.createTempFile("wr_empty", ".xlsx")
        try {
            XlsxWriter.write(file, "", "", "", "")
            val content = XlsxContentReader.read(file.readBytes())
            requireNotNull(content)
            assertEquals("", content.title)
            assertEquals("", content.doneWorks)
            assertTrue(content.isEmpty)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `不是 xlsx 的数据返回 null 而不是抛异常`() {
        assertNull(XlsxContentReader.read(byteArrayOf()))
        assertNull(XlsxContentReader.read("这不是 xlsx".toByteArray()))
        assertNull(XlsxContentReader.read(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)))
    }

    @Test
    fun `完整链路：写入 → 加密 → 解密 → 解析`() {
        val zip = File.createTempFile("wr_full", ".zip")
        try {
            val xlsx = makeXlsx()
            EncryptedZipWriter.write(
                target = zip,
                password = password,
                entries = listOf("张三-2026年10月第3周工作报告.xlsx" to xlsx),
            )
            val content = EncryptedZipReader.readContent(zip, password)
            requireNotNull(content)
            assertEquals("2026年10月第3周工作报告", content.title)
            assertEquals("2026.10.19-2026.10.25", content.timeRange)
            assertTrue(content.doneWorks.contains("参加部门例会"))
            assertTrue(content.planWorks.contains("继续推进办理进度"))
        } finally {
            zip.delete()
        }
    }

    @Test
    fun `密码不对时解不出内容`() {
        val zip = File.createTempFile("wr_wrongpw", ".zip")
        try {
            EncryptedZipWriter.write(
                target = zip,
                password = password,
                entries = listOf("a.xlsx" to makeXlsx()),
            )
            assertNull(EncryptedZipReader.readContent(zip, "99999999"))
        } finally {
            zip.delete()
        }
    }

    @Test
    fun `文件不存在时返回 null`() {
        assertNull(EncryptedZipReader.readContent(File("/tmp/绝对不存在-xyz.zip"), password))
    }
}
