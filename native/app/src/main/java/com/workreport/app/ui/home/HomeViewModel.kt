package com.workreport.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.ReportDraft
import com.workreport.app.data.local.ReportEntity
import com.workreport.app.data.local.SettingsStore
import com.workreport.app.domain.model.ShortcutSlot
import com.workreport.app.domain.model.ShortcutTemplate
import com.workreport.app.data.repository.ReportRepository
import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.util.DateUtils
import com.workreport.app.util.MailApp
import com.workreport.app.util.MailHelper
import com.workreport.app.util.MaskAnchor
import com.workreport.app.util.MaskSection
import com.workreport.app.util.MaskSummary
import com.workreport.app.util.PlanMerge
import com.workreport.app.util.SensitiveWordEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.io.File

/** 首页表单状态 */
data class HomeUiState(
    val userName: String = "",
    val timeRange: String = "",
    val title: String = "",
    val titleManual: Boolean = false,
    val doneWorks: String = "",
    val planWorks: String = "",
    val records: List<String> = emptyList(),
    val recordInput: String = "",
    /** 预览区：已格式化的三列文本 */
    val previewDone: String = "",
    val previewPlan: String = "",
    val previewTitle: String = "",
    val previewTimeRange: String = "",
    val weekLabel: String = "",
    val password: String = "",
    val fileName: String = "",
    val generating: Boolean = false,
    /** 本次脱敏中被放行的**具体出现位置**（这些处保持原文不替换） */
    val maskExcluded: Set<MaskAnchor> = emptySet(),
    /** 本次脱敏命中的词与处数，用于「脱敏预览」面板 */
    val maskSummary: MaskSummary = MaskSummary(),
    /** 上一份报告留下的「下周工作计划」，可一键填入本周已完成工作梳理 */
    val carriedPlan: String = "",
    /** 上一份报告的日期描述，用于提示条文案 */
    val carriedPlanSource: String = "",
    /** 最近一份历史报告（用于判断本周是否已生成、提取上周计划） */
    val latestReport: ReportEntity? = null,
    /** 一次性提示消息 */
    val message: String? = null,
)

