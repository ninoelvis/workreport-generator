package com.workreport.app.export

import net.lingala.zip4j.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 加密 ZIP 测试。
 *
 * 与旧版 zip.js（`encryptionStrength: 3`）保持一致：
 * **AES-256 + HMAC-SHA1（WinZip AE-1）+ Deflate**。
 */
class EncryptedZipWriterTest {

    private val password = "20261022"
    private val entryName = "张三-2026年10月第3周工作报告.xlsx"
    private val payload = "这是一个测试文件内容".toByteArray()

    private fun makeZip(): File {
        val file = File.createTempFile("wr_t", ".zip")
        EncryptedZipWriter.write(file, password, listOf(entryName to payload))
        return file
    }

    @Test
    fun `正确密码可以解密出原始内容`() {
        val zip = makeZip()
        try {
            val zf = ZipFile(zip)
            zf.setPassword(password.toCharArray())
            val data = zf.getInputStream(zf.fileHeaders.first()).readBytes()
            assertEquals(String(payload), String(data))
        } finally {
            zip.delete()
        }
    }

    @Test
    fun `错误密码解密失败`() {
        val zip = makeZip()
        try {
            val zf = ZipFile(zip)
            zf.setPassword("wrong-password".toCharArray())
            var failed = false
            try {
                zf.getInputStream(zf.fileHeaders.first()).readBytes()
            } catch (e: Exception) {
                failed = true
            }
            assertTrue("错误密码应当解密失败", failed)
        } finally {
            zip.delete()
        }
    }

    @Test
    fun `条目被标记为加密且是 AES`() {
        val zip = makeZip()
        try {
            val zf = ZipFile(zip)
            val header = zf.fileHeaders.first()
            assertTrue(header.isEncrypted)
            assertEquals("AES", header.encryptionMethod.toString())
            assertEquals(entryName, header.fileName)
        } finally {
            zip.delete()
        }
    }

    @Test
    fun `使用标准 WinZip AES extra field`() {
        val zip = makeZip()
        try {
            val bytes = zip.readBytes()
            // 0x9901 = WinZip AES extra field id（小端存放：01 99）
            val idx = indexOf(bytes, byteArrayOf(0x01, 0x99.toByte(), 0x07, 0x00))
            assertTrue("未找到标准 AES extra field", idx >= 0)
            // version = 1 (AE-1 / HMAC-SHA1), vendor = 'AE', strength = 3 (AES-256)
            val p = idx + 4
            assertEquals(1, bytes[p].toInt() and 0xFF)
            assertEquals(0, bytes[p + 1].toInt())
            assertEquals('A'.code, bytes[p + 2].toInt())
            assertEquals('E'.code, bytes[p + 3].toInt())
            assertEquals(3, bytes[p + 4].toInt() and 0xFF)
            // compression method = 8 (Deflate)
            assertEquals(8, bytes[p + 5].toInt() and 0xFF)
        } finally {
            zip.delete()
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
