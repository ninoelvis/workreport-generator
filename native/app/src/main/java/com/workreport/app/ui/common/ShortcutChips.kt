package com.workreport.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workreport.app.domain.model.ShortcutTemplate

/**
 * 快捷记录模板选择区。
 *
 * 默认收起，只显示一条「⚡ 快捷记录」标题栏；点开后列出模板，点一下就插入。
 * 模板为空时给一个「去添加」入口，直接跳到「我的」页配置。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutChips(
    templates: List<ShortcutTemplate>,
    onPick: (ShortcutTemplate) -> Unit,
    modifier: Modifier = Modifier,
    /** 跳到「我的 → 快捷记录模板」配置 */
    onManage: () -> Unit,
    /** 提示语，说明点一下会发生什么 */
    hint: String = "",
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "快捷记录",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (templates.isEmpty()) "未设置" else "${templates.size} 条",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (templates.isNotEmpty()) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                if (templates.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "还没有快捷模板，在「我的」页把常写的重复内容存成模板，这里就能一键插入。",
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onManage) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text("去添加", fontSize = 12.sp)
                        }
                    }
                } else {
                    if (hint.isNotEmpty()) {
                        Text(
                            text = hint,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 7.dp),
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        templates.forEach { tpl ->
                            ShortcutChipItem(tpl = tpl, onClick = { onPick(tpl) })
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    TextButton(
                        onClick = onManage,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 4.dp, vertical = 0.dp,
                        ),
                    ) {
                        Text("管理快捷记录模板…", fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

/** 单个快捷模板 chip */
@Composable
private fun ShortcutChipItem(tpl: ShortcutTemplate, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(
            text = tpl.display(28),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
