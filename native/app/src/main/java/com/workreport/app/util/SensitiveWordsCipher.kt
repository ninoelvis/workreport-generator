package com.workreport.app.util

import java.util.Base64

/**
 * 敏感词库密文的**纯逻辑**编解码（v2.0.0 起）。
 *
 * 为什么要加密：
 * 敏感词库（同事姓名、客户名、项目代号）原来在 SharedPreferences 里是**明文**。
 * 哪怕 App 本身不联网，系统云备份也会把它打包带走。
 * v2.0.0 起它以 AES-GCM 密文落盘，密钥存在 Android Keystore 里（**不进备份、不可导出**）。
 *
 * 存储格式：`enc:v1:<base64(iv ‖ ciphertext)>`
 *   · `enc:v1:` 前缀是**版本标记**，用来区分「旧的明文」与「新的密文」——
 *     迁移时靠它判断该走哪条路。
 *   · GCM 的 IV 固定 12 字节；ciphertext 末尾自带 16 字节认证标签，
 *     所以只要记住「前 12 字节是 IV」，剩下的就是密文。
 *
 * ⚠️ 刻意用 `java.util.Base64` 而不是 `android.util.Base64` ——
 * 后者在 JVM 单元测试里只是 stub，跑不了真正的编解码往返。
 */
object SensitiveWordsCipher {

    /** 密文前缀（含版本号） */
    const val PREFIX = "enc:v1:"

    /** GCM 的 IV 长度（字节） */
    const val IV_LENGTH = 12

    /**
     * 把 IV 与密文拼成一个 base64 字符串。
     *
     * @param iv 随机 IV，由 Keystore 生成的 cipher 产出
     * @param cipherText 密文（含 GCM 认证标签）
     */
    fun encode(iv: ByteArray, cipherText: ByteArray): String {
        require(iv.size == IV_LENGTH) { "GCM 的 IV 必须是 $IV_LENGTH 字节，实际 ${iv.size}" }
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return PREFIX + Base64.getEncoder().encodeToString(combined)
    }

    /** 解码结果 */
    data class Decoded(val iv: ByteArray, val cipherText: ByteArray)

    /**
     * 解析已存储的值。
     *
     * @return `null` 表示**不是本格式的密文**（比如是旧版明文，或根本不是敏感词库数据）
     */
    fun decode(stored: String): Decoded? {
        if (!stored.startsWith(PREFIX)) return null
        val body = stored.substring(PREFIX.length)
        if (body.isEmpty()) return null
        val combined = try {
            Base64.getDecoder().decode(body)
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (combined.size <= IV_LENGTH) return null
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val cipherText = combined.copyOfRange(IV_LENGTH, combined.size)
        return Decoded(iv, cipherText)
    }

    /** 值是不是密文 */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)
}
