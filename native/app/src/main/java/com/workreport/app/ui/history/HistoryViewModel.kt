package com.workreport.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.ReportEntity
import com.workreport.app.data.local.SettingsStore
import com.workreport.app.data.repository.ReportRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class HistoryUiState(
    val keyword: String = "",
    val reports: List<ReportEntity> = emptyList(),
    val totalCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: ReportRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val keyword = MutableStateFlow("")

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val reports: StateFlow<List<ReportEntity>> = keyword
        .flatMapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preferredMailPackage: StateFlow<String> = settings.preferredMailPackage

    init {
        viewModelScope.launch {
            repository.observeAll().collect { all ->
                _uiState.value = _uiState.value.copy(totalCount = all.size)
            }
        }
        viewModelScope.launch {
            reports.collect { list ->
                _uiState.value = _uiState.value.copy(reports = list)
            }
        }
    }

    fun onKeywordChange(value: String) {
        keyword.value = value
        _uiState.value = _uiState.value.copy(keyword = value)
    }

    /** 二次下载：把历史报告再存一份到系统「下载」目录 */
    fun downloadAgain(entity: ReportEntity, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.copyToDownloads(entity)
            onDone(ok)
        }
    }

    /** 二次发送：拉起邮箱 App 并带上附件 */
    fun sendAgain(entity: ReportEntity, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val file = File(entity.filePath)
            // 双保险：异常一律吞掉，不能让 App 因为发邮件崩掉
            val ok = runCatching {
                com.workreport.app.util.MailHelper.launchWithAttachment(
                    context = ServiceLocator.context,
                    packageName = settings.preferredMailPackage.value.ifEmpty { null },
                    file = file,
                    // 主题与压缩包名保持一致
                    subject = entity.fileName,
                    // 正文留空，收件人在邮箱 App 里自己填
                    body = "",
                )
            }.getOrElse { e ->
                android.util.Log.e("MailHelper", "发送邮件失败", e)
                false
            }
            onDone(ok)
        }
    }

    fun rememberMailPackage(pkg: String) {
        settings.setPreferredMailPackage(pkg)
    }

    fun delete(entity: ReportEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(entity)
            onDone()
        }
    }

    fun fileOf(entity: ReportEntity): File = File(entity.filePath)

    companion object {
        fun factory(): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(
                        repository = ServiceLocator.reportRepository,
                        settings = ServiceLocator.settings,
                    ) as T
                }
            }
    }
}
