package com.workreport.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.workreport.app.ui.common.GlassBackdrop
import com.workreport.app.ui.common.GlassPalette
import com.workreport.app.ui.common.GlassSurface

/** 悬浮胶囊的内部高度（只放图标，比带文字时矮一点） */
val BAR_CONTENT_HEIGHT = 56.dp

/** 悬浮胶囊的外壳高度 */
val BAR_CAPSULE_HEIGHT = 56.dp

/** 胶囊到系统导航条之间留的空隙 */
val BAR_BOTTOM_GAP = 12.dp

/** 胶囊离屏幕右边缘的距离（v1.8.0 起整体靠右） */
val BAR_END_MARGIN = 16.dp

/** 胶囊圆角 */
val BAR_CORNER_RADIUS = 28.dp

/**
 * 单个图标按钮的宽度。
 *
 * v1.8.0：底栏从「通栏」缩成窄胶囊。
 * 原来通栏宽度 = 屏宽 − 2×16dp（约 370dp），现在 3×60 = 180dp，
 * 正好是原来的一半左右；60dp 的点击区也满足 48dp 的最小可点击尺寸。
 */
private val BAR_ITEM_WIDTH = 60.dp

/** 选中态高亮圆的直径 */
private val SELECTED_DOT_SIZE = 40.dp

/** 指示条尺寸 */
private val INDICATOR_WIDTH = 16.dp
private val INDICATOR_HEIGHT = 3.dp
private val INDICATOR_BOTTOM_GAP = 5.dp

/**
 * 一级页面底部需要让出的高度（胶囊占掉的部分 + 系统导航条）。
 *
 * 胶囊是悬浮的、不占布局，所以这个值由**页面自己**加在内容底部，
 * 而不是由 Scaffold 全局处理 —— 这正是「进入二级页面时内容不跳动」的关键。
 */
val CAPSULE_SPACE = BAR_CAPSULE_HEIGHT + BAR_BOTTOM_GAP + 8.dp

/**
 * 玻璃配色（浅色 / 深色两套，各自私自调参）。
 *
 * v1.8.0：用户要求「玻璃效果再加大一些，能透过导航栏看见下方内容」，
 * 所以两个方向都调了：
 *  · **模糊更强**：blurRadius 从 16dp 提到 26dp，下方内容的轮廓被磨得更开；
 *  · **遮盖更少**：tint / highlight 的 alpha 明显降低，玻璃不再是「一块灰板」，
 *    而是能真正看见底下的文字和卡片。
 */
@Composable
private fun glassPalette(): GlassPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        // 深色：玻璃整体压得更低，几乎只留高光和边框，让下方内容透上来
        GlassPalette(
            tint = Color.White.copy(alpha = 0.055f),
            highlight = Color.White.copy(alpha = 0.05f),
            border = Color.White.copy(alpha = 0.22f),
            blurRadius = 26.dp,
        )
    } else {
        // 浅色：保留一点白雾感（不然图标在白底上看不清），但比原来薄得多
        GlassPalette(
            tint = Color.White.copy(alpha = 0.38f),
            highlight = Color.White.copy(alpha = 0.34f),
            border = Color.White.copy(alpha = 0.92f),
            blurRadius = 26.dp,
        )
    }
}

/**
 * 悬浮玻璃底栏（v1.8.0：窄胶囊 + 只留图标 + 靠右对齐 + 点击动效）。
 *
 * 相对 v1.7.0 的变化：
 * · 从**通栏**改成**窄胶囊**：宽度约为原来的一半，整体靠右对齐；
 * · **去掉文字**，只留三个图标（无障碍标签仍保留在 contentDescription 里）；
 * · 点击切换时加了**按下回弹**动效：按下瞬间图标缩小、松手弹回，
 *   选中态的圆点高亮也跟着平滑移动。
 *
 * 保留 v1.6.0 的三项动效：指示条平移、图标缩放、选中高亮移动。
 *
 * 底栏整体仍然**悬浮在内容之上、不占布局位置**，
 * 进入二级页面时淡出下沉、返回时浮起，内容区高度全程不变。
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
            .padding(end = BAR_END_MARGIN)
            .padding(bottom = BAR_BOTTOM_GAP)
    ) {
        GlassSurface(
            backdrop = backdrop,
            shape = RoundedCornerShape(BAR_CORNER_RADIUS),
            palette = glassPalette(),
            modifier = Modifier
                .width(BAR_ITEM_WIDTH * destinations.size)
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
                    // 选中态圆点高亮：跟着选中项平滑移动
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = itemWidth * selectedIndex + (itemWidth - SELECTED_DOT_SIZE) / 2,
                                y = (BAR_CONTENT_HEIGHT - SELECTED_DOT_SIZE) / 2,
                            )
                            .size(SELECTED_DOT_SIZE)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(percent = 50),
                            )
                    )

                    // 指示条：随选中项平滑平移
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
                            .height(BAR_CONTENT_HEIGHT),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        destinations.forEach { destination ->
                            val selected = destination.route == selectedRoute
                            val interactionSource = remember { MutableInteractionSource() }
                            // 点击反馈：按下时图标先缩小一点，松手再弹回
                            val pressed by interactionSource.collectIsPressedAsState()
                            val pressScale by animateFloatAsState(
                                targetValue = if (pressed) 0.82f else 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.55f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                label = "pressScale",
                            )
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.16f else 0.92f,
                                animationSpec = spring(
                                    dampingRatio = 0.6f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                label = "iconScale",
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

                            Box(
                                modifier = Modifier
                                    .size(BAR_ITEM_WIDTH, BAR_CONTENT_HEIGHT)
                                    .selectable(
                                        selected = selected,
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = { onSelect(destination) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(destination.icon),
                                    // 文字虽然不显示了，但无障碍朗读仍需要这个标签
                                    contentDescription = destination.label,
                                    tint = tint,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale * pressScale
                                            scaleY = iconScale * pressScale
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
