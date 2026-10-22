package com.workreport.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** 一个可用的邮件应用 */
data class MailApp(
    val packageName: String,
    val label: String,
)

/**
 * 邮件应用探测与「带附件拉起邮箱」。
 *
 * 逻辑与 WebView 旧版的原生桥完全一致：
 *  1. 能响应 `ACTION_SENDTO + mailto:` 的应用 —— 邮件客户端的可靠特征；
 *  2. 应用名/包名里带 mail / email / inbox / outlook / 邮箱 / 邮件 字样的；
 *  3. 排除微信、QQ、微博这类只是「能分享文件」的社交应用。
 * 阿里邮箱排最前，其余按应用名排序。
 */
object MailHelper {

    /** 明确排除的「只是能分享文件」的社交应用 */
    private val NON_MAIL_PACKAGES = setOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.tencent.weibo",
        "com.sina.weibo",
        "com.xingin.xhs",
        "com.alibaba.android.rimet",
    )

    /** 阿里邮箱候选（含历史包名与钉钉内置邮箱），按优先级排列 */
    private val MAIL_PACKAGES = listOf(
        "com.alibaba.cloudmail",
        "com.alibaba.android.mail",
    )

    /** 枚举本机可用的邮箱类应用 */
    fun resolveMailApps(context: Context): List<MailApp> {
        val pm = context.packageManager

        // 1) 能响应 mailto: 的应用
        val mailtoPackages = linkedSetOf<String>()
        runCatching {
            val query = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:") }
            pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
                .forEach { info: ResolveInfo ->
                    info.activityInfo?.applicationInfo?.packageName?.let { mailtoPackages.add(it) }
                }
        }

        // 2) 所有能接收分享的应用
        val candidates = linkedMapOf<String, ApplicationInfo>()
        listOf(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain" },
            Intent(Intent.ACTION_SEND).apply { type = "*/*" },
        ).forEach { query ->
            runCatching {
                pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
                    .forEach { info: ResolveInfo ->
                        val ai = info.activityInfo?.applicationInfo ?: return@forEach
                        candidates.putIfAbsent(ai.packageName, ai)
                    }
            }
        }

        // 3) 过滤出邮箱类应用
        val result = candidates.values.filter { ai ->
            val pkg = ai.packageName
            if (pkg in NON_MAIL_PACKAGES) return@filter false
            val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg)
            pkg in mailtoPackages || looksLikeMailApp(pkg, label)
        }

        // 阿里邮箱排前面，其余按应用名排序
        return result
            .sortedWith(
                compareBy<ApplicationInfo> { if (it.packageName == MAIL_PACKAGES[0]) 0 else 1 }
                    .thenBy { runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault("") },
            )
            .map { ai ->
                MailApp(
                    packageName = ai.packageName,
                    label = runCatching { pm.getApplicationLabel(ai).toString() }
                        .getOrDefault(ai.packageName),
                )
            }
    }

    private fun looksLikeMailApp(pkg: String, label: String): Boolean {
        val s = "$pkg $label".lowercase(Locale.US)
        return s.contains("mail") || s.contains("email") || s.contains("inbox") ||
            s.contains("outlook") || s.contains("邮箱") || s.contains("邮件")
    }

    /**
     * 拉起指定邮件应用，并把 [file] 作为附件带入写邮件界面。
     *
     * @param recipient 收件人邮箱（可为空），写进 `EXTRA_EMAIL`。
     * @param body      邮件正文。**默认留空** —— 主题、收件人、附件才是必需的。
     * @return 是否成功启动
     */
    fun launchWithAttachment(
        context: Context,
        packageName: String?,
        file: File,
        subject: String = "",
        body: String = "",
        recipient: String = "",
    ): Boolean {
        if (!file.exists()) return false
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }.getOrNull() ?: return false

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
            // 正文默认留空，只有明确传了内容才写进去
            if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
            if (recipient.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("attachment", uri)
        }
        if (!packageName.isNullOrEmpty()) {
            intent.setPackage(packageName)
        }

        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            // 指定的应用装不上了，退回系统选择器
            try {
                val chooser = Intent.createChooser(intent, "选择邮件应用").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /** 复制文件名到剪贴板 */
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager ?: return
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("filename", text))
    }
}
