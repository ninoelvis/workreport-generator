package com.workreport.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workreport.app.R
import com.workreport.app.data.local.ReportEntity
import com.workreport.app.ui.CAPSULE_SPACE
import com.workreport.app.ui.BULK_BAR_SPACE
import com.workreport.app.ui.common.UnderlineTabBar
import com.workreport.app.ui.common.EmptyState
import androidx.compose.ui.platform.LocalContext
import com.workreport.app.ui.common.FullscreenPreview
import com.workreport.app.ui.common.PreviewTable
import com.workreport.app.ServiceLocator
import com.workreport.app.util.MailHelper
import com.workreport.app.util.ShareHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect

/**
 * 全屏预览要展示的内容。
 *
 * 首页预览直接来自输入框，历史页预览来自压缩包里的 Excel，
 * 这里统一成一份，好让两处都能用同一个 [FullscreenPreview]。
 */
private data class PreviewContent(
    val title: String,
    val timeRange: String,
    val doneWorks: String,
    val planWorks: String,
)

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
    // 用户存放目录里的外部压缩包（自己拷进去的）
    val externalReports by viewModel.externalReports.collectAsStateWithLifecycle()

    // 全屏预览（点「放大查看」）
    var fullscreenContent by remember { mutableStateOf<PreviewContent?>(null) }
    val externalPreviews by viewModel.externalPreviews.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    // 多选模式：真相源在 ServiceLocator（与 AppRoot 底栏读的是同一份）
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    // 用户有没有选过自定义存放目录（选了才能识别外部文件）
    val storageCustomized by remember {
        ServiceLocator.storageLocation.customized
    }.collectAsStateWithLifecycle()
    var mailPickerFor by remember { mutableStateOf<ReportEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ReportEntity?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    /** 当前展开预览的历史报告（同一时刻只展开一张） */
    var previewId by remember { mutableStateOf<Long?>(null) }
    /** 正在展开的外部压缩包 */
    var expandedExternal by remember { mutableStateOf<String?>(null) }
    /** 需要手输密码的外部压缩包 */
    var passwordTarget by remember { mutableStateOf<ExternalReport?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    /** 待载入首页的历史报告（等用户二次确认） */
    var loadTarget by remember { mutableStateOf<ReportEntity?>(null) }

    // 进页面就扫一遍存放目录，把用户自己拷进去的压缩包找出来
    LaunchedEffect(Unit) {
        viewModel.refreshExternal()
    }
    /** 待取回「下周计划」的历史报告（等用户二次确认） */
    var planTarget by remember { mutableStateOf<ReportEntity?>(null) }
    /** 载入过程中的报告（用于置灰按钮、防止重复点） */
    var loadingId by remember { mutableStateOf<Long?>(null) }

    // 顶部两个视图的切换：0 = 列表，1 = 统计（v2.0.0 起）
    var tab by remember { mutableStateOf(0) }

    // v2.0.1：多选模式下按返回手势（边缘滑动返回）**只退出多选**，不离开这个页面。
    //
    // 坑（v2.0.0 修）：之前多选状态只有「取消」按钮和批量操作完成会清，
    // 系统返回手势完全不走这条路 → 全局标志卡在 true → **底部导航栏永久消失**。
    //
    // 符合 Android 惯例：多选是页面内的临时状态，返回 = 取消选择。
    BackHandler(enabled = selectionMode) {
        viewModel.exitSelection()
    }

    // 当前选中的报告实体（按 id 取，顺序跟列表一致）
    val selectedEntities = remember(state.reports, state.selectedIds) {
        state.reports.filter { state.selectedIds.contains(it.id) }
    }

    // 最外层用 Box：批量操作条要**浮**在内容之上（v2.0.0 起）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    Column(
        modifier = Modifier.fillMaxSize(),
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
                "共 ${state.totalCount} 份 · 按文件名与内容搜索",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            // 列表 / 统计 两个视图的切换（v2.0.0 起）
            UnderlineTabBar(
                labels = listOf("列表", "统计"),
                selected = tab,
                onSelect = { tab = it },
            )

            // 搜索框只在「列表」视图里出现
            if (tab == 0) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "搜索文件名或内容…",
                        fontSize = 13.sp,
                    )
                },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                // 16sp：字号低于 16sp 时，部分 ROM 的输入法会强制放大整页，
                // 结果是输入框被放大裁掉，看不见自己打的字
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
            }

        }

        // 统计视图（v2.0.0 起）
        if (tab == 1) {
            StatsContent(
                stats = stats,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
            )
            return@Column
        }

        // ⚠️ 列表**总是**渲染：数据库里一条记录都没有时，
        // 用户自己拷进存放目录的压缩包也要能列出来。
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                // 底部留出悬浮玻璃胶囊的高度；
                // 多选模式下胶囊淡出，改留批量操作条的位置。
                start = 14.dp, end = 14.dp,
                bottom = 20.dp + if (selectionMode) BULK_BAR_SPACE else CAPSULE_SPACE,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.reports.isEmpty()) {
                item {
                    EmptyState(
                        icon = if (state.keyword.isBlank()) R.drawable.ic_document else R.drawable.ic_search,
                        title = if (state.keyword.isBlank()) "还没有生成过报告" else "没有匹配的报告",
                        description = if (state.keyword.isBlank()) {
                            "在「首页」填写并生成后，这里会列出全部历史报告，可再次下载或发送邮件。"
                        } else {
                            "换个关键词试试，搜索会匹配文件名与周报正文。"
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(state.reports, key = { it.id }) { report ->
                    ReportCard(
                        report = report,
                        selectionMode = selectionMode,
                        selected = state.selectedIds.contains(report.id),
                        onLongClick = { viewModel.enterSelection(report.id) },
                        onToggleSelect = { viewModel.toggleSelection(report.id) },
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
                        onFullscreen = { fullscreenContent = it },
                    )
                }
            }   // ← 闭合 else 分支（数据库有记录时的列表）

            // ⚠️ 下面两段**必须在 LazyColumn 层**，不能放进 else 里 ——
            //    否则数据库里一条记录都没有时，外部压缩包就列不出来了。

            // 没选自定义目录时，提醒一下外部文件识别需要先选目录
            if (externalReports.isEmpty() && !storageCustomized) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bulb),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "在「我的 → 周报存放目录」里选一个自己的文件夹，" +
                                    "你手动拷进去的压缩包也会在这里列出来。",
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

            // 用户自己拷进存放目录的压缩包
            if (externalReports.isNotEmpty()) {
                    item {
                        Text(
                            "存放目录里的其它压缩包（${externalReports.size} 个）",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    items(externalReports, key = { it.uri.toString() }) { item ->
                        ExternalReportCard(
                            item = item,
                            previewExpanded = expandedExternal == item.uri.toString(),
                            previewState = externalPreviews[item.uri.toString()] ?: PreviewState(),
                            onTogglePreview = {
                                val key = item.uri.toString()
                                val expanding = expandedExternal != key
                                expandedExternal = if (expanding) key else null
                                if (expanding) {
                                    viewModel.loadExternalPreview(item)
                                }
                            },
                            onNeedPassword = {
                                passwordTarget = item
                                passwordInput = ""
                            },
                            onShare = {
                                val file = viewModel.externalCacheFile(item)
                                if (file == null) {
                                    toast = "读不出这个文件"
                                } else {
                                    val ok = ShareHelper.shareFile(context, file)
                                    if (!ok) toast = "未能拉起系统分享"
                                }
                            },
                            onFullscreen = { fullscreenContent = it },
                        )
                    }
                }
            }
        }

    // 全屏放大预览
    fullscreenContent?.let { c ->
        FullscreenPreview(
            title = c.title,
            timeRange = c.timeRange,
            doneWorks = c.doneWorks,
            planWorks = c.planWorks,
            onClose = { fullscreenContent = null },
        )
    }

    // 外部压缩包：手输解压密码
    passwordTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { passwordTarget = null },
            title = { Text("输入解压密码", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        target.fileName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        singleLine = true,
                        label = { Text("解压密码") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pwd = passwordInput.trim()
                    passwordTarget = null
                    if (pwd.isNotEmpty()) {
                        viewModel.loadExternalPreview(target, pwd)
                    }
                }) { Text("打开") }
            },
            dismissButton = {
                TextButton(onClick = { passwordTarget = null }) { Text("取消") }
            },
        )
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
                        "取回后会放到首页的「工作记录」里，共 ${lines.size} 条，一条一条分开。\n" +
                        "你可以在那里补上这周新增的内容、调顺序，点「汇总」才进「本周已完成工作梳理」。\n\n" +
                        "⚠️ 首页现在的「工作记录」和「周报内容」都会被清空。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    planTarget = null
                    viewModel.takePlanIntoHome(report) { ok ->
                        if (ok) {
                            // 跳转放在回调里：确保计划已经交给首页了再切页面
                            onGoHome()
                        } else {
                            toast = "取回失败"
                        }
                    }
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

        // 批量操作条（v2.0.0 起）
        // 多选时底部让位给它，玻璃胶囊同时淡出（AppRoot 监听 selectionMode）。
        //
        // 动效：从下方滑入 + 淡入，与底栏胶囊的淡出下沉**反向呼应** ——
        // 一个退场一个进场，视觉上是「交接」而不是「打架」。
        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn(tween(durationMillis = 200)) +
                slideInVertically(tween(durationMillis = 260)) { it / 2 },
            exit = fadeOut(tween(durationMillis = 140)) +
                slideOutVertically(tween(durationMillis = 180)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BulkActionBar(
                selectedCount = state.selectedIds.size,
                totalCount = state.reports.size,
                onCancel = { viewModel.exitSelection() },
                onToggleSelectAll = { viewModel.toggleSelectAll(state.reports.map { it.id }) },
                onDelete = { viewModel.deleteSelected(selectedEntities) { n -> toast = "已删除 $n 份" } },
                onDownload = { viewModel.downloadSelected(selectedEntities) { n -> toast = "已重新保存 $n 份" } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReportCard(
    report: ReportEntity,
    /** 是否处于多选模式（v2.0.0）：卡片精简，只留标题与勾选框 */
    selectionMode: Boolean,
    /** 多选模式下是否已勾选 */
    selected: Boolean,
    /** 长按卡片 → 进入多选并选中这一份 */
    onLongClick: () -> Unit,
    /** 多选模式下点卡片 → 切换勾选 */
    onToggleSelect: () -> Unit,
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
    /** 点「放大查看」→ 全屏预览这份 Excel */
    onFullscreen: (PreviewContent) -> Unit,
) {
    // v2.0.0：多选的动效跟 v1.8.0 底栏用**同一套体系** ——
    //   按压回弹（pressScale）、颜色过渡（animateColorAsState）、
    //   出现/消失（淡入淡出 + 缩放）。
    // 之前这里是「瞬间切换」，进多选像被电了一下。
    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val cardPressScale by animateFloatAsState(
        targetValue = if (cardPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardPressScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 180),
        label = "cardContainerColor",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardPressScale
                scaleY = cardPressScale
            }
            // 长按进入多选；多选模式下点一下就是切换勾选
            .combinedClickable(
                interactionSource = cardInteraction,
                indication = null,
                onClick = {
                    if (selectionMode) onToggleSelect() else onTogglePreview()
                },
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    report.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 多选模式：右上角一个勾选框
                //
                // 动效与 v1.8.0 底栏同一套：缩放出现（spring）+ 勾颜色过渡 + 按压回弹。
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectionMode,
                    enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                    exit = fadeOut(tween(120)) + scaleOut(tween(120)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {

                                            Spacer(Modifier.width(10.dp))
                                            val checkInteraction = remember { MutableInteractionSource() }
                                            val checkPressed by checkInteraction.collectIsPressedAsState()
                                            val checkScale by animateFloatAsState(
                                                targetValue = if (checkPressed) 0.85f else 1f,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                label = "checkPressScale",
                                            )
                                            val checkFill by animateColorAsState(
                                                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                animationSpec = tween(durationMillis = 160),
                                                label = "checkFill",
                                            )
                                            val checkBorder by animateColorAsState(
                                                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                animationSpec = tween(durationMillis = 160),
                                                label = "checkBorder",
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .graphicsLayer {
                                                        scaleX = checkScale
                                                        scaleY = checkScale
                                                    }
                                                    .clip(RoundedCornerShape(11.dp))
                                                    .background(checkFill)
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = checkBorder,
                                                        shape = RoundedCornerShape(11.dp),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                // 对勾：勾上时弹一下（scale）
                                                // ⚠️ 用全限定名：RowScope 里也有一个 AnimatedVisibility，
                                                //    直接写名字会解析成 RowScope 那个而编译不过。
                                                androidx.compose.animation.AnimatedVisibility(
                                                    visible = selected,
                                                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                                                    exit = scaleOut(tween(100)),
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_check),
                                                        contentDescription = "已选中",
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                            }
                
                    }}
            }
            // 多选模式：卡片**精简** —— 只留标题与勾选框，
            // 其余（时间/密码/大小/摘要/预览/操作按钮）全部收起，
            // 一眼就能看清有哪些、选中了哪些。
            //
            // v2.0.0：收起/展开是**带动画**的（之前是瞬间塌缩，看着像卡了一下）。
            AnimatedVisibility(
                visible = !selectionMode,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(200)),
            ) {
                Column {

                                Spacer(Modifier.height(8.dp))
                                Row {
                                    MetaChip(R.drawable.ic_calendar, formatTime(report.createdAt))
                                    Spacer(Modifier.width(8.dp))
                                    MetaChip(R.drawable.ic_lock, report.password)
                                    Spacer(Modifier.width(8.dp))
                                    MetaChip(R.drawable.ic_folder, formatSize(report.sizeBytes))
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

                                // 操作按钮区：多选模式下整块收起（多选的操作统一走底部操作条）
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SmallAction(
                                        icon = if (previewExpanded) R.drawable.ic_visibility_off else R.drawable.ic_eye,
                                        label = if (previewExpanded) "收起内容" else "查看内容",
                                        modifier = Modifier.weight(1f),
                                        onClick = onTogglePreview,
                                    )
                                    SmallAction(R.drawable.ic_download, "再次下载", Modifier.weight(1f), onDownload)
                                    SmallAction(R.drawable.ic_mail, "发送邮件", Modifier.weight(1f), onMail)
                                    IconButton(
                                        onClick = onDelete,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                                    ) {
                                        Icon(painterResource(R.drawable.ic_delete),
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
                                                icon = R.drawable.ic_playlist_add,
                                                label = "取回下周计划",
                                                modifier = Modifier.weight(1f),
                                                onClick = onTakePlan,
                                            )
                                        }
                                        SmallAction(
                                            icon = R.drawable.ic_share,
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
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        onFullscreen(
                                                            PreviewContent(
                                                                title = content.title.ifBlank { report.displayName },
                                                                timeRange = content.timeRange,
                                                                doneWorks = content.doneWorks,
                                                                planWorks = content.planWorks,
                                                            ),
                                                        )
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                ) {
                                                    Icon(painterResource(R.drawable.ic_fullscreen),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp),
                                                    )
                                                    Spacer(Modifier.width(3.dp))
                                                    Text("放大查看", fontSize = 12.sp)
                                                }
                                            }
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
                                                    Icon(painterResource(R.drawable.ic_edit),
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
            
                }}
        }
    }
}

@Composable
private fun MetaChip(@DrawableRes icon: Int, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(11.dp),
        )
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
    @DrawableRes icon: Int,
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
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
        )
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

