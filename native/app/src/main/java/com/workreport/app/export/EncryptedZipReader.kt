package com.workreport.app.export

import net.lingala.zip4j.ZipFile
import java.io.File

/**
 * 读取加密 ZIP 里的内容。
 *
 * 这个 App 生成的压缩包是 AES-256 + HMAC-SHA1（WinZip AE-1）加密的，
 * 密码就是本周周四的日期，App 自己算得出，所以解密是「零输入」的。
 *
 * 解出来的 xlsx 本身是普通 zip，用 [XlsxContentReader] 解析。
 */
object EncryptedZipReader {

    /**
     * 从加密 zip 里取出里面那个 xlsx 的字节内容。
     *
     * @param zipFile 加密的 zip 文件
     * @param password 解压密码
     * @return xlsx 字节；找不到或解密失败返回 null
     */
    fun readFirstXlsx(zipFile: File, password: String): ByteArray? {
        if (!zipFile.exists() || zipFile.length() == 0L) return null
        return try {
            val zf = ZipFile(zipFile)
            zf.setPassword(password.toCharArray())
            val header = zf.fileHeaders.firstOrNull { h ->
                !h.isDirectory && h.fileName.endsWith(".xlsx", ignoreCase = true)
            } ?: return null
            zf.getInputStream(header).use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 一步到位：解密 zip 并解析出周报内容。
     */
    fun readContent(zipFile: File, password: String): XlsxContentReader.Content? {
        val bytes = readFirstXlsx(zipFile, password) ?: return null
        return XlsxContentReader.read(bytes)
    }
}
