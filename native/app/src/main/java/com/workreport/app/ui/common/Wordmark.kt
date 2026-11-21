package com.workreport.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.workreport.app.R

/**
 * WorkReport 字标（v2.0.3 · 方案 A · 纯文字）。
 *
 * **没有任何图形**，就是「WorkReport」这一行字 —— 跟桌面图标同一套设计。
 * 字形直接取自设计稿原图（不是字体重排），所以笔触跟设计稿一模一样。
 *
 * 为什么拆成 Work / Report 两份图片：
 * 两份都是**单色形状**，运行时用 `ColorFilter.tint` 按主题染色，
 * 于是浅色/深色**自动跟随**，不用维护两套图。
 *
 * 为什么没有边框、也不垫底色：
 * 用户要求「字样与背景融为一体」—— 所以这里既不画框，也不加底色方块，
 * 字直接贴在页面背景上。
 */
@Composable
fun Wordmark(
    /** 整体宽度；高度按设计稿比例（宽高比 5.69）自动算 */
    width: Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    // Work 段占 45.1%，Report 段占 54.9%（取自设计稿原图的比例）
    val workW = width * 0.451f
    val reportW = width * 0.549f
    val h = width / 5.69f

    val workColor = MaterialTheme.colorScheme.onSurfaceVariant
    val reportColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_wordmark_work),
            contentDescription = null,
            colorFilter = ColorFilter.tint(workColor),
            modifier = Modifier
                .width(workW)
                .height(h),
        )
        Image(
            painter = painterResource(R.drawable.ic_wordmark_report),
            contentDescription = null,
            colorFilter = ColorFilter.tint(reportColor),
            modifier = Modifier
                .width(reportW)
                .height(h),
        )
    }
}
