package com.workreport.app.data.local

/**
 * 首页表单草稿。
 *
 * 用户填到一半可能被打断（接电话、切 App、锁屏），这时内容不能丢。
 * 所以每次输入都会写一份草稿到本机，下次打开自动恢复。
 *
 * **生成压缩包成功后草稿会被清掉** —— 那一周的周报已经产出，
 * 不需要再暂存；下次打开是干净的一份。
 *
 * 姓名不放这里：它已经由 [SettingsStore.setUserName] 单独长期保存。
 */
data class ReportDraft(
    /** 本周工作时间（yyyy.MM.dd-yyyy.MM.dd） */
    val timeRange: String = "",
    /** 报告标题 */
    val title: String = "",
    /** 标题是否被用户手动改过（手动改过就不再被自动标题覆盖） */
    val titleManual: Boolean = false,
    /** 本周已完成工作梳理 */
    val doneWorks: String = "",
    /** 下周工作计划 */
    val planWorks: String = "",
    /** 工作记录列表 */
    val records: List<String> = emptyList(),
    /** 「工作记录」输入框里还没提交的文字 */
    val recordInput: String = "",
)
