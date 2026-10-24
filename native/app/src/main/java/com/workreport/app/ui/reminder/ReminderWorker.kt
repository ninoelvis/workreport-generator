package com.workreport.app.ui.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.SettingsStore
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每日兜底任务。
 *
 * 闹钟（[ReminderScheduler]）是**主通道**：16:00 触发，系统在 16:00–18:00 之间择机提醒。
 * WorkManager 的周期任务不保证在 16:00 跑，所以这个 Worker 只做两件事：
 *
 * 1. **补发**：今天在选中的周几、已经过了 16:00、今天还没提醒过 → 补一条通知
 *    （比如闹钟被系统清掉了、或用户临时改了选中的星期）；
 * 2. **重排**：每天重新排一次闹钟。setWindow 是一次性闹钟，
 *    虽然接收器触发后会自动续上，但设备重启 / 系统清理都可能把闹钟清掉，这里再兜一层。
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Worker 有可能先于 Application 初始化被拉起，这里兜一下底
        ServiceLocator.init(applicationContext)

        val settings = ServiceLocator.settings
        if (!settings.reminderEnabled.value) return Result.success()

        // 1) 补发
        catchUpIfNeeded(applicationContext, settings)

        // 2) 重排下一次闹钟
        ReminderScheduler.schedule(
            applicationContext,
            settings.reminderEnabled.value,
            settings.reminderWeekdays.value,
        )
        return Result.success()
    }

    companion object {

        /** 周期任务的唯一名字 */
        private const val WORK_NAME = "workreport_reminder_daily"

        /**
         * 补发的截止时间。
         *
         * WorkManager 不保证什么时候跑，万一凌晨三点才轮到它，
         * 就没必要半夜弹通知打扰用户了。
         */
        private const val CATCH_UP_END_HOUR = 22

        /**
         * 登记每日兜底任务（每天跑一次）。
         *
         * 建议在「开启提醒」时调用一次；也可以在 WorkReportApp.onCreate 里无条件调用。
         */
        fun scheduleDailyWorker(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** 取消每日兜底任务 */
        fun cancelDailyWorker(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }

        /**
         * 补发提醒。
         *
         * 条件：今天在选中的周几 && 当前时间已过 16:00 && 今天还没提醒过。
         */
        private fun catchUpIfNeeded(context: Context, settings: SettingsStore) {
            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)
            // 还没到提醒时间，不用补
            if (hour < SettingsStore.REMINDER_WINDOW_START_HOUR) return
            // 太晚了就别打扰用户
            if (hour >= CATCH_UP_END_HOUR) return

            val weekdays = settings.reminderWeekdays.value
            if (!ReminderScheduler.isReminderDay(weekdays, ReminderScheduler.isoDayOfWeek(now))) return

            // 今天已经提醒过了
            if (ReminderScheduler.hasFiredToday(context, now)) return

            if (ReminderReceiver.postReminderNotification(context)) {
                ReminderScheduler.markFiredToday(context, now)
            }
        }
    }
}
