package com.workreport.app.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v2.0.1 的防回归测试：**卡片内容重叠** + 搜索整合 + 统计维度。
 *
 * 坑（v2.0.1 修，最严重的一个）：
 * 历史报告页的卡片里，**正文摘要压在操作按钮上面**，两坨字叠在一起。
 *
 * 根因不是布局参数，而是 Compose 的一个硬约束：
 * **`AnimatedVisibility` 的 content 只能放一个子节点。**
 * 它把 content 当成一个整体来测量与绘制，放多个子节点时，
 * 它们会被叠放在同一个位置 —— 表现就是正文压住按钮。
 *
 * v2.0.0 的「多选动效」给卡片内容区套了 `AnimatedVisibility`，
 * content 里却直接堆了 Spacer / Row / Row / 预览表格…… 于是全叠在一起了。
 * 这个 bug 在 v2.0.1 首次发布时被用户真机截图发现。
 *
 * 所以这里用**源码扫描**的方式锁死：
 * 每处 `AnimatedVisibility` 的 content 必须有且只有一个子节点
 * （要么直接是一个节点，要么整体包在 Column / Row 里）。
 */
class CardOverlapRegressionTest {

    private val srcRoot: String = File("src/main/java/com/workreport/app").absolutePath

    private fun read(path: String): String = File(srcRoot, path).readText()

    /**
     * 逐处检查每个 `AnimatedVisibility` 的 content 是不是**单个**子节点。
     *
     * 为什么用白名单而不是通用扫描：
     * 通用扫描没法区分「一个多行参数的调用」和「多个并列节点」——
     * `BulkActionBar(\n  a = 1,\n  b = 2,\n)` 的参数行和右括号跟子节点同缩进，
     * 会被数成 3 个节点（假阳性）。
     *
     * 所以这里**逐个点名**检查 —— 每处都明确写出它必须是哪个形状，
     * 新增 AnimatedVisibility 时忘了包容器，也不会漏掉。
     */
    private fun assertAvContent(marker: String, mustContain: String, what: String) {
        val screen = read("ui/history/HistoryScreen.kt")
        val i = screen.indexOf(marker)
        assertTrue("应能找到：$what（标记：$marker）", i > 0)
        val seg = screen.substring(i, minOf(screen.length, i + 600))
        assertTrue(
            "$what 的 content 必须是单个子节点（应包含 $mustContain）",
            seg.contains(mustContain),
        )
    }

    @Test
    fun `AnimatedVisibility的content只能是单个子节点`() {
        // 1) 卡片内容区：多个节点 → 必须包 Column
        assertAvContent(
            "visible = !selectionMode,",
            "Column {",
            "卡片内容区（正文摘要 + 操作按钮 + 预览表格）",
        )
        // 2) 多选勾选框：Spacer + Box 两个节点 → 必须包 Row
        assertAvContent(
            "enter = fadeIn(tween(Motion.duration(Motion.FAST))) + scaleIn(",
            "Row(verticalAlignment = Alignment.CenterVertically)",
            "多选勾选框",
        )
        // 3) 对勾：单个 Icon
        assertAvContent(
            "visible = selected,",
            "Icon(",
            "对勾",
        )
        // 4) 底部批量操作条：单个 BulkActionBar
        assertAvContent(
            "modifier = Modifier.align(Alignment.BottomCenter),",
            "BulkActionBar(",
            "底部批量操作条",
        )
        // 5) 快捷模板展开：单个 Column
        val chips = read("ui/common/ShortcutChips.kt")
        val i = chips.indexOf("AnimatedVisibility(visible = expanded)")
        assertTrue("应能找到快捷模板的 AnimatedVisibility", i > 0)
        assertTrue(
            "快捷模板展开的 content 必须是单个 Column",
            chips.substring(i, minOf(chips.length, i + 200)).contains("Column("),
        )
    }

    @Test
    fun `卡片内容区必须包一层Column`() {
        val screen = read("ui/history/HistoryScreen.kt")
        val i = screen.indexOf("visible = !selectionMode,")
        assertTrue("应有多选时的卡片内容区 AnimatedVisibility", i > 0)
        val seg = screen.substring(i, minOf(screen.length, i + 400))
        assertTrue(
            "卡片内容区的 content 必须包一层 Column（否则正文会压住按钮）",
            seg.contains("Column {"),
        )
    }

    @Test
    fun `勾选框必须包一层Row`() {
        val screen = read("ui/history/HistoryScreen.kt")
        val i = screen.indexOf("enter = fadeIn(tween(Motion.duration(Motion.FAST))) + scaleIn(")
        assertTrue("应有勾选框的 AnimatedVisibility", i > 0)
        val seg = screen.substring(i, minOf(screen.length, i + 300))
        assertTrue(
            "勾选框的 content 必须包一层 Row（Spacer + Box 是两个节点）",
            seg.contains("Row("),
        )
    }

