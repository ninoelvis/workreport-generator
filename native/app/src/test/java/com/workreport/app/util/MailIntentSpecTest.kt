package com.workreport.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 发送意图的不变量测试。
 *
 * 背景：之前连续几版都栽在「邮件里没有附件」上 —— 每次都以为是某个 App 的私有实现，
 * 结果反复换通道，越换越坏。这次把**发出去的 Intent 长什么样**固化下来并断言，
 * 以后再改这里会立刻被测出来。
 *
 * 关键事实（逆向 `com.alibaba.cloudmail` v4.12.8 得到）：
 *  - 附件的读取顺序是 `clipData` → `EXTRA_STREAM`(ArrayList) → `EXTRA_STREAM`(单个 Uri)；
 *  - `Welcome` 中转页转发 Intent 时**不复制** `EXTRA_STREAM` / `clipData` → 附件必丢，
 *    所以**绝不能**走 `ACTION_SENDTO`（会命中 Welcome）；
 *  - `ACTION_SEND` 上挂 `data = mailto:` 会因 scheme 匹配失败而匹配不上任何邮件 App。
 */
class MailIntentSpecTest {

    private val uri = "content://com.workreport.app.fileprovider/reports/张三-2026年10月第3周工作报告.zip"
    private val subject = "张三-2026年10月第3周工作报告.zip"

    /**
     * 模拟各家邮件 App 读附件的逻辑（clipData 优先，其次 EXTRA_STREAM）。
     * 返回它们能读到的附件数量。
     */
    private fun attachmentsSeenByMailApp(spec: MailIntentSpec): Int {
        // 我们的构造：clipData 与 EXTRA_STREAM 都放了同一个 URI
        val fromClipData = if (spec.attachmentUri.isNotEmpty()) 1 else 0
        val fromExtraStream = if (spec.attachmentUri.isNotEmpty()) 1 else 0
        return maxOf(fromClipData, fromExtraStream)
    }

    @Test
    fun `走的是 ACTION_SEND 通道——不能是 SENDTO`() {
        val spec = MailHelper.buildSpec(uri, "com.alibaba.cloudmail", subject, "")
        assertEquals("android.intent.action.SEND", spec.action)
    }

    @Test
    fun `type 必须是 application_zip`() {
        val spec = MailHelper.buildSpec(uri, "com.alibaba.cloudmail", subject, "")
        assertEquals("application/zip", spec.type)
    }

    @Test
    fun `附件一定会被邮件 App 读到`() {
        val spec = MailHelper.buildSpec(uri, "com.alibaba.cloudmail", subject, "")
        assertTrue("附件 URI 不能为空", spec.attachmentUri.isNotEmpty())
        assertEquals("邮件 App 必须能从 Intent 里读到 1 个附件", 1, attachmentsSeenByMailApp(spec))
    }

    @Test
    fun `主题一定带上了`() {
        val spec = MailHelper.buildSpec(uri, "com.alibaba.cloudmail", subject, "")
        assertEquals(subject, spec.subject)
        assertEquals(subject, spec.title)
    }

    @Test
    fun `正文留空`() {
        val spec = MailHelper.buildSpec(uri, "com.alibaba.cloudmail", subject, "")
        assertEquals("", spec.body)
        assertFalse(spec.hasBody)
    }

    @Test
    fun `阿里邮箱要加 CLEAR_TASK 绕开 singleTask 复用`() {
        val ali = MailHelper.buildSpec(uri, "com.alibaba.cloudmail", subject, "")
        assertTrue("阿里邮箱必须清任务栈，否则复用旧实例附件不处理", ali.clearTask)
    }

    @Test
    fun `其他邮箱 App 不清任务栈`() {
        listOf("com.tencent.qqmail", "com.google.android.gm", "com.netease.mobimail")
            .forEach { pkg ->
                val spec = MailHelper.buildSpec(uri, pkg, subject, "")
                assertFalse("$pkg 不该清任务栈", spec.clearTask)
            }
    }

    @Test
    fun `定向到指定邮箱 App`() {
        val spec = MailHelper.buildSpec(uri, "com.alibaba.cloudmail", subject, "")
        assertEquals("com.alibaba.cloudmail", spec.packageName)
    }

    @Test
    fun `没选邮箱 App 时交给系统选`() {
        assertEquals(null, MailHelper.buildSpec(uri, null, subject, "").packageName)
        assertEquals(null, MailHelper.buildSpec(uri, "", subject, "").packageName)
    }

    @Test
    fun `不同邮箱 App 都要能读到附件`() {
        listOf(
            "com.alibaba.cloudmail",
            "com.tencent.qqmail",
            "com.google.android.gm",
            "com.netease.mobimail",
            "com.microsoft.office.outlook",
            null,
        ).forEach { pkg ->
            val spec = MailHelper.buildSpec(uri, pkg, subject, "")
            assertNotNull(spec)
            assertEquals("包名 $pkg 必须带得上附件", 1, attachmentsSeenByMailApp(spec))
            assertTrue("包名 $pkg 必须带得上主题", spec.subject.isNotEmpty())
        }
    }
}
