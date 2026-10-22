package com.workreport.app.export

import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.AesVersion
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * 加密 ZIP 打包。
 *
 * 与 WebView 旧版（zip.js，`encryptionStrength: 3`）保持一致：
 * **AES-256 + HMAC-SHA1**（WinZip AE-1），Deflate 压缩。
 * WinRAR / 7-Zip / Windows 资源管理器 / 手机解压软件都能正常打开。
 */
object EncryptedZipWriter {

    /**
     * 把 [entries] 打包成一个 AES-256 加密的 zip。
     *
     * @param password 加密密码
     */
    fun write(
        target: File,
        password: String,
        entries: List<Pair<String, ByteArray>>,
    ) {
        target.parentFile?.mkdirs()
        val params = ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            // AE-1 = AES-256 + HMAC-SHA1，与旧版 zip.js 生成的包一致
            aesVersion = AesVersion.ONE
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = CompressionLevel.NORMAL
        }

        FileOutputStream(target).use { fos ->
            ZipOutputStream(fos, password.toCharArray(), StandardCharsets.UTF_8).use { zos ->
                entries.forEach { (name, bytes) ->
                    params.fileNameInZip = name
                    zos.putNextEntry(params)
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
    }
}
