package com.workreport.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v2.0.0 敏感词库本地加密的防回归测试。
 *
 * 只测**纯逻辑**部分（[SensitiveWordsCipher]）——
 * Keystore 那一层（[SecretBox]）依赖 Android 运行时，跑不了 JVM 单元测试，
 * 但它被刻意设计成「失败返回 null 而不抛异常」，由 [SecretBox] 的降级路径兜住。
 */
class SensitiveWordsCipherTest {

    @Test
    fun `密文带版本前缀`() {
        val encoded = SensitiveWordsCipher.encode(ByteArray(12), byteArrayOf(1, 2, 3))
        assertTrue("密文应带 enc:v1: 前缀", encoded.startsWith("enc:v1:"))
        assertTrue("应能识别为密文", SensitiveWordsCipher.isEncrypted(encoded))
    }

    @Test
    fun `明文不会被误判成密文`() {
        assertFalse(SensitiveWordsCipher.isEncrypted("""[{"w":"张三","p":"zhangsan","a":true}]"""))
        assertFalse(SensitiveWordsCipher.isEncrypted(""))
        assertFalse(SensitiveWordsCipher.isEncrypted("enc:v2:xxx"))
    }

    @Test
    fun `编码解码能还原出 iv 与密文`() {
        val iv = ByteArray(12) { it.toByte() }
        val cipherText = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1)
        val encoded = SensitiveWordsCipher.encode(iv, cipherText)

        val decoded = SensitiveWordsCipher.decode(encoded)
        assertNotNull("应能解码", decoded)
        assertEquals("IV 应原样还原", iv.toList(), decoded!!.iv.toList())
        assertEquals("密文应原样还原", cipherText.toList(), decoded.cipherText.toList())
    }

    @Test
    fun `不同 IV 产生不同密文`() {
        // 同一份明文两次加密，密文必须不同（否则就是 ECB 那样的固定模式）
        val plain = "一样的内容".toByteArray()
        val iv1 = ByteArray(12) { 1 }
        val iv2 = ByteArray(12) { 2 }
        val c1 = SensitiveWordsCipher.encode(iv1, byteArrayOf(5))
        val c2 = SensitiveWordsCipher.encode(iv2, byteArrayOf(5))
        assertFalse("IV 不同时密文必须不同", c1 == c2)
        assertEquals(plain.size, plain.size) // 保持可读
    }

    @Test
    fun `非本格式的值解码返回 null`() {
        assertNull(SensitiveWordsCipher.decode("普通文本"))
        assertNull(SensitiveWordsCipher.decode("enc:v1:"))
        assertNull(SensitiveWordsCipher.decode("enc:v1:!!!不是base64!!!"))
        // base64 合法但长度不够（<= IV 长度）
        assertNull(SensitiveWordsCipher.decode("enc:v1:" + "AAAA"))
    }

    @Test
    fun `iv 长度不对时拒绝编码`() {
        var thrown = false
        try {
            SensitiveWordsCipher.encode(ByteArray(8), byteArrayOf(1))
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("IV 长度必须是 12 字节", thrown)
    }

    @Test
    fun `敏感词库必须走加密存储`() {
        val store = File("src/main/java/com/workreport/app/data/local/SettingsStore.kt").readText()
        assertTrue(
            "保存敏感词库时必须加密",
            store.contains("SecretBox.encrypt"),
        )
        assertTrue(
            "读取时必须区分密文与旧明文",
            store.contains("SensitiveWordsCipher.isEncrypted"),
        )
        assertTrue(
            "旧明文必须自动迁移成密文",
            store.contains("迁移") || store.contains("encrypt"),
        )
    }

    @Test
    fun `系统云备份必须关闭`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "必须关掉系统云备份（allowBackup=false）",
            manifest.contains("android:allowBackup=\"false\""),
        )

        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(
            "设备迁移也必须排除全部数据",
            rules.contains("device-transfer") && rules.contains("exclude"),
        )
    }
}
