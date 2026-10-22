package com.workreport.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.content.FileProvider
import com.workreport.app.domain.model.MailSendMode
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

    /**
     * 阿里邮箱的包名。它有私有通道，可以一条 Intent 同时带齐收件人 + 主题 + 附件。
     * `com.alibaba.android.mail` 是历史包名，同样按阿里邮箱处理。
     */
    private val ALI_MAIL_PACKAGES = setOf(
        "com.alibaba.cloudmail",
        "com.alibaba.android.mail",
    )

    /**
     * 阿里邮箱写邮件页的私有 action。
     * 逆向 v4.12.8 得到：它只在 `MessageComposeOpen` 上声明，且该 filter 不带
     * data / mimeType 约束，所以挂 `data = mailto:` 依然能精确命中。
     */
    private const val ALI_MAIL_ACTION = "com.alibaba.cloudmail.intent.action.standard.MAIL"

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
     * ## 通道策略
     *
     * Android 的两个标准通道各有各的标准字段：
     *
     * | 通道 | 附件 | 收件人 |
     * |------|------|--------|
     * | `ACTION_SEND` | ✅ 标准（`EXTRA_STREAM` + `clipData`） | ⚠️ 非标准，部分 App 忽略 `EXTRA_EMAIL` |
     * | `ACTION_SENDTO` | ⚠️ 非标准，多数 App 忽略 `EXTRA_STREAM` | ✅ 标准（`data = mailto:`） |
     *
     * 通用情况下这两头没法在同一个 Intent 里都保证，所以由 [mode] 决定。
     *
     * **但阿里邮箱有私有通道**（见 [launchAliMail]），能一条 Intent 同时带齐
     * 收件人 + 主题 + 附件，所以优先走它。
     *
     * ### ⚠️ 为什么 `ACTION_SEND` 上不能直接 `setData(mailto:)`
     *
     * Android 的 Intent 匹配规则是「**带了 data URI 就按 scheme 匹配、忽略 mimeType**」。
     * 邮件 App 的 `SEND` filter 基本不声明 scheme，于是 `data = mailto:xxx` 会让
     * 整条 Intent **匹配失败**（`ActivityNotFoundException`），最后落到 chooser 兜底，
     * 收件人和附件两头都丢。
     *
     * 想既收窄候选到邮件 App、又保住 `SEND` 的 mimeType 匹配，正确做法是**用 selector**：
     * 匹配走 selector（`ACTION_SENDTO` + `mailto:`），送达的仍是原始 `ACTION_SEND` Intent，
     * `data` 保持为空 —— 附件通道完全不受影响。
     *
     * @param recipient 收件人邮箱（可为空）
     * @param body      邮件正文。**默认留空** —— 主题、收件人、附件才是必需的。
     * @param mode      发送方式：自动 / 附件优先 / 收件人优先
     * @return 是否成功启动
     */
    fun launchWithAttachment(
        context: Context,
        packageName: String?,
        file: File,
        subject: String = "",
        body: String = "",
        recipient: String = "",
        mode: MailSendMode = MailSendMode.AUTO,
    ): Boolean {
        if (!file.exists()) return false
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }.getOrNull() ?: return false

        val to = recipient.trim()

        // 阿里邮箱专用通道：收件人 + 主题 + 附件一次全带，优先尝试
        if (to.isNotEmpty() && isAliMail(packageName)) {
            if (launchAliMail(context, packageName, uri, subject, body, to)) return true
        }

        return when (mode) {
            MailSendMode.RECIPIENT_FIRST -> launchRecipientFirst(context, packageName, uri, subject, body, to)
            // AUTO 与 ATTACHMENT_FIRST 同走标准附件通道：
            // 附件是本 App 的核心产出物，优先保证它真的挂上；收件人靠 EXTRA_EMAIL 尽力带上。
            MailSendMode.AUTO, MailSendMode.ATTACHMENT_FIRST ->
                launchAttachmentFirst(context, packageName, uri, subject, body, to)
        }
    }

    /**
     * 阿里邮箱专用通道。
     *
     * 逆向 `com.alibaba.cloudmail` v4.12.8 得到的结论：
     *
     *  - 写邮件页 `MessageComposeOpen` 除了标准通道，还声明了**私有 action**
     *    `com.alibaba.cloudmail.intent.action.standard.MAIL`，且该 intent-filter
     *    **不带 data / mimeType 约束** —— 所以挂 `data = mailto:xxx` 不会破坏匹配。
     *  - 它的收件人解析有两条路：mailto URI 的 `getSchemeSpecificPart()`，
     *    以及 extras 里的 `EXTRA_EMAIL`（读法是 `bundle.get()` 再强转 `String[]`）。
     *    两者都提供，收件人必定填上。
     *  - 主题读 `EXTRA_SUBJECT`；附件按 `clipData` → `EXTRA_STREAM(ArrayList)` →
     *    `EXTRA_STREAM`(单个 Uri) 的顺序取，**这一段与 action 无关**。
     *
     * 所以一条 Intent 就能同时带上收件人、主题和附件。
     *
     * ⚠️ 反面教材：`ACTION_SENDTO + mailto:` 在阿里邮箱里命中的是 `Welcome` 中转页，
     * 它转发 Intent 时会**丢掉附件** —— 这正是上一版「附件没带进去」的病根。
     */
    private fun launchAliMail(
        context: Context,
        packageName: String?,
        uri: Uri,
        subject: String,
        body: String,
        to: String,
    ): Boolean {
        val intent = Intent(ALI_MAIL_ACTION).apply {
            // 收件人：mailto URI（schemeSpecificPart）+ EXTRA_EMAIL 双保险
            data = Uri.parse("mailto:$to")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
            // 附件：clipData 与 EXTRA_STREAM 两种形式都给
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("attachment", uri)
        }
        if (!packageName.isNullOrEmpty()) intent.setPackage(packageName)
        return startActivity(context, intent)
    }

    /** 是否是阿里邮箱（独立 App） */
    private fun isAliMail(packageName: String?): Boolean =
        packageName != null && packageName in ALI_MAIL_PACKAGES

    /**
     * 附件优先：`ACTION_SEND` 通道，压缩包必带，收件人靠 `EXTRA_EMAIL` 尽力带上。
     *
     * 兜底链：完整版（含 selector 收窄候选）→ 纯净版（不带 selector，兼容性最高）→ 系统 chooser。
     */
    private fun launchAttachmentFirst(
        context: Context,
        packageName: String?,
        uri: Uri,
        subject: String,
        body: String,
        to: String,
    ): Boolean {
        fun build(withSelector: Boolean): Intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TITLE, subject)
            if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
            if (to.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("attachment", uri)
            if (withSelector && to.isNotEmpty()) {
                // 匹配走 selector：只让邮件类 App 出现在候选里；
                // 送达的仍是这条 ACTION_SEND，data 保持为空，附件通道不受影响。
                selector = Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mailto:"))
            }
        }

        val main = build(withSelector = true)
        if (!packageName.isNullOrEmpty()) main.setPackage(packageName)
        if (startActivity(context, main)) return true

        // 兜底 1：去掉 selector 的纯净版 —— 兼容性最高，附件照样带
        val plain = build(withSelector = false)
        if (!packageName.isNullOrEmpty()) plain.setPackage(packageName)
        if (startActivity(context, plain)) return true

        // 兜底 2：系统选择器
        return startChooser(context, plain)
    }

    /**
     * 收件人优先：`ACTION_SENDTO` 通道，收件人必定填上，附件靠 `EXTRA_STREAM` 尽力带上。
     *
     * 兜底链：带附件版 → 纯收件人版（去掉附件，防止个别 App 直接拒绝带附件的 SENDTO）。
     */
    private fun launchRecipientFirst(
        context: Context,
        packageName: String?,
        uri: Uri,
        subject: String,
        body: String,
        to: String,
    ): Boolean {
        if (to.isEmpty()) {
            // 没有收件人就没必要走收件人通道，退回附件通道
            return launchAttachmentFirst(context, packageName, uri, subject, body, to)
        }

        fun build(withAttachment: Boolean): Intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$to")
            if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
            // 双保险：部分 App 只认 EXTRA_EMAIL
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            if (withAttachment) {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("attachment", uri)
            }
        }

        val main = build(withAttachment = true)
        if (!packageName.isNullOrEmpty()) main.setPackage(packageName)
        if (startActivity(context, main)) return true

        // 兜底：个别邮箱 App 见到 SENDTO + EXTRA_STREAM 会直接拒绝处理，退成纯收件人
        val plain = build(withAttachment = false)
        if (!packageName.isNullOrEmpty()) plain.setPackage(packageName)
        if (startActivity(context, plain)) return true

        return startChooser(context, plain)
    }

    /**
     * 启动 Activity；失败就返回 false，由调用方决定走哪条兜底。
     */
    private fun startActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 兜底：系统选择器 */
    private fun startChooser(context: Context, intent: Intent): Boolean {
        return try {
            val chooser = Intent.createChooser(intent, "选择邮件应用").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 复制文件名到剪贴板 */
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager ?: return
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("filename", text))
    }
}
