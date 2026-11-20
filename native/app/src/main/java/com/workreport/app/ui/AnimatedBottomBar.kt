package com.workreport.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars

/** 底部栏内容区高度（不含系统手势条 inset） */
private val BAR_CONTENT_HEIGHT = 62.dp
private val INDICATOR_WIDTH = 22.dp
private val INDICATOR_HEIGHT = 3.dp
private val INDICATOR_BOTTOM_GAP = 7.dp

/**
 * 自绘底部导航栏。
 *
 * 为什么不用 Material 的 NavigationBar？
 *   Material 的 NavigationBarItem 不带图标缩放动画，也不带指示条平移动画，
 *   这里改成自绘，才能做到：
 *     · 指示条在选中项之间平滑平移
 *     · 图标选中放大到 1.12，未选中缩到 0.92
 *     · 文字只在选中时淡入，未选中时淡出（固定高度，不会顶动布局）
 */
@Composable
fun AnimatedBottomBar(
    destinations: List<TopLevelDestination>,
    selectedRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val selectedIndex = destinations.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            val itemWidth = maxWidth / destinations.size
            val indicatorX by animateDpAsState(
                targetValue = itemWidth * selectedIndex + (itemWidth - INDICATOR_WIDTH) / 2,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "bottomIndicatorX",
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BAR_CONTENT_HEIGHT)
            ) {
                // 选中指示条：随选中项平滑平移
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = indicatorX,
                            y = BAR_CONTENT_HEIGHT - INDICATOR_BOTTOM_GAP - INDICATOR_HEIGHT,
                        )
                        .size(INDICATOR_WIDTH, INDICATOR_HEIGHT)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(INDICATOR_HEIGHT / 2),
                        )
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(BAR_CONTENT_HEIGHT)
                ) {
                    destinations.forEach { destination ->
                        val selected = destination.route == selectedRoute
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.12f else 0.92f,
                            animationSpec = spring(
                                dampingRatio = 0.6f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "iconScale",
                        )
                        val labelAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = tween(durationMillis = 220),
                            label = "labelAlpha",
                        )
                        val tint by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 220),
                            label = "iconTint",
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(
                                    selected = selected,
                                    onClick = { onSelect(destination) },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(destination.icon),
                                contentDescription = destination.label,
                                tint = tint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = destination.label,
                                fontSize = 11.sp,
                                color = tint,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .height(15.dp)
                                    .alpha(labelAlpha),
                            )
                        }
                    }
                }
            }
        }
    }
}
