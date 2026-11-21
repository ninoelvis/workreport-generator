package com.workreport.app.ui.history

import androidx.lifecycle.ViewModel
import com.workreport.app.util.PlanItems
import androidx.lifecycle.viewModelScope
import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.ReportDraft
import com.workreport.app.data.local.ReportEntity
import com.workreport.app.data.local.SettingsStore
import com.workreport.app.data.repository.ReportRepository
import com.workreport.app.domain.model.ReportStats
import com.workreport.app.domain.model.ReportStatsCalculator
import com.workreport.app.domain.model.SearchScope
import com.workreport.app.export.EncryptedZipReader
import com.workreport.app.export.XlsxContentReader
import com.workreport.app.util.ReportContentFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    /** 搜索范围：仅文件名 / 连正文一起搜（v2.0.0 起） */
    val searchScope: SearchScope = SearchScope.FILE_NAME,
    /** 已选中的报告 id */
    val selectedIds: Set<Long> = emptySet(),
    val reports: List<ReportEntity> = emptyList(),
    val totalCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * 用户存放目录里的一个**外部压缩包**（不是 App 自己生成的）。
 *
 * 用户把压缩包手动拷进存放目录后，App 会在这里认出来并列出来。
 */
data class ExternalReport(
    /** 完整文件名，例如「张三-2026年10月第3周工作报告.zip」 */
    val fileName: String,
    /** 不含扩展名的展示名 */
    val displayName: String,
    /** 在 SAF 里的文档 URI */
    val uri: android.net.Uri,
    val sizeBytes: Long,
    val lastModified: Long,
    /**
     * 从文件名反推出来的**候选**解压密码。
     *
     * ⚠️ 周数对应的是一个 7 天的区间，起始日可能是其中任意一天，
     * 所以密码有 7 个候选，解压时依次尝试。
     */
    val passwordCandidates: List<String>,
) {
    /** 候选密码（按可能性排序，第一个最可能） */
    fun candidates(): List<String> = passwordCandidates
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: ReportRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val keyword = MutableStateFlow("")

    /** 搜索范围（v2.0.0 起）：用户可以切成「连正文一起搜」 */
    private val searchScope = MutableStateFlow(SearchScope.FILE_NAME)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val reports: StateFlow<List<ReportEntity>> = combine(keyword, searchScope) { kw, scope -> kw to scope }
        .flatMapLatest { (kw, scope) -> repository.search(kw, scope) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 是否处于多选模式（v2.0.0 起）。
     *
     * ⚠️ 真相源**只有这一个**（[ServiceLocator.selectionMode]），不放在 `_uiState` 里。
     *
     * 坑（v2.0.1 修）：之前 `_uiState.selectionMode` 和 `ServiceLocator.selectionMode`
     * 是**两份**，清了一处另一处还留着 ——
     * 系统返回手势没走 `exitSelection()`，全局标志卡在 true，
     * 底栏就永久消失了（切 Tab 离开历史页也一样）。
     *
     * 现在只有一份：底栏（AppRoot 读它）和卡片（这里读它）永远一致。
     */
    val selectionMode: StateFlow<Boolean> = ServiceLocator.selectionMode

    /**
     * 全部历史报告（**不受搜索关键词影响**）。
     *
     * 统计页要看的是「一共有多少」，不是「当前筛出来的多少」——
     * 搜索框里打了字也不该让统计数字跟着变。
     */
    val allReports: StateFlow<List<ReportEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 周报统计（v2.0.0 起），由全部历史报告算出 */
    val stats: StateFlow<ReportStats> = allReports
        .map { list ->
            ReportStatsCalculator.calculate(
                list.map {
                    ReportStatsCalculator.StatsEntry(
                        year = it.year,
                        month = it.month,
                        doneWorks = it.doneWorks,
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportStats.EMPTY)

    val preferredMailPackage: StateFlow<String> = settings.preferredMailPackage

    /** 每份报告的预览状态，按报告 id 存 */
    private val _previews = MutableStateFlow<Map<Long, PreviewState>>(emptyMap())
    val previews: StateFlow<Map<Long, PreviewState>> = _previews.asStateFlow()

    /** 用户存放目录里的外部压缩包 */
    private val _externalReports = MutableStateFlow<List<ExternalReport>>(emptyList())
    val externalReports: StateFlow<List<ExternalReport>> = _externalReports.asStateFlow()

    /** 外部压缩包的预览状态，key 用 uri 字符串 */
    private val _externalPreviews = MutableStateFlow<Map<String, PreviewState>>(emptyMap())
    val externalPreviews: StateFlow<Map<String, PreviewState>> = _externalPreviews.asStateFlow()

    /**
     * 扫描用户存放目录，把里面的压缩包找出来。
     *
     * 只列出**不在数据库里的**那些 —— App 自己生成的已经有一份记录了，
     * 不重复显示。
     */
    fun refreshExternal() {
        viewModelScope.launch {
            val storage = ServiceLocator.storageLocation
            val known = repository.allFileNames()
            val list = withContext(Dispatchers.IO) {
                storage.listZips().mapNotNull { f ->
                    if (f.displayName in known) return@mapNotNull null
                    ExternalReport(
                        fileName = f.displayName,
                        displayName = f.displayName.removeSuffix(".zip").removeSuffix(".ZIP"),
                        uri = f.uri,
                        sizeBytes = f.sizeBytes,
                        lastModified = f.lastModified,
                        passwordCandidates = com.workreport.app.util.ExternalZipPassword.candidates(f.displayName),
                    )
                }
            }
            _externalReports.value = list
        }
    }

    /**
     * 读一个外部压缩包的内容。
     *
     * SAF 里的文件不能直接交给 zip4j，所以先复制一份到缓存目录再解密。
     *
     * @param password 密码；传 null 就用从文件名推断出来的
     */
    fun loadExternalPreview(item: ExternalReport, password: String? = null) {
        val key = item.uri.toString()
        if (_externalPreviews.value[key]?.content != null) return
        setExternalPreview(key, PreviewState(loading = true))
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                if (password != null) {
                    // 用户手输的密码
                    readExternalContent(item, password)
                } else {
                    // 文件名里的周数能推出 7 个候选密码，依次尝试
                    var found: XlsxContentReader.Content? = null
                    for (candidate in item.candidates()) {
                        found = readExternalContent(item, candidate)
                        if (found != null) break
                    }
                    found
                }
            }
            setExternalPreview(
                key,
                if (content != null) {
                    PreviewState(content = content)
                } else if (password == null) {
                    // 候选密码都不对 —— 让用户手输一次
                    PreviewState(error = "NEED_PASSWORD")
                } else {
                    PreviewState(error = "密码不对，解不开这个压缩包。")
                },
            )
        }
    }

    /** 把外部压缩包复制到缓存目录并解密读出内容 */
    private fun readExternalContent(item: ExternalReport, password: String): XlsxContentReader.Content? {
        val storage = ServiceLocator.storageLocation
        val bytes = storage.readBytes(item.uri) ?: return null
        val cacheDir = File(ServiceLocator.context.cacheDir, "external").apply { mkdirs() }
        val tmp = File(cacheDir, item.fileName)
        return try {
            tmp.writeBytes(bytes)
            EncryptedZipReader.readContent(tmp, password)
        } catch (e: Exception) {
            null
        } finally {
            try { tmp.delete() } catch (e: Exception) { /* 忽略 */ }
        }
    }

    /** 外部压缩包复制到缓存后的临时文件（用于分享） */
    fun externalCacheFile(item: ExternalReport): File? {
        val storage = ServiceLocator.storageLocation
        val bytes = storage.readBytes(item.uri) ?: return null
        val cacheDir = File(ServiceLocator.context.cacheDir, "external").apply { mkdirs() }
        return try {
            File(cacheDir, item.fileName).also { it.writeBytes(bytes) }
        } catch (e: Exception) {
            null
        }
    }

    private fun setExternalPreview(key: String, state: PreviewState) {
        _externalPreviews.value = _externalPreviews.value + (key to state)
    }

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
     * ⚠️ 预览区显示的必须是**实际导出的 Excel 里的原文**，不能有差别 ——
     *    导出格式是红线，预览就得以它为准。
     *
     * 两条路径都指向同一个真相：
     *  - 新版生成的报告：数据库里存了「导出时写进 Excel 的那两格」，直接用，秒开；
     *  - 旧版生成的报告：没存，就去把加密压缩包解开、读里面的 xlsx —— 那本来就是 Excel 原文。
     */
    fun loadPreview(entity: ReportEntity) {
        if (_previews.value[entity.id]?.content != null) return

        if (entity.hasExportedContent) {
            // 数据库里存的就是 Excel 里的原文（已格式化 + 已脱敏），直接用
            setPreview(
                entity.id,
                PreviewState(
                    content = XlsxContentReader.Content(
                        title = entity.reportTitle,
                        timeRange = entity.timeRange,
                        doneWorks = entity.doneWorksExported,
                        planWorks = entity.planWorksExported,
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

    // ---------------- 多选（v2.0.0 起）----------------

    /**
     * 进入多选模式（长按卡片触发）。
     *
     * 进入时把该报告直接选中 —— 用户长按的那一张就是「想操作的那一张」，
     * 不用再点一次。
     */
    fun enterSelection(id: Long) {
        if (selectionMode.value) return
        _uiState.value = _uiState.value.copy(selectedIds = setOf(id))
        ServiceLocator.setSelectionMode(true)
    }

    /** 切换某一份报告的选中状态 */
    fun toggleSelection(id: Long) {
        val cur = _uiState.value
        if (!selectionMode.value) return
        val next = if (cur.selectedIds.contains(id)) {
            cur.selectedIds - id
        } else {
            cur.selectedIds + id
        }
        _uiState.value = cur.copy(selectedIds = next)
    }

    /** 全选 / 取消全选（当前列表里的全部） */
    fun toggleSelectAll(allIds: List<Long>) {
        val cur = _uiState.value
        if (!selectionMode.value) return
        val next = if (cur.selectedIds.size == allIds.size && allIds.isNotEmpty()) {
            emptySet()
        } else {
            allIds.toSet()
        }
        _uiState.value = cur.copy(selectedIds = next)
    }

    /** 退出多选模式 */
    fun exitSelection() {
        _uiState.value = _uiState.value.copy(selectedIds = emptySet())
        ServiceLocator.setSelectionMode(false)
    }

    /**
     * 批量删除：删数据库记录 + 删本机的压缩包文件。
     *
     * 只删数据库记录是不够的 —— 压缩包还在硬盘上，
     * 用户在存放目录里还能看到「删了但文件还在」，会以为没删成功。
     */
    fun deleteSelected(
        entities: List<ReportEntity>,
        onDone: (Int) -> Unit,
    ) {
        viewModelScope.launch {
            var deleted = 0
            for (e in entities) {
                runCatching { repository.delete(e) }
                    .onSuccess {
                        deleted++
                        // 文件删不掉不算失败（可能已经被用户删了）
                        runCatching { File(e.filePath).delete() }
                    }
            }
            exitSelection()
            onDone(deleted)
        }
    }

    /**
     * 批量再下载：把选中的报告重新存一份到存放目录。
     *
     * 只做「再下载」而不做「批量发邮件」——
     * 多附件的 ACTION_SEND Intent 可能超出大小限制，也可能被某些邮箱 App 拒收，
     * 而批量下载只是复制文件，稳。
     */
    fun downloadSelected(
        entities: List<ReportEntity>,
        onDone: (Int) -> Unit,
    ) {
        viewModelScope.launch {
            var ok = 0
            for (e in entities) {
                if (repository.copyToDownloads(e)) ok++
            }
            exitSelection()
            onDone(ok)
        }
    }

    /**
     * 切换搜索范围（v2.0.0 起）。
     *
     * 「仅文件名」是原有行为；「全部内容」会把本周完成、下周计划也纳入匹配。
     */
    fun onSearchScopeChange(scope: SearchScope) {
        if (searchScope.value == scope) return
        searchScope.value = scope
        _uiState.value = _uiState.value.copy(searchScope = scope)
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
