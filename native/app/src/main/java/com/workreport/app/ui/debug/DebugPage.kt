package com.workreport.app.ui.debug

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workreport.app.BuildConfig
import com.workreport.app.ServiceLocator
import com.workreport.app.export.EncryptedZipReader
import com.workreport.app.export.EncryptedZipWriter
import com.workreport.app.export.ReportNaming
import com.workreport.app.export.XlsxWriter
import com.workreport.app.ui.profile.ProfileSubPage
import com.workreport.app.ui.reminder.ReminderReceiver
import com.workreport.app.ui.reminder.ReminderScheduler
import com.workreport.app.util.AppLog
import com.workreport.app.util.DateUtils
import com.workreport.app.util.SensitiveWordEngine
import com.workreport.app.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * 调试工具页。
 *
 * **隐藏入口**：「我的 → 关于」里连点版本号 7 次。
 *
 * 用途：
 *  - 测通知 / 提醒到底通不通（不用等到点）；
 *  - **核对公司红线**：真实生成一份压缩包，把文件名、密码、加密方式、
 *    Excel 的合并单元格、列宽、行高、样式索引全列出来；
 *  - 测脱敏引擎对不对（改完词库立刻能看效果）；
 *  - 查历史报告的存储状态（文件还在不在、预览数据齐不齐）；
 *  - 看异常日志（App 里被吞掉的异常 + 本 App 的 logcat）。
 *
 * ⚠️ 这个页面**不影响任何正常功能**：
 *  - 入口是隐藏的，不点进不来；
 *  - 所有测试动作都是独立的，不改动用户的周报数据；
 *  - 唯一会写文件的是「红线自检」，它只往 App 自己的缓存目录写一份测试文件。
 */
