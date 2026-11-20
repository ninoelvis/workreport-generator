package com.workreport.app.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 防回归：**横竖两个滚动容器不能套在同一个 `Modifier` 链上**。
 *
 * v1.5.0 的教训（用户实机发现）：
 * 「放大查看」的全屏预览里写了
 *
 * ```kotlin
 * Box(Modifier.width(620.dp).horizontalScroll(h).verticalScroll(v)) {
 *     PreviewTable(modifier = Modifier.fillMaxWidth())
 * }
 * ```
 *
 * `horizontalScroll` 会给子内容「**宽度无上界**」的约束，
 * 而 `PreviewTable` 里的 `fillMaxWidth()` 在宽度无上界时，
 * 宽度会退化成 `minWidth`（也就是 0）——
 * 结果整个表格宽度为 0，**点开「放大查看」里面一片空白**，
 * 连表头和边框都看不到。
 *
 * 正确写法：**分层**（外层竖向滚、内层横向滚），
 * 并把表格的宽度**显式写死**在 PreviewTable 的 modifier 上。
 *
 * 这里用静态扫描把这条禁令锁死：同一个 `Modifier` 链里不允许同时出现
 * `horizontalScroll` 和 `verticalScroll`。
 */
class NoMixedScrollContainerTest {

    private fun kotlinSources(): List<File> {
        val root = File("src/main/java/com/workreport/app")
        assertTrue("找不到源码目录：${root.absolutePath}", root.isDirectory)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** 去掉行注释和块注释，只留真实代码 */
    private fun stripComments(text: String): String = text
        .replace(Regex("(?s)/\\*.*?\\*/"), "")
        .replace(Regex("//.*"), "")

    @Test
    fun `横竖滚动不能同时挂在同一个 Modifier 链上`() {
        val hits = mutableListOf<String>()
        for (f in kotlinSources()) {
            val code = stripComments(f.readText())
            // 找形如 Box(Modifier ... ) / Column(Modifier ... ) 的修饰符链
            val chains = Regex("(?:Box|Column|Row)\\(\\s*modifier\\s*=\\s*Modifier((?:.|\\n){0,600}?)\\)\\s*[,{]")
                .findAll(code)
            for (m in chains) {
                val chain = m.groupValues[1]
                val h = chain.contains("horizontalScroll(")
                val v = chain.contains("verticalScroll(")
                if (h && v) {
                    val line = code.substring(0, m.range.first).count { it == '\n' } + 1
                    hits += "${f.name}:$line  横竖滚动同时挂在一个 Modifier 链上（宽度会退化成 0，表格看不见）"
                }
            }
        }
        assertTrue(
            "横竖滚动必须分层（外层竖向、内层横向），不能挂在同一个 Modifier 链上：\n" +
                hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }

    @Test
    fun `全屏预览必须显式给表格宽度`() {
        val f = File("src/main/java/com/workreport/app/ui/common/FullscreenPreview.kt")
        assertTrue("找不到 FullscreenPreview.kt", f.isFile)
        val code = stripComments(f.readText())
        assertTrue(
            "全屏预览必须用 Modifier.width(FULL_TABLE_WIDTH) 显式给表格宽度，不能只靠 fillMaxWidth",
            code.contains("Modifier.width(FULL_TABLE_WIDTH)"),
        )
        assertTrue(
            "全屏预览必须把竖向滚动放在外层、横向滚动放在内层",
            code.indexOf("verticalScroll(vScroll)") < code.indexOf("horizontalScroll(hScroll)"),
        )
    }
}
