package com.workreport.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.domain.model.ShortcutSlot
import com.workreport.app.domain.model.ShortcutTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 主题模式 */
enum class ThemeMode(val storageValue: String) {
    /** 跟随系统深浅色 */
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/**
 * 轻量本地设置（SharedPreferences）。
 *
 * 只保存体量小、结构简单的键值；历史报告这种结构化数据走 Room。
 * 每次写入都会同步刷新对应的 StateFlow，Compose 侧直接 collect 即可。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ---------------- 姓名 ----------------

    private val _userName = MutableStateFlow(prefs.getString(KEY_USER_NAME, "").orEmpty())
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun setUserName(value: String) {
        val v = value.trim()
        prefs.edit().putString(KEY_USER_NAME, v).apply()
        _userName.value = v
    }

    // ---------------- 主题 ----------------

    private val _themeMode = MutableStateFlow(ThemeMode.from(prefs.getString(KEY_THEME_MODE, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.storageValue).apply()
        _themeMode.value = mode
    }

    // ---------------- 敏感词库 ----------------

    private val _sensitiveWords = MutableStateFlow(loadSensitiveWords())
    val sensitiveWords: StateFlow<List<SensitiveWord>> = _sensitiveWords.asStateFlow()

    fun setSensitiveWords(words: List<SensitiveWord>) {
        val array = JSONArray()
        words.forEach { w ->
            array.put(
                JSONObject()
                    .put("w", w.word)
                    .put("p", w.pinyin)
                    .put("a", w.auto),
            )
        }
        prefs.edit().putString(KEY_SENSITIVE_WORDS, array.toString()).apply()
        _sensitiveWords.value = words
    }

    // ---------------- 默认发送应用 ----------------

    private val _preferredMailPackage = MutableStateFlow(prefs.getString(KEY_MAIL_PACKAGE, "").orEmpty())
    val preferredMailPackage: StateFlow<String> = _preferredMailPackage.asStateFlow()

    fun setPreferredMailPackage(pkg: String) {
        prefs.edit().putString(KEY_MAIL_PACKAGE, pkg).apply()
        _preferredMailPackage.value = pkg
    }

    // ---------------- 生成后是否自动发邮件 ----------------
    /**
     * true  = 以后每次生成都直接拉起邮箱 App 发送，不再询问
     * false = 每次生成后都弹窗询问
     */
    private val _autoSendMail = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SEND_MAIL, false))
    val autoSendMail: StateFlow<Boolean> = _autoSendMail.asStateFlow()

    fun setAutoSendMail(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SEND_MAIL, value).apply()
        _autoSendMail.value = value
    }

    // ---------------- 首页表单草稿 ----------------

    /**
     * 首页填写到一半的草稿。每次输入都会存一份，下次打开自动恢复。
     * 生成压缩包成功后由 [clearDraft] 清掉。
     */
    private val _draft = MutableStateFlow(loadDraft())
    val draft: StateFlow<ReportDraft> = _draft.asStateFlow()

    fun saveDraft(draft: ReportDraft) {
        prefs.edit().putString(KEY_DRAFT, DraftCodec.encode(draft)).apply()
        _draft.value = draft
    }

    /** 草稿已随周报产出，清掉 */
    fun clearDraft() {
        prefs.edit().remove(KEY_DRAFT).apply()
        _draft.value = ReportDraft()
    }

    private fun loadDraft(): ReportDraft =
        DraftCodec.decode(prefs.getString(KEY_DRAFT, null))

    // ---------------- 快捷记录模板 ----------------

    /**
     * 两套互相独立的快捷模板：一套给「本周工作记录」，一套给「下周工作计划」。
     * 每次改动都会同步刷新对应的 StateFlow。
     */
    private val _shortcuts: Map<ShortcutSlot, MutableStateFlow<List<ShortcutTemplate>>> =
        ShortcutSlot.entries.associateWith { slot ->
            MutableStateFlow(loadShortcuts(slot))
        }

    fun shortcuts(slot: ShortcutSlot): StateFlow<List<ShortcutTemplate>> =
        _shortcuts.getValue(slot).asStateFlow()

    /** 覆盖某个分区的全部模板 */
    fun setShortcuts(slot: ShortcutSlot, templates: List<ShortcutTemplate>) {
        prefs.edit().putString(slot.storageKey, ShortcutCodec.encode(templates)).apply()
        _shortcuts.getValue(slot).value = templates
    }

    /** 新增一条模板，正文为空则忽略 */
    fun addShortcut(slot: ShortcutSlot, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val list = _shortcuts.getValue(slot).value.toMutableList()
        list.add(ShortcutTemplate(id = newId(), text = body))
        setShortcuts(slot, list)
    }

    /** 修改模板正文 */
    fun updateShortcut(slot: ShortcutSlot, id: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val list = _shortcuts.getValue(slot).value.map {
            if (it.id == id) it.copy(text = body) else it
        }
        setShortcuts(slot, list)
    }

    /** 删除模板 */
    fun removeShortcut(slot: ShortcutSlot, id: String) {
        val list = _shortcuts.getValue(slot).value.filterNot { it.id == id }
        setShortcuts(slot, list)
    }

    /** 模板 id：时间戳 + 随机尾巴，够用且不用引三方库 */
    private fun newId(): String =
        "${System.currentTimeMillis()}-${(0..0xFFFF).random().toString(16)}"

    private fun loadShortcuts(slot: ShortcutSlot): List<ShortcutTemplate> =
        ShortcutCodec.decode(prefs.getString(slot.storageKey, null))

    // ---------------- 周报提醒 ----------------

    /**
     * 是否开启周报提醒。
     *
     * 提醒时间固定在 16:00–18:00 之间（用户只需选周几，不需要选时间）。
     */
    private val _reminderEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDER_ENABLED, false))
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    /** 提醒的星期几，1=周一 … 7=周日 */
    private val _reminderWeekdays = MutableStateFlow(loadReminderWeekdays())
    val reminderWeekdays: StateFlow<Set<Int>> = _reminderWeekdays.asStateFlow()

    fun setReminderEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, value).apply()
        _reminderEnabled.value = value
    }

    fun setReminderWeekdays(days: Set<Int>) {
        val valid = days.filter { it in 1..7 }.toSet()
        prefs.edit().putString(KEY_REMINDER_WEEKDAYS, valid.sorted().joinToString(",")).apply()
        _reminderWeekdays.value = valid
    }

    private fun loadReminderWeekdays(): Set<Int> {
        val raw = prefs.getString(KEY_REMINDER_WEEKDAYS, null) ?: return setOf(DEFAULT_REMINDER_WEEKDAY)
        val parsed = raw.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
        return parsed.ifEmpty { setOf(DEFAULT_REMINDER_WEEKDAY) }
    }

    // ---------------- 配置备份 ----------------

    /**
     * 导出当前个人配置（不含历史报告与已生成的 ZIP）。
     */
    fun exportConfig(): ConfigSnapshot = ConfigSnapshot(
        userName = _userName.value,
        themeMode = _themeMode.value.storageValue,
        sensitiveWords = _sensitiveWords.value,
        workShortcuts = _shortcuts.getValue(ShortcutSlot.WORK).value,
        planShortcuts = _shortcuts.getValue(ShortcutSlot.PLAN).value,
        preferredMailPackage = _preferredMailPackage.value,
        autoSendMail = _autoSendMail.value,
        reminderEnabled = _reminderEnabled.value,
        reminderWeekdays = _reminderWeekdays.value,
        draft = _draft.value,
    )

    /**
     * 导入一份配置。
     *
     * @param merge true=合并（敏感词按词去重、模板按正文去重，姓名/提醒等直接覆盖，
     *              草稿保留当前正在写的那份）；false=整体覆盖。
     */
    fun importConfig(snapshot: ConfigSnapshot, merge: Boolean) {
        if (!merge) {
            setUserName(snapshot.userName)
            setThemeMode(ThemeMode.from(snapshot.themeMode))
            setSensitiveWords(snapshot.sensitiveWords)
            setShortcuts(ShortcutSlot.WORK, snapshot.workShortcuts)
            setShortcuts(ShortcutSlot.PLAN, snapshot.planShortcuts)
            setPreferredMailPackage(snapshot.preferredMailPackage)
            setAutoSendMail(snapshot.autoSendMail)
            setReminderEnabled(snapshot.reminderEnabled)
            setReminderWeekdays(snapshot.reminderWeekdays)
            saveDraft(snapshot.draft)
            return
        }

        // 合并模式：敏感词按「词」去重，模板按「正文」去重
        val mergedWords = LinkedHashMap<String, SensitiveWord>()
        for (w in _sensitiveWords.value + snapshot.sensitiveWords) {
            mergedWords[w.word] = w
        }
        setSensitiveWords(mergedWords.values.toList())

        val mergedWork = mergeTemplates(_shortcuts.getValue(ShortcutSlot.WORK).value, snapshot.workShortcuts)
        val mergedPlan = mergeTemplates(_shortcuts.getValue(ShortcutSlot.PLAN).value, snapshot.planShortcuts)
        setShortcuts(ShortcutSlot.WORK, mergedWork)
        setShortcuts(ShortcutSlot.PLAN, mergedPlan)

        // 姓名、邮箱偏好、提醒设置直接以导入方为准；草稿保留当前正在写的那份
        if (snapshot.userName.isNotBlank()) setUserName(snapshot.userName)
        if (snapshot.preferredMailPackage.isNotBlank()) {
            setPreferredMailPackage(snapshot.preferredMailPackage)
        }
        setReminderEnabled(snapshot.reminderEnabled)
        if (snapshot.reminderWeekdays.isNotEmpty()) {
            setReminderWeekdays(snapshot.reminderWeekdays)
        }
    }

    /** 合并模板：按正文去重，保留已有的（id 更稳定） */
    private fun mergeTemplates(
        current: List<ShortcutTemplate>,
        incoming: List<ShortcutTemplate>,
    ): List<ShortcutTemplate> {
        val merged = LinkedHashMap<String, ShortcutTemplate>()
        for (t in current) merged[t.text] = t
        for (t in incoming) if (!merged.containsKey(t.text)) merged[t.text] = t
        return merged.values.toList()
    }

    // ---------------- 引导页 ----------------

    /** 是否已经看过功能介绍（首次启动会弹，之后可在「我的」页随时再看） */
    private val _introShown = MutableStateFlow(prefs.getBoolean(KEY_INTRO_SHOWN, false))
    val introShown: StateFlow<Boolean> = _introShown.asStateFlow()

    fun setIntroShown(value: Boolean) {
        prefs.edit().putBoolean(KEY_INTRO_SHOWN, value).apply()
        _introShown.value = value
    }

    // ---------------- 通知权限引导 ----------------

    /**
     * 用户是否已经对「开启通知权限」这个引导做过选择。
     *
     *  - 点了「去开启」→ 记为已处理（不管系统那边给没给）；
     *  - 点了「以后再说」→ 也记为已处理，**不再重复打扰**。
     *
     * 真正的权限状态每次都问系统问，不靠这个字段判断。
     */
    private val _notificationPromptHandled =
        MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATION_PROMPT_HANDLED, false))
    val notificationPromptHandled: StateFlow<Boolean> = _notificationPromptHandled.asStateFlow()

    fun setNotificationPromptHandled(value: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_PROMPT_HANDLED, value).apply()
        _notificationPromptHandled.value = value
    }

    // ---------------- 上次填写的时间范围（草稿恢复用） ----------------

    private val _lastTimeRange = MutableStateFlow(prefs.getString(KEY_LAST_TIME_RANGE, "").orEmpty())
    val lastTimeRange: StateFlow<String> = _lastTimeRange.asStateFlow()

    fun setLastTimeRange(value: String) {
        prefs.edit().putString(KEY_LAST_TIME_RANGE, value).apply()
        _lastTimeRange.value = value
    }

    // ---------------- 清除全部本地数据 ----------------

    fun clearAll() {
        prefs.edit().clear().apply()
        _userName.value = ""
        _themeMode.value = ThemeMode.SYSTEM
        _sensitiveWords.value = loadSensitiveWords()
        _preferredMailPackage.value = ""
        _autoSendMail.value = false
        _reminderEnabled.value = false
        _reminderWeekdays.value = setOf(DEFAULT_REMINDER_WEEKDAY)
        _lastTimeRange.value = ""
        _draft.value = ReportDraft()
        ShortcutSlot.entries.forEach { _shortcuts.getValue(it).value = emptyList() }
        // 引导页不随「清除数据」重置，否则清完又会被弹一次
        _notificationPromptHandled.value = false
    }

    private fun loadSensitiveWords(): List<SensitiveWord> {
        val raw = prefs.getString(KEY_SENSITIVE_WORDS, null) ?: return defaultSensitiveWords()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val w = o.optString("w").trim()
                    if (w.isEmpty()) continue
                    add(
                        SensitiveWord(
                            word = w,
                            pinyin = o.optString("p"),
                            auto = o.optBoolean("a", true),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            defaultSensitiveWords()
        }
    }

    companion object {
        private const val PREF_NAME = "workreport_settings"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SENSITIVE_WORDS = "sensitive_words"
        private const val KEY_MAIL_PACKAGE = "preferred_mail_package"
        /** 草稿整体存成一个 JSON，字段编解码见 DraftCodec */
        private const val KEY_DRAFT = "report_draft"
        private const val KEY_LAST_TIME_RANGE = "last_time_range"
        private const val KEY_INTRO_SHOWN = "intro_shown"
        private const val KEY_NOTIFICATION_PROMPT_HANDLED = "notification_prompt_handled"
        private const val KEY_AUTO_SEND_MAIL = "auto_send_mail"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_WEEKDAYS = "reminder_weekdays"

        /** 默认提醒星期：周四（4） */
        const val DEFAULT_REMINDER_WEEKDAY = 4

        /** 提醒时间窗口：16:00–18:00（用户只选周几，时间固定） */
        const val REMINDER_WINDOW_START_HOUR = 16
        const val REMINDER_WINDOW_END_HOUR = 18

        /** 内置默认词库，与 WebView 版保持一致 */
        val DEFAULT_SENSITIVE_WORDS: List<SensitiveWord> = listOf(
            SensitiveWord("纪委", "jw"),
            SensitiveWord("监委", "jw"),
            SensitiveWord("案管", "ag"),
            SensitiveWord("审理", "sl"),
            SensitiveWord("党风", "df"),
            SensitiveWord("一体化", "yth"),
            SensitiveWord("查询", "cx"),
            SensitiveWord("措施", "cs"),
            SensitiveWord("线索", "xs"),
            SensitiveWord("案件", "aj"),
            SensitiveWord("信访", "xf"),
            SensitiveWord("法规", "fg"),
            SensitiveWord("保密", "bm"),
        )

        fun defaultSensitiveWords(): List<SensitiveWord> = DEFAULT_SENSITIVE_WORDS
    }
}
