package com.workreport.app.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 敏感词库的本地加密（Android Keystore + AES-256-GCM）。
 *
 * 密钥存在 **Android Keystore** 里：
 *   · 由系统保管，**不进应用数据、不会被备份、不可导出**；
 *   · 即使 App 自己的数据目录被人翻出来，没有密钥也解不开密文。
 *
 * ⚠️ Keystore 在少数场景下会不可用（设备重启后密钥需要用户解锁才能用、
 * 某些 ROM 的 Keystore 有 bug、用户重置了锁屏密码）。
 * 所以这里的每个方法都**返回 null 而不是抛异常**，
 * 由调用方降级（退回明文存储或默认词库），**绝不让 App 因为加密失败而崩掉**。
 */
object SecretBox {

    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "workreport_sensitive_words_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * 加密。
     *
     * @return 形如 [SensitiveWordsCipher.encode] 产出的字符串；失败返回 null
     */
    fun encrypt(plainText: ByteArray): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText)
        SensitiveWordsCipher.encode(cipher.iv, encrypted)
    } catch (e: Exception) {
        // Keystore 不可用 / 密钥失效 —— 交给调用方降级
        null
    }

    /**
     * 解密。
     *
     * @return 明文；失败（密文损坏、密钥失效）返回 null
     */
    fun decrypt(stored: String): ByteArray? {
        val decoded = SensitiveWordsCipher.decode(stored) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, decoded.iv),
            )
            cipher.doFinal(decoded.cipherText)
        } catch (e: Exception) {
            null
        }
    }

    /** Keystore 现在能不能用 */
    fun isAvailable(): Boolean = try {
        getOrCreateKey()
        true
    } catch (e: Exception) {
        false
    }

    /**
     * 取密钥，没有就建一个。
     *
     * 密钥的用途只有「加解密敏感词库」，因此不需要用户认证
     * （`setUserAuthenticationRequired(false)`）——
     * 否则用户每次开 App 都要解锁才能读敏感词库，体验很差。
     */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
