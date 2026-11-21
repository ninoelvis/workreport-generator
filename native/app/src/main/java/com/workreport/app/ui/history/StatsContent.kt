package com.workreport.app.ui.history

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workreport.app.R
import com.workreport.app.domain.model.MonthStat
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

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp, end = 14.dp, bottom = 20.dp,
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
                stats.busiestMonth?.let { busiest ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "产出最多的是 ${busiest.label}，" +
                            "写了 ${busiest.itemCount} 条工作记录（${busiest.reportCount} 份周报）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "每月工作条目",
                icon = R.drawable.ic_calendar,
                hint = "深色柱 = 报告份数",
            ) {
                MonthBarChart(stats.months)
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
 * 每月工作条目柱状图（Canvas 手绘）。
 *
 * 柱子高度 = 该月的工作条目数；柱子里再叠一条深色段表示报告份数（取条目最多的那个月的份数做满格参考）。
 */
@Composable
private fun MonthBarChart(months: List<MonthStat>) {
    if (months.isEmpty()) return
    val maxItems = max(1, months.maxOf { it.itemCount })
    val maxReports = max(1, months.maxOf { it.reportCount })
    val barColor = MaterialTheme.colorScheme.primary
    val barTrack = MaterialTheme.colorScheme.surfaceVariant
    val reportColor = MaterialTheme.colorScheme.onPrimary

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val count = months.size
            if (count == 0) return@Canvas
            val slot = size.width / count
            val barWidth = (slot * 0.5f).coerceAtMost(28.dp.toPx())
            val gap = (slot - barWidth) / 2f
            val bottom = size.height

            months.forEachIndexed { index, m ->
                val h = if (maxItems == 0) 0f
                else (m.itemCount.toFloat() / maxItems) * (size.height - 4.dp.toPx())
                val left = index * slot + gap
                val top = bottom - h

                // 底槽
                drawRoundRect(
                    color = barTrack,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
                if (h > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                    // 报告份数：叠在柱子底部的一段深色
                    val rh = if (maxReports == 0) 0f
                    else (m.reportCount.toFloat() / maxReports) * h
                    if (rh > 0f) {
                        drawRoundRect(
                            color = reportColor,
                            topLeft = Offset(left, bottom - rh),
                            size = Size(barWidth, rh),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        // 月份刻度（数量多时只标首尾与间隔，避免挤成一团）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            months.forEachIndexed { index, m ->
                val show = months.size <= 6 || index == 0 || index == months.size - 1 || index % 3 == 0
                Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (show) "${m.month}月" else "",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
