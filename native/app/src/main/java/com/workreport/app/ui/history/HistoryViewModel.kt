package com.workreport.app.ui.history

import androidx.lifecycle.ViewModel
import com.workreport.app.util.PlanItems
import androidx.lifecycle.viewModelScope
import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.ReportDraft
import com.workreport.app.data.local.ReportEntity
import com.workreport.app.data.local.SettingsStore
import com.workreport.app.data.repository.ReportRepository
import com.workreport.app.export.EncryptedZipReader
import com.workreport.app.export.XlsxContentReader
import com.workreport.app.util.ReportContentFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 某一份历史报告的预览状态。
 *
 * loading 时正在解密压缩包；content 有值表示已拿到完整内容；
 * error 非空表示压缩包读不出来（多半是被用户删掉了）。
 */
data class PreviewState(
    val loading: Boolean = false,
    val content: XlsxContentReader.Content? = null,
    val error: String? = null,
)

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

    /** 每份报告的预览状态，按报告 id 存 */
    private val _previews = MutableStateFlow<Map<Long, PreviewState>>(emptyMap())
    val previews: StateFlow<Map<Long, PreviewState>> = _previews.asStateFlow()

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

    /**
     * 加载某份历史报告的完整内容。
     *
     * 新版生成的报告在数据库里存了完整内容，直接用；
     * 旧版生成的没有存，就去把加密压缩包解开、读里面的 xlsx —— 密码就是本周周四，
     * 数据库里本来就有，所以是「自动带入」的。
     */
    fun loadPreview(entity: ReportEntity) {
        if (_previews.value[entity.id]?.content != null) return

        if (entity.hasFullContent) {
            setPreview(
                entity.id,
                PreviewState(
                    content = XlsxContentReader.Content(
                        title = entity.reportTitle,
                        timeRange = entity.timeRange,
                        doneWorks = entity.doneWorks,
                        planWorks = entity.planWorks,
                    ),
                ),
            )
            return
        }

        setPreview(entity.id, PreviewState(loading = true))
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                EncryptedZipReader.readContent(File(entity.filePath), entity.password)
            }
            setPreview(
                entity.id,
                if (content != null) {
                    PreviewState(content = content)
                } else {
                    PreviewState(error = "读不出压缩包了 —— 可能文件已被删除或移动。")
                },
            )
        }
    }

    private fun setPreview(id: Long, state: PreviewState) {
        _previews.value = _previews.value + (id to state)
    }

    /**
     * 把一份历史报告的内容载回首页，供二次编辑。
     *
     * 新版生成的记录在数据库里存了完整内容，直接用；
     * 旧版生成的没有存，就去把加密压缩包解开读出来（密码本来就有）。
     *
     * 载回后写进草稿并通知首页重新读取。
     */
    fun loadIntoHome(entity: ReportEntity, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val content = if (entity.hasFullContent) {
                // 数据库里存的就是用户当初写的原始内容，直接用
                XlsxContentReader.Content(
                    title = entity.reportTitle,
                    timeRange = entity.timeRange,
                    doneWorks = entity.doneWorks,
                    planWorks = entity.planWorks,
                )
            } else {
                // 老报告没有存内容，只能去解开压缩包读 Excel。
                // ⚠️ Excel 里那两格是**格式化后**的文本，
                // 必须拆回原始条目再填进输入框，否则重新生成会二次格式化变成乱码。
                withContext(Dispatchers.IO) {
                    EncryptedZipReader.readContent(File(entity.filePath), entity.password)
                }?.let {
                    it.copy(
                        doneWorks = ReportContentFormat.unformatDoneWorks(it.doneWorks),
                        planWorks = ReportContentFormat.unformatPlanWorks(it.planWorks),
                    )
                }
            }

            if (content == null) {
                onDone(false)
                return@launch
            }

            settings.saveDraft(
                ReportDraft.fromContent(
                    title = content.title,
                    timeRange = content.timeRange,
                    doneWorks = content.doneWorks,
                    planWorks = content.planWorks,
                ),
            )
            // 首页可能还活着（切 Tab 不一定重建 ViewModel），通知它重新读草稿
            ServiceLocator.notifyDraftReloaded()
            onDone(true)
        }
    }

    fun onKeywordChange(value: String) {
        keyword.value = value
        _uiState.value = _uiState.value.copy(keyword = value)
    }

    /**
     * 把一份历史报告的「下周工作计划」取回首页。
     *
     * 语义（用户定的）：取回之后
     *  - 首页的「工作记录」和「周报内容」**都会被清空**；
     *  - 这份报告的「下周计划」被拆成一条一条，放进首页的「工作记录」；
     *  - 用户可以在「工作记录」里补这周新增的内容、调顺序；
     *  - 点「汇总」才进「本周已完成工作梳理」。
     *
     * ⚠️ 搬运由**首页自己**做（见 [HomeViewModel.applyPendingWorkItems]）：
     * 历史页只负责把条目交过去。
     *
     * 之前的历史页是「把计划合并进草稿 → 通知首页重读草稿」，
     * 搬运基准是草稿而不是首页当前的内容，
     * 两者一旦对不上，用户点了就是「首页毫无变化」。
     */
    fun takePlanIntoHome(entity: ReportEntity, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            // 旧报告（v1.3.9 之前）里存的「下周计划」是**格式化后**的文本
            // （「下周预计共2项工作任务：」+「1、xxx；」），
            // 直接按行拆会把表头也当成一条计划 —— 先拆回原始条目。
            val raw = ReportContentFormat.unformatPlanWorks(entity.planWorks)
            val items = PlanItems.split(raw)
            if (items.isEmpty()) {
                onDone(false)
                return@launch
            }
            // 交给首页：首页会清空工作记录和周报内容，再把这些条目放进工作记录
            ServiceLocator.deliverWorkItems(items)
            // 通知首页刷新一下（首页会先同步草稿，再处理这批条目）
            ServiceLocator.notifyDraftReloaded()
            onDone(true)
        }
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
                com.workreport.app.util.AppLog.error("History", "发送邮件失败", e)
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
