package com.workreport.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.ConfigCodec
import com.workreport.app.data.local.ConfigSnapshot
import com.workreport.app.export.EncryptedZipWriter
import com.workreport.app.util.DateUtils
import net.lingala.zip4j.ZipFile
import java.io.File
import java.util.Calendar

/**
 * 个人配置的备份与恢复。
 *
 * 只备份「这台手机上属于我个人的东西」：姓名、主题、敏感词库、两套快捷模板、
 * 邮箱偏好、周报提醒设置、首页未完成的草稿。
 * **不备份**历史报告记录与已生成的 ZIP —— 那些已经导出到「下载」目录了。
 *
 * 备份文件用 **AES-256 加密的 ZIP**（复用周报压缩包同一套能力），
 * 因为里面装着敏感词库和真实工作模板，明文外传有泄漏风险。
 */
object ConfigBackupManager {

    /** 备份包内的文件名 */
    private const val ENTRY_NAME = "workreport-config.txt"

    /** 存放「导入前快照」的 SharedPreferences */
    private const val PREFS_NAME = "workreport_config_backup"
    private const val KEY_ROLLBACK = "rollback_snapshot"

    /** 导入结果 */
    sealed interface ImportOutcome {
        /** 人类可读的结果说明 */
        val message: String
        data class Success(override val message: String, val snapshot: ConfigSnapshot) : ImportOutcome
        data class Failure(override val message: String) : ImportOutcome
    }

    /**
     * 生成备份内容（加密 ZIP 的字节）。
     *
     * @param password 加密密码。建议直接用周报密码规则，用户零学习成本。
     */
    fun buildBackupBytes(password: String): ByteArray {
        val snapshot = ServiceLocator.settings.exportConfig()
        val text = ConfigCodec.encode(snapshot)
        val temp = File.createTempFile("wr_backup", ".zip")
        return try {
            EncryptedZipWriter.write(
                target = temp,
                password = password,
                entries = listOf(ENTRY_NAME to text.toByteArray(Charsets.UTF_8)),
            )
            temp.readBytes()
        } finally {
            temp.delete()
        }
    }

    /**
     * 解开备份内容，读出配置快照。
     *
     * 密码错 / 文件损坏 / 格式不认识，都会返回带中文原因的 Failure。
     */
    fun readSnapshot(bytes: ByteArray, password: String): ImportOutcome {
        if (bytes.isEmpty()) {
            return ImportOutcome.Failure("文件是空的，换一个文件试试")
        }
        val temp = try {
            File.createTempFile("wr_backup_read", ".zip")
        } catch (e: Exception) {
            return ImportOutcome.Failure("无法创建临时文件：${e.message ?: "未知错误"}")
        }
        return try {
            temp.writeBytes(bytes)
            val zf = ZipFile(temp)
            zf.setPassword(password.toCharArray())
            val header = zf.fileHeaders.firstOrNull { !it.isDirectory }
                ?: return ImportOutcome.Failure("这个压缩包里没有配置文件")
            val text = zf.getInputStream(header).use { it.readBytes().toString(Charsets.UTF_8) }
            val snapshot = ConfigCodec.decode(text)
                ?: return ImportOutcome.Failure("文件格式不认识，可能不是本 App 的备份")
            ImportOutcome.Success("已读取配置", snapshot)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("password", ignoreCase = true) ||
                msg.contains("incorrect", ignoreCase = true) ||
                msg.contains("Wrong", ignoreCase = true)
            ) {
                ImportOutcome.Failure("密码不正确，请重新输入")
            } else {
                ImportOutcome.Failure("读取失败：${msg.ifBlank { "文件可能已损坏" }}")
            }
        } finally {
            temp.delete()
        }
    }

    /**
     * 把配置应用到本机。
     *
     * @param merge true=合并（敏感词按词去重、模板按正文去重，姓名/提醒以导入方为准，
     *              当前正在写的草稿保留不动）；false=整体覆盖。
     */
    fun apply(snapshot: ConfigSnapshot, merge: Boolean): ImportOutcome {
        // 覆盖前先存一份快照，用户后悔了能回滚
        saveRollback(ServiceLocator.settings.exportConfig())
        ServiceLocator.settings.importConfig(snapshot, merge)
        return ImportOutcome.Success(
            if (merge) "已合并配置" else "已覆盖配置",
            snapshot,
        )
    }

    // ---------------- 回滚 ----------------

    /** 导入前把当前配置存一份，用户可以一键退回 */
    fun saveRollback(snapshot: ConfigSnapshot) {
        val prefs = ServiceLocator.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ROLLBACK, ConfigCodec.encode(snapshot)).apply()
    }

    fun hasRollback(): Boolean {
        val prefs = ServiceLocator.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_ROLLBACK, null).isNullOrBlank()
    }

    /** 回到上一次导入之前的配置 */
    fun rollback(): ImportOutcome {
        val prefs = ServiceLocator.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ROLLBACK, null)
            ?: return ImportOutcome.Failure("没有可回滚的记录")
        val snapshot = ConfigCodec.decode(raw)
            ?: return ImportOutcome.Failure("回滚记录已损坏")
        ServiceLocator.settings.importConfig(snapshot, merge = false)
        return ImportOutcome.Success("已恢复到导入前的配置", snapshot)
    }

    // ---------------- 导出到「下载」目录 ----------------

    /**
     * 把备份写到系统「下载」目录。
     *
     * Android 10 (API 29) 起走 MediaStore 免权限；Android 8/9 直接写公共目录。
     */
    fun exportToDownloads(context: Context, password: String): String? {
        val bytes = try {
            buildBackupBytes(password)
        } catch (e: Exception) {
            return null
        }
        val stamp = DateUtils.fmtDate8(Calendar.getInstance().time)
        val fileName = "工作报告配置备份-$stamp.zip"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out -> out.write(bytes) } ?: return null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, fileName).writeBytes(bytes)
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }
}
