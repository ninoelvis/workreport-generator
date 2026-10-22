package com.workreport.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workreport.app.domain.model.MailSendMode

/**
 * 邮件发送方式选择器。
 *
 * 「带压缩包」与「优先填收件人」对应两条不同的 Intent 通道 —— 附件和收件人分属
 * `ACTION_SEND` / `ACTION_SENDTO` 两条通道的标准字段，没法在同一个 Intent 里都保证，
 * 所以由用户按自己邮箱 App 的脾气来选（选了会记住）。
 */
@Composable
fun MailModeSelector(
    mode: MailSendMode,
    onChange: (MailSendMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            "发送方式",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == MailSendMode.AUTO,
                onClick = { onChange(MailSendMode.AUTO) },
                label = { Text("自动", fontSize = 12.sp) },
            )
            FilterChip(
                selected = mode == MailSendMode.ATTACHMENT_FIRST,
                onClick = { onChange(MailSendMode.ATTACHMENT_FIRST) },
                label = { Text("只带压缩包", fontSize = 12.sp) },
            )
            FilterChip(
                selected = mode == MailSendMode.RECIPIENT_FIRST,
                onClick = { onChange(MailSendMode.RECIPIENT_FIRST) },
                label = { Text("只填收件人", fontSize = 12.sp) },
            )
        }
        Text(
            when (mode) {
                MailSendMode.AUTO ->
                    "自动判断：阿里邮箱会走它的专用通道，收件人、主题、压缩包一次全带；" +
                        "其他邮箱 App 优先保证压缩包挂上，收件人尽力带上。"
                MailSendMode.ATTACHMENT_FIRST ->
                    "只带压缩包：压缩包必挂在附件上。部分邮箱 App 在这条通道下不认收件人，" +
                        "遇到这种情况就换「只填收件人」。"
                MailSendMode.RECIPIENT_FIRST ->
                    "只填收件人：收件人必定自动填上。部分邮箱 App 在这条通道下不认附件，" +
                        "遇到这种情况从「下载」目录手动添加压缩包即可。"
            },
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
