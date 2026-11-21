package com.workreport.app.util

/**
 * SQLite `LIKE` 模式里的通配符转义。
 *
 * 坑：`LIKE '%' || :keyword || '%'` 这种写法虽然防了 SQL 注入，
 * 但**没有转义 LIKE 自己的通配符**。用户在搜索框里输入 `%` 或 `_`，
 * 会被当成通配符 —— 搜 `50%` 会匹配所有含 `50` 的行，搜 `a_b` 会匹配 `axb`。
 *
 * 修法：转义这三个字符，并在 SQL 里声明 `ESCAPE '\'`。
 *
 * 转义规则（与 SQLite 的 `ESCAPE` 配套）：
 *   `\` → `\\`
 *   `%` → `\%`
 *   `_` → `\_`
 *
 * 顺序很重要：**必须先转义反斜杠本身**，否则后面加的反斜杠会被二次转义。
 */
object LikeEscape {

    /** SQL 里声明的转义字符 */
    const val ESCAPE_CHAR = '\\'

    /**
     * 把用户输入转义成可以安全放进 `LIKE ... ESCAPE '\'` 的模式片段。
     *
     * 这是纯函数，不依赖 Android，可以在 JVM 单元测试里直接跑。
     */
    fun escape(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length + 8)
        for (ch in input) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '%' -> sb.append("\\%")
                '_' -> sb.append("\\_")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
