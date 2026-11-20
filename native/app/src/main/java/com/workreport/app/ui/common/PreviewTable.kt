package com.workreport.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.workreport.app.ui.theme.LocalPreviewColors
import androidx.compose.ui.unit.Dp

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

/**
 * 表头行的固定高度。
 *
 * 对应 Excel 里表头行的固定行高（28pt），够放两行 8.5sp 的表头文字。
 */
private val HEADER_ROW_HEIGHT: Dp = 34.dp

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
    /**
     * 数据区每个单元格的高度。
     *
     * 默认 132.dp —— 和原来完全一样，首页预览区的表现**一个字都没变**。
     * 传 null 则高度随内容增长（长的工作梳理不再被裁掉），全屏预览用这个。
     */
    cellHeight: Dp? = 132.dp,
    /**
     * 字号缩放。默认 1f —— 和原来完全一样。
     * 全屏预览传 1.25f，手机上更易读。
     */
    textScale: Float = 1f,
) {
    // 表头、标题与边框的颜色跟随深浅色主题，
    // 避免深色模式下出现「深底深字」以及表格轮廓糊掉
    val previewColors = LocalPreviewColors.current
    val border = previewColors.border
    val headerBg = previewColors.headerBg
    val headerInk = previewColors.headerInk

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
                fontSize = (14 * textScale).sp,
                fontWeight = FontWeight.Bold,
                color = previewColors.titleInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        PreviewDivider(border)

        // 第 2 行：表头（Excel 里黑体 10pt、填充 B5C6EA、水平垂直居中）
        //
        // ⚠️ Excel 的表头行高是**固定**的（ROW_HEIGHTS[1] = 28pt），
        //    不会因为某一列的文字换行而变高。所以这里也用**固定高度**：
        //    「客户（及用户）反馈」这种长标题在窄屏上会换到两行，
        //    但所有列的表头框一样高、一样垂直居中 —— 不会出现
        //    「其他」孤零零顶在上面、而别的列撑高整行的情况。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PREVIEW_HEADERS.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(PREVIEW_COL_WEIGHTS[index])
                        .height(HEADER_ROW_HEIGHT)
                        .border(0.5.dp, border)
                        .padding(horizontal = 1.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = (8.5 * textScale).sp,
                        lineHeight = (11 * textScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = headerInk,
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
                fontSize = (8.5 * textScale).sp,
                wrapText = false,
                centerVertically = true,
                cellHeight = cellHeight,
            )
            // B / C 列：仿宋 9pt、左对齐、换行
            PreviewCell(doneWorks, PREVIEW_COL_WEIGHTS[1], border, (8.5 * textScale).sp, cellHeight = cellHeight)
            PreviewCell(planWorks, PREVIEW_COL_WEIGHTS[2], border, (8.5 * textScale).sp, cellHeight = cellHeight)
            // D / E / F 列：仿宋 11pt、左对齐、换行（内容留空，供以后扩展）
            PreviewCell("", PREVIEW_COL_WEIGHTS[3], border, (8.5 * textScale).sp, cellHeight = cellHeight)
            PreviewCell("", PREVIEW_COL_WEIGHTS[4], border, (8.5 * textScale).sp, cellHeight = cellHeight)
            PreviewCell("", PREVIEW_COL_WEIGHTS[5], border, (8.5 * textScale).sp, cellHeight = cellHeight)
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
    cellHeight: Dp? = 132.dp,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            // 传 null 时高度随内容增长，长文本不再被裁掉；
            // 传具体值时和原来的表现一模一样（132.dp）。
            .then(
                if (cellHeight != null) Modifier.height(cellHeight)
                else Modifier.heightIn(min = 132.dp)
            )
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
