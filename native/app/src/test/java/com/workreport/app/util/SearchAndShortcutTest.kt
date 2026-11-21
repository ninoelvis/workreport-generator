package com.workreport.app.util

import com.workreport.app.domain.model.SearchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v2.0.0「搜索覆盖正文 + 时间范围快捷 + 主题护栏」的防回归测试。
 *
 * 三个要守住的东西：
 *
 * 1. **搜索要能搜到正文**，且搜的是**原始条目**（用户自己写的话），
 *    不是脱敏格式化后的文本 —— 否则用户写「张三」却搜不到。
 *    同时 `LIKE` 的通配符必须转义，用户搜 `50%` 不能变成匹配所有含 `50` 的行。
 * 2. **时间范围的快捷项必须复用 DateUtils**，格式与手填时逐字一致 ——
 *    这个文本会写进 Excel 的 B2 格（红线）。
 * 3. **不许出现 `values-night` 资源限定符** ——
 *    App 的深色模式是手动切换的，跟系统深浅色不是一回事。
 */
class SearchAndShortcutTest {

    private val srcRoot: String =
        File("src/main/java/com/workreport/app").absolutePath

    private fun read(path: String): String = File(srcRoot, path).readText()

    // ---------------- 1. 搜索覆盖正文 ----------------

    @Test
    fun `搜索范围必须同时提供文件名与全部内容两种`() {
        val dao = read("data/local/ReportDao.kt")
        assertTrue("DAO 应提供仅按文件名搜索的查询", dao.contains("fun searchByName"))
        assertTrue("DAO 应提供连正文一起搜的查询", dao.contains("fun searchAll"))
        assertTrue(
            "全部内容搜索应覆盖本周完成（原始条目）",
            dao.contains("done_works LIKE"),
        )
        assertTrue(
            "全部内容搜索应覆盖下周计划",
            dao.contains("plan_works LIKE"),
        )
    }

    @Test
    fun `全部内容搜索不能搜脱敏后的文本`() {
        val dao = read("data/local/ReportDao.kt")
        assertTrue(
            "不应拿脱敏后的导出文本做搜索 —— 用户写的是原文，搜脱敏文本会搜不到",
            !dao.contains("done_works_exported LIKE"),
        )
    }

    @Test
    fun `LIKE 查询必须声明 ESCAPE`() {
        val dao = read("data/local/ReportDao.kt")
        // 两个查询里每一个 LIKE 都要带 ESCAPE '\'
        val likes = dao.lines().filter { it.contains("LIKE '%'") }
        assertTrue("应至少有两处 LIKE 查询", likes.size >= 2)
        likes.forEach { line ->
            assertTrue(
                "每个 LIKE 都要声明 ESCAPE（否则用户输入的 % 会被当通配符）：$line",
                line.contains("ESCAPE"),
            )
        }
    }

    @Test
    fun `关键词必须先转义再进查询`() {
        val repo = read("data/repository/ReportRepository.kt")
        assertTrue(
            "Repository 应调用 LikeEscape 转义关键词",
            repo.contains("LikeEscape.escape"),
        )

        // 转义规则本身
        assertEquals("普通文本不变", "abc", LikeEscape.escape("abc"))
        assertEquals("百分号要转义", "50\\%", LikeEscape.escape("50%"))
        assertEquals("下划线要转义", "a\\_b", LikeEscape.escape("a_b"))
        assertEquals("反斜杠要转义", "a\\\\b", LikeEscape.escape("a\\b"))
        // 反斜杠必须先转义，否则后面加的转义符会被二次转义
        // 输入 a\%b：反斜杠先变成两个，百分号再变成 \% —— 结果 a\\\%b
        assertEquals("a\\\\\\%b", LikeEscape.escape("a\\%b"))
        assertEquals("空串", "", LikeEscape.escape(""))
    }

    @Test
    fun `搜索范围切换要接到界面上`() {
        val vm = read("ui/history/HistoryViewModel.kt")
        assertTrue(
            "ViewModel 应维护搜索范围状态",
            vm.contains("searchScope"),
        )
        assertTrue(
            "搜索应同时依赖关键词与范围",
            vm.contains("combine(keyword, searchScope)"),
        )

        val screen = read("ui/history/HistoryScreen.kt")
        assertTrue(
            "界面上应有范围切换控件",
            screen.contains("onSearchScopeChange"),
        )
        assertTrue(
            "界面上应展示两种范围",
            screen.contains("SearchScope.entries"),
        )
    }

    @Test
    fun `搜索范围枚举有两个取值且默认按文件名`() {
        assertEquals(2, SearchScope.entries.size)
        assertEquals(SearchScope.FILE_NAME, SearchScope.entries.first())
        assertEquals(SearchScope.FILE_NAME, SearchScope.from(null))
        assertEquals(SearchScope.FILE_NAME, SearchScope.from("file_name"))
        assertEquals(SearchScope.ALL_CONTENT, SearchScope.from("all_content"))
    }