    // ---------------- 搜索整合 ----------------

    @Test
    fun `搜索必须是文件名与内容一次全搜`() {
        val dao = read("data/local/ReportDao.kt")
        assertTrue(
            "DAO 应当只有一个统一的 search 方法",
            dao.contains("fun search(keyword: String)"),
        )
        assertTrue(
            "不应再有按文件名/按内容分开的两个查询",
            !dao.contains("fun searchByName(") && !dao.contains("fun searchAll("),
        )
        // 一次查询里同时覆盖文件名与正文
        val qStart = dao.lastIndexOf("@Query(", dao.indexOf("fun search(keyword: String)"))
        val query = dao.substring(qStart).substringBefore("fun search(keyword: String)")
        assertTrue("统一搜索应当覆盖字段 file_name", query.contains("file_name LIKE"))
        for (field in listOf("display_name", "report_title", "time_range", "done_works", "plan_works")) {
            assertTrue(
                "统一搜索应当覆盖字段 $field",
                query.contains("OR $field LIKE"),
            )
        }
    }

    @Test
    fun `界面上去掉搜索范围切换`() {
        val screen = read("ui/history/HistoryScreen.kt")
        assertTrue(
            "不应再有 SearchScope 切换控件",
            !screen.contains("SearchScope"),
        )
        assertTrue(
            "搜索框提示应当说明可以搜文件名或内容",
            screen.contains("搜索文件名或内容…"),
        )
        val vm = read("ui/history/HistoryViewModel.kt")
        assertTrue(
            "ViewModel 不应再有 searchScope",
            !vm.contains("searchScope"),
        )
    }

    @Test
    fun `搜索要搜原始正文而不是脱敏后的文本`() {
        val dao = read("data/local/ReportDao.kt")
        // 只看 SQL（KDoc 里会提到 done_works_exported 做说明，那不算）
        val qStart = dao.lastIndexOf("@Query(", dao.indexOf("fun search(keyword: String)"))
        val query = dao.substring(qStart).substringBefore("fun search(keyword: String)")
        assertTrue("搜 done_works 原始条目", query.contains("OR done_works LIKE"))
        assertTrue("搜 plan_works 原始条目", query.contains("OR plan_works LIKE"))
        assertTrue(
            "不能搜 done_works_exported（脱敏后的文本，否则搜不到用户自己写的名字）",
            !query.contains("done_works_exported"),
        )
    }

    // ---------------- 统计维度 ----------------

    @Test
    fun `统计要支持按月和按周两个维度`() {
        val stats = read("domain/model/ReportStats.kt")
        assertTrue("要有按周的统计", stats.contains("data class WeekStat"))
        assertTrue("要有单份报告的产出统计", stats.contains("data class ReportItemStat"))
        assertTrue("ReportStats 要带 weeks", stats.contains("val weeks: List<WeekStat>"))
        assertTrue("ReportStats 要带 topReports", stats.contains("val topReports: List<ReportItemStat>"))
        assertTrue("ReportStats 要能算平均每份", stats.contains("avgItemsPerReport"))
    }

    @Test
    fun `统计页要有维度切换和单份产出区块`() {
        val content = read("ui/history/StatsContent.kt")
        assertTrue(
            "统计页要有「按月 / 按周」维度切换",
            content.contains("listOf(\"按月\", \"按周\")"),
        )
        assertTrue(
            "统计页要有「每份报告的产出」区块",
            content.contains("每份报告的产出"),
        )
        assertTrue(
            "要画出按周的柱状图",
            content.contains("ProductionBarChart") && content.contains("stats.weeks"),
        )
    }

    @Test
    fun `周数为零的报告不参与按周统计`() {
        val stats = read("domain/model/ReportStats.kt")
        assertTrue(
            "weekNum 为 0 的老数据不能算出一个「第0周」",
            stats.contains("if (e.weekNum > 0)"),
        )
    }

    @Test
    fun `顶部Tab要用公共下划线组件`() {
        val screen = read("ui/history/HistoryScreen.kt")
        assertTrue(
            "历史页顶部要用 UnderlineTabBar",
            screen.contains("UnderlineTabBar("),
        )
        assertTrue(
            "不该再有页面私有的 HistoryTabBar",
            !screen.contains("private fun HistoryTabBar("),
        )
        val common = read("ui/common/UnderlineTabBar.kt")
        assertTrue(
            "下划线 tab 组件必须存在",
            common.contains("fun UnderlineTabBar("),
        )
        assertTrue(
            "指示条要用弹簧动画（与底栏同一套语言）",
            common.contains("Spring.DampingRatioMediumBouncy"),
        )
    }
}
