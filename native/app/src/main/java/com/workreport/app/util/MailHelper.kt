package com.workreport.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Log
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

    private const val TAG = "MailHelper"

    /**
     * 阿里邮箱的包名。它有私有通道，可以一条 Intent 同时带齐收件人 + 主题 + 附件。
     * `com.alibaba.android.mail` 是历史包名，同样按阿里邮箱处理。
     */
    private val ALI_MAIL_PACKAGES = setOf(
        "com.alibaba.cloudmail",
        "com.alibaba.android.mail",
    )

    /**
     * 阿里邮箱写邮件页的 Activity 全名。
     *
     * 逆向 v4.12.8 得到：收件人和附件的解析都发生在这个 Activity 挂载的
     * `MessageComposeFragment` 里 —— 收件人读 `EXTRA_EMAIL` / mailto URI，
     * 附件读 `clipData` / `EXTRA_STREAM`（**与 action 无关**）。
     *
     * ⚠️ 必须用 `setComponent` 直达，不能靠 IntentFilter 匹配：
     *  - 它的 `ACTION_SEND` filter 只声明了 mimeType，而 Intent 一旦带 data URI
     *    就改为按 scheme 匹配 → 匹配失败；
     *  - 它的 `standard.MAIL` filter 什么都没声明，同样不接 data URI；
     *  - `ACTION_SENDTO + mailto:` 会命中 `Welcome` 中转页，而 `Welcome` 转发时
     *    只复制 TEXT/SUBJECT/EMAIL/CC/BCC 和 data，**不复制 EXTRA_STREAM/clipData**
     *    → 附件必丢。
     *
     * 指定组件后系统不再做 IntentFilter 匹配，data 想带就带。
     */
    private const val ALI_MAIL_COMPOSE_ACTIVITY =
        "com.alibaba.alimei.ui.library.activity.MessageComposeOpen"

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
     * 两个标准通道各有各的标准字段：
     *
     * | 通道 | 附件 | 收件人 |
     * |------|------|--------|
     * | `ACTION_SEND` | ✅ 标准（`EXTRA_STREAM` + `clipData`） | ⚠️ 非标准，部分 App 忽略 `EXTRA_EMAIL` |
     * | `ACTION_SENDTO` | ⚠️ 非标准，多数 App 忽略 `EXTRA_STREAM` | ✅ 标准（`data = mailto:`） |
     *
     * ### ⚠️ 三条踩过的坑，改之前先看一眼
     *
     * 1. **Intent 带 data URI 就按 scheme 匹配、忽略 mimeType。**
     *    所以 `ACTION_SEND` 上挂 `data = mailto:xxx` 会匹配失败 —— 邮件 App 的
     *    `SEND` filter 基本不声明 scheme。
     * 2. **不要用 `intent.selector` 收窄候选。**
     *    `Intent.createChooser()` 不会处理 target 的 selector，系统 ResolverActivity
     *    处理带 selector 的 EXTRA_INTENT 时在新版 Android 上会 NPE —— 直接闪退。
     * 3. **filter 既没声明 scheme 也没声明 mimeType 时，带 data URI 的 Intent 一律不匹配。**
     *    所以想带 `data = mailto:` 就得用 `setComponent` 直达 Activity。
     *
     * ### 阿里邮箱：一条 Intent 全部带上
     *
     * 逆向 `com.alibaba.cloudmail` v4.12.8 得到的结论见 [launchAliMailDirect]。
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
        // 整条链路兜住异常 —— 拉起邮箱失败也不能让 App 崩
        return runCatching {
            if (!file.exists()) return@runCatching false
            val uri: Uri = runCatching {
                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            }.getOrNull() ?: return@runCatching false

            val to = recipient.trim()

            // 阿里邮箱：直达写邮件页，收件人 + 主题 + 附件一次全带
            if (to.isNotEmpty() && isAliMail(packageName)) {
                if (launchAliMailDirect(context, packageName, uri, subject, body, to)) {
                    return@runCatching true
                }
            }

            when (mode) {
                MailSendMode.RECIPIENT_FIRST ->
                    launchRecipientFirst(context, packageName, uri, subject, body, to)
                // AUTO 与 ATTACHMENT_FIRST 同走标准附件通道：
                // 附件是本 App 的核心产出物，优先保证它真的挂上；收件人靠 EXTRA_EMAIL 尽力带上。
                MailSendMode.AUTO, MailSendMode.ATTACHMENT_FIRST ->
                    launchAttachmentFirst(context, packageName, uri, subject, body, to)
            }
        }.getOrElse { e ->
            Log.e(TAG, "拉起邮件应用失败", e)
            false
        }
    }

    /**
     * 阿里邮箱专用通道：**用 `setComponent` 直达写邮件页**。
     *
     * 逆向 `com.alibaba.cloudmail` v4.12.8 得到的结论：
     *
     *  - 收件人、主题、附件的解析都发生在 `MessageComposeOpen` 挂载的
     *    `MessageComposeFragment` 里；
     *  - 收件人有两条路：mailto URI 的 `getSchemeSpecificPart()`，以及 extras 里的
     *    `EXTRA_EMAIL`（读法是 `bundle.get()` 后强转 `String[]`）—— 两条都给，必定填上；
     *  - 主题读 `EXTRA_SUBJECT`；
     *  - 附件按 `clipData` → `EXTRA_STREAM(ArrayList)` → `EXTRA_STREAM`(单个 Uri) 的顺序取，
     *    **且这一段与 action 无关**。
     *
     * 为什么必须 `setComponent`：它的 filter 都不接受带 data URI 的 Intent（见类注释），
     * 而 `ACTION_SENDTO + mailto:` 会命中 `Welcome` 中转页 —— `Welcome` 转发时只复制
     * TEXT/SUBJECT/EMAIL/CC/BCC 和 data，**不复制 EXTRA_STREAM / clipData** → 附件必丢。
     *
     * 指定组件后系统不再做 IntentFilter 匹配，data 想带就带。
     */
    private fun launchAliMailDirect(
        context: Context,
        packageName: String?,
        uri: Uri,
        subject: String,
        body: String,
        to: String,
    ): Boolean {
        val pkg = packageName ?: return false
        val component = ComponentName(pkg, ALI_MAIL_COMPOSE_ACTIVITY)

        // 先确认这个 Activity 确实存在（版本升级可能改类名），免得白跑一趟
        val exists = runCatching {
            context.packageManager.getActivityInfo(component, 0) != null
        }.getOrDefault(false)
        if (!exists) {
            Log.w(TAG, "阿里邮箱写邮件页不存在，退回标准通道: $component")
            return false
        }

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            this.component = component
            // 收件人：mailto URI（schemeSpecificPart）+ EXTRA_EMAIL 双保险
            data = Uri.parse("mailto:$to")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            // 附件：clipData 与 EXTRA_STREAM 两种形式都给
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("attachment", uri)
        }
        return startActivity(context, intent)
    }

    /** 是否是阿里邮箱（独立 App） */
    private fun isAliMail(packageName: String?): Boolean =
        packageName != null && packageName in ALI_MAIL_PACKAGES

    /**
     * 附件优先：`ACTION_SEND` 通道，压缩包必带，收件人靠 `EXTRA_EMAIL` 尽力带上。
     *
     * 兜底链：定向到指定 App → 系统选择器。
     */
    private fun launchAttachmentFirst(
        context: Context,
        packageName: String?,
        uri: Uri,
        subject: String,
        body: String,
        to: String,
    ): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            // SUBJECT / TEXT 始终放（正文为空就放空串）：
            // 有些邮箱 App 转发 Intent 时会直接 .toString()，缺 key 会 NPE
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (to.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("attachment", uri)
        }
        if (!packageName.isNullOrEmpty()) intent.setPackage(packageName)
        if (startActivity(context, intent)) return true

        // 兜底：系统选择器。用一份干净的 Intent（不带 setPackage），
        // 否则 chooser 可能因为候选为空而什么都列不出来。
        return startChooser(context, Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (to.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("attachment", uri)
        })
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
            // SUBJECT / TEXT 始终放（正文为空就放空串），理由同上
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
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
     * 启动 Activity；任何异常都吞掉并返回 false，由调用方决定兜底。
     */
    private fun startActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Log.w(TAG, "启动邮件 Activity 失败: ${intent.action}", e)
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
            Log.w(TAG, "系统选择器启动失败", e)
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
