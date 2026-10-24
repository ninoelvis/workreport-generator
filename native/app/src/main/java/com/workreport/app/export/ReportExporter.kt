package com.workreport.app.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.util.DateUtils
import com.workreport.app.util.MaskAnchor
import com.workreport.app.util.SensitiveWordEngine
import java.io.File
import java.util.Calendar
import java.util.Date

/** 一次导出的结果 */
data class ExportResult(
    /** 加密 ZIP 的文件名，例如「张三-2026年10月第3周工作报告.zip」 */
    val fileName: String,
    /** 不含扩展名的名字，同时也是 zip 内部 xlsx 的名字 */
    val baseName: String,
    /** 解压密码（yyyyMMdd，取本周周四） */
    val password: String,
    /** 加密 ZIP 在应用私有目录中的路径 */
    val file: File,
    /** 是否已成功复制到系统「下载」目录 */
    val savedToDownloads: Boolean,
    val year: Int,
    val month: Int,
    val weekNum: Int,
    val createdAt: Long,
)

/**
 * 报告导出器：负责把「本周填写内容」变成一个 AES 加密的 ZIP。
 *
 * 命名规则、解压密码、Excel 内容与样式**全部沿用 WebView 旧版**，未做任何改动。
 */
class ReportExporter(
    private val context: Context,
) {

    /**
     * 生成并导出。
     *
     * @param timeRangeText 本周工作时间，形如 2026.10.19-2026.10.25
     */
    fun export(
        userName: String,
        title: String,
        timeRangeText: String,
        doneWorks: String,
        planWorks: String,
        sensitiveWords: List<SensitiveWord>,
        outputDir: File,
        now: Date = Date(),
        excludedAnchors: Set<MaskAnchor> = emptySet(),
    ): ExportResult {
        // 命名与密码规则集中在 ReportNaming 里（可单元测试，避免改动时破坏兼容性）
        val plan = ReportNaming.plan(userName, timeRangeText)
        val password = plan.password
        val weekNum = plan.weekNum
        val year = plan.year
        val month = plan.month
        val baseName = plan.baseName
        val zipFileName = plan.zipFileName
        val xlsxFileName = plan.xlsxFileName

        val engine = SensitiveWordEngine(sensitiveWords)
        val doneFormatted = SensitiveWordEngine.formatDoneWorks(doneWorks, engine, excludedAnchors)
        val planFormatted = SensitiveWordEngine.formatPlanWorks(planWorks, engine, excludedAnchors)

        val xlsxBytes = XlsxWriter.toBytes(
            title = title,
            timeRange = timeRangeText.trim(),
            doneWorks = doneFormatted,
            planWorks = planFormatted,
        )

        // 内部文件的名字就是公司规定的 zipFileName —— 一个字符都不多。
        //
        // ⚠️ 血泪教训：之前给内部文件加过时间戳前缀（"1753..._张三-....zip"），
        // 以为「对外展示的名字没变就行」。结果是错的 ——
        // 邮件附件和系统分享都是把**这个文件**直接交给 FileProvider 的，
        // 而 FileProvider 查 OpenableColumns.DISPLAY_NAME 时返回的是
        // `File.getName()`（磁盘上的真实文件名），
        // 于是收件人看到的附件名就带上了时间戳 —— 改的是用户拿到手的文件名，红线破了。
        //
        // 现在改成：**文件名保持原样，唯一性由子目录承担**。
        // 同一周生成多次 → 落到不同子目录 → 互不覆盖；
        // 文件本身的名字从头到尾都是 zipFileName，分享/发信/下载三处拿到的是同一个名字。
        val target = targetFileFor(outputDir, now, zipFileName)
        EncryptedZipWriter.write(
            target = target,
            password = password,
            entries = listOf(xlsxFileName to xlsxBytes),
        )

        val saved = copyToDownloads(target, zipFileName)

        return ExportResult(
            fileName = zipFileName,
            baseName = baseName,
            password = password,
            file = target,
            savedToDownloads = saved,
            year = year,
            month = month,
            weekNum = weekNum,
            createdAt = now.time,
        )
    }

    /**
     * 把已有的文件复制到系统「下载」目录（供历史报告「二次下载」使用）。
     */
    fun copyExistingToDownloads(source: File, displayName: String): Boolean =
        copyToDownloads(source, displayName)

    /**
     * 复制到系统「下载」目录。
     *
     * Android 10 (API 29) 起使用 MediaStore，无需任何存储权限；
     * Android 8/9 直接写公共目录（Manifest 里已按 maxSdkVersion=28 申请权限）。
     */
    private fun copyToDownloads(source: File, displayName: String): Boolean {
        return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val dest = File(dir, displayName)
            source.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /**
         * 计算这次导出要写到的文件。
         *
         * 规则：
         *  - **文件名 = 公司规定的 zipFileName，一个字符都不改**
         *    （下载目录、邮件附件、系统分享三处拿到的必须是同一个名字）
         *  - 每次生成落到独立子目录，避免同一周生成多次互相覆盖
         */
        internal fun targetFileFor(outputDir: File, now: Date, zipFileName: String): File {
            val dir = File(outputDir, now.time.toString()).apply { mkdirs() }
            return File(dir, zipFileName)
        }

        /** 应用私有目录下的报告存放位置（用于 FileProvider 分享与历史记录） */
        fun reportsDir(context: Context): File =
            File(context.filesDir, "reports").apply { mkdirs() }

        /** 生成一个可用于分享的 content:// URI */
        fun shareUri(context: Context, file: File): Uri =
            FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
    }
}
