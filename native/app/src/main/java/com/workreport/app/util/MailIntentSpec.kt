package com.workreport.app.util

/**
 * 一次「发邮件」意图的**描述**。
 *
 * 抽成纯数据（不依赖 Android 的 Intent），有两个好处：
 *
 *  1. 构造真实 Intent 的地方只有一处，行为不会在两条分支里跑偏；
 *  2. 可以写单元测试，**直接断言我们发出去的到底是什么**——
 *     比如「附件 URI 一定在 clipData 里」「主题一定非空」，
 *     而不用真机点一遍才发现附件没带上。
 *
 * 字段与 Android `Intent` 一一对应：
 *  - [action] → `Intent.action`
 *  - [type] → `Intent.type`
 *  - [subject] → `EXTRA_SUBJECT`
 *  - [title] → `EXTRA_TITLE`
 *  - [body] → `EXTRA_TEXT`
 *  - [attachmentUri] → `EXTRA_STREAM` + `clipData`（两种形式都给）
 */
data class MailIntentSpec(
    val action: String,
    val type: String?,
    val subject: String,
    val title: String,
    val body: String,
    /** 附件的 content:// URI（字符串形式，方便单测断言） */
    val attachmentUri: String,
    /** 定向到哪个邮箱 App（包名），空 = 交给系统选 */
    val packageName: String?,
    /** 是否强制清掉目标 App 的任务栈（见 MailHelper 里的 singleTask 说明） */
    val clearTask: Boolean = false,
) {
    /** 正文是否为空 —— 邮件正文默认留空，主题和附件才是必需的 */
    val hasBody: Boolean get() = body.isNotEmpty()
}
