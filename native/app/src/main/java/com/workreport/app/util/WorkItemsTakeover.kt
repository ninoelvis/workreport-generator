package com.workreport.app.util

/**
 * 「取回下周计划」这条动作的纯逻辑。
 *
 * 抽出来是为了能直接跑单元测试 ——
 * 这条动作会**清掉用户已经写好的内容**，语义必须锁死，不能靠"看起来对"。
 */
object WorkItemsTakeover {

    /** 取回之后首页该是什么样子 */
    data class Result(
        /** 新的「工作记录」 */
        val records: List<String>,
        /** 新的「本周已完成工作梳理」 */
        val doneWorks: String,
        /** 新的「下周计划」 */
        val planWorks: String,
    )

    /**
     * 执行取回。
     *
     * 规则（用户定的）：
     *  - 「工作记录」= 取回的那批条目；
     *  - 「周报内容」整体清空（已完成梳理 + 下周计划 + 输入框）。
     */
    fun apply(items: List<String>): Result {
        val cleaned = items.map { it.trim() }.filter { it.isNotEmpty() }
        return Result(
            records = cleaned,
            doneWorks = "",
            planWorks = "",
        )
    }
}
