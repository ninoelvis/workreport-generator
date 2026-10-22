package com.workreport.app

import android.app.Application

/**
 * 应用入口。
 *
 * 这里刻意保持极简：所有单例都放在 ServiceLocator 里按需初始化，
 * 避免 Application 阶段做重活，也避免长期持有 Context 造成泄漏。
 */
class WorkReportApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
