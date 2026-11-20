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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workreport.app.ui.common.GlassBackdrop
import com.workreport.app.ui.common.GlassPalette
import com.workreport.app.ui.common.GlassSurface

/** 底部栏内容区高度（胶囊内） */
val BAR_CONTENT_HEIGHT = 58.dp

/** 悬浮胶囊的外壳高度 */
val BAR_CAPSULE_HEIGHT = 58.dp

/** 胶囊到系统导航条之间留的空隙 */
val BAR_BOTTOM_GAP = 12.dp

/** 胶囊左右边距 */
val BAR_HORIZONTAL_MARGIN = 16.dp

/** 胶囊圆角 */
val BAR_CORNER_RADIUS = 29.dp

/** 指示条尺寸 */
private val INDICATOR_WIDTH = 20.dp
private val INDICATOR_HEIGHT = 3.dp
private val INDICATOR_BOTTOM_GAP = 6.dp

/**
 * 一级页面底部需要让出的高度（胶囊占掉的部分 + 系统导航条）。
 *
 * 胶囊是悬浮的、不占布局，所以这个值由**页面自己**加在内容底部，
 * 而不是由 Scaffold 全局处理 —— 这正是「进入二级页面时内容不跳动」的关键。
 */
val CAPSULE_SPACE = BAR_CAPSULE_HEIGHT + BAR_BOTTOM_GAP + 8.dp

/** 玻璃配色（浅色 / 深色两套，各自私自调参） */
@Composable
private fun glassPalette(): GlassPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        // 深色：玻璃比底色更亮一点，但整体压得很低，避免和文字抢对比
        GlassPalette(
            tint = Color.White.copy(alpha = 0.10f),
            highlight = Color.White.copy(alpha = 0.07f),
            border = Color.White.copy(alpha = 0.20f),
            blurRadius = 16.dp,
        )
    } else {
        // 浅色：玻璃偏白 + 一条明确的细边框（iOS 27 的可读性处理：不靠投影，靠边框）
        GlassPalette(
            tint = Color.White.copy(alpha = 0.55f),
            highlight = Color.White.copy(alpha = 0.50f),
            border = Color.White.copy(alpha = 0.95f),
            blurRadius = 16.dp,
        )
    }
}

/**
 * 悬浮玻璃底栏。
 *
 * 相对 v1.6.0 的变化：
 * · 从 `Scaffold.bottomBar` 挪出来，改成**悬浮在内容之上**的玻璃胶囊
 *   —— 内容要能从它下面穿过去，玻璃才有东西可模糊；
 * · 进入二级页面时整条胶囊**淡出并下沉**，返回时**浮起**
 *   （内容区的底部留白是一级页面自己加的，二级页面没有，所以内容不会跳动）。
 *
 * 保留 v1.6.0 的三项动效：指示条平移、图标缩放、文字淡入淡出。
 */
@Composable
fun AnimatedBottomBar(
    backdrop: GlassBackdrop,
    destinations: List<TopLevelDestination>,
    selectedRoute: String?,
    visible: Boolean,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = destinations.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)

    // 二级页面：整条胶囊淡出并下沉；返回时浮起
    val barAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "barAlpha",
    )
    val barOffset by animateDpAsState(
        targetValue = if (visible) 0.dp else BAR_CAPSULE_HEIGHT + BAR_BOTTOM_GAP,
        animationSpec = tween(durationMillis = 260),
        label = "barOffsetY",
    )

    Box(
        modifier
            .alpha(barAlpha)
            .offset(y = barOffset)
            .navigationBarsPadding()
            .padding(horizontal = BAR_HORIZONTAL_MARGIN)
            .padding(bottom = BAR_BOTTOM_GAP)
    ) {
        GlassSurface(
            backdrop = backdrop,
            shape = RoundedCornerShape(BAR_CORNER_RADIUS),
            palette = glassPalette(),
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_CAPSULE_HEIGHT),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val itemWidth = maxWidth / destinations.size
                val indicatorX by animateDpAsState(
                    targetValue = itemWidth * selectedIndex + (itemWidth - INDICATOR_WIDTH) / 2,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "bottomIndicatorX",
                )

                Box(Modifier.fillMaxSize()) {
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
                                        .size(23.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        },
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = destination.label,
                                    fontSize = 10.sp,
                                    color = tint,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .height(13.dp)
                                        .alpha(labelAlpha),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