@Composable
fun DebugPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    var maskInput by remember { mutableStateOf("完成纪委查询工作，落实措施\n必须查询平台这四个字的查询我需要脱敏") }
    var maskResult by remember { mutableStateOf("") }
    var maskHits by remember { mutableStateOf("") }
    var excelInput by remember { mutableStateOf("完成纪委查询工作\n落实整改措施") }
    var excelPlanInput by remember { mutableStateOf("下周继续查询整改") }
    var excelText by remember { mutableStateOf("") }
    var redLineText by remember { mutableStateOf("") }
    var storageText by remember { mutableStateOf("") }
    var historyText by remember { mutableStateOf("正在读取…") }
    var logcatText by remember { mutableStateOf("") }
    var selfCheck by remember { mutableStateOf("正在读取…") }
    var historyCount by remember { mutableStateOf(-1) }

    val settings = ServiceLocator.settings
    val reminderEnabled by settings.reminderEnabled.collectAsStateWithLifecycle()
    val reminderWeekdays by settings.reminderWeekdays.collectAsStateWithLifecycle()
    val sensitiveWords by settings.sensitiveWords.collectAsStateWithLifecycle()
    val userName by settings.userName.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        val reports = withContext(Dispatchers.IO) { ServiceLocator.reportRepository.observeAll().first() }
        historyCount = reports.size
        historyText = buildString {
            append("共 ").append(reports.size).append(" 份\n\n")
            reports.take(20).forEachIndexed { index, r ->
                val file = File(r.filePath)
                val exists = file.exists()
                append(index + 1).append(". ").append(r.displayName).append('\n')
                append("   时间：").append(formatTime(r.createdAt)).append('\n')
                append("   文件：").append(if (exists) "在（${file.length()} B）" else "不见了").append('\n')
                append("   密码：").append(r.password).append('\n')
                append("   预览来源：")
                    .append(if (r.hasExportedContent) "数据库（与 Excel 逐字一致）" else "解压缩包读 Excel")
                    .append('\n')
                append("   可载入编辑：").append(if (r.hasFullContent) "是" else "需解压缩包").append('\n')
            }
            if (reports.size > 20) append("\n（只列了前 20 份）")
        }
        selfCheck = buildString {
            append("版本：").append(BuildConfig.VERSION_NAME)
            append("（versionCode ").append(BuildConfig.VERSION_CODE).append("）\n")
            append("系统：Android ").append(Build.VERSION.RELEASE)
            append("（API ").append(Build.VERSION.SDK_INT).append("）\n")
            append("设备：").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append('\n')
            append("姓名：").append(userName.ifEmpty { "（未填）" }).append('\n')
            append("敏感词：").append(sensitiveWords.size).append(" 条\n")
            append("提醒：").append(if (reminderEnabled) "已开启" else "已关闭")
            append("　星期：").append(describeWeekdays(reminderWeekdays)).append('\n')
            append("下次提醒：")
                .append(ReminderScheduler.nextTriggerDescription(reminderWeekdays))
                .append('\n')
            append("通知权限：")
                .append(if (ReminderReceiver.hasNotificationPermission(context)) "已授权" else "未授权（Android 13+ 需要授权）")
                .append('\n')
            append("历史报告：").append(if (historyCount >= 0) "$historyCount 份" else "读取中").append('\n')
        }
    }

    ProfileSubPage(
        title = "调试工具",
        subtitle = "隐藏页面 · 仅供自测",
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------- 红线自检 ----------------
            item {
                DebugCard("公司红线自检（真实产物）") {
                    Text(
                        "真的导出一份到缓存目录，再把文件名、密码、加密方式和 Excel 的" +
                            "合并单元格、列宽、行高、样式索引全读出来 —— 和 Python / 7-Zip 的结果对得上才算数。",
                        style = DebugTextStyle,
                    )
                    Spacer(Modifier.height(8.dp))
                    DebugButton("跑一次红线自检") {
                        scope.launch {
                            redLineText = withContext(Dispatchers.IO) { runRedLineCheck(context) }
                            toast = "红线自检完成"
                        }
                    }
                    if (redLineText.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(redLineText, style = DebugMonoStyle)
                    }
                }
            }

            // ---------------- 通知与提醒 ----------------
            item {
                DebugCard("通知与提醒") {
                    Text(
                        "通知权限：${if (ReminderReceiver.hasNotificationPermission(context)) "已授权" else "未授权"}",
                        style = DebugTextStyle,
                    )
                    Text(
                        "提醒开关：${if (reminderEnabled) "已开启" else "已关闭"}　" +
                            "星期：${describeWeekdays(reminderWeekdays)}",
                        style = DebugTextStyle,
                    )
                    Text(
                        "下次提醒：${ReminderScheduler.nextTriggerDescription(reminderWeekdays)}",
                        style = DebugTextStyle,
                    )
                    Spacer(Modifier.height(6.dp))
                    DebugButton("发一条测试通知（不过闹钟）") {
                        val ok = ReminderReceiver.postReminderNotification(context)
                        toast = if (ok) {
                            "已发出通知，看看通知栏有没有"
                        } else {
                            "没发出去 —— 多半是 Android 13+ 没给通知权限"
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    DebugButton("立刻触发一次提醒（走完整闹钟链路）") {
                        val ok = triggerReminderNow(context)
                        toast = if (ok) {
                            "已排上 2 秒后的闹钟，等 2 秒看通知"
                        } else {
                            "排闹钟失败（系统可能限制了后台闹钟）"
                        }
                    }
                }
            }

            // ---------------- 脱敏引擎 ----------------
            item {
                DebugCard("脱敏引擎") {
                    OutlinedTextField(
                        value = maskInput,
                        onValueChange = { maskInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("测试文本", fontSize = 12.sp) },
                        minLines = 3,
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    DebugButton("跑一次脱敏") {
                        val engine = SensitiveWordEngine(sensitiveWords)
                        maskResult = engine.replace(maskInput)
                        val summary = engine.analyzeMultiline(maskInput)
                        maskHits = if (summary.isEmpty) {
                            "没有命中任何敏感词"
                        } else {
                            summary.hits.mapIndexed { i, hit ->
                                val ctx = hit.anchor.before + "【" + hit.word + "】" + hit.anchor.after
                                "${i + 1}. $ctx → ${hit.replacement}" +
                                    (if (hit.excluded) "（已放行）" else "") +
                                    "　锚点=${hit.anchor.section}/第${hit.anchor.nth + 1}个"
                            }.joinToString("\n")
                        }
                    }
                    if (maskResult.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("脱敏结果：", style = DebugLabelStyle)
                        Text(maskResult, style = DebugMonoStyle)
                    }
                    if (maskHits.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("命中明细：", style = DebugLabelStyle)
                        Text(maskHits, style = DebugMonoStyle)
                    }
                }
            }

            // ---------------- Excel 文本对照 ----------------
            item {
                DebugCard("Excel 文本对照") {
                    Text(
                        "输入原始条目，看 Excel 里 B3 / C3 那两格最终会写成什么 —— " +
                            "首页预览区和历史报告预览区显示的就是这个。",
                        style = DebugTextStyle,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = excelInput,
                        onValueChange = { excelInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("本周已完成工作梳理（原始条目，一行一条）", fontSize = 12.sp) },
                        minLines = 3,
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = excelPlanInput,
                        onValueChange = { excelPlanInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("下周工作计划（原始条目，一行一条）", fontSize = 12.sp) },
                        minLines = 2,
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    DebugButton("看 Excel 里那两格会是什么") {
                        val engine = SensitiveWordEngine(sensitiveWords)
                        val done = SensitiveWordEngine.formatDoneWorks(excelInput, engine)
                        val plan = SensitiveWordEngine.formatPlanWorks(excelPlanInput, engine)
                        excelText = buildString {
                            append("B3（本周已完成工作梳理）：\n")
                            append(done.ifEmpty { "（空）" }).append("\n\n")
                            append("C3（下周工作计划）：\n")
                            append(plan.ifEmpty { "（空）" })
                        }
                    }
                    if (excelText.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(excelText, style = DebugMonoStyle)
                    }
                }
            }

            // ---------------- 外部通道 ----------------
            item {
                DebugCard("外部通道") {
                    DebugButton("测试系统分享（拉起分享面板）") {
                        val file = createTestFile(context)
                        val ok = ShareHelper.shareFile(context, file)
                        toast = if (ok) "已拉起分享面板" else "拉起失败"
                    }
                    Spacer(Modifier.height(6.dp))
                    DebugButton("测试邮件通道（拉起邮箱 App）") {
                        val file = createTestFile(context)
                        val ok = com.workreport.app.util.MailHelper.launchWithAttachment(
                            context = context,
                            packageName = null,
                            file = file,
                            subject = file.name,
                            body = "",
                        )
                        toast = if (ok) "已拉起邮箱 App" else "没找到可用的邮箱 App"
                    }
                    Spacer(Modifier.height(6.dp))
                    DebugButton("导出配置备份（写到「下载」目录）") {
                        scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                com.workreport.app.data.backup.ConfigBackupManager
                                    .exportToDownloads(context.applicationContext, backupPassword())
                            }
                            toast = if (path != null) "已导出：$path" else "导出失败"
                        }
                    }
                }
            }

            // ---------------- 历史报告体检 ----------------
            item {
                DebugCard("历史报告体检") {
                    Text(historyText, style = DebugMonoStyle)
                }
            }

            // ---------------- 存储自检 ----------------
            item {
                DebugCard("存储自检") {
                    DebugButton("检查文件与目录") {
                        scope.launch {
                            storageText = withContext(Dispatchers.IO) { inspectStorage(context) }
                        }
                    }
                    if (storageText.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(storageText, style = DebugMonoStyle)
                    }
                }
            }

            // ---------------- 自检 ----------------
            item {
                DebugCard("自检信息") {
                    Text(selfCheck, style = DebugMonoStyle)
                }
            }

            // ---------------- 日志 ----------------
            item {
                DebugCard("日志") {
                    Text(
                        "应用内错误缓冲：${AppLog.recent(500).size} 条",
                        style = DebugTextStyle,
                    )
                    Spacer(Modifier.height(6.dp))
                    DebugButton("读取本 App 的 logcat") {
                        logcatText = readOwnLogcat()
                        toast = if (logcatText.isBlank()) "没读到内容" else "已读取"
                    }
                    Spacer(Modifier.height(6.dp))
                    DebugButton("清空错误缓冲") {
                        AppLog.clear()
                        toast = "已清空"
                    }
                    if (logcatText.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("logcat（最后 200 行）：", style = DebugLabelStyle)
                        Text(logcatText, style = DebugMonoStyle)
                    }
                    val errs = AppLog.recent(50)
                    if (errs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("错误缓冲：", style = DebugLabelStyle)
                        Text(
                            errs.joinToString("\n") {
                                "${it.time} [${it.level}] ${it.tag}：${it.message}"
                            },
                            style = DebugMonoStyle,
                        )
                    }
                }
            }

            item {
                Text(
                    "这个页面是隐藏入口，不影响任何正常功能；测试动作不会改动你的周报内容。",
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ==================== 红线自检 ====================

/**
 * 真实导出一份压缩包，然后把「文件里真实的字节」读出来核对。
 *
 * 不看代码里的常量，只看**产物本身**：
 *  - 外层文件名、压缩包内条目名（直接解析 ZIP 中央目录）；
 *  - 加密标志、压缩方法（ZIP 头里的 flag bits / method）；
 *  - 用密码解开后，Excel 里的合并单元格、列宽、行高、样式索引、每格文本。
 */
private fun runRedLineCheck(context: Context): String {
    return runCatching {
        val cacheDir = File(context.cacheDir, "debug").apply { mkdirs() }
        cacheDir.listFiles()?.forEach { it.delete() }

        val name = ServiceLocator.settings.userName.value.ifBlank { "姓名" }
        val range = DateUtils.currentWeekRangeText()
        val plan = ReportNaming.plan(name, range)

        val engine = SensitiveWordEngine(ServiceLocator.settings.sensitiveWords.value)
        val doneFormatted = SensitiveWordEngine.formatDoneWorks(
            "完成共2项工作内容样例\n配合纪委完成案件审理",
            engine,
        )
        val planFormatted = SensitiveWordEngine.formatPlanWorks(
            "下周计划样例一\n下周计划样例二",
            engine,
        )

        val xlsxBytes = XlsxWriter.toBytes(
            title = "${plan.year}年${plan.month}月第${plan.weekNum}周工作报告",
            timeRange = range,
            doneWorks = doneFormatted,
            planWorks = planFormatted,
        )
        val zipFile = File(cacheDir, plan.zipFileName)
        EncryptedZipWriter.write(
            target = zipFile,
            password = plan.password,
            entries = listOf(plan.xlsxFileName to xlsxBytes),
        )

        val zipInfo = inspectZipDirectory(zipFile)
        val decrypted = EncryptedZipReader.readFirstXlsx(zipFile, plan.password)
        val sheet = decrypted?.let { extractSheetXml(it) }.orEmpty()

        buildString {
            append("【命名与密码】\n")
            append("  外层文件名：").append(plan.zipFileName).append('\n')
            append("  内层条目名：").append(plan.xlsxFileName).append('\n')
            append("  解压密码：").append(plan.password).append('\n')
            append("  压缩包大小：").append(zipFile.length()).append(" B\n\n")

            append("【ZIP 头里读出来的】\n")
            append(zipInfo).append('\n')

            append("\n【解出来的 Excel】\n")
            if (decrypted == null) {
                append("  ⚠️ 用密码解不开！\n")
            } else {
                append("  合并单元格：").append(mergeCells(sheet)).append('\n')
                append("  列宽：").append(colWidths(sheet)).append('\n')
                append("  行高：").append(rowHeights(sheet)).append('\n')
                append("  样式索引：").append(styleIndexes(sheet)).append('\n')
                append("  sheet 名：").append(sheetName(decrypted)).append('\n')
            }

            append("\n【B3 / C3 的实际文本】\n")
            append("  B3：").append(doneFormatted.replace("\n", " ⏎ ")).append('\n')
            append("  C3：").append(planFormatted.replace("\n", " ⏎ ")).append('\n')

            append("\n测试文件在：").append(zipFile.absolutePath)
        }
    }.getOrElse { e ->
        AppLog.error("Debug", "红线自检失败", e)
        "红线自检失败：${e.message}"
    }
}

/** 直接解析 ZIP 中央目录，读出真实的条目名 / 加密标志 / 压缩方法 */
private fun inspectZipDirectory(file: File): String {
    val raw = file.readBytes()
    var eocd = -1
    for (i in raw.size - 22 downTo 0) {
        if (i >= 0 && raw[i] == 0x50.toByte() && raw[i + 1] == 0x4B.toByte() &&
            raw[i + 2] == 0x05.toByte() && raw[i + 3] == 0x06.toByte()
        ) {
            eocd = i
            break
        }
    }
    if (eocd < 0) return "  ⚠️ 找不到 ZIP 中央目录"
    val cdOffset = readIntLE(raw, eocd + 16)
    if (cdOffset !in raw.indices) return "  ⚠️ 中央目录偏移异常"
    val flags = readShortLE(raw, cdOffset + 8)
    val method = readShortLE(raw, cdOffset + 10)
    val nameLen = readShortLE(raw, cdOffset + 28)
    val name = String(raw, cdOffset + 46, cdOffset + 46 + nameLen, Charsets.UTF_8)
    val encrypted = (flags and 1) != 0
    // method 99 = WinZip AE-1 (AES)；0 = 存储；8 = Deflate
    val methodText = when (method) {
        99 -> "WinZip AE-1（AES 加密）"
        8 -> "Deflate"
        0 -> "Store（不压缩）"
        else -> "未知($method)"
    }
    return buildString {
        append("  条目名：").append(name).append('\n')
        append("  加密标志：").append(if (encrypted) "已加密" else "未加密").append('\n')
        append("  压缩方法：").append(methodText).append('\n')
        append("  （我们设置的是 AES-256 + HMAC-SHA1 + Deflate，AE-1）\n")
    }
}

private fun readShortLE(raw: ByteArray, offset: Int): Int {
    if (offset + 1 >= raw.size) return 0
    return (raw[offset].toInt() and 0xFF) or ((raw[offset + 1].toInt() and 0xFF) shl 8)
}

private fun readIntLE(raw: ByteArray, offset: Int): Int {
    if (offset + 3 >= raw.size) return 0
    return (raw[offset].toInt() and 0xFF) or
        ((raw[offset + 1].toInt() and 0xFF) shl 8) or
        ((raw[offset + 2].toInt() and 0xFF) shl 16) or
        ((raw[offset + 3].toInt() and 0xFF) shl 24)
}

/** 从 xlsx 字节里取出 sheet1.xml */
private fun extractSheetXml(xlsx: ByteArray): String = runCatching {
    java.util.zip.ZipInputStream(xlsx.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name.endsWith("sheet1.xml")) {
                return zis.readBytes().toString(Charsets.UTF_8)
            }
            entry = zis.nextEntry
        }
        ""
    }
}.getOrElse { "" }

private fun mergeCells(sheet: String): String =
    Regex("<mergeCell ref=\"([^\"]+)\"").findAll(sheet)
        .map { it.groupValues[1] }.toList().joinToString(" ")

private fun colWidths(sheet: String): String =
    Regex("<col [^>]*width=\"([^\"]+)\"").findAll(sheet)
        .map { it.groupValues[1] }.toList().joinToString(" / ")

private fun rowHeights(sheet: String): String =
    Regex("<row r=\"(\\d+)\"[^>]*ht=\"([^\"]+)\"").findAll(sheet)
        .map { "r${it.groupValues[1]}=${it.groupValues[2]}" }.toList().joinToString(" ")

private fun styleIndexes(sheet: String): String =
    Regex("s=\"(\\d+)\"").findAll(sheet)
        .map { it.groupValues[1] }.toList().joinToString(" ")

private fun sheetName(xlsx: ByteArray): String = runCatching {
    java.util.zip.ZipInputStream(xlsx.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "xl/workbook.xml") {
                val xml = zis.readBytes().toString(Charsets.UTF_8)
                return Regex("<sheet name=\"([^\"]+)\"").find(xml)?.groupValues?.get(1) ?: "?"
            }
            entry = zis.nextEntry
        }
        "?"
    }
}.getOrElse { "?" }

// ==================== 存储自检 ====================

private fun inspectStorage(context: Context): String = runCatching {
    val app = context.applicationContext
    val reportsDir = File(app.filesDir, "reports")
    val files = reportsDir.listFiles()?.toList() ?: emptyList()
    val total = files.sumOf { it.length() }

    val downloads = app.getExternalFilesDir(null)
    val downloadsWritable = downloads?.let { it.exists() && it.canWrite() } ?: false

    buildString {
        append("应用私有目录：").append(app.filesDir.absolutePath).append('\n')
        append("报告目录：").append(reportsDir.absolutePath).append('\n')
        append("  文件数：").append(files.size).append('\n')
        append("  占用：").append(total / 1024).append(" KB\n")
        append("  子目录数：").append(files.count { it.isDirectory }).append('\n')
        append("应用外部目录：").append(downloads?.absolutePath ?: "（无）").append('\n')
        append("  可写：").append(if (downloadsWritable) "是" else "否").append('\n')
        append("缓存目录：").append(app.cacheDir.absolutePath).append('\n')
        append("  可写：").append(if (app.cacheDir.canWrite()) "是" else "否").append('\n')
        append("数据库：").append(app.getDatabasePath("workreport.db").absolutePath)
    }
}.getOrElse { "存储自检失败：${it.message}" }

// ==================== 小工具 ====================

/** 把星期集合转成「周四、周五」这样的文字 */
private fun describeWeekdays(weekdays: Set<Int>): String {
    if (weekdays.isEmpty()) return "未选择"
    val names = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
    return weekdays.sorted().joinToString("、") { "周" + (names[it] ?: it.toString()) }
}

/** 时间戳转成可读时间 */
private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "未知"
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "%04d-%02d-%02d %02d:%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
    )
}

