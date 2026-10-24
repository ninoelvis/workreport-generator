package com.workreport.app.util

/**
 * 把「下周工作计划」文本拆成一条一条。
 *
 * 报告里的计划是多行的，取回时必须一条一条拆开放进「工作记录」，
 * 用户才能逐条排序、逐条删。
 */
object PlanItems {

    /**
     * 按行拆分并去掉空白行。
     *
     * - 支持 \n 和 \r\n；
     * - 每行首尾空白去掉；
     * - 空行丢掉。
     */
    fun split(planWorks: String): List<String> =
        planWorks.split('\n')
            .map { it.trim().removePrefix("﻿") }
            .filter { it.isNotEmpty() }
}
