package com.workreport.app.domain.model

/**
 * 快捷记录模板。
 *
 * 用户把每周都要重复写的工作内容存成模板，之后在首页一键插入，
 * 不用每次都手动敲一遍。
 *
 * 两套模板互相独立：
 * - [Slot.WORK] 用在首页「工作记录」区；
 * - [Slot.PLAN]  用在首页「下周工作计划」区。
 */
data class ShortcutTemplate(
    /** 稳定 id，删除/排序时用来定位，不依赖下标 */
    val id: String,
    /** 模板正文，点击后插入到输入区 */
    val text: String,
) {
    /** 展示用：太长的正文截断，避免列表行被撑爆 */
    fun display(maxChars: Int = 40): String =
        if (text.length <= maxChars) text else text.take(maxChars) + "…"
}

/** 快捷模板的用途分区 */
enum class ShortcutSlot(val storageKey: String, val title: String, val hint: String) {
    /** 本周工作记录 */
    WORK("shortcut_work", "本周工作记录", "插入到首页「工作记录」区，每点一次新增一条记录"),

    /** 下周工作计划 */
    PLAN("shortcut_plan", "下周工作计划", "插入到首页「下周工作计划」框，每点一次追加一行"),
}
