package com.workreport.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全 App **统一动效规格**（v2.0.4 起）。
 *
 * 以前每个地方的动画时长都是手写数字（120 / 180 / 220 / 280…），
 * 散在十几个文件里，改动的时候很容易漏掉一处，久了就变成"半个 App 有动效、半个没有"。
 *
 * 现在统一收口到这里：
 *   · 时长只有 4 档，语义固定
 *   · 开发者页可以**全局关掉动效**（对比效果 / 排查闪烁）
 *   · 开发者页可以**调快调慢**（看清每一步动画的细节）
 *
 * ⚠️ 关掉动效时时长会变成 0（瞬时），
 *    但**按压回弹那种物理反馈不关** —— 它是手感，不是装饰。
 */
object Motion {

    /** 极快：消失、收起 */
    const val FAST = 120

    /** 标准：淡入淡出、颜色过渡 */
    const val NORMAL = 180

    /** 展开：内容展开/收起 */
    const val MEDIUM = 220

    /** 慢：页面切换 */
    const val SLOW = 280

    /**
     * 全局动效开关。
     *
     * 开发者页（我的 → 关于 → 连点版本号 7 次）里可以临时关掉，
     * 用来对比「有动效 / 没动效」的观感，或者排查某个动画导致的闪烁。
     */
    var enabled by mutableStateOf(true)

    /**
     * 速度系数。
     *
     * 1 = 正常；0.5 = 慢一倍（时长 ×2）；2 = 快一倍（时长 ÷2）。
     */
    var speed by mutableFloatStateOf(1f)

    /**
     * 按全局设置缩放一个时长。
     *
     * 动效关闭 → 0（瞬时）。
     */
    fun duration(base: Int): Int {
        if (!enabled) return 0
        val s = if (speed <= 0f) 1f else speed
        return (base / s).toInt().coerceIn(1, 4000)
    }
}
