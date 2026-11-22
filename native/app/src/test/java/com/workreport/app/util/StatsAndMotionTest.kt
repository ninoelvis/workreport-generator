package com.workreport.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v2.0.4 的四项修复的防回归测试。
 *
 * 1. 统计页底部被悬浮玻璃胶囊挡住
 * 2. 统计图表改成单一指标、清晰标注
 * 3. 开发者页要有最近新增功能的调试入口
 * 4. 历史报告展开要有动效，且全 App 动效时长统一收口
 */
class StatsAndMotionTest {

    private val srcRoot: File = File("src/main/java/com/workreport/app")
    private val resRoot: File = File("src/main/res")

    private fun read(rel: String): String = File(srcRoot, rel).readText()

    // ---------- 1. 底部避让 ----------

    /**
     * 统计页必须给悬浮玻璃胶囊让出底部空间。
     *
     * 坑（v2.0.4 修）：底栏是浮在内容之上的胶囊，**不占布局位置**。
     * 首页 / 我的 / 历史列表都写了 `bottom = ... + CAPSULE_SPACE`，
     * 唯独统计页漏了 → 滚到底时最后那段说明文字被胶囊压在底下。
     */
    @Test
    fun `统计页要给悬浮底栏让出底部空间`() {
        val stats = read("ui/history/StatsContent.kt")
        assertTrue(
            "统计页必须引入 CAPSULE_SPACE",
            stats.contains("import com.workreport.app.ui.CAPSULE_SPACE"),
        )
        assertTrue(
            "统计页的 contentPadding 必须加上 CAPSULE_SPACE",
            Regex("bottom\\s*=\\s*[^,]*CAPSULE_SPACE").containsMatchIn(stats),
        )
    }

    /** 三个一级页面的内容都要让开胶囊（防止以后再漏一个） */
    @Test
    fun `三个一级页面的内容都让开悬浮底栏`() {
        val pages = listOf(
            "ui/home/HomeScreen.kt",
            "ui/profile/ProfileScreen.kt",
            "ui/history/HistoryScreen.kt",
            "ui/history/StatsContent.kt",
        )
        for (p in pages) {
            val src = read(p)
            assertTrue("$p 必须留出 CAPSULE_SPACE", src.contains("CAPSULE_SPACE"))
        }
    }

    // ---------- 2. 统计图表 ----------

    /**
     * 统计图表只能编码**一个**指标。
     *
     * 坑（v2.0.4 修）：之前是「柱高 = 条目数」+「柱底叠一段深色 = 报告份数」的双编码。
     * 深色模式下 `onPrimary` 是**白色**，所以"深色柱"反而变亮，与卡片上的提示正好相反；
     * 报告份数占满整根柱子时整根被盖成同一个颜色，看着像空白。
     */
    @Test
    fun `统计图表不再用深浅双编码`() {
        val stats = read("ui/history/StatsContent.kt")
        assertTrue(
            "不能再用 onPrimary 当「报告份数」那段颜色（深色模式下它是白色）",
            !stats.contains("reportColor"),
        )
        assertTrue(
            "图表应改成单一指标：柱高 = 工作条目数",
            stats.contains("ProductionBarChart"),
        )
        // v2.1.2：用户每周只写一份周报，「N 份」这个计数没有意义，去掉
        assertTrue(
            "图表里不该再出现「N 份」这个计数",
            !stats.contains("reportCount = it.reportCount"),
        )
        assertTrue(
            "ProductionBar 不该再有 reportCount 字段",
            !Regex("data class ProductionBar\\([^)]*reportCount").containsMatchIn(stats),
        )
        // 每月明细表也不该再有「N 份」那一列
        assertTrue(
            "每月明细表不该再显示「N 份」",
            !stats.contains("m.reportCount"),
        )
    }

    /** 卡片标题与提示要跟新图表对得上 */
    @Test
    fun `统计卡片标题与提示改成单一指标的说法`() {
        val stats = read("ui/history/StatsContent.kt")
        assertTrue("标题应为「产出节奏」", stats.contains("\"产出节奏\""))
        assertTrue(
            "不能再出现旧的双编码提示「深色柱 = 报告份数」",
            !stats.contains("深色柱"),
        )
        assertTrue("提示应说明柱高代表什么", stats.contains("柱高 = 写了几条"))
    }

    // ---------- 3. 开发者调试入口 ----------

    /** 最近新增的功能都要有对应的调试入口 */
    @Test
    fun `开发者页面覆盖最近新增功能的调试入口`() {
        val debug = read("ui/debug/DebugPage.kt")
        val tools = read("ui/debug/DebugTools.kt")
        val all = debug + tools

        val required = listOf(
            "统计样本数据工厂" to "createSampleReports",
            "清掉样本报告" to "clearSampleReports",
            "搜索命中自检" to "probeSearch",
            "周范围自检" to "probeWeekRanges",
            "图标资源自检" to "probeLauncherIcons",
        )
        for ((label, fn) in required) {
            assertTrue("开发者页应提供「$label」", all.contains(fn))
        }

        assertTrue(
            "开发者页应提供主题即时切换",
            all.contains("setThemeMode"),
        )
        assertTrue(
            "开发者页应提供动效开关",
            all.contains("Motion.enabled"),
        )
        assertTrue(
            "开发者页应提供动效速度调节",
            all.contains("Motion.speed"),
        )
    }

