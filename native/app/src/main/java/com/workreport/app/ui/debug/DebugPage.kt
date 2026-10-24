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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workreport.app.BuildConfig
import com.workreport.app.ServiceLocator
import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.ui.profile.ProfileSubPage
import com.workreport.app.ui.reminder.ReminderReceiver
import com.workreport.app.ui.reminder.ReminderScheduler
import com.workreport.app.util.AppLog
import com.workreport.app.util.SensitiveWordEngine
import com.workreport.app.util.ShareHelper
import kotlinx.coroutines.Dispatchers
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
 *  - 测脱敏引擎对不对（改完词库立刻能看效果）；
 *  - 测系统分享、邮件、配置备份这些外部通道；
 *  - 看异常日志（App 里被吞掉的异常 + 本 App 的 logcat）。
 *
 * ⚠️ 这个页面**不影响任何正常功能**：
 *  - 入口是隐藏的，不点进不来；
 *  - 所有测试动作都是独立的，不改动用户的周报数据；
 *  - 唯一会写数据的是「导出配置备份」，那是用户主动点的。
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
    var logcatText by remember { mutableStateOf("") }
    var selfCheck by remember { mutableStateOf("正在读取…") }
    var historyCount by remember { mutableStateOf(-1) }

    val settings = ServiceLocator.settings
    val reminderEnabled by settings.reminderEnabled.collectAsStateWithLifecycle()
    val reminderWeekdays by settings.reminderWeekdays.collectAsStateWithLifecycle()
    val sensitiveWords by settings.sensitiveWords.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        historyCount = withContext(Dispatchers.IO) { ServiceLocator.reportRepository.count() }
        selfCheck = buildString {
            append("版本：").append(BuildConfig.VERSION_NAME)
            append("（versionCode ").append(BuildConfig.VERSION_CODE).append("）\n")
            append("系统：Android ").append(Build.VERSION.RELEASE)
            append("（API ").append(Build.VERSION.SDK_INT).append("）\n")
            append("设备：").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            append("敏感词：").append(sensitiveWords.size).append(" 条\n")
            append("提醒：").append(if (reminderEnabled) "已开启" else "已关闭")
            append("　星期：").append(describeWeekdays(reminderWeekdays)).append("\n")
            append("下次提醒：")
                .append(ReminderScheduler.nextTriggerDescription(reminderWeekdays))
                .append("\n")
            append("通知权限：")
                .append(if (ReminderReceiver.hasNotificationPermission(context)) "已授权" else "未授权（Android 13+ 需要授权）")
                .append("\n")
            append("历史报告：").append(if (historyCount >= 0) "$historyCount 份" else "读取中").append("\n")
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
                                    (if (hit.excluded) "（已放行）" else "")
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

/** 备份密码默认沿用周报那套规则（本周周四 yyyyMMdd） */
private fun backupPassword(): String = com.workreport.app.util.DateUtils.fmtDate8(
    com.workreport.app.util.DateUtils.getThursday(
        com.workreport.app.util.DateUtils.parseDateRange(
            com.workreport.app.util.DateUtils.currentWeekRangeText(),
        )?.first ?: Calendar.getInstance().time,
    ),
)

/** 把星期集合转成「周四、周五」这样的文字 */
private fun describeWeekdays(weekdays: Set<Int>): String {
    if (weekdays.isEmpty()) return "未选择"
    val names = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
    return weekdays.sorted().joinToString("、") { "周" + (names[it] ?: it.toString()) }
}

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
        Text(text, fontSize = 12.5.sp)
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
