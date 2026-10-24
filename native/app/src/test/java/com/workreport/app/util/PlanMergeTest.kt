package com.workreport.app.util

import com.workreport.app.ServiceLocator
import com.workreport.app.data.local.ReportDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---------- 一次只并入一次 ----------

    @Test
    fun `取走计划后就没有了 不会重复并入`() {
        val holder = ServiceLocator.PendingPlanHolder()
        holder.deliver("计划A")
        assertEquals("计划A", holder.take())
        assertNull("计划取走后不该再有", holder.take())
    }

    @Test
    fun `多次送来依次追加 不互相覆盖`() {
        val holder = ServiceLocator.PendingPlanHolder()
        holder.deliver("计划A")
        holder.deliver("计划B")
        assertEquals("计划A\n计划B", holder.take())
    }

    @Test
    fun `空计划不会被受理`() {
        val holder = ServiceLocator.PendingPlanHolder()
        holder.deliver("   ")
        assertNull(holder.take())
    }

    // ---------- 端到端场景：先「载入首页继续编辑」，再「取回下周计划」----------

    @Test
    fun `载入之后再取回 计划会接在已载入内容后面`() {
        // 1. 历史页「载入首页继续编辑」：把报告内容写进草稿
        val draft = ReportDraft.fromContent(
            title = "2026年10月工作报告",
            timeRange = "2026.10.19-2026.10.25",
            doneWorks = "已完成A\n已完成B",
            planWorks = "计划1\n计划2",
        )
        assertEquals("已完成A\n已完成B", draft.doneWorks)

        // 2. 首页恢复草稿 —— 首页当前显示的内容
        val homeContent = draft.doneWorks
        assertEquals("已完成A\n已完成B", homeContent)

        // 3. 历史页「取回下周计划」：把计划交给首页
        val holder = ServiceLocator.PendingPlanHolder()
        holder.deliver(draft.planWorks)
        ServiceLocator.deliverPlanToHome(draft.planWorks)

        // 4. 首页合并：基准是首页当前显示的内容
        val pending = ServiceLocator.takePlanForHome()!!
        val merged = PlanMerge.merge(homeContent, pending)
        assertEquals("已完成A\n已完成B\n计划1\n计划2", merged)

        // 5. 再来一次通知（首页重建 / 重复通知）不会重复并入
        val again = ServiceLocator.takePlanForHome()
        assertNull("同一段计划不该被并入两次", again)
        assertEquals("已完成A\n已完成B\n计划1\n计划2", merged)
    }

    @Test
    fun `取回之后首页内容一定和草稿里不一样（也就是用户能看到变化）`() {
        val draft = ReportDraft.fromContent(
            title = "2026年10月工作报告",
            timeRange = "2026.10.19-2026.10.25",
            doneWorks = "已完成A",
            planWorks = "计划1",
        )
        val homeContent = draft.doneWorks
        ServiceLocator.deliverPlanToHome(draft.planWorks)
        val merged = PlanMerge.merge(homeContent, ServiceLocator.takePlanForHome()!!)
        assertTrue(
            "合并后内容必须和原来不同，否则用户点了看不到变化",
            merged != homeContent,
        )
        assertTrue(merged.contains("已完成A"))
        assertTrue(merged.contains("计划1"))
    }
}
