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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import com.workreport.app.ui.common.PreviewTable
import com.workreport.app.util.MailHelper
import com.workreport.app.util.ShareHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    /** 载回首页后切回「首页」Tab */
    onGoHome: () -> Unit = {},

    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferredMail by viewModel.preferredMailPackage.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    var mailPickerFor by remember { mutableStateOf<ReportEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ReportEntity?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    /** 当前展开预览的历史报告（同一时刻只展开一张） */
    var previewId by remember { mutableStateOf<Long?>(null) }
    /** 待载入首页的历史报告（等用户二次确认） */
    var loadTarget by remember { mutableStateOf<ReportEntity?>(null) }
    /** 待取回「下周计划」的历史报告（等用户二次确认） */
    var planTarget by remember { mutableStateOf<ReportEntity?>(null) }
    /** 载入过程中的报告（用于置灰按钮、防止重复点） */
    var loadingId by remember { mutableStateOf<Long?>(null) }

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
                        previewExpanded = previewId == report.id,
                        previewState = previews[report.id] ?: PreviewState(),
                        onTogglePreview = {
                            val expanding = previewId != report.id
                            previewId = if (expanding) report.id else null
                            if (expanding) viewModel.loadPreview(report)
                        },
                        canLoadIntoHome = previews[report.id]?.content != null,
                        loadingIntoHome = loadingId == report.id,
                        onLoadIntoHome = { loadTarget = report },
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
                                        "已拉起邮件应用，主题和附件都带上了"
                                    } else {
                                        "没有找到可用的邮件应用"
                                    }
                                }
                            } else {
                                mailPickerFor = report
                            }
                        },
                        onDelete = { deleteTarget = report },
                        onTakePlan = { planTarget = report },
                        onShare = {
                            val file = viewModel.fileOf(report)
                            if (!file.exists()) {
                                toast = "文件已被系统清理，无法分享"
                            } else {
                                val ok = ShareHelper.shareFile(context, file)
                                if (!ok) toast = "未能拉起系统分享"
                            }
                        },
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

    // 取回「下周计划」的二次确认
    planTarget?.let { report ->
        val lines = report.planWorks.split("\n").filter { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = { planTarget = null },
            title = { Text("取回这份报告的「下周工作计划」？") },
            text = {
                Text(
                    "这份报告是 ${report.year}年${report.month}月第${report.weekNum}周的，" +
                        "它的「下周工作计划」就是这一周该做的事。\n\n" +
                        "取回后会填进首页的「本周已完成工作梳理」，共 ${lines.size} 条。" +
                        "这只是当作起点，填完你可以随意修改或删除。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    planTarget = null
                    viewModel.takePlanIntoHome(report) { ok ->
                        toast = if (ok) "已填入首页「本周已完成工作梳理」" else "取回失败"
                    }
                    onGoHome()
                }) { Text("取回") }
            },
            dismissButton = {
                TextButton(onClick = { planTarget = null }) { Text("再想想") }
            },
        )
    }

    // 载入首页的二次确认
    loadTarget?.let { report ->
        AlertDialog(
            onDismissRequest = { loadTarget = null },
            title = { Text("把这份报告载入首页编辑？") },
            text = {
                Text(
                    "首页现在填写的内容会被这份报告替换。\n\n" +
                        "载入后可以随便改，改完重新生成会新增一条记录，原来的记录不受影响。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    loadTarget = null
                    loadingId = report.id
                    viewModel.loadIntoHome(report) { ok ->
                        loadingId = null
                        if (ok) {
                            toast = "已载入，去首页改完重新生成就行"
                            onGoHome()
                        } else {
                            toast = "读不出这份报告的内容，载入失败"
                        }
                    }
                }) { Text("载入并去首页") }
            },
            dismissButton = { TextButton(onClick = { loadTarget = null }) { Text("再想想") } },
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
    previewExpanded: Boolean,
    previewState: PreviewState,
    onTogglePreview: () -> Unit,
    /** 内容已读到，可以载入首页 */
    canLoadIntoHome: Boolean,
    loadingIntoHome: Boolean,
    onLoadIntoHome: () -> Unit,
    onDownload: () -> Unit,
    onMail: () -> Unit,
    onDelete: () -> Unit,
    /** 把这份报告的「下周工作计划」取回当新一周的起点 */
    onTakePlan: () -> Unit,
    /** 拉起系统分享面板 */
    onShare: () -> Unit,
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

            // 内容摘要：直接在卡片上就能瞄一眼，不用点进去
            val summary = report.doneWorks.ifBlank { report.contentPreview }
            if (summary.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "本周完成",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        summary.replace("\n", " · "),
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(
                    icon = if (previewExpanded) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    label = if (previewExpanded) "收起内容" else "查看内容",
                    modifier = Modifier.weight(1f),
                    onClick = onTogglePreview,
                )
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

            // 第二行：把这份报告的「下周计划」取回当新一周的起点；系统分享
            if (report.planWorks.isNotBlank() || !report.filePath.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (report.planWorks.isNotBlank()) {
                        SmallAction(
                            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                            label = "取回下周计划",
                            modifier = Modifier.weight(1f),
                            onClick = onTakePlan,
                        )
                    }
                    SmallAction(
                        icon = Icons.Filled.Share,
                        label = "分享",
                        modifier = Modifier.weight(1f),
                        onClick = onShare,
                    )
                }
            }

            // 展开：直接看到压缩包里那份 Excel 的样子
            if (previewExpanded) {
                Spacer(Modifier.height(12.dp))
                when {
                    previewState.loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "正在解压压缩包、读取里面的 Excel…",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    previewState.content != null -> {
                        val content = previewState.content
                        PreviewTable(
                            title = content.title.ifBlank { report.displayName },
                            timeRange = content.timeRange,
                            doneWorks = content.doneWorks,
                            planWorks = content.planWorks,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "解压密码：${report.password}（压缩包里的 Excel 就是上面这份）",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onLoadIntoHome,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            enabled = canLoadIntoHome && !loadingIntoHome,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            if (loadingIntoHome) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("正在载入…", fontSize = 12.5.sp)
                            } else {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("载入首页继续编辑", fontSize = 12.5.sp)
                            }
                        }
                    }

                    else -> {
                        // 解不开时的兜底：至少把摘要显示出来
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                        ) {
                            Text(
                                previewState.error ?: "这份报告没法预览完整内容。",
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val fallback = report.contentPreview.ifBlank { report.doneWorks }
                            if (fallback.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "以下为当时保存的内容摘要：",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    fallback,
                                    fontSize = 11.5.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
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
