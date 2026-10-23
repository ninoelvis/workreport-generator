package com.workreport.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Excel 效果预览表。
 *
 * ⚠️ 这里**只是预览**，不影响导出的 xlsx 内容。列宽比例直接取自 XlsxWriter 的真实列宽，
 *    行高、对齐方式也对齐 Excel 的样式定义，保证「所见即所得」。
 *
 * Excel 的真实列宽（单位：字符）：
 *   20.71484375, 29.8125, 26.36328125, 19.97265625, 21.46484375, 13.83203125
 * 归一化后作为预览里 6 列的宽度占比。
 *
 * 首页「预览区」和历史报告的「查看内容」共用这一份实现，保证两处看到的是同一套样式。
 */
private val PREVIEW_COL_WEIGHTS: FloatArray = floatArrayOf(
    20.71484375f, 29.8125f, 26.36328125f, 19.97265625f, 21.46484375f, 13.83203125f,
).let { raw ->
    val total = raw.sum()
    FloatArray(raw.size) { raw[it] / total }
}

private val PREVIEW_HEADERS = listOf(
    "本周工作时间",
    "本周已完成工作梳理",
    "下周工作计划",
    "客户（及用户）反馈",
    "项目经理评级",
    "其他",
)

/**
 * 渲染一张与最终 Excel 一致的预览表。
 *
 * @param title 报告标题（Excel 里 A1:F1 合并单元格）
 * @param timeRange 本周工作时间（Excel A3:A4 合并）
 * @param doneWorks 本周已完成工作梳理（Excel B3:B4 合并）
 * @param planWorks 下周工作计划（Excel C3:C4 合并）
 */
@Composable
fun PreviewTable(
    title: String,
    timeRange: String,
    doneWorks: String,
    planWorks: String,
    modifier: Modifier = Modifier,
) {
    val border = MaterialTheme.colorScheme.outline
    val headerBg = Color(0xFFB5C6EA)
    val ink = Color(0xFF1F2329)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, border, RoundedCornerShape(6.dp)),
    ) {
        // 第 1 行：标题（Excel 里 A1:F1 合并，水平+垂直居中）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.ifEmpty { "-" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        PreviewDivider(border)

        // 第 2 行：表头（Excel 里黑体 10pt、填充 B5C6EA、水平垂直居中）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg),
        ) {
            PREVIEW_HEADERS.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(PREVIEW_COL_WEIGHTS[index])
                        .border(0.5.dp, border)
                        .padding(horizontal = 1.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 8.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        PreviewDivider(border)

        // 第 3、4 行：数据区（Excel 里 A3:A4 … F3:F4 六列全部纵向合并）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // A 列：仿宋 9pt、左对齐、垂直居中、不换行
            PreviewCell(
                text = timeRange,
                weight = PREVIEW_COL_WEIGHTS[0],
                border = border,
                fontSize = 8.5.sp,
                wrapText = false,
                centerVertically = true,
            )
            // B / C 列：仿宋 9pt、左对齐、换行
            PreviewCell(doneWorks, PREVIEW_COL_WEIGHTS[1], border, 8.5.sp)
            PreviewCell(planWorks, PREVIEW_COL_WEIGHTS[2], border, 8.5.sp)
            // D / E / F 列：仿宋 11pt、左对齐、换行（内容留空，供以后扩展）
            PreviewCell("", PREVIEW_COL_WEIGHTS[3], border, 8.5.sp)
            PreviewCell("", PREVIEW_COL_WEIGHTS[4], border, 8.5.sp)
            PreviewCell("", PREVIEW_COL_WEIGHTS[5], border, 8.5.sp)
        }
    }
}

/** 预览表格里的细分隔线 */
@Composable
private fun PreviewDivider(border: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(border),
    )
}

@Composable
private fun RowScope.PreviewCell(
    text: String,
    weight: Float,
    border: Color,
    fontSize: TextUnit,
    wrapText: Boolean = true,
    centerVertically: Boolean = false,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(132.dp)
            .border(0.5.dp, border)
            .padding(horizontal = 3.dp, vertical = 4.dp),
        contentAlignment = if (centerVertically) Alignment.Center else Alignment.TopStart,
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
            maxLines = if (wrapText) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (centerVertically) Modifier.fillMaxWidth() else Modifier,
        )
    }
}
