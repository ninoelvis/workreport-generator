package com.workreport.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.workreport.app.ui.theme.LocalPreviewColors
import kotlinx.coroutines.launch

/**
 * 周报预览的**全屏放大**视图。
 *
 * 解决首页预览区「看不全」的问题：
 *  - 预览区里每个单元格固定 132.dp 高，长的工作梳理会被裁掉；
 *  - 这里改成**高度随内容增长**，一行都不裁；
 *  - 表格宽度给到 [FULL_TABLE_WIDTH]，竖屏也能左右滑动看每一列；
 *  - 双指捏合可以整体缩放（1x ~ 4x），双击在 1x / 2x 之间切换。
 *
 * ⚠️ 这里只是把 Excel「照原样画大一点」，**不影响导出的 xlsx 内容**。
 *    表格用的是和首页预览区同一份 [PreviewTable]，样式完全一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenPreview(
    title: String,
    timeRange: String,
    doneWorks: String,
    planWorks: String,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        val hScroll = rememberScrollState()
        val vScroll = rememberScrollState()
        val scope = rememberCoroutineScope()

        val transformState = rememberTransformableState { zoomChange, _, _ ->
            scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // ---------- 顶部条 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title.ifEmpty { "周报预览" },
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        "放大预览 · 和导出的 Excel 一致",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 还原（重置缩放 + 回到左上角）
                IconButton(onClick = {
                    scale = 1f
                    scope.launch {
                        hScroll.animateScrollTo(0)
                        vScroll.animateScrollTo(0)
                    }
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "还原")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            // ---------- 表格 ----------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        // 缩放以左上角为原点，放大后从左上开始看
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .transformable(state = transformState)
                        .combinedClickable(
                            onClick = {},
                            onDoubleClick = {
                                scale = if (scale > 1.5f) MIN_SCALE else 2f
                            },
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .width(FULL_TABLE_WIDTH)
                            .horizontalScroll(hScroll)
                            .verticalScroll(vScroll),
                    ) {
                        PreviewTable(
                            title = title,
                            timeRange = timeRange,
                            doneWorks = doneWorks,
                            planWorks = planWorks,
                            // 高度随内容增长 —— 长文本一行都不裁
                            cellHeight = null,
                            // 字号放大一点，手机上更易读
                            textScale = 1.25f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ---------- 底部提示 ----------
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "双指捏合缩放 · 双击放大/还原 · 左右滑动看全部列",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(20),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            "${(scale * 100).toInt()}%",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 4f

/**
 * 全屏下表格的宽度。
 *
 * 手机竖屏放不下 6 列 Excel 的真实比例，所以给一个「横向可滑动」的宽度，
 * 用户左右滑动就能看完每一列 —— 内容一个字都不少。
 */
private val FULL_TABLE_WIDTH: Dp = 620.dp

/** 供预览卡片上的放大按钮使用 */
@Composable
fun previewAccentColor(): androidx.compose.ui.graphics.Color =
    LocalPreviewColors.current.border
