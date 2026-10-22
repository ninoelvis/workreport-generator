package com.workreport.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.SettingsStore
import com.workreport.app.data.local.ThemeMode
import com.workreport.app.domain.model.SensitiveWord
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
    /** 收件人邮箱 */
    val mailRecipient: String = "",
    /** 发送邮件使用的应用包名（空 = 未选择） */
    val preferredMailPackage: String = "",
    /** 当前选择的邮件应用名称 */
    val preferredMailLabel: String = "",
    /** 本机可用的邮件应用 */
    val mailApps: List<MailApp> = emptyList(),
    /** 是否每次生成后都直接拉起邮箱 App 发送（不再询问） */
    val autoSendMail: Boolean = false,
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
            settings.mailRecipient.collect { _state.value = _state.value.copy(mailRecipient = it) }
        }
        viewModelScope.launch {
            settings.autoSendMail.collect { _state.value = _state.value.copy(autoSendMail = it) }
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

    fun onMailRecipientChange(value: String) {
        _state.value = _state.value.copy(mailRecipient = value)
        settings.setMailRecipient(value)
    }

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
