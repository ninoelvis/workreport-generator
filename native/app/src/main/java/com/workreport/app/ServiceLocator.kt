package com.workreport.app

import android.content.Context
import androidx.room.Room
import com.workreport.app.data.local.ReportDatabase
import com.workreport.app.data.local.SettingsStore
import com.workreport.app.data.repository.ReportRepository
import com.workreport.app.data.remote.ApiClient

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
