package com.workreport.app.util

/**
 * 工作记录的顺序调整。
 *
 * 抽成纯函数是为了能直接跑单元测试 —— 排序是用户明确要的功能，
 * 不能靠"看起来对"就交付。
 */
object RecordOrder {

    /**
     * 把 [items] 里第 [from] 条移到第 [to] 个位置。
     *
     * 下标越界或 from == to 时原样返回。
     */
    fun move(items: List<String>, from: Int, to: Int): List<String> {
        if (from !in items.indices || to !in items.indices) return items
        if (from == to) return items
        val next = items.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        return next
    }
}
