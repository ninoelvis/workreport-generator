package com.workreport.app.ui.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.workreport.app.data.local.SettingsStore
import java.util.Calendar

/**
 * 周报提醒的闹钟调度。
 *
 * 规则（用户已拍板，不接受改动）：
 * - 只选「周几」，**不选时间**：系统固定在 16:00–18:00 之间提醒；
 * - 星期编号 1=周一 … 7=周日（与 java.util.Calendar 的取值习惯对齐）；
 * - 默认选中周四（=4，见 [SettingsStore.DEFAULT_REMINDER_WEEKDAY]）。
 *
 * 这里刻意用 [AlarmManager.setWindow] 而不是 setExactAndAllowWhileIdle：
 * 前者是「非精确闹钟」，不需要 SCHEDULE_EXACT_ALARM 权限，
 * Android 13+ 也不用让用户单独去系统设置里授权精确闹钟；
 * 系统会在 16:00 之后的 2 小时窗口内择机唤醒，
 * 正好符合「16:00–18:00 之间提醒都行」的要求。
 */
object ReminderScheduler {

    /** 闹钟广播 action（接收器靠它识别是不是我们的闹钟） */
    const val ACTION_REMINDER = "com.workreport.app.action.REMINDER_FIRED"

    /**
     * 点击通知后带的 extra：打开首页并聚焦「工作记录」输入框。
     *
     * MainActivity 里这样读：
     * ```kotlin
     * val focus = intent.getBooleanExtra(ReminderScheduler.EXTRA_FOCUS_WORK_RECORD, false)
     * ```
     */
    const val EXTRA_FOCUS_WORK_RECORD = "FOCUS_WORK_RECORD"

    /**
     * 「强制触发」标记（仅调试页用）。
     *
     * 带上这个 extra 的闹钟会跳过「开关是否打开」「今天是不是选中的星期」这两道检查，
     * 直接发通知 —— 这样不改任何设置也能验证闹钟链路到底通不通。
     *
     * 正常排的提醒**不带**这个标记，行为一个字都没变。
     */
    const val EXTRA_FORCE_TEST = "FORCE_TEST"

    /**
     * 闹钟 PendingIntent 的固定 requestCode。
     *
     * 必须稳定：requestCode 变了就等于换了一个闹钟，旧的 cancel 不掉。
     */
    private const val REQUEST_CODE = 0x5752

    /** 提醒时间窗口长度：2 小时（16:00 → 18:00） */
    const val WINDOW_MILLIS: Long = 2L * 60L * 60L * 1000L

    /** 周几的中文名，索引 0 对应 1=周一 */
    val WEEKDAY_LABELS: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    // ---------------- 对外 API ----------------