    // ---------------- 2. 时间范围快捷 ----------------

    @Test
    fun `时间范围快捷项必须复用 DateUtils 不能自己拼字符串`() {
        val screen = read("ui/home/HomeScreen.kt")
        assertTrue(
            "快捷项应调用 DateUtils 的现成函数",
            screen.contains("DateUtils.currentWeekRangeText()"),
        )
        assertTrue(
            "快捷项应调用 DateUtils 的现成函数",
            screen.contains("DateUtils.previousWeekRangeText()"),
        )
        assertTrue(
            "快捷组件应存在",
            screen.contains("fun TimeRangeShortcuts"),
        )
    }

    @Test
    fun `上一周的时间范围必须是本周往前推七天`() {
        // 周日 ~ 周六 为一周（见 DateUtils.getWeekRange）
        val today = parse("2026-11-21")   // 周六
        val thisWeek = DateUtils.currentWeekRangeText(today)
        val lastWeek = DateUtils.previousWeekRangeText(today)

        assertEquals("2026.11.15-2026.11.21", thisWeek)
        assertEquals("2026.11.08-2026.11.14", lastWeek)

        // 格式必须与 parseDateRange 对得上（否则填进去会校验失败）
        assertTrue(
            "本周时间范围应能被解析",
            DateUtils.parseDateRange(thisWeek) != null,
        )
        assertTrue(
            "上周时间范围应能被解析",
            DateUtils.parseDateRange(lastWeek) != null,
        )
    }

    @Test
    fun `时间范围格式必须是 yyyyMMdd-yyyyMMdd`() {
        val today = parse("2026-11-21")
        val text = DateUtils.currentWeekRangeText(today)
        assertTrue("时间范围格式应形如 2026.11.15-2026.11.21，实际：$text",
            Regex("""\d{4}\.\d{2}\.\d{2}-\d{4}\.\d{2}\.\d{2}""").matches(text))
    }

    @Test
    fun `一周是周日到周六不是周一到周日`() {
        // 2026-11-15 是周日，2026-11-21 是周六
        val (start, end) = DateUtils.getWeekRange(parse("2026-11-21"))
        assertEquals("2026-11-15", fmt(start))
        assertEquals("2026-11-21", fmt(end))
    }

    // ---------------- 3. 主题护栏 ----------------

    @Test
    fun `不许出现 values-night 资源限定符`() {
        val resRoot = File("src/main/res").absolutePath
        // 只禁 **values-night*（主题/颜色/字符串这类跟随系统深浅色的资源）**。
        //
        // ⚠️ `drawable-night` / `mipmap-night-*` 是**允许**的 ——
        //   那是 v1.6.0 刻意做的「夜间版桌面图标」多色位图。
        //   桌面图标跟随**系统**深浅色是合理的（桌面本身就跟随系统），
        //   和 App 内部那套手动主题是两回事。
        val nightValueDirs = File(resRoot).listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values-night") }
            ?: emptyList()
        assertTrue(
            "App 的深色模式是手动切换的，跟系统深浅色不是一回事，" +
                "禁止使用 values-night（否则会跟手动主题打架）：${nightValueDirs.map { it.name }}",
            nightValueDirs.isEmpty(),
        )
    }

    @Test
    fun `themes 必须写明不要用 night 限定符`() {
        val themes = File("src/main/res/values/themes.xml").readText()
        assertTrue(
            "themes.xml 里应留下明确警告，避免以后有人踩同一个坑",
            themes.contains("values-night"),
        )
        assertTrue(
            "themes.xml 里应说明深色是运行时切换的",
            themes.contains("手动切换") || themes.contains("运行时"),
        )
    }

    @Test
    fun `enableEdgeToEdge 必须显式指定系统栏样式`() {
        val activity = read("MainActivity.kt")
        assertTrue(
            "enableEdgeToEdge 必须显式传 SystemBarStyle（默认参数按系统深浅色套浅色 scrim）",
            activity.contains("SystemBarStyle.auto"),
        )
        assertTrue(
            "两套系统栏都要显式指定",
            activity.contains("statusBarStyle =") && activity.contains("navigationBarStyle ="),
        )
    }

    // ---------------- 工具 ----------------

    private fun parse(text: String): java.util.Date {
        val c = java.util.Calendar.getInstance()
        val parts = text.split("-").map { it.toInt() }
        c.clear()
        c.set(parts[0], parts[1] - 1, parts[2])
        return c.time
    }

    private fun fmt(date: java.util.Date): String {
        val c = java.util.Calendar.getInstance().apply { time = date }
        return "%04d-%02d-%02d".format(
            c.get(java.util.Calendar.YEAR),
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }
}
