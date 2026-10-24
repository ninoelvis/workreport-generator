package com.workreport.app.util

/**
 * 「要不要弹通知权限引导」的判断。
 *
 * 抽成纯函数是为了能直接跑单元测试 ——
 * 这个弹窗一旦乱弹会很烦人（用户明确说了：点过取消就别再提示），
 * 条件必须锁死。
 */
object NotificationPrompt {

    /** Android 13（API 33）起才有运行时通知权限 */
    const val TIRAMISU = 33

    /**
     * 四个条件同时满足才弹：
     *
     *  1. [sdkInt] ≥ 33 —— 低版本通知权限安装时自动给了，不用问；
     *  2. 系统里**还没有**通知权限；
     *  3. 用户**没有**做过选择（点过「去开启」或「以后再说」都不再问）；
     *  4. 已经填了姓名 —— 用户说的时序是「输入姓名进入首页后」才提示。
     */
    fun shouldShow(
        sdkInt: Int,
        hasPermission: Boolean,
        handled: Boolean,
        userNameFilled: Boolean,
    ): Boolean =
        sdkInt >= TIRAMISU && !hasPermission && !handled && userNameFilled
}
