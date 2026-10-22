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
     * 邮件里只带**主题**和**附件**，收件人由用户在邮箱 App 里自己填 ——
     * 这样最省事，也最不容易被各家邮箱 App 的私有实现坑到。
     *
     * ## 通道
     *
     * **通用**：`ACTION_SEND` + `type=application/zip` + `EXTRA_STREAM` + `clipData`。
     * 这是 Android 的标准附件分享方式，Gmail / QQ邮箱 / 网易 / Outlook / 阿里邮箱都认。
     *
     * **阿里邮箱**（`com.alibaba.cloudmail`）：额外做两件事 ——
     *
     *  1. 用 `setComponent` 直达它的写邮件页 `MessageComposeOpen`。
     *     逆向 v4.12.8 得知：它的收件人和主题只有在「data 是 mailto 且 action ∈
     *     {VIEW, SENDTO, standard.MAIL}」时才会被解析，而 `ACTION_SEND` 走不到那条分支
     *     → 主题带不进去。指定组件后系统不再做 IntentFilter 匹配，data 想带就带。
     *  2. 加 `FLAG_ACTIVITY_CLEAR_TASK`：它的写邮件页是 `launchMode=singleTask`，
     *     而它的 `onNewIntent` **只弹了个 toast、根本没把新 Intent 交给 Fragment 解析**
     *     → 只要那个页面还在任务栈里，每次启动都复用旧实例，附件永远处理不了。
     *     CLEAR_TASK 会清掉目标 App 的任务栈，逼它走 `onCreate` 重新解析。
     *
     * ## ⚠️ 几条踩过的坑，改之前先看一眼
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
     * @param body 邮件正文。**默认留空** —— 主题和附件才是必需的。
     * @return 是否成功启动
     */
    fun launchWithAttachment(
        context: Context,
        packageName: String?,
        file: File,
        subject: String = "",
        body: String = "",
    ): Boolean {
        // 整条链路兜住异常 —— 拉起邮箱失败也不能让 App 崩
        return runCatching {
            if (!file.exists()) return@runCatching false
            val uri: Uri = runCatching {
                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            }.getOrNull() ?: return@runCatching false

            // 阿里邮箱：直达写邮件页，主题和附件一起带
            if (isAliMail(packageName)) {
                if (launchAliMailDirect(context, packageName, uri, subject, body)) {
                    return@runCatching true
                }
            }

            launchAttachmentFirst(context, packageName, uri, subject, body)
        }.getOrElse { e ->
            Log.e(TAG, "拉起邮件应用失败", e)
            false
        }
    }

    /**
     * 阿里邮箱专用通道：**`setComponent` 直达写邮件页 + CLEAR_TASK 强制重建**。
     *
     * 逆向 `com.alibaba.cloudmail` v4.12.8 得到的结论：
     *
     *  - 主题读 `EXTRA_SUBJECT`，但前提是 `data` 的 scheme 是 `mailto`
     *    且 action ∈ {VIEW, SENDTO, standard.MAIL} → 这里用 `ACTION_SENDTO` + `mailto:` 满足；
     *  - 附件按 `clipData` → `EXTRA_STREAM`(ArrayList) → `EXTRA_STREAM`(单个 Uri) 的顺序取，
     *    **且这一段与 action 无关**；
     *  - 收件人由用户在邮箱 App 里自己填，这里不传任何地址。
     *
     * 失败时返回 false，由调用方退回标准通道。
     */
    private fun launchAliMailDirect(
        context: Context,
        packageName: String?,
        uri: Uri,
        subject: String,
        body: String,
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
            // 只带上 mailto 协议头，不带任何地址 —— 收件人由用户自己填。
            // 这个 data 的作用是让阿里邮箱走「读 EXTRA_SUBJECT」那条分支。
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            // 正文为空就放空串：有些 App 转发 Intent 时会直接 .toString()，缺 key 会出问题
            putExtra(Intent.EXTRA_TEXT, body)
            // 附件：clipData 与 EXTRA_STREAM 两种形式都给
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 它的写邮件页是 singleTask 且 onNewIntent 不处理新 Intent，
            // 不清任务栈就会一直复用旧实例 → 附件永远带不上
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            clipData = android.content.ClipData.newRawUri("attachment", uri)
        }
        return startActivity(context, intent)
    }

    /** 是否是阿里邮箱（独立 App） */
    private fun isAliMail(packageName: String?): Boolean =
        packageName != null && packageName in ALI_MAIL_PACKAGES

    /**
     * 标准通道：`ACTION_SEND` + `type=application/zip`，压缩包必带，主题一起带。
     *
     * 兜底链：定向到指定 App → 系统选择器（用干净的 Intent，避免候选为空列不出来）。
     */
    private fun launchAttachmentFirst(
        context: Context,
        packageName: String?,
        uri: Uri,
        subject: String,
        body: String,
    ): Boolean {
        fun build(): Intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            // SUBJECT / TEXT 始终放（正文为空就放空串）
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("attachment", uri)
        }

        val intent = build()
        if (!packageName.isNullOrEmpty()) intent.setPackage(packageName)
        if (startActivity(context, intent)) return true

        // 兜底：系统选择器。用一份干净的 Intent（不带 setPackage），
        // 否则 chooser 可能因为候选为空而什么都列不出来。
        return startChooser(context, build())
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
