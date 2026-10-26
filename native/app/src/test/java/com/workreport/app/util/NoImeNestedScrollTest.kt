package com.workreport.app.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 防回归：源码里不允许再出现 `imeNestedScroll()` 的**实际调用**。
 *
 * v1.4.2 的教训：
 * 为了「输入框被键盘挡住时自动滚到可见处」给列表加了 `Modifier.imeNestedScroll()`，
 * 结果它把「滑到底后剩余的滚动量」交给输入法去唤起键盘 ——
 * 用户在首页滑到最下面一松手，输入法就自己弹出来了。
 *
 * 而且它压根不需要：AppRoot 已经把整个内容区压到键盘上沿，
 * 输入框本来就不会被键盘遮住，Compose 自带的 bringIntoView 会把聚焦的输入框滚进视口。
 *
 * 所以这里用静态扫描把这条禁令锁死。注释里可以提到它（那是在解释为什么不能加）。
 */
class NoImeNestedScrollTest {

    private fun kotlinSources(): List<File> {
        val root = File("src/main/java/com/workreport/app")
        assertTrue("找不到源码目录：${root.absolutePath}", root.isDirectory)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** 去掉行注释和块注释，只留真实代码 */
    private fun stripComments(text: String): String {
        var out = StringBuilder()
        var inBlock = false
        for (raw in text.lines()) {
            val line = raw.trim()
            if (inBlock) {
                val end = line.indexOf("*/")
                if (end < 0) continue
                inBlock = false
                out.append(line.substring(end + 2)).append('\n')
                continue
            }
            val start = line.indexOf("/*")
            if (start >= 0) {
                val end = line.indexOf("*/", start + 2)
                if (end < 0) {
                    inBlock = true
                    continue
                }
                out.append(line.substring(0, start)).append(line.substring(end + 2)).append('\n')
                continue
            }
            out.append(line.substringBefore("//")).append('\n')
        }
        return out.toString()
    }

    @Test
    fun `代码里不能再调用 imeNestedScroll`() {
        val hits = mutableListOf<String>()
        for (f in kotlinSources()) {
            val code = stripComments(f.readText())
            if (code.contains("imeNestedScroll")) {
                f.readText().lines().forEachIndexed { i, line ->
                    if (line.contains("imeNestedScroll") && !line.trim().startsWith("//") && !line.trim().startsWith("*")) {
                        hits += "${f.name}:${i + 1}  ${line.trim()}"
                    }
                }
                if (!hits.any { it.startsWith(f.name) }) {
                    hits += "${f.name}  （调用形式被换行了）"
                }
            }
        }
        assertTrue(
            "imeNestedScroll() 不能出现在代码里（滑到底会自己唤起输入法）：\n" + hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }

    @Test
    fun `AppRoot 必须保留统一的底部避让`() {
        val appRoot = File("src/main/java/com/workreport/app/ui/AppRoot.kt")
        assertTrue("找不到 AppRoot.kt", appRoot.isFile)
        val code = stripComments(appRoot.readText())
        assertTrue(
            "AppRoot 必须用 BottomAvoidance.compute 统一算底部避让",
            code.contains("BottomAvoidance.compute"),
        )
        assertTrue(
            "AppRoot 必须读 WindowInsets.ime",
            code.contains("WindowInsets.ime"),
        )
    }

    @Test
    fun `页面里不能再各自加 imePadding`() {
        val hits = mutableListOf<String>()
        for (f in kotlinSources()) {
            if (f.name == "AppRoot.kt") continue
            val code = stripComments(f.readText())
            if (code.contains("imePadding(")) {
                hits += f.name
            }
        }
        assertTrue(
            "页面里不要再各自加 imePadding()（会和 AppRoot 的统一避让重复计算）：\n" + hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }
}
