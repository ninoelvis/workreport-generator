package com.workreport.app.util

import com.workreport.app.ServiceLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「取回下周计划」相关的纯逻辑测试。
 *
 * 覆盖三件事：
 *  1. 取回之后工作记录 / 周报内容是不是按约定那样变了；
 *  2. 一次性暂存是不是只会被并入一次；
 *  3. 工作记录排序是不是对。
 */
class WorkItemsTakeoverTest {

    // ---------------- 取回的语义 ----------------

    @Test
    fun `取回后工作记录变成取回的条目`() {
        val result = WorkItemsTakeover.apply(listOf("完成查询工作", "落实措施"))
        assertEquals(listOf("完成查询工作", "落实措施"), result.records)
    }

    @Test
    fun `取回后周报内容被清空`() {
        val result = WorkItemsTakeover.apply(listOf("A", "B"))
        assertEquals("", result.doneWorks)
        assertEquals("", result.planWorks)
    }

    @Test
    fun `取回时空白条目被丢掉`() {
        val result = WorkItemsTakeover.apply(listOf("  A  ", "", "   ", "B"))
        assertEquals(listOf("A", "B"), result.records)
    }

    @Test
    fun `取回空列表等于什么都不做`() {
        val result = WorkItemsTakeover.apply(emptyList())
        assertEquals(emptyList<String>(), result.records)
        assertEquals("", result.doneWorks)
        assertEquals("", result.planWorks)
    }

    // ---------------- 一次性暂存 ----------------

    @Test
    fun `暂存取出后就被清空 不会重复并入`() {
        val holder = ServiceLocator.PendingWorkItemsHolder()
        holder.deliver(listOf("A", "B"))
        assertEquals(listOf("A", "B"), holder.take())
        assertNull(holder.take())
    }

    @Test
    fun `多次送来依次追加 不互相覆盖`() {
        val holder = ServiceLocator.PendingWorkItemsHolder()
        holder.deliver(listOf("A"))
        holder.deliver(listOf("B", "C"))
        assertEquals(listOf("A", "B", "C"), holder.take())
    }

    @Test
    fun `送来空内容等于没送`() {
        val holder = ServiceLocator.PendingWorkItemsHolder()
        holder.deliver(listOf("", "  "))
        assertNull(holder.take())
    }

    // ---------------- 拆条 ----------------

    @Test
    fun `下周计划按行拆成一条条`() {
        assertEquals(
            listOf("完成查询工作", "落实措施"),
            PlanItems.split("完成查询工作\n落实措施"),
        )
    }

    @Test
    fun `拆条时丢掉空行和首尾空白`() {
        assertEquals(
            listOf("A", "B"),
            PlanItems.split("\n  A  \n\n\nB\n"),
        )
    }

    @Test
    fun `拆条时兼容 CRLF`() {
        assertEquals(listOf("A", "B"), PlanItems.split("A\r\nB"))
    }

    @Test
    fun `空计划拆出空列表`() {
        assertEquals(emptyList<String>(), PlanItems.split(""))
    }

    @Test
    fun `旧报告的格式化计划先拆格式再拆条`() {
        // 旧报告（v1.3.9 之前）存的是格式化后的文本
        val formatted = "下周预计共2项工作任务：\n1、配合纪委完成案件审理；\n2、组织案管系统培训。"
        val raw = ReportContentFormat.unformatPlanWorks(formatted)
        assertEquals(listOf("配合纪委完成案件审理", "组织案管系统培训"), PlanItems.split(raw))
    }

    @Test
    fun `新报告的原始计划不受拆格式影响`() {
        val raw = "配合纪委完成案件审理\n组织案管系统培训"
        assertEquals(raw, ReportContentFormat.unformatPlanWorks(raw))
        assertEquals(listOf("配合纪委完成案件审理", "组织案管系统培训"), PlanItems.split(raw))
    }

    // ---------------- 排序 ----------------

    @Test
    fun `上移一条`() {
        val list = listOf("A", "B", "C")
        assertEquals(listOf("B", "A", "C"), RecordOrder.move(list, 1, 0))
        assertEquals(listOf("A", "C", "B"), RecordOrder.move(list, 2, 1))
    }

    @Test
    fun `下移一条`() {
        val list = listOf("A", "B", "C")
        // 移完之后，这一项就落在 to 这个下标上
        assertEquals(listOf("B", "A", "C"), RecordOrder.move(list, 0, 1))
        assertEquals(listOf("A", "C", "B"), RecordOrder.move(list, 1, 2))
    }

    @Test
    fun `上下相邻移动就是互换位置`() {
        val list = listOf("A", "B", "C")
        assertEquals(listOf("B", "A", "C"), RecordOrder.move(list, 0, 1))
        assertEquals(listOf("A", "C", "B"), RecordOrder.move(list, 2, 1))
    }

    @Test
    fun `越界不动`() {
        val list = listOf("A", "B")
        assertEquals(listOf("A", "B"), RecordOrder.move(list, 0, 5))
        assertEquals(listOf("A", "B"), RecordOrder.move(list, -1, 0))
        assertEquals(listOf("A", "B"), RecordOrder.move(list, 0, 0))
    }
}
