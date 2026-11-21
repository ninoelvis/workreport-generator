package com.workreport.app.ui.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workreport.app.R
import com.workreport.app.domain.model.MonthStat
import com.workreport.app.domain.model.ReportItemStat
import com.workreport.app.domain.model.WeekStat
import com.workreport.app.ui.CAPSULE_SPACE
import com.workreport.app.ui.common.Motion
import com.workreport.app.ui.common.UnderlineTabBar
import com.workreport.app.domain.model.ReportStats
import com.workreport.app.ui.common.EmptyState
import com.workreport.app.ui.common.SectionCard
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 周报统计视图（v2.0.0 起）。
 *
 * 图表用 Compose 的 [Canvas] **手绘**，不引第三方图表库 ——
 * 保持自 v1.7.0 起「手写官方 API，零第三方依赖」的既有路线。
 *
 * ⚠️ 统计**只覆盖本 App 生成的报告**（数据库里的记录）。
 * 用户手动拷进存放目录的外部压缩包不在其中，界面上要标出来。
 */
@Composable
fun StatsContent(
    stats: ReportStats,
    modifier: Modifier = Modifier,
) {
    if (stats.totalReports == 0) {
        EmptyState(
            icon = R.drawable.ic_document,
            title = "还没有可统计的报告",
            description = "在「首页」生成周报后，这里会按月份统计产出情况。",
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    // 维度切换：按月 / 按周（v2.0.1 起）
    // 0 = 按月，1 = 按周
    var dimension by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            // ⚠️ 底部必须留出悬浮玻璃胶囊的高度（v2.0.4 修）。
            //    底栏是浮在内容之上的，不占布局位置 —— 内容不主动让开，
            //    最后一段说明文字就会被胶囊压在底下看不见（首页/我的/列表页都留了，
            //    唯独统计页漏了）。
            start = 14.dp, end = 14.dp,
            bottom = 20.dp + CAPSULE_SPACE,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionCard(
                title = "总览",
                icon = R.drawable.ic_document,
                hint = "共 ${stats.monthCount} 个月",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile("报告份数", stats.totalReports.toString(), Modifier.weight(1f))
                    StatTile("工作条目", stats.totalItems.toString(), Modifier.weight(1f))
                    StatTile(
                        "覆盖月份",
                        stats.monthCount.toString(),
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        "平均每份",
                        String.format("%.1f", stats.avgItemsPerReport),
                        Modifier.weight(1f),
                    )
                    StatTile(
                        "最忙的月份",
                        stats.busiestMonth?.let { "${it.month}月" } ?: "—",
                        Modifier.weight(1f),
                    )
                    StatTile(
                        "最忙的一周",
                        stats.weeks.maxByOrNull { it.itemCount }?.label ?: "—",
                        Modifier.weight(1f),
                    )
                }
                stats.busiestMonth?.let { busiest ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "产出最多的是 ${busiest.label}，" +
                            "写了 ${busiest.itemCount} 条工作记录（${busiest.reportCount} 份周报）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                stats.topReport?.let { top ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "写得最细的是「${top.displayName}」，一共 ${top.itemCount} 条",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "产出节奏",
                icon = R.drawable.ic_calendar,
                hint = "柱高 = 工作条目数",
            ) {
                // 维度切换：按月 / 按周
                UnderlineTabBar(
                    labels = listOf("按月", "按周"),
                    selected = dimension,
                    onSelect = { dimension = it },
                )
                Spacer(Modifier.height(10.dp))
                // v2.0.4：按月 / 按周 切换时图表淡入淡出（之前是瞬间替换）
                AnimatedContent(
                    targetState = dimension,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(Motion.duration(Motion.NORMAL))) togetherWith
                            fadeOut(animationSpec = tween(Motion.duration(Motion.FAST)))
                    },
                    label = "statsDimension",
                ) { dim ->
                    if (dim == 0) {
                        ProductionBarChart(
                            bars = stats.months.map {
                                ProductionBar(
                                    label = "${it.month}月",
                                    itemCount = it.itemCount,
                                    reportCount = it.reportCount,
                                )
                            },
                        )
                    } else {
                        ProductionBarChart(
                            bars = stats.weeks.map {
                                ProductionBar(
                                    label = it.label,
                                    itemCount = it.itemCount,
                                    reportCount = it.reportCount,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "柱子越高，那段时间写的工作条目越多；柱子下面的小字是那段时间生成了几份周报。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(
                title = "每份报告的产出",
                icon = R.drawable.ic_clipboard,
                hint = "平均 ${String.format("%.1f", stats.avgItemsPerReport)} 条",
            ) {
                if (stats.topReports.isEmpty()) {
                    Text(
                        "还没有可统计的条目。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    stats.topReports.forEachIndexed { index, r ->
                        if (index > 0) Spacer(Modifier.height(8.dp))
                        TopReportRow(r, index + 1)
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "每月明细",
                icon = R.drawable.ic_clipboard,
            ) {
                MonthStatTable(stats.months)
            }
        }

        item {
            Text(
                "统计只包含本 App 生成的 ${stats.totalReports} 份报告。" +
                    "你自己拷进存放目录的压缩包不在统计范围内。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

/** 单个数字块 */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp),
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 柱状图的一根柱子。
 *
 * v2.0.4 起这个图表**只编码一个指标**（工作条目数 = 柱高）。
 *
 * 之前是"柱高 = 条目数 + 柱底叠一段深色 = 报告份数"的双编码，
 * 结果：
 *   · 深色模式下 `onPrimary` 是**白色**，所以那段"深色"反而变成亮色，
 *     与卡片右上角那句提示正好相反；
 *   · 报告份数占满整根柱子时（该月份数就是最多的那次），
 *     整根柱子会被盖成同一个颜色，看起来像"空白"；
 *   · 两根柱子时又被均分到左右两端，中间大片空白，更看不懂。
 *
 * 现在报告份数**写在柱子下面**，一眼就能读，不用猜颜色。
 */
private data class ProductionBar(
    /** 轴上的短标签，如「11月」「第3周」 */
    val label: String,
    /** 工作条目数（柱高） */
    val itemCount: Int,
    /** 报告份数（写在柱下的数字） */
    val reportCount: Int,
)

/**
 * 产出节奏柱状图（v2.0.4 重做）。
 *
 * 布局：
 * ```
 *        23                 9          ← 柱顶数字 = 工作条目数
 *        ███                █
 *        ███                █
 *   █    ███                █
 *  ───────────────────────────         ← 基线
 *  10月       11月                     ← 轴标签
 *  1份        3份                      ← 报告份数
 * ```
 *
 * 柱子数量少时**居中紧凑排列**（不再均分整个宽度），
 * 数量多时自动收窄间距，保证不越界。
 */
@Composable
private fun ProductionBarChart(bars: List<ProductionBar>) {
    if (bars.isEmpty()) {
        Text(
            "还没有可统计的数据。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val measurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    // 布局常量
    val topSpace = 18.dp      // 柱顶数字
    val bottomSpace = 34.dp   // 轴标签 + 份数
    val maxBarWidth = 26.dp
    val maxGap = 16.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp),
    ) {
        val n = bars.size
        val maxItems = max(1, bars.maxOf { it.itemCount })

        // 基线以上的可用高度
        val areaTop = topSpace.toPx()
        val baseline = size.height - bottomSpace.toPx()
        val areaHeight = baseline - areaTop

        // 每根柱子占一格；数量多时自动收窄
        val maxSlot = size.width / n
        val slot = minOf(maxBarWidth.toPx() + maxGap.toPx(), maxSlot)
        val barWidth = minOf(maxBarWidth.toPx(), slot * 0.56f)
        val totalWidth = slot * n
        val startX = (size.width - totalWidth) / 2f

        // 基线
        drawLine(
            color = baselineColor,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx(),
        )

        bars.forEachIndexed { index, bar ->
            val slotLeft = startX + index * slot
            val left = slotLeft + (slot - barWidth) / 2f
            val centerX = left + barWidth / 2f

            // 柱高
            val h = (bar.itemCount.toFloat() / maxItems) * areaHeight
            val top = baseline - h

            if (h > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }

            // 柱顶数字：工作条目数
            val countLayout = measurer.measure(
                text = bar.itemCount.toString(),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = barColor,
                ),
            )
            drawText(
                textLayoutResult = countLayout,
                topLeft = Offset(
                    x = centerX - countLayout.size.width / 2f,
                    y = (top - countLayout.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                ),
            )

            // 轴标签
            val labelLayout = measurer.measure(
                text = bar.label,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = axisColor,
                ),
            )
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(
                    x = centerX - labelLayout.size.width / 2f,
                    y = baseline + 4.dp.toPx(),
                ),
            )

            // 柱子下面：报告份数
            val reportLayout = measurer.measure(
                text = "${bar.reportCount} 份",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = axisColor,
                ),
            )
            drawText(
                textLayoutResult = reportLayout,
                topLeft = Offset(
                    x = centerX - reportLayout.size.width / 2f,
                    y = baseline + 18.dp.toPx(),
                ),
            )
        }
    }
}

/**
 * 单份报告产出的一行（v2.0.1 起）。
 *
 * 左边一个名次，右边报告名 + 条数。
 */
@Composable
private fun TopReportRow(r: ReportItemStat, rank: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    if (rank == 1) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                rank.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (rank == 1) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.displayName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                r.period,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${r.itemCount} 条",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 每月明细表 */
@Composable
private fun MonthStatTable(months: List<MonthStat>) {
    Column {
        months.forEachIndexed { index, m ->
            if (index > 0) {
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    m.label,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(84.dp),
                )
                Text(
                    "${m.reportCount} 份",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    "${m.itemCount} 条",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
