package com.workreport.app.ui.common

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 手写的「液态玻璃」底层能力（零第三方依赖）。
 *
 * ## 它是怎么做到背景模糊的
 *
 * Compose 自带的 `Modifier.blur()` 只能模糊**自己绘制的内容**，
 * 没法模糊「自己背后的东西」—— 这正是玻璃拟态最难的一环。
 *
 * 这里用 Android 12（API 31）起的 `RenderEffect` + Compose 的 `GraphicsLayer` 手动实现：
 *
 * 1. [GlassBackdrop] 持有一个 `GraphicsLayer`；
 * 2. [GlassContentHost] 把**整屏内容**录进这一层（`layer.record { drawContent() }`），
 *    内容滚动时这一层会跟着重绘，所以玻璃里看到的内容始终是「实时」的；
 * 3. [GlassSurface] 在绘制时把这层按**坐标差**平移采样到自己的位置，
 *    再用 `RenderEffect.createBlurEffect` 模糊，就得到「背后内容被磨砂」的效果。
 *
 * ## 为什么拆成「模糊层 + 清晰层」
 *
 * 模糊是通过 `graphicsLayer { renderEffect = ... }` 做的，
 * **它会把这一层里画的所有东西一起模糊**——包括边框和文字。
 * 所以玻璃面被拆成：
 *   · 模糊层：只画采样来的背景 + 半透明色
 *   · 清晰层：边框 + 高光 + 图标 + 文字（不参与模糊）
 *
 * ## 版本要求
 *
 * `RenderEffect` 需要 API 31（Android 12）。本 App 的 minSdk 已经是 31，
 * 所以**不需要任何降级分支**。如果将来要降回 Android 11，
 * 只需把 `blurRadius` 传 0（不挂 renderEffect）即可自动退化成纯半透明。
 */
class GlassBackdrop internal constructor(
    internal val layer: GraphicsLayer,
) {
    /** 内容层在窗口里的位置（用于坐标对齐） */
    var offset: Offset by mutableStateOf(Offset.Zero)
        internal set
}

/** 创建一个玻璃背景层，整 App 共用一个 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * 玻璃的内容宿主：把子内容录制成可被玻璃采样的背景层。
 *
 * 放在整棵内容树的**最外层**，玻璃控件再从中采样。
 */
@Composable
fun GlassContentHost(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { backdrop.offset = it.positionInWindow() }
            .drawWithContent {
                // 把整屏内容录进背景层。
                // 记录用的是**宿主自己的坐标系**（原点 = 宿主左上角），
                // 采样时只要把「宿主原点」和「玻璃原点」的差平移过去就对齐了。
                //
                // 注意：这里**只画录制的层，不再直接画一次内容**，
                // 否则内容会被画两遍（一遍在层里、一遍在屏幕上），白白多一倍绘制开销。
                backdrop.layer.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop.layer)
            }
    ) {
        content()
    }
}

/** 玻璃的配色（浅色 / 深色各一套），可在调用处覆盖 */
data class GlassPalette(
    /** 玻璃本体的半透明色 */
    val tint: Color,
    /** 顶部高光（模拟光从上方打下来） */
    val highlight: Color,
    /** 细边框色 */
    val border: Color,
    /** 模糊半径 */
    val blurRadius: Dp = 14.dp,
)

/**
 * 采样坐标的计算（抽成纯函数，方便单元测试）。
 *
 * ## 推导
 *
 * · 背景层里画的是**宿主坐标系**下的内容：宿主局部坐标 `p` 对应屏幕坐标 `backdrop + p`；
 * · 玻璃自己的局部坐标 `g` 对应屏幕坐标 `glass + g`；
 * · 玻璃局部坐标 `g` 处应该显示的，是它**背后**那个屏幕位置的内容，
 *   即背景层局部坐标 `g + glass - backdrop`；
 * · 而把背景层整体平移 `t` 后绘制，背景层局部坐标 `p` 会落到玻璃局部坐标 `p + t`。
 *   要让 `p + t = g + glass - backdrop`，就得取 **`t = backdrop - glass`**。
 */
object GlassSampling {
    fun offset(
        backdropX: Float,
        backdropY: Float,
        glassX: Float,
        glassY: Float,
    ): Offset = Offset(backdropX - glassX, backdropY - glassY)
}

/**
 * 一块玻璃面。
 *
 * @param backdrop 由 [GlassContentHost] 提供的内容层
 * @param shape    玻璃形状（胶囊、圆角矩形等）
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop,
    shape: Shape,
    palette: GlassPalette,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var selfOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned { selfOffset = it.positionInWindow() }
    ) {
        // ---- 模糊层：采样背景 + 半透明色（整体被模糊）----
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    renderEffect = if (palette.blurRadius > 0.dp) {
                        RenderEffect.createBlurEffect(
                            palette.blurRadius.toPx(),
                            palette.blurRadius.toPx(),
                            Shader.TileMode.CLAMP,
                        ).asComposeRenderEffect()
                    } else {
                        null
                    }
                }
                .drawWithContent {
                    // 按坐标差平移采样背景层，让它落到玻璃自己的位置上
                    val d = GlassSampling.offset(
                        backdropX = backdrop.offset.x,
                        backdropY = backdrop.offset.y,
                        glassX = selfOffset.x,
                        glassY = selfOffset.y,
                    )
                    drawIntoCanvas { canvas ->
                        canvas.save()
                        canvas.translate(d.x, d.y)
                        drawLayer(backdrop.layer)
                        canvas.restore()
                    }
                    // 玻璃本体：一层半透明色（只做模糊会发灰发脏，必须叠色）
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to palette.tint.copy(alpha = palette.tint.alpha * 1.15f),
                            1f to palette.tint.copy(alpha = palette.tint.alpha * 0.85f),
                        )
                    )
                }
        )

        // ---- 清晰层：高光 + 边框 + 内容（不参与模糊）----
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to palette.highlight,
                        1f to Color.Transparent,
                    ),
                    shape,
                )
                .border(1.dp, palette.border, shape)
        ) {
            content()
        }
    }
}