    /**
     * 样本数据必须落在应用私有目录，不能污染用户的「下载」目录。
     *
     * ⚠️ 关键坑：`ReportExporter.export` 的最后一步 `saveOutside(...)`，
     *    在 `storage == null` 时**恰恰是写进系统「下载」目录**（不是"不外发"！），
     *    每点一次「造样本」就会往用户下载目录里丢一批文件。
     *
     *    所以样本工厂必须**绕开 export**，
     *    自己调用同一批红线组件写文件（产物等价性由 SampleDataParityTest 守着）。
     */
    @Test
    fun `样本报告不写进用户的下载目录`() {
        val tools = read("ui/debug/DebugTools.kt")
        assertTrue(
            "样本不能走 ReportExporter.export（它最后一步会写进用户的下载目录）",
            !tools.contains("exporter.export("),
        )
        assertTrue(
            "样本不能靠 storage = null 来表达'不外发'（null 恰恰是写下载目录）",
            !tools.contains("storage = null"),
        )
        assertTrue(
            "样本要落在应用私有目录",
            tools.contains("ReportExporter.reportsDir"),
        )
        assertTrue(
            "样本要用同一批红线组件自己写，保证与正式导出等价",
            tools.contains("ReportNaming.plan(") &&
                tools.contains("XlsxWriter.toBytes(") &&
                tools.contains("EncryptedZipWriter.write("),
        )
    }

    // ---------- 4. 统一动效 ----------

    /** 历史报告展开必须有动效（原来是瞬间的 if） */
    @Test
    fun `历史报告展开用的是带动画的组件而不是瞬间的if`() {
        val history = read("ui/history/HistoryScreen.kt")
        val previewIdx = history.indexOf("visible = previewExpanded")
        assertTrue("预览展开应该有 AnimatedVisibility", previewIdx > 0)
        val segment = history.substring(previewIdx, previewIdx + 600)
        assertTrue(
            "展开要有淡入 + 竖向展开的组合动画",
            segment.contains("fadeIn") && segment.contains("expandVertically"),
        )
        assertTrue(
            "收起要有淡出 + 竖向收起的组合动画",
            segment.contains("fadeOut") && segment.contains("shrinkVertically"),
        )
        assertTrue(
            "展开动画时长要走统一规格 Motion",
            segment.contains("Motion.duration"),
        )
    }

    /**
     * 列表 / 统计 视图切换要**交叉淡入**。
     *
     * v2.1.1 修：以前是「整体透明度淡入」—— 切换瞬间旧视图直接消失、
     * 新视图从全透明开始，看着像"闪一下"；而且列表离开组合后，
     * 切回来时滚动位置回到顶部，用户被甩上去。
     *
     * 现在用 Crossfade：新旧视图同时在场交叉淡入，
     * 并且列表的滚动位置由 listState 保持。
     */
    @Test
    fun `历史报告视图切换用交叉淡入并保留滚动位置`() {
        val history = read("ui/history/HistoryScreen.kt")
        assertTrue("视图切换应用 Crossfade 交叉淡入", history.contains("Crossfade("))
        assertTrue("列表要用 rememberLazyListState 保住滚动位置", history.contains("rememberLazyListState()"))
        assertTrue("LazyColumn 要绑定 listState", history.contains("state = listState"))
        assertTrue(
            "不能再用整体透明度淡入（会有全透明空帧）",
            !history.contains("viewAlpha"),
        )
    }

    /** 搜索框出现/消失也要有动效 */
    @Test
    fun `搜索框出现消失有动效`() {
        val history = read("ui/history/HistoryScreen.kt")
        assertTrue(
            "搜索框应该用 AnimatedVisibility（带展开动画）",
            history.contains("visible = tab == 0"),
        )
    }

    /** 全 App 的动效时长都走 Motion，不许再散落硬编码数字 */
    @Test
    fun `动效时长统一走Motion规格`() {
        val offenders = mutableListOf<String>()
        val files = File(srcRoot, "ui").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val pattern = Regex("tween\\((?:durationMillis = )?(\\d+)")
        for (f in files) {
            for (m in pattern.findAll(f.readText())) {
                offenders += "${f.name}: ${m.value}"
            }
        }
        assertTrue(
            "这些地方还在写死动画时长，应改用 Motion.duration(...): $offenders",
            offenders.isEmpty(),
        )
    }

    /** Motion 本身的行为 */
    @Test
    fun `Motion规格的时长缩放正确`() {
        val motion = read("ui/common/Motion.kt")
        assertTrue("Motion 必须提供统一时长缩放", motion.contains("fun duration("))
        assertTrue("Motion 必须有全局开关", motion.contains("var enabled"))
        assertTrue("Motion 必须有速度系数", motion.contains("var speed"))
        // 四档时长必须齐全且递增
        val values = Regex("const val (FAST|NORMAL|MEDIUM|SLOW) = (\\d+)").findAll(motion)
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
        assertEquals(4, values.size)
        assertTrue(values["FAST"]!! < values["NORMAL"]!!)
        assertTrue(values["NORMAL"]!! < values["MEDIUM"]!!)
        assertTrue(values["MEDIUM"]!! < values["SLOW"]!!)
    }
}
