package com.workreport.app.ui

/**
 * 页面切换方向。
 *
 * 背景：底栏三个 Tab 用的是 `navigate(route) { popUpTo(start) { saveState = true } }`，
 * 而 navigation-compose 的 `ComposeNavigator.navigate()` 在**最后**会把 `isPop` 重置为 false
 * （`popBackStack()` 才会把它设成 true）。所以 Tab 切换**永远走 enterTransition / exitTransition**，
 * 不会走 popEnter / popExit。
 *
 * 结果就是：不管从左边的 Tab 点到右边的、还是从右边的点回左边的，
 * 新页面都是「从右往左滑进来」—— 从「我的」切到「历史报告」时方向是反的，
 * 看起来像「历史报告从右边压过来」，而不是「往回退」。
 *
 * 这里按**目标 Tab 的索引**判断方向：
 *   · Tab → Tab：索引变大 = 向前（从右滑入）；索引变小 = 向后（从左滑入）
 *   · Tab → 子页面：向前
 *   · 子页面 → Tab：向后（系统返回键 / 左上角返回按钮都走这条）
 */
internal fun navDirection(fromRoute: String?, toRoute: String?): Int {
    // 没有来源页（首次进入）或没有目标页 → 不做位移，只淡入淡出
    if (fromRoute == null || toRoute == null) return 0
    val from = TopLevelDestination.entries.indexOfFirst { it.route == fromRoute }
    val to = TopLevelDestination.entries.indexOfFirst { it.route == toRoute }
    // 停在同一页 → 不做位移，只淡入淡出
    if (from == to) return 0
    return when {
        from >= 0 && to >= 0 -> if (to > from) 1 else -1
        from >= 0 -> 1      // Tab → 子页面：向前
        to >= 0 -> -1       // 子页面 → Tab：向后
        else -> 1           // 子页面 → 子页面：向前
    }
}
