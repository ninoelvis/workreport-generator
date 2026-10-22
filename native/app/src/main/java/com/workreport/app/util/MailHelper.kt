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
     * ⚠️ **收件人为什么不能只靠 EXTRA_EMAIL**：
     * `Intent.EXTRA_EMAIL` 属于 `ACTION_SENDTO` 的标准字段，在 `ACTION_SEND`（附件的标准通道）
     * 下**只有部分邮箱 App 会读**（Gmail 之类还要求 String[]），其余 App 直接忽略，
     * 结果就是「收件人栏是空的」。
     * 而 `ACTION_SENDTO` + `mailto:` 虽然收件人必定填上，却不是附件的标准通道。
     *
     * 所以这里走**双通道**：
     *  1. 首选 `ACTION_SENDTO` + `data = mailto:收件人` → 收件人 100% 填上，
     *     同时把附件通过 `EXTRA_STREAM` + `clipData` 一起带上；
     *  2. 若目标 App 不响应 `ACTION_SENDTO`（或没配收件人），自动退回 `ACTION_SEND`，
     *     收件人同时写进 `data = mailto:` 与 `EXTRA_EMAIL` 两处，覆盖面最大。
     *
     * @param recipient 收件人邮箱（可为空）
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

        val to = recipient.trim()
        // 邮箱地址里的字符（@ . + - _）都是 URI 合法字符，不用 encode
        val mailtoUri = if (to.isNotEmpty()) Uri.parse("mailto:$to") else null

        // ---- 通道 1：ACTION_SENDTO + mailto:（收件人必定填上） ----
        // 只有目标邮箱 App 确实响应 ACTION_SENDTO 时才走这条通道；
        // 否则某些 App（如 Gmail）会进邮件界面但把附件丢掉，得不偿失。
        if (mailtoUri != null &&
            !packageName.isNullOrEmpty() && packageName !in SEND_PREFERRED_PACKAGES &&
            supportsSendTo(context, packageName)
        ) {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = mailtoUri
                if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
                if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
                // 双重保险：部分 App 只认 EXTRA_EMAIL
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                // 附件一起带上
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("attachment", uri)
            }
            if (!packageName.isNullOrEmpty()) intent.setPackage(packageName)
            if (startActivity(context, intent)) return true
        }

        // ---- 通道 2：ACTION_SEND（附件最可靠），收件人写进 data 与 EXTRA_EMAIL 两处 ----
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
            if (to.isNotEmpty()) {
                // 收件人写两处：data(mailto:) 与 EXTRA_EMAIL，覆盖不同 App 的读取习惯
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                data = mailtoUri
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("attachment", uri)
        }
        if (!packageName.isNullOrEmpty()) {
            sendIntent.setPackage(packageName)
        }

        return startActivity(context, sendIntent)
    }

    /**
     * 这些 App 在 `ACTION_SENDTO` 下会**丢掉附件**，但在 `ACTION_SEND` 下会正常读
     * `EXTRA_EMAIL` 填收件人 —— 所以对它们必须走 ACTION_SEND 通道。
     */
    private val SEND_PREFERRED_PACKAGES = setOf(
        "com.google.android.gm", // Gmail
    )

    /**
     * 判断目标邮件 App 是否响应 `ACTION_SENDTO + mailto:`。
     *
     * 响应 → 说明它按标准邮件 URI 语义处理，用 ACTION_SENDTO 能保证收件人填上；
     * 不响应 → 它只认 ACTION_SEND（附件场景），此时只能靠 EXTRA_EMAIL + mailto data 碰运气。
     */
    private fun supportsSendTo(context: Context, packageName: String?): Boolean {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:") }
        return runCatching {
            val matches = pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
            if (packageName.isNullOrEmpty()) {
                matches.isNotEmpty()
            } else {
                matches.any { it.activityInfo?.packageName == packageName }
            }
        }.getOrDefault(false)
    }

    /**
     * 启动 Activity；指定包启动不了时退回系统选择器。
     */
    private fun startActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
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
