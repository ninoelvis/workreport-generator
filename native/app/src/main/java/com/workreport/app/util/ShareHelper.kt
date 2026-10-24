package com.workreport.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 系统分享：把文件丢给手机自带的分享面板。
 *
 * 刻意**不指定**任何目标应用（不写 setPackage / setComponent），
 * 系统会列出所有支持该类型的目标（微信、企业微信、QQ、邮件、文件管理器、蓝牙…），
 * 由用户自己挑。
 *
 * ⚠️ 三个容易踩的坑，这里都处理了：
 *  1. 不能传 file:// —— Android 7.0+ 会直接抛 FileUriExposedException，
 *     必须用 FileProvider 转成 content://。
 *  2. createChooser 会把原 Intent 包进 EXTRA_INTENT，**ClipData 不会自动继承**，
 *     不少接收方靠 ClipData 拿附件，不手动补就会出现「分享过去没有附件」。
 *  3. 必须显式加 FLAG_GRANT_READ_URI_PERMISSION，接收方才读得到文件。
 */
object ShareHelper {

    /**
     * 拉起系统分享面板分享一个文件。
     *
     * @param file 要分享的文件（一般是加密 ZIP）
     * @param mimeType 文件类型，默认 zip
     * @param title 分享面板标题
     * @return 是否成功拉起
     */
    fun shareFile(
        context: Context,
        file: File,
        mimeType: String = "application/zip",
        title: String = "分享周报",
    ): Boolean {
        if (!file.exists()) {
            Toast.makeText(context, "文件不存在，无法分享", Toast.LENGTH_SHORT).show()
            return false
        }
        return runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, title).apply {
                // 关键：chooser 不继承 ClipData，必须手动补，否则部分 App 收不到附件
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
            true
        }.getOrElse { e ->
            android.util.Log.e("ShareHelper", "拉起系统分享失败", e)
            AppLog.error("ShareHelper", "拉起系统分享失败", e)
            false
        }
    }
}
