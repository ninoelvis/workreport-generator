package com.workreport.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 发送方式的存取值必须稳定 —— 改错会导致老用户设置丢失 */
class MailSendModeTest {

    @Test
    fun `默认值是自动`() {
        assertEquals(MailSendMode.AUTO, MailSendMode.from(null))
        assertEquals(MailSendMode.AUTO, MailSendMode.from(""))
        assertEquals(MailSendMode.AUTO, MailSendMode.from("不存在的值"))
    }

    @Test
    fun `存取值可以原样往返`() {
        MailSendMode.entries.forEach { mode ->
            assertEquals(mode, MailSendMode.from(mode.storageValue))
        }
    }

    @Test
    fun `存储值不随文案变化`() {
        // 这些值写进了 SharedPreferences，改了就等于清空老用户的设置
        assertEquals("auto", MailSendMode.AUTO.storageValue)
        assertEquals("attachment", MailSendMode.ATTACHMENT_FIRST.storageValue)
        assertEquals("recipient", MailSendMode.RECIPIENT_FIRST.storageValue)
    }
}
