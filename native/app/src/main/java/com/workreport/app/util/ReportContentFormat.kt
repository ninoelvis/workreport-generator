package com.workreport.app.util

/**
 * Excel 里那两格的文本是**格式化后**的：
 *
 * ```
 * 完成共2项工作内容：
 * 1、配合纪委完成案件审理；
 * 2、组织案管系统培训。
 * ```
 *
 * 「载入首页继续编辑」时必须拆回用户当初写的原始条目：
 *
 * ```
 * 配合纪委完成案件审理
 * 组织案管系统培训
 * ```
 *
 * ⚠️ 不拆的话，首页输入框里放的是格式化后的文本，
 * 重新生成会**二次格式化**，变成
 * 「完成共2项工作内容：\n1、完成共2项工作内容：；\n2、1、配合纪委…；」这种乱码。
 *
 * 拆完再格式化回去是**无损**的：
 * 结尾的「；」「。」本来就由格式化那一步补上。
 */
object ReportContentFormat {

    private const val DONE_HEADER = "完成共"
    private const val DONE_HEADER_SUFFIX = "项工作内容："
    private const val PLAN_HEADER = "下周预计共"
    private const val PLAN_HEADER_SUFFIX = "项工作任务："

    /** 「1、」「2、」这种序号前缀 */
    private val INDEX_PREFIX = Regex("^\\d+、")

    /** 把「本周已完成工作梳理」的格式化文本拆回原始条目 */
    fun unformatDoneWorks(text: String): String = unformat(text, DONE_HEADER, DONE_HEADER_SUFFIX)

    /** 把「下周工作计划」的格式化文本拆回原始条目 */
    fun unformatPlanWorks(text: String): String = unformat(text, PLAN_HEADER, PLAN_HEADER_SUFFIX)

    private fun unformat(text: String, header: String, headerSuffix: String): String {
        if (text.isBlank()) return ""
        val lines = text.split('\n')
        val first = lines.firstOrNull()?.trim().orEmpty()
        // 不是格式化过的文本（比如用户直接写的原始内容）→ 原样返回，绝不乱动
        if (!first.startsWith(header) || !first.endsWith(headerSuffix)) {
            return text.trim()
        }
        val items = lines.drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val body = line.replaceFirst(INDEX_PREFIX, "")
                // 去掉结尾一个分隔符；格式化那一步会再补回来，无损
                when {
                    body.endsWith("；") -> body.dropLast(1)
                    body.endsWith("。") -> body.dropLast(1)
                    else -> body
                }
            }
        return items.joinToString("\n")
    }
}
