package com.workreport.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知权限引导的弹出条件。
 *
 * 用户的要求：首次安装后、填完姓名进首页时提示一次；
 * 点「去开启」拉起系统授权框；点「以后再说」就**不再提示**。
 */
class NotificationPromptTest {

    @Test
    fun `Android 13 以上 没授权 没处理过 填了姓名 才弹`() {
        assertTrue(
            NotificationPrompt.shouldShow(
                sdkInt = 33,
                hasPermission = false,
                handled = false,
                userNameFilled = true,
            ),
        )
    }

    @Test
    fun `Android 12 以下不弹（通知权限安装时自动给）`() {
        assertFalse(
            NotificationPrompt.shouldShow(
                sdkInt = 32,
                hasPermission = false,
                handled = false,
                userNameFilled = true,
            ),
        )
    }

    @Test
    fun `已经有权限了就不弹`() {
        assertFalse(
            NotificationPrompt.shouldShow(
                sdkInt = 34,
                hasPermission = true,
                handled = false,
                userNameFilled = true,
            ),
        )
    }

    @Test
    fun `用户点过取消就再也不弹`() {
        assertFalse(
            NotificationPrompt.shouldShow(
                sdkInt = 34,
                hasPermission = false,
                handled = true,
                userNameFilled = true,
            ),
        )
    }

    @Test
    fun `还没填姓名先不弹`() {
        assertFalse(
            NotificationPrompt.shouldShow(
                sdkInt = 34,
                hasPermission = false,
                handled = false,
                userNameFilled = false,
            ),
        )
    }
}