    /**
     * 排下一次提醒。
     *
     * @param enabled 关闭提醒（或一个星期都没选）时等同于 [cancel]。
     * @param weekdays 选中的星期，1=周一 … 7=周日。
     */
    fun schedule(context: Context, enabled: Boolean, weekdays: Set<Int>) {
        val app = context.applicationContext
        if (!enabled || weekdays.none { it in 1..7 }) {
            cancel(app)
            return
        }
        val triggerAt = nextTriggerAt(Calendar.getInstance(), weekdays)
        if (triggerAt == null) {
            cancel(app)
            return
        }
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // setWindow：不精确闹钟，不需要 SCHEDULE_EXACT_ALARM 权限
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            WINDOW_MILLIS,
            alarmPendingIntent(app),
        )
    }

    /** 取消提醒 */
    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(alarmPendingIntent(app))
    }

    /**
     * 纯函数：算出「下一次提醒的时刻」，方便直接写单元测试。
     *
     * 规则：找下一个「星期在集合里、且当天 16:00 还没到」的日子。
     * 如果今天的 16:00 已经过了、而今天恰好是选中的星期 → 顺延到下一个选中的星期。
     *
     * @param now 当前时刻（不会被修改）
     * @param weekdays 选中的星期，1=周一 … 7=周日
     * @return 触发时刻（毫秒时间戳）；一个星期都没选时返回 null。
     */
    fun nextTriggerAt(now: Calendar, weekdays: Set<Int>): Long? {
        val days = weekdays.filter { it in 1..7 }.toSet()
        if (days.isEmpty()) return null
        // 往后找 7 天一定够（只要有一天被选中，这一周内就能排上）
        for (offset in 0..7) {
            val candidate = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, offset)
                set(Calendar.HOUR_OF_DAY, SettingsStore.REMINDER_WINDOW_START_HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // 16:00 已经过去的今天直接跳过
            if (candidate.timeInMillis <= now.timeInMillis) continue
            if (isReminderDay(days, isoDayOfWeek(candidate))) return candidate.timeInMillis
        }
        return null
    }

    /**
     * 下次提醒的中文描述，例如「下次提醒：10月30日（周四）16:00 – 18:00」。
     *
     * 一个星期都没选时返回「尚未选择提醒的星期」。
     */
    fun nextTriggerDescription(weekdays: Set<Int>, now: Calendar = Calendar.getInstance()): String {
        val triggerAt = nextTriggerAt(now, weekdays) ?: return "尚未选择提醒的星期"
        val trigger = (now.clone() as Calendar).apply { timeInMillis = triggerAt }
        val month = trigger.get(Calendar.MONTH) + 1
        val day = trigger.get(Calendar.DAY_OF_MONTH)
        val weekday = WEEKDAY_LABELS[isoDayOfWeek(trigger) - 1]
        return "下次提醒：${month}月${day}日（周${weekday}）" +
            "${SettingsStore.REMINDER_WINDOW_START_HOUR}:00 – ${SettingsStore.REMINDER_WINDOW_END_HOUR}:00"
    }

    /** 某个星期（1=周一 … 7=周日）是否在提醒集合里 */
    fun isReminderDay(weekdays: Set<Int>, dayOfWeek: Int): Boolean =
        dayOfWeek in 1..7 && weekdays.contains(dayOfWeek)

    /**
     * 把 [Calendar.DAY_OF_WEEK]（1=周日 … 7=周六）换算成 1=周一 … 7=周日。
     */
    fun isoDayOfWeek(calendar: Calendar): Int {
        val raw = calendar.get(Calendar.DAY_OF_WEEK)
        return if (raw == Calendar.SUNDAY) 7 else raw - 1
    }

    // ---------------- 每日状态（SharedPreferences） ----------------

    /** 记录「今天已提醒」的 SharedPreferences */
    private const val PREFS_NAME = "workreport_reminder_state"

    /** 已发过通知的前缀，key 形如 reminder_fired_20261024 */
    private const val KEY_FIRED_PREFIX = "reminder_fired_"

    /** 已弹过横幅的前缀，key 形如 reminder_banner_20261024 */
    private const val KEY_BANNER_PREFIX = "reminder_banner_"

    /** 今天是不是已经弹过提醒通知了 */
    fun hasFiredToday(context: Context, now: Calendar = Calendar.getInstance()): Boolean =
        prefs(context).getBoolean(KEY_FIRED_PREFIX + dateKey(now), false)

    /** 记一笔：今天已经提醒过了（同一天不再重复弹） */
    fun markFiredToday(context: Context, now: Calendar = Calendar.getInstance()) {
        prefs(context).edit().putBoolean(KEY_FIRED_PREFIX + dateKey(now), true).apply()
    }

    /** 今天是不是已经弹过首页横幅了 */
    fun hasBannerShownToday(context: Context, now: Calendar = Calendar.getInstance()): Boolean =
        prefs(context).getBoolean(KEY_BANNER_PREFIX + dateKey(now), false)

    /** 记一笔：今天已经弹过首页横幅了 */
    fun markBannerShownToday(context: Context, now: Calendar = Calendar.getInstance()) {
        prefs(context).edit().putBoolean(KEY_BANNER_PREFIX + dateKey(now), true).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 日期键：yyyyMMdd，例如 20261024 */
    private fun dateKey(now: Calendar): String = "%04d%02d%02d".format(
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH) + 1,
        now.get(Calendar.DAY_OF_MONTH),
    )

    // ---------------- 内部 ----------------

    /** 指向 [ReminderReceiver] 的闹钟 PendingIntent */
    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
