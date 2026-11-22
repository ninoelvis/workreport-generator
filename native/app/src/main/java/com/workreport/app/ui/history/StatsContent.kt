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
import androidx.compose.ui.unit.TextUnit
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
                        // ⚠️ 一格只有 ~77dp 可用（20sp 的「26年11月」要 85dp），
                        //    所以方块里只放短标签，完整年份在下面的说明文字里给。
                        stats.busiestMonth?.let { monthLabel(it.year, it.month) } ?: "—",
                        Modifier.weight(1f),
                    )
                    StatTile(
                        "最忙的一周",
                        // 「11月3周」@20sp 约 74dp，余量只有 3dp ——
                        //    小屏或系统字号放大就会被截成「11月3…」，所以这格用小一号字。
                        stats.busiestWeek?.let { weekTileLabel(it) } ?: "—",
                        Modifier.weight(1f),
                        valueFontSize = 17.sp,
                    )
                }
                stats.busiestMonth?.let { busiest ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "写得最多的是 ${busiest.label}，" +
                            "一共 ${busiest.itemCount} 条工作记录",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                stats.busiestWeek?.let { busiest ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // ⚠️ 必须用带年份的完整标签：
                        //    「11月第3周」在跨年时看不出是 2025 年还是 2026 年
                        "单周最多的是 ${busiest.fullLabel}，" +
                            "写了 ${busiest.itemCount} 条",
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
                hint = "柱高 = 写了几条",
            ) {
                // 维度切换：按月 / 按周
                UnderlineTabBar(
                    labels = listOf("按月", "按周"),
                    selected = dimension,
                    onSelect = { dimension = it },
                )
                Spacer(Modifier.height(6.dp))
                // ⚠️ 「按周」按**年 + 月 + 周**分组（v2.1.2 修），
                //    标签直接带上月份，不会把十月的第 4 周和十一月的第 4 周混成一根。
                Text(
                    if (dimension == 0) {
                        "按月份看：每个月一共写了几条工作记录。"
                    } else {
                        "按周看：按「年 + 月 + 周」分组，" +
                            "不会把不同月份的同一周混在一起。" +
                            if (stats.weeks.size > RECENT_WEEK_LIMIT) {
                                "只画最近 $RECENT_WEEK_LIMIT 组。"
                            } else {
                                ""
                            }
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stats.hasMultipleYears) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "数据跨了多个年份，标签带年份；" +
                            "下方「每月明细」里是完整年份。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
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
                                    // 完整版带年份（跨年时才显示得出来），短版只有月份
                                    labelTop = if (stats.hasMultipleYears) {
                                        "${it.year % 100}年${it.month}月"
                                    } else {
                                        "${it.month}月"
                                    },
                                    labelTopShort = "${it.month}月",
                                    itemCount = it.itemCount,
                                )
                            },
                        )
                    } else {
                        // ⚠️ 只画最近 RECENT_WEEK_LIMIT 组，
                        //    否则积累几个月后柱子太多、标签挤成一团。
                        val recent = stats.weeks.takeLast(RECENT_WEEK_LIMIT)
                        ProductionBarChart(
                            bars = recent.map {
                                ProductionBar(
                                    // 两行标签：上面月份、下面第几周
                                    // —— 挤在一行会互相叠在一起
                                    labelTop = if (stats.hasMultipleYears) {
                                        "${it.year % 100}年${it.month}月"
                                    } else {
                                        "${it.month}月"
                                    },
                                    labelTopShort = "${it.month}月",
                                    // ⚠️ 用「3周」不用「第3周」：12 组时每格只有 25dp，
                                    //    「第3周」放不下会被整行跳过
                                    labelBottom = it.shortLabel,
                                    itemCount = it.itemCount,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    // ⚠️ 说明文字必须跟实际显示的东西对得上 ——
                    //    「下方每月明细有完整数字」只对按月成立，按周没有那张表。
                    when {
                        dimension == 1 && stats.weeks.size > RECENT_WEEK_LIMIT ->
                            "柱子越高，那段时间写的工作条目越多。" +
                                "分组太多，只画了最近 $RECENT_WEEK_LIMIT 组，" +
                                "更早的月份在下方「每月明细」里（按月合计）。"
                        dimension == 1 ->
                            "柱子越高，那段时间写的工作条目越多。" +
                                "轴标签上面是月份、下面是第几周。"
                        stats.months.size > 6 ->
                            "柱子越高，那段时间写的工作条目越多。" +
                                "月份太多时只标了首尾，下方「每月明细」有完整数字。"
                        else ->
                            "柱子越高，那段时间写的工作条目越多。"
                    },
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

/** 「按周」维度最多画多少组（最近的这些），再多柱子就挤成一团了 */
private const val RECENT_WEEK_LIMIT = 12

/** 单个数字块 */
@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueFontSize: TextUnit = 20.sp,
) {
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
            fontSize = valueFontSize,
            color = MaterialTheme.colorScheme.onSurface,
            // ⚠️ 一格只有 ~77dp 可用，数值一折行就会把整行撑高，
            //    旁边两格是顶对齐，视觉上就不对称了
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 方块里的月份标签 —— 永远用短形式。
 *
 * ⚠️ 方块只有 ~77dp 可用，带年份的「26年11月」要 85dp，会被截断。
 *    跨年的完整信息由下面的说明文字给出。
 */
private fun monthLabel(year: Int, month: Int): String = "${month}月"

/**
 * 「最忙的一周」那一格的值。
 *
 * 一格只有 ~77dp 可用，「11月第3周」放不下会折行，
 * 所以去掉「第」写成「11月3周」。
 */
private fun weekTileLabel(week: WeekStat): String = "${week.month}月${week.weekNum}周"

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
    /** 轴标签第一行（完整版），如「26年11月」 */
    val labelTop: String,
    /**
     * 轴标签第一行（短版），如「11月」。
     *
     * ⚠️ 完整版太长时（跨年才带年份）放不进一格，
     *    就退到短版，至少还能看出是几月。
     */
    val labelTopShort: String = labelTop,
    /** 轴标签第二行（周次），如「3周」；按月视图留空 */
    val labelBottom: String = "",
    /** 工作条目数（柱高） */
    val itemCount: Int,
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

    // ⚠️ 文本测量必须在**组合阶段**做完，不能放进 DrawScope。
    //
    // 坑：DrawScope 里每根柱子每帧要测 3 次文本，24 根柱子就是 72 次/帧；
    // `rememberTextMeasurer` 的默认缓存只有 8 条，会被打穿 → 每帧全部重测，
    // 切「按月 / 按周」时两张图同时存在，帧率明显掉。
    // 现在预先测好并 remember 住，Canvas 里只画不测。
    val countStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = barColor,
    )
    val axisStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    val countLayouts = remember(bars, countStyle) {
        bars.map { measurer.measure(it.itemCount.toString(), countStyle) }
    }
    val labelTopLayouts = remember(bars, axisStyle) {
        bars.map { measurer.measure(it.labelTop, axisStyle) }
    }
    val labelTopShortLayouts = remember(bars, axisStyle) {
        bars.map { measurer.measure(it.labelTopShort, axisStyle) }
    }
    val labelBottomLayouts = remember(bars, axisStyle) {
        bars.map { measurer.measure(it.labelBottom, axisStyle) }
    }


    // 布局常量
    val topSpace = 18.dp      // 柱顶数字
    // 轴标签（两行）：高度按实际文字行高算，字号放大也不会被裁掉
    // （单位是 px —— 在 DrawScope 里用，那里才有 Density）
    val labelLineHeight: Float = labelTopLayouts.maxOfOrNull { it.size.height.toFloat() } ?: 0f
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
        // 轴标签占的高度：上下留白 + 实际用到的行数 × 行高
        //
        // ⚠️ 按月视图只有一行标签（第二行是空的），
        //    恒按两行算会让图表底部多空一截，柱子整体矮一截。
        val hasSecondLine = labelBottomLayouts.any { it.size.width > 0 }
        val labelRows = if (hasSecondLine) 2f else 1f
        val bottomSpace: Float = 4.dp.toPx() + labelLineHeight * labelRows + 4.dp.toPx()
        val baseline = size.height - bottomSpace
        val areaHeight = baseline - areaTop

        // 每根柱子占一格；数量多时自动收窄
        //
        // ⚠️ slot 上限不能写死：
        //    以前固定 26dp(柱宽) + 16dp(间距) = 42dp，判定条件是「标签宽 <= slot - 2dp」，
        //    于是「26年11月」(42.5dp) 在**任何屏幕宽度下**都放不下 ——
        //    跨年时整个 x 轴一个标签都不显示，比不显示年份还糟。
        //    现在 slot 按**实际标签宽度**撑开，画布不够时才收窄。
        val maxSlot = size.width / n
        val widestLabel: Float = maxOf(
            labelTopLayouts.maxOfOrNull { it.size.width.toFloat() } ?: 0f,
            labelBottomLayouts.maxOfOrNull { it.size.width.toFloat() } ?: 0f,
        )
        val minSlot = maxBarWidth.toPx() + maxGap.toPx()
        val wantedSlot = maxOf(minSlot, widestLabel + 2.dp.toPx())
        val slot = minOf(maxSlot, wantedSlot)
        val barWidth = minOf(maxBarWidth.toPx(), slot * 0.56f)
        val totalWidth = slot * n
        val startX = (size.width - totalWidth) / 2f

        // 柱子太窄就不标数字（否则会叠在一起）
        val showCountOnBar = barWidth >= 13.dp.toPx()

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
            //
            // ⚠️ 柱子多的时候（比如 20 多个月的记录），每根柱子只有几个 dp 宽，
            //    数字比柱子还宽就会互相叠在一起糊成一团。
            //    所以**只有柱子够宽时才标数字**，不够就靠柱子高度自己说话。
            val countLayout = countLayouts[index]
            if (showCountOnBar && countLayout.size.width <= barWidth + 2.dp.toPx()) {
                drawText(
                    textLayoutResult = countLayout,
                    topLeft = Offset(
                        x = centerX - countLayout.size.width / 2f,
                        y = (top - countLayout.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                    ),
                )
            }

            // 轴标签：两行（月份 / 第几周）
            //
            // ⚠️ 窄的时候只标首尾，中间留空；
            //    而且每一行都要**按实际文字宽度**判断放不放得下 ——
            //    固定阈值判断不了：「11月第4周」这种长标签 4 根柱子时就会叠在一起。
            val labelVisible = index == 0 || index == n - 1 || slot >= 40.dp.toPx()
            if (labelVisible) {
                // ⚠️ 完整标签（跨年带年份）放不下时，退到短标签，
                //    宁可少一个年份，也不能一个标签都不画。
                val fullTop = labelTopLayouts[index]
                val shortTop = labelTopShortLayouts[index]
                val topLayout = when {
                    fullTop.size.width <= slot - 2.dp.toPx() -> fullTop
                    shortTop.size.width <= slot - 2.dp.toPx() -> shortTop
                    else -> null
                }
                if (topLayout != null) {
                    drawText(
                        textLayoutResult = topLayout,
                        topLeft = Offset(
                            x = (centerX - topLayout.size.width / 2f)
                                .coerceIn(0f, (size.width - topLayout.size.width).coerceAtLeast(0f)),
                            y = baseline + 4.dp.toPx(),
                        ),
                    )
                }
                val bottomLayout = labelBottomLayouts[index]
                if (bottomLayout.size.width > 0 && bottomLayout.size.width <= slot - 2.dp.toPx()) {
                    drawText(
                        textLayoutResult = bottomLayout,
                        topLeft = Offset(
                            x = (centerX - bottomLayout.size.width / 2f)
                                .coerceIn(0f, (size.width - bottomLayout.size.width).coerceAtLeast(0f)),
                            // ⚠️ 底行的 y 要跟着顶行的**实际行高**走，
                            //    写死 17dp 的话，系统字号放大 1.3 倍时两行会压在一起
                            y = baseline + 4.dp.toPx() +
                                (topLayout?.size?.height?.toFloat() ?: labelLineHeight) +
                                1.dp.toPx(),
                        ),
                    )
                }
            }

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
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${m.itemCount} 条",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
