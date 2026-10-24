package com.workreport.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.SettingsStore
import com.workreport.app.data.local.ThemeMode
import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.domain.model.ShortcutSlot
import com.workreport.app.domain.model.ShortcutTemplate
import com.workreport.app.util.MailApp
import com.workreport.app.util.MailHelper
import com.workreport.app.util.PinyinTable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val userName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sensitiveWords: List<SensitiveWord> = emptyList(),
    /** 发送邮件使用的应用包名（空 = 未选择） */
    val preferredMailPackage: String = "",
    /** 当前选择的邮件应用名称 */
    val preferredMailLabel: String = "",
    /** 本机可用的邮件应用 */
    val mailApps: List<MailApp> = emptyList(),
    /** 是否每次生成后都直接拉起邮箱 App 发送（不再询问） */
    val autoSendMail: Boolean = false,
    /** 是否开启周报提醒 */
    val reminderEnabled: Boolean = false,
    /** 提醒的星期几，1=周一 … 7=周日 */
    val reminderWeekdays: Set<Int> = emptySet(),
    /** 「本周工作记录」快捷模板 */
    val workShortcuts: List<ShortcutTemplate> = emptyList(),
    /** 「下周工作计划」快捷模板 */
    val planShortcuts: List<ShortcutTemplate> = emptyList(),
    val message: String? = null,
)

class ProfileViewModel(
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.userName.collect { _state.value = _state.value.copy(userName = it) }
        }
        viewModelScope.launch {
            settings.themeMode.collect { _state.value = _state.value.copy(themeMode = it) }
        }
        viewModelScope.launch {
            settings.sensitiveWords.collect { _state.value = _state.value.copy(sensitiveWords = it) }
        }
        viewModelScope.launch {
            settings.autoSendMail.collect { _state.value = _state.value.copy(autoSendMail = it) }
        }
        viewModelScope.launch {
            settings.reminderEnabled.collect { _state.value = _state.value.copy(reminderEnabled = it) }
        }
        viewModelScope.launch {
            settings.reminderWeekdays.collect { _state.value = _state.value.copy(reminderWeekdays = it) }
        }
        viewModelScope.launch {
            settings.shortcuts(ShortcutSlot.WORK).collect {
                _state.value = _state.value.copy(workShortcuts = it)
            }
        }
        viewModelScope.launch {
            settings.shortcuts(ShortcutSlot.PLAN).collect {
                _state.value = _state.value.copy(planShortcuts = it)
            }
        }
        viewModelScope.launch {
            settings.preferredMailPackage.collect { pkg ->
                val label = if (pkg.isEmpty()) "" else {
                    _state.value.mailApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                }
                _state.value = _state.value.copy(preferredMailPackage = pkg, preferredMailLabel = label)
            }
        }
        // 枚举本机邮箱应用（放到 IO 线程，别卡 UI）
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                MailHelper.resolveMailApps(ServiceLocator.context)
            }
            val pkg = settings.preferredMailPackage.value
            val label = if (pkg.isEmpty()) "" else {
                apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
            }
            _state.value = _state.value.copy(mailApps = apps, preferredMailLabel = label)
        }
    }

    // ---------------- 邮件设置 ----------------

    /** 是否每次生成后都直接拉起邮箱 App 发送 */
    fun setAutoSendMail(value: Boolean) {
        settings.setAutoSendMail(value)
    }

    fun setPreferredMailPackage(pkg: String) {
        settings.setPreferredMailPackage(pkg)
        val label = _state.value.mailApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
        _state.value = _state.value.copy(
            preferredMailPackage = pkg,
            preferredMailLabel = if (pkg.isEmpty()) "" else label,
        )
    }

    fun onUserNameChange(value: String) {
        _state.value = _state.value.copy(userName = value)
        settings.setUserName(value)
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.setThemeMode(mode)
    }

    // ---------------- 快捷记录模板 ----------------

    /** 新增一条快捷模板 */
    fun addShortcut(slot: ShortcutSlot, text: String) {
        val body = text.trim()
        if (body.isEmpty()) {
            showMessage("模板内容不能为空")
            return
        }
        settings.addShortcut(slot, body)
        showMessage("已添加模板")
    }

    /** 修改快捷模板 */
    fun updateShortcut(slot: ShortcutSlot, id: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) {
            showMessage("模板内容不能为空")
            return
        }
        settings.updateShortcut(slot, id, body)
        showMessage("已保存")
    }

    /** 删除快捷模板 */
    fun removeShortcut(slot: ShortcutSlot, id: String) {
        settings.removeShortcut(slot, id)
        showMessage("已删除模板")
    }

    // ---------------- 敏感词库 ----------------

    /** 计算某个词的缩写（逐字拼音首字母） */
    fun autoPinyin(word: String): String = PinyinTable.initialsOf(word)

    fun addWord(word: String, pinyin: String) {
        val w = word.trim()
        if (w.isEmpty()) {
            showMessage("请填写敏感词")
            return
        }
        if (_state.value.sensitiveWords.any { it.word == w }) {
            showMessage("「$w」已经在词库里了")
            return
        }
        val auto = autoPinyin(w)
        val p = pinyin.trim().ifEmpty { auto }
        val list = _state.value.sensitiveWords + SensitiveWord(w, p, p == auto)
        settings.setSensitiveWords(list)
        showMessage("已添加「$w」→「$p」")
    }

    fun updateWord(index: Int, word: String, pinyin: String) {
        val list = _state.value.sensitiveWords.toMutableList()
        if (index !in list.indices) return
        val w = word.trim()
        if (w.isEmpty()) {
            showMessage("敏感词不能为空")
            return
        }
        val auto = autoPinyin(w)
        val p = pinyin.trim().ifEmpty { auto }
        list[index] = SensitiveWord(w, p, p == auto)
        settings.setSensitiveWords(list)
        showMessage("已更新")
    }

    fun removeWord(index: Int) {
        val list = _state.value.sensitiveWords.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        settings.setSensitiveWords(list)
        showMessage("已删除")
    }

    fun restoreDefaults() {
        settings.setSensitiveWords(SettingsStore.defaultSensitiveWords())
        showMessage("已恢复默认词库")
    }

    fun clearAllData() {
        settings.clearAll()
        showMessage("已清除全部本地数据")
    }

    fun showMessage(text: String?) {
        _state.value = _state.value.copy(message = text)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    companion object {
        fun factory(): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProfileViewModel(settings = ServiceLocator.settings) as T
                }
            }
    }
}