/**
 * 用户存放目录里的**外部压缩包**卡片。
 *
 * 这不是 App 自己生成的（那种在上面的列表里），是用户自己拷进去的。
 * 能预览内容（自动推断密码，推不出来就手输）、能分享。
 */
@Composable
private fun ExternalReportCard(
    item: ExternalReport,
    previewExpanded: Boolean,
    previewState: PreviewState,
    onTogglePreview: () -> Unit,
    onNeedPassword: () -> Unit,
    onShare: () -> Unit,
    /** 点「放大查看」→ 全屏预览这份 Excel */
    onFullscreen: (PreviewContent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    item.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(20),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "外部文件",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(formatSize(item.sizeBytes))
                    if (item.lastModified > 0) {
                        append(" · ")
                        append(formatTime(item.lastModified))
                    }
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(
                    icon = if (previewExpanded) R.drawable.ic_visibility_off else R.drawable.ic_eye,
                    label = if (previewExpanded) "收起内容" else "查看内容",
                    modifier = Modifier.weight(1f),
                    onClick = onTogglePreview,
                )
                SmallAction(
                    icon = R.drawable.ic_share,
                    label = "分享",
                    modifier = Modifier.weight(1f),
                    onClick = onShare,
                )
            }

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
                            title = content.title.ifBlank { item.displayName },
                            timeRange = content.timeRange,
                            doneWorks = content.doneWorks,
                            planWorks = content.planWorks,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    onFullscreen(
                                        PreviewContent(
                                            title = content.title.ifBlank { item.displayName },
                                            timeRange = content.timeRange,
                                            doneWorks = content.doneWorks,
                                            planWorks = content.planWorks,
                                        ),
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_fullscreen),
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text("放大查看", fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "这是压缩包里的 Excel 原样内容（预览即导出）。",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    previewState.error == "NEED_PASSWORD" -> {
                        Column {
                            Text(
                                "这个压缩包的文件名看不出密码，需要你手动输入一次。",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onNeedPassword,
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_lock_open),
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("输入解压密码", fontSize = 12.5.sp)
                            }
                        }
                    }

                    previewState.error != null -> {
                        Text(
                            previewState.error,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}





/**
 * 批量操作条（v2.0.0 起）。
 *
 * 多选模式时浮在页面**底部居中**，玻璃胶囊同时淡出。
 * 只提供两个真正适合批量的操作：
 *  · **再次下载** —— 只是复制文件，稳；
 *  · **删除** —— 删数据库记录 + 本机压缩包。
 *
 * 「载入首页」「取回下周计划」是单选语义（会互相覆盖），
 * 「批量发邮件」的多附件 Intent 可能超限或被邮箱拒收，所以都没做。
 */
@Composable
private fun BulkActionBar(
    selectedCount: Int,
    totalCount: Int,
    onCancel: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onCancel) {
                    Text("取消", fontSize = 13.sp)
                }
                TextButton(onClick = onToggleSelectAll) {
                    Text(
                        if (selectedCount == totalCount && totalCount > 0) "取消全选" else "全选",
                        fontSize = 13.sp,
                    )
                }
                Text(
                    "已选 $selectedCount",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                // 两个按钮都带**按压回弹**，和 v1.8.0 底栏图标的手感一致
                PressSquishIconButton(
                    onClick = onDownload,
                    enabled = selectedCount > 0,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = "再次下载",
                        modifier = Modifier.size(18.dp),
                    )
                }
                PressSquishIconButton(
                    onClick = onDelete,
                    enabled = selectedCount > 0,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}


/**
 * 带**按压回弹**的图标按钮（v2.0.0 起）。
 *
 * 和 v1.8.0 底栏的图标是同一套手感：按下缩到 0.82，松手用弹簧弹回。
 * Material3 自带的 `IconButton` 没有这个反馈。
 */
@Composable
private fun PressSquishIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: androidx.compose.material3.IconButtonColors =
        IconButtonDefaults.filledTonalIconButtonColors(),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pressSquish",
    )
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = colors,
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        content = content,
    )
}