class HomeViewModel(
    private val settings: SettingsStore,
    private val repository: ReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _records = MutableStateFlow<List<String>>(emptyList())

    /** 本机可用的邮件应用（首次生成时让用户选，之后记住） */
    /** 「工作记录」区的快捷模板 */
    val workShortcuts: StateFlow<List<ShortcutTemplate>> = settings.shortcuts(ShortcutSlot.WORK)

    /** 「下周工作计划」区的快捷模板 */
    val planShortcuts: StateFlow<List<ShortcutTemplate>> = settings.shortcuts(ShortcutSlot.PLAN)

    private val _mailApps = MutableStateFlow<List<MailApp>>(emptyList())
    val mailApps: StateFlow<List<MailApp>> = _mailApps.asStateFlow()

    /** 标题是否被用户手动改过（手动改过后不再被自动标题覆盖） */
    private var titleManual = false
    private var lastAutoTitle: String = ""

    init {
        // 恢复上次没填完的草稿 —— 用户中途退出 App 不该丢内容
        restoreDraft()
        // 历史页可能刚好送来了一段「下周计划」，在这里就并进去
        applyPendingPlan()
        viewModelScope.launch {
            settings.userName.collect { name ->
                _state.value = _state.value.copy(userName = name)
            }
        }
        viewModelScope.launch {
            combine(settings.sensitiveWords, settings.userName) { words, name -> words to name }
                .collect { (words, name) ->
                    _state.value = _state.value.copy(userName = name)
                    recompute(words)
                }
        }
        recompute(settings.sensitiveWords.value)

        viewModelScope.launch {
            _mailApps.value = withContext(Dispatchers.IO) {
                MailHelper.resolveMailApps(ServiceLocator.context)
            }
        }
        // 历史报告「编辑并回填」后，重新读一次草稿
        viewModelScope.launch {
            ServiceLocator.draftReload.collect {
                // 先把表单同步到草稿，再把可能送来的计划并进去
                // （顺序不能反：合并的基准是首页当前显示的内容）
                restoreDraft()
                applyPendingPlan()
            }
        }
        // 观察历史报告：用来判断「本周是否已生成」，以及提供上一份报告的下周计划
        viewModelScope.launch {
            repository.observeAll().collect { list ->
                _state.value = _state.value.copy(latestReport = list.firstOrNull())
            }
        }
    }

    /**
     * 把上一份报告的「下周工作计划」填入「本周已完成工作梳理」。
     *
     * 只作为**起点**：填进去之后完全可以改、可以删，避免把上周没做完的事
     * 变成这周凭空多出来的一条。
     */
    fun applyCarriedPlan() {
        val plan = _state.value.carriedPlan.trim()
        if (plan.isEmpty()) return
        val merged = PlanMerge.merge(_state.value.doneWorks, plan)
        _state.value = _state.value.copy(doneWorks = merged, carriedPlan = "")
        recompute(settings.sensitiveWords.value)
        persistDraft()
        showMessage("已把上周计划填入「本周已完成工作梳理」，可自行修改")
    }

    /**
     * 并入历史页送来的「下周计划」。
     *
     * 合并基准是**首页当前正在显示的内容**（`state.doneWorks`），
     * 不是草稿 —— 这条是这个功能曾经"点了没反应"的根因。
     *
     * 计划取走即清空，保证同一段计划只并入一次。
     */
    private fun applyPendingPlan() {
        val plan = ServiceLocator.takePlanForHome() ?: return
        if (plan.isBlank()) return
        val merged = PlanMerge.merge(_state.value.doneWorks, plan)
        _state.value = _state.value.copy(doneWorks = merged)
        recompute(settings.sensitiveWords.value)
        persistDraft()
        showMessage("已把这份报告的「下周计划」填入「本周已完成工作梳理」，可自行修改")
    }

    /** 忽略上一份报告留下的计划 */
    fun dismissCarriedPlan() {
        if (_state.value.carriedPlan.isEmpty()) return
        _state.value = _state.value.copy(carriedPlan = "")
    }

    // ---------------- 脱敏预览 ----------------

    /**
     * 切换某一处命中的放行状态。
     *
     * 粒度是**每一次出现**：同一句里两次「查询」是两条独立记录，
     * 可以只放行其中一处，另一处照常脱敏。
     *
     * 放行只影响**本次生成**，不会改动敏感词库 —— 永久删除某个词要去
     * 「我的 → 敏感词」里操作。
     */
    fun toggleMaskExclusion(anchor: MaskAnchor) {
        val current = _state.value.maskExcluded
        val next = if (anchor in current) current - anchor else current + anchor
        _state.value = _state.value.copy(maskExcluded = next)
        recompute(settings.sensitiveWords.value)
    }

    /** 全部还原：本次一处都不脱敏 */
    fun excludeAllMasking() {
        val all = _state.value.maskSummary.hits.map { it.anchor }.toSet()
        if (all.isEmpty()) return
        _state.value = _state.value.copy(maskExcluded = all)
        recompute(settings.sensitiveWords.value)
    }

    /** 恢复脱敏：清掉全部豁免 */
    fun restoreAllMasking() {
        if (_state.value.maskExcluded.isEmpty()) return
        _state.value = _state.value.copy(maskExcluded = emptySet())
        recompute(settings.sensitiveWords.value)
    }

    /**
     * 从草稿恢复首页内容。
     *
     * 首次打开时恢复上次没填完的内容；
     * 历史报告「编辑并回填」后也会再调一次。
     */
    private fun restoreDraft() {
        val draft = settings.draft.value
        val currentRange = DateUtils.currentWeekRangeText()
        _records.value = draft.records
        titleManual = draft.titleManual
        _state.value = _state.value.copy(
            userName = settings.userName.value,
            timeRange = draft.timeRange.ifEmpty { currentRange },
            // 标题没被手动改过的话，用自动标题，不恢复草稿里的旧标题
            title = if (draft.titleManual) draft.title else "",
            titleManual = draft.titleManual,
            doneWorks = draft.doneWorks,
            planWorks = draft.planWorks,
            records = draft.records,
            recordInput = draft.recordInput,
        )
        recompute(settings.sensitiveWords.value)
    }

    // ---------------- 输入 ----------------

    fun onUserNameChange(value: String) {
        _state.value = _state.value.copy(userName = value)
        settings.setUserName(value)
    }

    fun onTimeRangeChange(value: String) {
        _state.value = _state.value.copy(timeRange = value)
        settings.setLastTimeRange(value)
        recompute(settings.sensitiveWords.value)
        persistDraft()
    }

    fun onTitleChange(value: String) {
        titleManual = true
        _state.value = _state.value.copy(title = value, titleManual = true)
        recompute(settings.sensitiveWords.value)
        persistDraft()
    }

    fun onDoneWorksChange(value: String) {
        _state.value = _state.value.copy(doneWorks = value)
        recompute(settings.sensitiveWords.value)
        persistDraft()
    }

    fun onPlanWorksChange(value: String) {
        _state.value = _state.value.copy(planWorks = value)
        recompute(settings.sensitiveWords.value)
        persistDraft()
    }

    /** 清空「本周已完成工作梳理」 */
    fun clearDoneWorks() {
        if (_state.value.doneWorks.isEmpty()) return
        _state.value = _state.value.copy(doneWorks = "")
        recompute(settings.sensitiveWords.value)
        persistDraft()
        showMessage("已清空「本周已完成工作梳理」")
    }

    /** 清空「下周工作计划」 */
    fun clearPlanWorks() {
        if (_state.value.planWorks.isEmpty()) return
        _state.value = _state.value.copy(planWorks = "")
        recompute(settings.sensitiveWords.value)
        persistDraft()
        showMessage("已清空「下周工作计划」")
    }

    fun onRecordInputChange(value: String) {
        _state.value = _state.value.copy(recordInput = value)
        persistDraft()
    }

    // ---------------- 工作记录 ----------------

    fun addRecord() {
        val text = _state.value.recordInput.trim()
        if (text.isEmpty()) return
        _records.value = _records.value + text
        _state.value = _state.value.copy(recordInput = "", records = _records.value)
        persistDraft()
    }

    fun deleteRecord(index: Int) {
        if (index !in _records.value.indices) return
        _records.value = _records.value.toMutableList().also { it.removeAt(index) }
        _state.value = _state.value.copy(records = _records.value)
        persistDraft()
    }

    /**
     * 快捷插入一条「工作记录」：直接进列表，不用再点「添加」。
     */
    fun insertWorkShortcut(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        _records.value = _records.value + body
        _state.value = _state.value.copy(records = _records.value)
        persistDraft()
    }

    /**
     * 快捷插入「下周工作计划」：在已有内容后面追加一行，不覆盖原内容。
     */
    fun appendPlanShortcut(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val current = _state.value.planWorks
        val next = if (current.isBlank()) body else current.trimEnd() + "\n" + body
        _state.value = _state.value.copy(planWorks = next)
        recompute(settings.sensitiveWords.value)
        persistDraft()
    }

    /** 把工作记录汇总进「本周已完成工作梳理」 */
    fun summarizeRecords() {
        val records = _records.value
        if (records.isEmpty()) {
            showMessage("还没有工作记录")
            return
        }
        val merged = (listOf(_state.value.doneWorks.trim()) + records)
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        _state.value = _state.value.copy(doneWorks = merged)
        recompute(settings.sensitiveWords.value)
        persistDraft()
        showMessage("已汇总 ${records.size} 条记录")
    }

    // ---------------- 生成 ----------------

    fun generate(onResult: (GenerateOutcome) -> Unit) {
        val s = _state.value
        if (s.generating) return
        if (DateUtils.parseDateRange(s.timeRange) == null) {
            showMessage("请输入正确格式的工作时间（yyyy.MM.dd-yyyy.MM.dd）")
            return
        }
        _state.value = s.copy(generating = true)
        viewModelScope.launch {
            runCatching {
                repository.generate(
                    userName = s.userName,
                    title = s.title,
                    timeRangeText = s.timeRange,
                    doneWorks = s.doneWorks,
                    planWorks = s.planWorks,
                    sensitiveWords = settings.sensitiveWords.value,
                    excludedAnchors = s.maskExcluded,
                )
            }.onSuccess { result ->
                // 周报已经产出：把「工作记录」和「周报内容」清空，
                // 下次打开就是干净的一份，不用再手动删。
                // 姓名、工作时间、标题属于「设置」不是「内容」，保留着方便连续写。
                _records.value = emptyList()
                _state.value = _state.value.copy(
                    generating = false,
                    doneWorks = "",
                    planWorks = "",
                    records = emptyList(),
                    recordInput = "",
                )
                recompute(settings.sensitiveWords.value)
                settings.clearDraft()
                onResult(
                    GenerateOutcome.Success(
                        fileName = result.fileName,
                        password = result.password,
                        savedToDownloads = result.savedToDownloads,
                        file = result.file,
                        title = s.title,
                    ),
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(generating = false)
                onResult(GenerateOutcome.Failure(e.message ?: "生成失败"))
            }
        }
    }

    fun showMessage(text: String?) {
        _state.value = _state.value.copy(message = text)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    // ---------------- 内部 ----------------

    /**
     * 把当前表单状态写进草稿。
     *
     * 每一次输入都会调用，所以用户中途退出 App、接电话、被系统杀进程都不丢内容。
     */
    private fun persistDraft() {
        val s = _state.value
        settings.saveDraft(
            ReportDraft(
                timeRange = s.timeRange,
                title = s.title,
                titleManual = s.titleManual || titleManual,
                doneWorks = s.doneWorks,
                planWorks = s.planWorks,
                records = _records.value,
                recordInput = s.recordInput,
            ),
        )
    }

    // ---------------- 内部：重算标题/周数/密码/预览 ----------------

    private fun recompute(sensitiveWords: List<SensitiveWord>) {
        val s = _state.value
        val parsed = DateUtils.parseDateRange(s.timeRange)
        if (parsed == null) {
            _state.value = s.copy(
                previewDone = "", previewPlan = "",
                weekLabel = "", password = "", fileName = "",
            )
            return
        }
        val sunday = parsed.first
        val thursday = DateUtils.getThursday(sunday)
        val password = DateUtils.fmtDate8(thursday)
        val weekNum = DateUtils.getWeekOfMonth(sunday)
        val c = Calendar.getInstance().apply { time = sunday }
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH) + 1

        // 自动标题：与旧版一致 —— 用户手动改过就不再覆盖
        val autoTitle = "${year}年${month}月工作报告"
        val title = if (!titleManual || s.title == lastAutoTitle) autoTitle else s.title
        if (title != s.title) {
            _state.value = s.copy(title = title, titleManual = titleManual)
        }
        lastAutoTitle = autoTitle

        val engine = SensitiveWordEngine(sensitiveWords)
        val name = s.userName.trim().ifEmpty { "姓名" }
        val excluded = s.maskExcluded

        // 脱敏命中汇总：与预览、导出走的是同一套引擎与放行集合，
        // 所以面板里显示的「替换了 N 处」和实际导出内容永远一致。
        val doneSummary = engine.analyzeMultiline(s.doneWorks, excluded, MaskSection.DONE)
        val planSummary = engine.analyzeMultiline(s.planWorks, excluded, MaskSection.PLAN)
        val mergedSummary = MaskSummary(doneSummary.hits + planSummary.hits)

        // 上一份报告留下的「下周计划」：只有当本周还没生成过报告时才提示
        val latest = s.latestReport
        val sameWeekAsLatest = latest != null &&
            latest.year == year && latest.month == month && latest.weekNum == weekNum
        val carried = if (sameWeekAsLatest) "" else (latest?.planWorks ?: "").trim()
        val carriedSource = if (carried.isEmpty()) "" else
            "${latest!!.year}年${latest.month}月第${latest.weekNum}周"

        _state.value = _state.value.copy(
            previewTitle = title,
            previewTimeRange = s.timeRange.trim(),
            previewDone = SensitiveWordEngine.formatDoneWorks(s.doneWorks, engine, excluded),
            previewPlan = SensitiveWordEngine.formatPlanWorks(s.planWorks, engine, excluded),
            maskSummary = mergedSummary,
            carriedPlan = carried,
            carriedPlanSource = carriedSource,
            weekLabel = "${year}年${month}月第${weekNum}周",
            password = password,
            fileName = "${name}-${year}年${month}月第${weekNum}周工作报告.zip",
        )
    }

    companion object {
        fun factory(): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(
                        settings = ServiceLocator.settings,
                        repository = ServiceLocator.reportRepository,
                    ) as T
                }
            }
    }
}

/** 生成结果 */
sealed interface GenerateOutcome {
    data class Success(
        val fileName: String,
        val password: String,
        val savedToDownloads: Boolean,
        /** 生成的加密 ZIP 路径（用于拉起邮箱时作为附件） */
        val file: File,
        /** 报告标题（作为邮件主题） */
        val title: String,
    ) : GenerateOutcome

    data class Failure(val message: String) : GenerateOutcome
}
