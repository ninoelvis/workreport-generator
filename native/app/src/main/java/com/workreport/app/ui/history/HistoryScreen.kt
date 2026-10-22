package com.workreport.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workreport.app.data.local.ReportEntity
import com.workreport.app.ui.common.EmptyState
import com.workreport.app.util.MailHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferredMail by viewModel.preferredMailPackage.collectAsStateWithLifecycle()
    var mailPickerFor by remember { mutableStateOf<ReportEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ReportEntity?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶部
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(top = 10.dp, bottom = 8.dp),
        ) {
            Text(
                "历史报告",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "共 ${state.totalCount} 份 · 支持按文件名搜索",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索文件名…", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.reports.isEmpty()) {
            EmptyState(
                icon = if (state.keyword.isBlank()) "📭" else "🔍",
                title = if (state.keyword.isBlank()) "还没有生成过报告" else "没有匹配的报告",
                description = if (state.keyword.isBlank()) {
                    "在「首页」填写并生成后，这里会列出全部历史报告，可再次下载或发送邮件。"
                } else {
                    "换个关键词试试，搜索是按文件名匹配的。"
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp, end = 14.dp, bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.reports, key = { it.id }) { report ->
                    ReportCard(
                        report = report,
                        onDownload = {
                            viewModel.downloadAgain(report) { ok ->
                                toast = if (ok) "已再次保存到「下载」目录" else "保存失败，请检查存储权限"
                            }
                        },
                        onMail = {
                            if (preferredMail.isNotEmpty() &&
                                viewModel.fileOf(report).exists()
                            ) {
                                viewModel.sendAgain(report) { ok ->
                                    toast = if (ok) {
                                        "已拉起邮件应用。若附件没带上，可在「我的」页切换发送方式"
                                    } else {
                                        "没有找到可用的邮件应用"
                                    }
                                }
                            } else {
                                mailPickerFor = report
                            }
                        },
                        onDelete = { deleteTarget = report },
                    )
                }
            }
        }
    }

    // 邮件应用选择
    mailPickerFor?.let { report ->
        MailAppPickerDialog(
            onDismiss = { mailPickerFor = null },
            onPick = { pkg ->
                viewModel.rememberMailPackage(pkg)
                mailPickerFor = null
                viewModel.sendAgain(report) { ok ->
                    toast = if (ok) "已拉起邮件应用" else "拉起失败"
                }
            },
        )
    }

    // 删除确认
    deleteTarget?.let { report ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这份报告？") },
            text = { Text("「${report.fileName}」将从本机删除，删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(report) {
                        deleteTarget = null
                        toast = "已删除"
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    toast?.let { text ->
        AlertDialog(
            onDismissRequest = { toast = null },
            title = { Text("提示") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("好的") } },
        )
    }
}

@Composable
private fun ReportCard(
    report: ReportEntity,
    onDownload: () -> Unit,
    onMail: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                report.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                MetaChip("🗓", formatTime(report.createdAt))
                Spacer(Modifier.width(8.dp))
                MetaChip("🔑", report.password)
                Spacer(Modifier.width(8.dp))
                MetaChip("📦", formatSize(report.sizeBytes))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(Icons.Filled.Download, "再次下载", Modifier.weight(1f), onDownload)
                SmallAction(Icons.Filled.Mail, "发送邮件", Modifier.weight(1f), onMail)
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaChip(icon: String, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 10.sp)
        Spacer(Modifier.width(3.dp))
        Text(
            text,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

/** 邮件应用选择弹窗（只列邮箱类应用） */
@Composable
private fun MailAppPickerDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apps = remember { MailHelper.resolveMailApps(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择发送邮件的应用") },
        text = {
            if (apps.isEmpty()) {
                Text("本机没有找到邮箱类应用，请先安装一个邮件客户端。")
            } else {
                Column {
                    apps.forEach { app ->
                        TextButton(
                            onClick = { onPick(app.packageName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                app.label,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}
