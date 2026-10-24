package com.workreport.app.ui.reminder

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.ReportEntity
import com.workreport.app.data.local.SettingsStore
import com.workreport.app.ui.common.SectionCard
import com.workreport.app.ui.common.SubPageTopBar
import com.workreport.app.util.DateUtils
import java.util.Calendar

/**
 * 周报提醒设置页（「我的 → 周报提醒」）。
 *
 * 用户只做两件事：**开关** + **选周几**。
 * 时间是固定的 16:00–18:00，不给选。
 */
@Composable
fun ReminderPage(
    /** 返回上一页 */
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = ServiceLocator.settings
    val enabled by settings.reminderEnabled.collectAsStateWithLifecycle()
    val weekdays by settings.reminderWeekdays.collectAsStateWithLifecycle()

    /** 是否已有通知权限（Android 13+ 需要） */
    var hasPermission by remember {
        mutableStateOf(ReminderReceiver.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SubPageTopBar(
            title = "周报提醒",
            subtitle = "到点提醒你别忘了写周报",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------- 开关 ----------------
            item {
                SectionCard(title = "提醒开关", icon = "🔔") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "开启周报提醒",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "开启后会在选中的星期提醒你写周报",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { newValue ->
                                settings.setReminderEnabled(newValue)
                                if (newValue) {
                                    ReminderScheduler.schedule(context, true, weekdays)
                                    // 顺便登记每日兜底任务
                                    ReminderWorker.scheduleDailyWorker(context)
                                } else {
                                    ReminderScheduler.cancel(context)
                                    ReminderWorker.cancelDailyWorker(context)
                                }
                            },
                        )
                    }
                }
            }

            // ---------------- 周几多选 ----------------
            item {
                SectionCard(
                    title = "提醒星期",
                    icon = "📅",
                    hint = if (weekdays.isEmpty()) "未选择" else "已选 ${weekdays.size} 天",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            (1..7).forEach { day ->
                                val selected = day in weekdays
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            val next =
                                                if (selected) weekdays - day else weekdays + day
                                            settings.setReminderWeekdays(next)
                                            ReminderScheduler.schedule(context, enabled, next)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = ReminderScheduler.WEEKDAY_LABELS[day - 1],
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) Color.White
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(
                            "点一下选中、再点一下取消，可以多选。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (weekdays.isEmpty()) {
                            Text(
                                "至少选一天，否则不会提醒。",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // ---------------- 固定时间 + 下次提醒 ----------------
            item {
                SectionCard(title = "提醒时间", icon = "⏰") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "提醒时间：" +
                                "${SettingsStore.REMINDER_WINDOW_START_HOUR}:00 – " +
                                "${SettingsStore.REMINDER_WINDOW_END_HOUR}:00 之间",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "时间固定，不用设置；系统会在这两个小时里挑个时间提醒你。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        Text(
                            text = if (enabled) {
                                ReminderScheduler.nextTriggerDescription(weekdays, Calendar.getInstance())
                            } else {
                                "开启后才会安排提醒"
                            },
                            fontSize = 13.sp,
                            color = if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---------------- 通知权限 ----------------
            if (enabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission
            ) {
                item {
                    SectionCard(title = "通知权限", icon = "🔕") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Android 13 及以上需要通知权限，不然收不到提醒。",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("授予通知权限", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 首页顶部的周报提醒横幅。
 *
 * 显示条件（缺一不可）：
 * 1. 周报提醒已开启；
 * 2. 今天在选中的周几里；
 * 3. 本周还没生成过周报；
 * 4. 今天还没弹过这条横幅。
 *
 * 内嵌提示条，不打断、不用 Dialog。挂在首页顶部即可：
 * ```kotlin
 * item {
 *     ReminderBanner(onFocusWorkRecord = { focusWorkRecordInput() })
 * }
 * ```
 */
@Composable
fun ReminderBanner(
    modifier: Modifier = Modifier,
    /** 点「去记录」时的回调，一般是聚焦首页「工作记录」输入框 */
    onFocusWorkRecord: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = ServiceLocator.settings
    val enabled by settings.reminderEnabled.collectAsStateWithLifecycle()
    val weekdays by settings.reminderWeekdays.collectAsStateWithLifecycle()

    // remember 一下，避免每次重组都重新订阅数据库
    val reportsFlow = remember { ServiceLocator.reportRepository.observeAll() }
    val reports by reportsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val now = Calendar.getInstance()
    val isReminderDay = enabled &&
        ReminderScheduler.isReminderDay(weekdays, ReminderScheduler.isoDayOfWeek(now))
    val generatedThisWeek = remember(reports) { reports.any { isReportOfThisWeek(it, now) } }
    val bannerShown = ReminderScheduler.hasBannerShownToday(context, now)
    val show = isReminderDay && !generatedThisWeek && !bannerShown

    // 真的显示出来了就记一笔，避免同一天反复弹
    LaunchedEffect(show) {
        if (show) ReminderScheduler.markBannerShownToday(context, now)
    }

    if (!show) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📝", fontSize = 16.sp, lineHeight = 16.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "该写本周周报了",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "这周做的事还没记完的话，现在顺手写两句。",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
        TextButton(onClick = onFocusWorkRecord) {
            Text("去记录", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * 这条历史报告是不是「本周」的。
 *
 * 优先比时间范围（首页默认就是本周的时间范围）；
 * 老记录没有存时间范围，就退化成按「年 + 月 + 周次」比对。
 */
private fun isReportOfThisWeek(entity: ReportEntity, now: Calendar): Boolean {
    if (entity.timeRange.isNotBlank()) {
        return entity.timeRange.trim() == DateUtils.currentWeekRangeText(now.time)
    }
    val sunday = DateUtils.getWeekRange(now.time).first
    val calendar = Calendar.getInstance().apply { time = sunday }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val weekNum = DateUtils.getWeekOfMonth(sunday)
    return entity.year == year && entity.month == month && entity.weekNum == weekNum
}
