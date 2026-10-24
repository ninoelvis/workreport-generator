package com.workreport.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workreport.app.util.MaskAnchor
import com.workreport.app.util.MaskHit
import com.workreport.app.util.MaskSection
import com.workreport.app.util.MaskSummary

/**
 * 脱敏预览面板。
 *
 * 解决两个真实痛点：
 *  1. **不知道哪些词被换了** —— 替换过程原本完全静默；
 *  2. **过度脱敏** —— 像「查询」「措施」这种词在技术岗的周报里是普通词，
 *     被换成「cx」「cs」交上去很可能被追问。
 *
 * v1.4.0 起粒度细到**每一次出现**：同一句话里出现两次「查询」时会列成两行，
 * 用户可以只放行其中一处，另一处照常脱敏。
 * 每行都带上下文，用户能一眼认出是哪一处。
 *
 * 豁免只影响本次生成，不动敏感词库。
 */
@Composable
fun MaskPanel(
    summary: MaskSummary,
    excluded: Set<MaskAnchor>,
    onToggleHit: (MaskAnchor) -> Unit,
    onExcludeAll: () -> Unit,
    onRestoreAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (summary.isEmpty && excluded.isEmpty()) return

    val allExcluded = summary.hits.isNotEmpty() &&
        summary.hits.all { it.anchor in excluded }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (summary.isEmpty) {
                    "本次未做脱敏"
                } else {
                    "本次脱敏 ${summary.maskedCount} 处" +
                        if (summary.excludedCount > 0) "，保留原文 ${summary.excludedCount} 处" else ""
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (excluded.isNotEmpty()) {
                TextButton(onClick = onRestoreAll) {
                    Text("恢复脱敏", fontSize = 12.sp)
                }
            } else if (summary.hits.isNotEmpty()) {
                TextButton(onClick = onExcludeAll) {
                    Text("全部还原", fontSize = 12.sp)
                }
            }
        }

        if (summary.isEmpty) {
            Text(
                "内容已按原文输出，未做任何替换。",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            if (allExcluded) {
                "下面每一处本次都保持原文。点一下可以恢复脱敏。"
            } else {
                "下面每一处都会被替换。点一下可以让这一处本次保持原文。"
            },
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        summary.hits.forEachIndexed { index, hit ->
            MaskHitRow(
                index = index + 1,
                hit = hit,
                isExcluded = hit.anchor in excluded,
                onToggle = { onToggleHit(hit.anchor) },
            )
        }

        Text(
            "这里只影响这一次生成。想永久删掉某个词，去「我的 → 敏感词」。",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 单条命中：序号 + 所属区 + 上下文（命中词高亮）+ 词与替换 + 放行开关。
 */
@Composable
private fun MaskHitRow(
    index: Int,
    hit: MaskHit,
    isExcluded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onToggle() }
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 $index 处",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (hit.anchor.section == MaskSection.DONE) "本周已完成" else "下周计划",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hit.anchor.nth > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "同上下文第 ${hit.anchor.nth + 1} 个",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = contextText(hit),
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hit.word,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "→",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (isExcluded) "本次保留原文" else hit.replacement,
                    fontSize = 12.5.sp,
                    fontWeight = if (isExcluded) FontWeight.Medium else FontWeight.Normal,
                    color = if (isExcluded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        // 用 Material3 自带的开关：颜色、圆角、动画都跟 App 整体统一，
        // 不再是手搓的方块滑块（深色模式下那个白点很跳）。
        Switch(
            checked = isExcluded,
            onCheckedChange = { onToggle() },
            modifier = Modifier
                .scale(0.78f)
                .semantics {
                    contentDescription = if (isExcluded) {
                        "第 $index 处本次保留原文，点一下恢复脱敏"
                    } else {
                        "第 $index 处本次会被脱敏，点一下保留原文"
                    }
                },
        )
    }
}

/**
 * 「上一份报告留下 N 条计划」的提示条。
 *
 * 放在首页顶部，写新一周周报时一眼就能看到，不用翻历史页。
 * 只是**提示**，点一下才会填入，填完可以随便改。
 */
@Composable
fun CarryOverBanner(
    sourceLabel: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            "$sourceLabel 留下的下周计划还在",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "填进「本周已完成工作梳理」当起点，填完可以随意改。",
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onApply) {
                Text("填入本周已完成", fontSize = 12.5.sp)
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onDismiss) {
                Text("忽略", fontSize = 12.5.sp)
            }
        }
    }
}

/**
 * 把上下文拼成一段带高亮的文字：命中词用主色加粗，前后的原文用常规色。
 */
@Composable
private fun contextText(hit: MaskHit): AnnotatedString {
    val before = hit.anchor.before
    val after = hit.anchor.after
    val highlight = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(before)
        withStyleCompat(highlight) {
            append(hit.word)
        }
        append(after)
    }
}

/**
 * [buildAnnotatedString] 里的 withStyle 需要 SpanStyle，这里包一层方便切换颜色。
 */
private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleCompat(
    color: Color,
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold))
    block()
    pop()
}
