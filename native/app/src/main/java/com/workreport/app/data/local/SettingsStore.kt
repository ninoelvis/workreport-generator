package com.workreport.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.workreport.app.domain.model.SensitiveWord
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

    // ---------------- 引导页 ----------------

    /** 是否已经看过功能介绍（首次启动会弹，之后可在「我的」页随时再看） */
    private val _introShown = MutableStateFlow(prefs.getBoolean(KEY_INTRO_SHOWN, false))
    val introShown: StateFlow<Boolean> = _introShown.asStateFlow()

    fun setIntroShown(value: Boolean) {
        prefs.edit().putBoolean(KEY_INTRO_SHOWN, value).apply()
        _introShown.value = value
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
        _lastTimeRange.value = ""
        // 引导页不随「清除数据」重置，否则清完又会被弹一次
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
        private const val KEY_LAST_TIME_RANGE = "last_time_range"
        private const val KEY_INTRO_SHOWN = "intro_shown"
        private const val KEY_AUTO_SEND_MAIL = "auto_send_mail"

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
