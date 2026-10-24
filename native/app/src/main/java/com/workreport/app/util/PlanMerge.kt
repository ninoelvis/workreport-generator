package com.workreport.app.util

/**
 * 「上周的下周计划」接到「本周已完成工作梳理」后面的合并规则。
 *
 * 抽成纯函数是为了能直接跑单元测试 —— 这条规则踩过一次坑：
 * 合并的基准必须是**首页当前正在显示的内容**，
 * 拿草稿（draft）当基准会和首页显示的内容对不上，用户就看不到变化。
 */
object PlanMerge {

    /**
     * 把 [plan] 接到 [currentDoneWorks] 后面。
     *
     * - [currentDoneWorks] 为空 → 直接就是 [plan]
     * - [plan] 为空 → 原样返回 [currentDoneWorks]
     * - 两者都有 → 换行拼接（去掉结尾多余的空行）
     */
    fun merge(currentDoneWorks: String, plan: String): String {
        val planText = plan.trim()
        if (planText.isEmpty()) return currentDoneWorks
        val current = currentDoneWorks.trimEnd()
        if (current.isEmpty()) return planText
        return current + "\n" + planText
    }
}
