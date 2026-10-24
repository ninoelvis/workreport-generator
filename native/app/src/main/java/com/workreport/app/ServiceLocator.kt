package com.workreport.app

import android.content.Context
import androidx.room.Room
import com.workreport.app.data.local.ReportDatabase
import com.workreport.app.data.local.SettingsStore
import com.workreport.app.data.repository.ReportRepository
import com.workreport.app.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 轻量依赖容器。
 *
 * 刻意不引入 Hilt：整个应用只有「数据库 / 设置 / 网络客户端 / 仓库」几个单例，
 * 手写容器更直观、启动开销更低，也方便后续替换实现。
 *
 * 全部使用 Application 上下文，不持有 Activity，避免内存泄漏。
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var dbInstance: ReportDatabase? = null

    @Volatile
    private var apiClientInstance: ApiClient? = null

    @Volatile
    private var settingsStoreInstance: SettingsStore? = null

    @Volatile
    private var reportRepositoryInstance: ReportRepository? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 「首页需要重新读取草稿」的信号。
     *
     * 历史报告的「编辑并回填」会把内容写回草稿，此时首页可能还活着（切 Tab 不一定
     * 重建 ViewModel），所以要通知它重新读一次。replay = 1 保证信号不丢。
     */
    private val _draftReload = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    val draftReload: SharedFlow<Unit> = _draftReload.asSharedFlow()

    /** 通知首页：草稿变了，重新读一次 */
    fun notifyDraftReloaded() {
        _draftReload.tryEmit(Unit)
    }

    /**
     * 「待处理的下周计划」暂存处。
     *
     * 历史页点「取回下周计划」时，把计划文本**交给首页**，
     * 由首页自己合并进「本周已完成工作梳理」——
     * 基准是首页当前显示的内容，而不是草稿。
     *
     * ⚠️ 踩过的坑：之前是历史页把计划合并进**草稿**，再通知首页重读草稿。
     * 结果合并基准（草稿）和首页显示的内容可能对不上，用户点了「首页毫无变化」。
     * 合并这件事只能有一个归属方 —— 就是首页自己。
     */
    private val pendingPlan = PendingPlanHolder()

    /** 历史页：把这份报告的「下周计划」交给首页 */
    fun deliverPlanToHome(plan: String) {
        pendingPlan.deliver(plan)
    }

    /** 首页：取走待处理的计划（取走即清空，保证只并入一次） */
    fun takePlanForHome(): String? = pendingPlan.take()

    /**
     * 待处理计划的一次性暂存。
     *
     * 抽成独立的小类（不依赖 Context），方便直接跑单元测试。
     */
    class PendingPlanHolder {
        private var text: String? = null

        /** 送来一段计划；多次送来就依次追加，不会互相覆盖 */
        fun deliver(plan: String) {
            val incoming = plan.trim()
            if (incoming.isEmpty()) return
            val current = text
            text = if (current.isNullOrEmpty()) incoming else current + "\n" + incoming
        }

        /** 取走并清空 */
        fun take(): String? {
            val value = text
            text = null
            return value
        }

        /** 只看不取（测试用） */
        fun peek(): String? = text
    }

    /** 应用上下文（只读，供需要 Context 的工具类使用） */
    val context: Context get() = requireContext()

    private fun requireContext(): Context =
        appContext ?: error("ServiceLocator 未初始化")

    val database: ReportDatabase
        get() = dbInstance ?: synchronized(this) {
            dbInstance ?: Room.databaseBuilder(
                requireContext(),
                ReportDatabase::class.java,
                ReportDatabase.NAME,
            )
                .addMigrations(ReportDatabase.MIGRATION_1_2)
                .build().also { dbInstance = it }
        }

    val settings: SettingsStore
        get() = settingsStoreInstance ?: synchronized(this) {
            settingsStoreInstance ?: SettingsStore(requireContext()).also { settingsStoreInstance = it }
        }

    val reportRepository: ReportRepository
        get() = reportRepositoryInstance ?: synchronized(this) {
            reportRepositoryInstance ?: ReportRepository(
                dao = database.reportDao(),
                context = requireContext(),
            ).also { reportRepositoryInstance = it }
        }

    /**
     * 网络客户端。
     *
     * 当前版本**不会发起任何网络请求**，这里只是为后续「在线模板 / 云端同步」等
     * 功能预留，接口层全部走 ApiClient，便于统一加超时、重试、鉴权。
     */
    val apiClient: ApiClient
        get() = apiClientInstance ?: synchronized(this) {
            apiClientInstance ?: ApiClient().also { apiClientInstance = it }
        }
}
