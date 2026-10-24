package com.workreport.app.ui.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.workreport.app.MainActivity
import com.workreport.app.R
import com.workreport.app.ServiceLocator
import java.util.Calendar

/**
 * 闹钟接收器：收到 [ReminderScheduler.ACTION_REMINDER] 后弹一条周报提醒通知。
 *
 * 触发前会再确认一遍：
 * - 提醒开关是打开的；
 * - 今天是选中的星期；
 * - 今天还没提醒过（避免一天里重复弹）。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // 兜底：极端情况下 ServiceLocator 可能还没初始化
        ServiceLocator.init(context.applicationContext)

        val settings = ServiceLocator.settings
        if (!settings.reminderEnabled.value) return

        val weekdays = settings.reminderWeekdays.value
        val today = Calendar.getInstance()
        if (!ReminderScheduler.isReminderDay(weekdays, ReminderScheduler.isoDayOfWeek(today))) return

        // 一天只提醒一次
        if (ReminderScheduler.hasFiredToday(context, today)) return

        if (postReminderNotification(context)) {
            ReminderScheduler.markFiredToday(context, today)
        }

        // setWindow 是一次性闹钟，触发完把下一次排上
        ReminderScheduler.schedule(
            context,
            settings.reminderEnabled.value,
            settings.reminderWeekdays.value,
        )
    }

    companion object {

        /** 通知渠道 id（固定，不要改：改了等于换了一个渠道） */
        const val CHANNEL_ID = "workreport_reminder"

        /** 通知 id（固定，方便覆盖而不是越堆越多） */
        private const val NOTIFICATION_ID = 0x5741

        /** 点击通知时用的 PendingIntent requestCode */
        private const val REQUEST_CODE_CLICK = 0x5753

        /**
         * 有没有通知权限。
         *
         * Android 13（API 33）及以上需要 POST_NOTIFICATIONS 运行时权限，
         * 没给就不发通知（不申请精确闹钟、也不崩）。
         */
        fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

        /**
         * 弹一条周报提醒通知。
         *
         * @return true = 真的发出去了；false = 没权限或系统拒绝。
         */
        fun postReminderNotification(context: Context): Boolean {
            val app = context.applicationContext
            // 没有权限就别发，留给 WorkManager 兜底补发
            if (!hasNotificationPermission(app)) return false

            ensureChannel(app)

            // 点击通知 → 打开 App 首页，并告诉首页聚焦「工作记录」输入框
            val contentIntent = PendingIntent.getActivity(
                app,
                REQUEST_CODE_CLICK,
                Intent(app, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(ReminderScheduler.EXTRA_FOCUS_WORK_RECORD, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("该写本周周报了")
                .setContentText("点开就能在「工作记录」里把这周做的事记下来。")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "本周还没写周报的话，点开 App 在「工作记录」里把这周做的事记下来，" +
                            "再一键生成周报就行。"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()

            return try {
                NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
                true
            } catch (e: SecurityException) {
                // 没给通知权限时 notify 会抛异常，吞掉即可，绝不能让 App 崩
                false
            }
        }

        /** 建通知渠道（Android 8+ 必需，渠道建好后系统才会展示通知） */
        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "周报提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "到点提醒你该写本周周报了"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
