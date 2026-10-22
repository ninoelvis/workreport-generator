package com.workreport.app.domain.model

/**
 * 拉起邮箱 App 时的发送方式。
 *
 * ## 为什么要分几种
 *
 * Android 的两个邮件通道各有各的「标准字段」，而各家邮箱 App 只认其中一套：
 *
 * | 通道 | 附件 | 收件人 |
 * |------|------|--------|
 * | `ACTION_SEND` | ✅ 标准通道（`EXTRA_STREAM` + `clipData`） | ⚠️ 非标准，部分 App 忽略 `EXTRA_EMAIL` |
 * | `ACTION_SENDTO` | ⚠️ 非标准，多数 App 直接忽略 `EXTRA_STREAM` | ✅ 标准通道（`data = mailto:`） |
 *
 * 同一个 Intent 里没法保证两者都成立，除非目标 App 提供了自己的私有通道
 * （阿里邮箱就有，见 [com.workreport.app.util.MailHelper]）。
 *
 *  - [AUTO]：默认。优先用目标 App 的私有/最优通道，实在没有再退回标准通道。
 *  - [ATTACHMENT_FIRST]：强制附件优先，压缩包必带，收件人尽力带上。
 *  - [RECIPIENT_FIRST]：强制收件人优先，收件人必填，附件尽力带上。
 */
enum class MailSendMode(val storageValue: String) {

    /** 自动：按目标邮箱 App 挑最优通道 */
    AUTO("auto"),

    /** 附件优先：`ACTION_SEND` 通道，压缩包必带，收件人尽力带上 */
    ATTACHMENT_FIRST("attachment"),

    /** 收件人优先：`ACTION_SENDTO` 通道，收件人必填，附件尽力带上 */
    RECIPIENT_FIRST("recipient"),
    ;

    companion object {
        fun from(value: String?): MailSendMode =
            entries.firstOrNull { it.storageValue == value } ?: AUTO
    }
}
