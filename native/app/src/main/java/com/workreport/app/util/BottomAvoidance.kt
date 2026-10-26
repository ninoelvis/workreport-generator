package com.workreport.app.util

/**
 * 底部避让高度的纯计算。
 *
 * 背景（v1.4.2 修的键盘空白 bug）：
 * AppRoot 的 Scaffold 已经用 innerPadding 把内容区抬高了一个「底部导航栏高度」，
 * 如果页面里再叠一层 imePadding()，等于把导航栏高度多减了一次，
 * 键盘上方就会凭空多出一条空白带（用户看到的那一大块灰色）。
 *
 * 正确做法：底部避让 = max(导航栏高度, 输入法高度)
 *  · 没弹键盘 → 留导航栏的位置，底部 Tab 不会被压住
 *  · 弹了键盘 → 留键盘的高度，内容正好停在键盘上沿，不多不少
 *
 * 抽成纯函数是为了能写单元测试锁住这个行为。
 */
object BottomAvoidance {

    /**
     * @param navBarPx 底部导航栏（或系统导航条）占的高度，像素
     * @param imePx    输入法占的高度，像素；没弹键盘时为 0
     * @return 底部需要让出的高度，像素
     */
    fun compute(navBarPx: Int, imePx: Int): Int {
        require(navBarPx >= 0) { "navBarPx 不能为负: $navBarPx" }
        require(imePx >= 0) { "imePx 不能为负: $imePx" }
        return if (navBarPx >= imePx) navBarPx else imePx
    }
}
