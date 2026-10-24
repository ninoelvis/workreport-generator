package com.workreport.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「取回下周计划」的合并逻辑。
 *
 * ⚠️ 这条功能出过一次 bug：历史页点「取回下周计划」后，
 * **首页毫无变化**。根因是合并的基准取错了 ——
 * 之前历史页把计划合并进**草稿**，再让首页重读草稿；
 * 而首页当前显示的内容和草稿可能对不上（先「载入首页继续编辑」再「取回」就会碰上），
 * 于是合并结果落在了看不见的地方。
 *
 * 现在的规则：**合并只由首页做，基准是首页当前显示的内容**。
 * 这个测试把规则钉死。
 */
class PlanMergeTest {

    // ---------- 合并规则 ----------

    @Test
    fun `内容为空时直接用计划`() {
        assertEquals("计划A", PlanMerge.merge("", "计划A"))
        assertEquals("计划A", PlanMerge.merge("   \n ", "计划A"))
    }

    @Test
    fun `计划为空时原样返回`() {
        assertEquals("已完成A", PlanMerge.merge("已完成A", ""))
        assertEquals("已完成A", PlanMerge.merge("已完成A", "  \n "))
    }

    @Test
    fun `两者都有时换行拼接`() {
        assertEquals("已完成A\n计划B", PlanMerge.merge("已完成A", "计划B"))
    }

    @Test
    fun `拼接前去掉原内容结尾的空行`() {
        assertEquals("已完成A\n计划B", PlanMerge.merge("已完成A\n\n  \n", "计划B"))
    }

    @Test
    fun `计划里的首尾空白会被去掉`() {
        assertEquals("已完成A\n计划B", PlanMerge.merge("已完成A", "  计划B  \n"))
    }

    // ---------- 说明 ----------
    //
    // 「取回下周计划」的一次性暂存（PendingWorkItemsHolder）
    // 和「取回会清空工作记录与周报内容」的语义，
    // 已经在 WorkItemsTakeoverTest 里覆盖，这里不再重复。
    //
    // PlanMerge 只剩下首页顶部横幅那条老路径在用（把上周计划并进本周已完成）。
}