/** 备份密码默认沿用周报那套规则（本周周四 yyyyMMdd） */
private fun backupPassword(): String = DateUtils.fmtDate8(
    DateUtils.getThursday(
        DateUtils.parseDateRange(DateUtils.currentWeekRangeText())?.first
            ?: Calendar.getInstance().time,
    ),
)

/**
 * 立刻排一次 2 秒后的提醒闹钟，走的是和正式提醒**完全一样**的链路。
 *
 * 带了 [ReminderScheduler.EXTRA_FORCE_TEST] 标记，所以不受「开关关着 / 今天不是选中星期」的限制 ——
 * 这正是测试时想要的：不改设置也能验证闹钟到底通不通。
 */
private fun triggerReminderNow(context: Context): Boolean = runCatching {
    val alarm = context.getSystemService(AlarmManager::class.java) ?: return false
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        action = ReminderScheduler.ACTION_REMINDER
        putExtra(ReminderScheduler.EXTRA_FORCE_TEST, true)
    }
    val pending = PendingIntent.getBroadcast(
        context,
        0x5759,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    alarm.setWindow(
        AlarmManager.RTC_WAKEUP,
        System.currentTimeMillis() + 2_000L,
        2_000L,
        pending,
    )
    true
}.getOrElse { e ->
    AppLog.error("Debug", "排测试闹钟失败", e)
    false
}

/** 造一个临时测试文件，用来测分享 / 邮件通道 */
private fun createTestFile(context: Context): File {
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    val file = File(dir, "调试测试文件.txt")
    file.writeText("这是「工作报告生成器」的调试测试文件，不是周报。\n")
    return file
}

/**
 * 读本 App 自己的 logcat。
 *
 * Android 4.1 之后普通 App 读不到别的 App 的日志，
 * 但**读自己的**是可以的（`--pid` 过滤）。
 */
private fun readOwnLogcat(): String = runCatching {
    val process = ProcessBuilder(
        "logcat", "-d", "-v", "time", "--pid=" + Process.myPid(),
    ).redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().readText()
    process.waitFor()
    text.lines().takeLast(200).joinToString("\n")
}.getOrElse { e ->
    AppLog.error("Debug", "读取 logcat 失败", e)
    "读取 logcat 失败：${e.message}"
}

// ---------------- 小组件样式 ----------------

@Composable
private fun DebugCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DebugButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val DebugTextStyle
    @Composable get() = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )

private val DebugLabelStyle
    @Composable get() = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )

private val DebugMonoStyle
    @Composable get() = androidx.compose.ui.text.TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
    )
