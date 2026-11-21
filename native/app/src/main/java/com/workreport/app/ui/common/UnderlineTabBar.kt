package com.workreport.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.workreport.app.ui.common.Motion
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 下划线 Tab（v2.0.1 起）。
 *
 * 文字 + 下方一条短横线，切换时**指示条平滑平移**。
 *
 * 为什么不做成 Material3 的 TabRow（波浪线指示器）：
 * 整个 App 的视觉语言是**液态玻璃 + 胶囊**，底栏指示条已经是「平移」这套语言了，
 * 顶部再用 Material3 的波浪线会显得是另一个 App。
 * 这里与底栏**共用同一套动画规格**（spring），两个区域串成一种语言。
 *
 * 复用点：
 *  · 历史报告页顶部「列表 / 统计」
 *  · 统计页「按月 / 按周」
 */
@Composable
fun UnderlineTabBar(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tabWidth = maxWidth / labels.size
        val indicatorX by animateDpAsState(
            targetValue = tabWidth * selected + (tabWidth - TAB_INDICATOR_WIDTH) / 2,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "tabIndicatorX",
        )

        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                labels.forEachIndexed { index, label ->
                    val isSelected = selected == index
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = Motion.duration(Motion.NORMAL)),
                        label = "tabTextColor",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(index) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor,
                        )
                    }
                }
            }

            // 指示条：短横线，在标签之间平滑平移
            Box(
                modifier = Modifier
                    .offset(x = indicatorX)
                    .width(TAB_INDICATOR_WIDTH)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * 下划线 Tab 的指示条宽度。
 *
 * 与底栏的指示条（16.dp）保持一致，两个区域用同一种视觉语言。
 */
val TAB_INDICATOR_WIDTH = 16.dp
