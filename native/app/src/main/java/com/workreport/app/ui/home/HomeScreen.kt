package com.workreport.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workreport.app.R
import com.workreport.app.ServiceLocator
import com.workreport.app.ui.common.FieldLabel
import com.workreport.app.ui.common.InfoStatRow
import com.workreport.app.ui.common.LabeledField
import com.workreport.app.ui.common.SectionCard
import com.workreport.app.util.MailApp
import com.workreport.app.util.MailHelper
import android.widget.Toast

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mailApps by viewModel.mailApps.collectAsStateWithLifecycle()
    var errorDialog by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val settings = ServiceLocator.settings
    val preferredMailPackage by settings.preferredMailPackage.collectAsStateWithLifecycle()
    val mailRecipient by settings.mailRecipient.collectAsStateWithLifecycle()

    /** 生成成功后待发送的邮件（等待用户首次选择邮箱应用） */
    var pendingMail by remember { mutableStateOf<GenerateOutcome.Success?>(null) }
    var showMailPicker by remember { mutableStateOf(false) }

    /** 第一步：确认是否生成 */
    var confirmGenerate by remember { mutableStateOf(false) }
    /** 第二步：确认是否拉起邮箱 App 发送 */
    var mailChoiceFor by remember { mutableStateOf<GenerateOutcome.Success?>(null) }
    /** 第二步弹窗里的「以后都这样做」勾选框 */
    var rememberChoice by remember { mutableStateOf(false) }
    /** 清空「本周已完成工作梳理」的二次确认 */
    var confirmClearDone by remember { mutableStateOf(false) }
    /** 清空「下周工作计划」的二次确认 */
    var confirmClearPlan by remember { mutableStateOf(false) }
    val autoSendMail by settings.autoSendMail.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            errorDialog = it
            viewModel.consumeMessage()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp, end = 14.dp, top = 0.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AppHeader() }

        item {
            InfoStatRow(
                items = listOf(
                    "周次" to state.weekLabel.ifEmpty { "-" },
                    "解压密码" to state.password.ifEmpty { "-" },
                    "文件名" to state.fileName.ifEmpty { "-" },
                ),
            )
        }

        item {
            SectionCard(title = "填写周报", icon = "📝", hint = "填写本周工作内容") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LabeledField(
                        label = "👤 姓名",
                        value = state.userName,
                        onValueChange = viewModel::onUserNameChange,
                        placeholder = "首次填写后自动保存，可随时修改",
                    )
                    LabeledField(
                        label = "📅 本周工作时间",
                        value = state.timeRange,
                        onValueChange = viewModel::onTimeRangeChange,
                        placeholder = "yyyy.MM.dd-yyyy.MM.dd",
                    )
                    LabeledField(
                        label = "🏷️ 报告标题",
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        placeholder = "默认按年月自动生成，可手动修改",
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "工作记录",
                icon = "📋",
                hint = "做完一项随手记一条，上报前统一汇总",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            LabeledField(
                                label = "本次完成的工作内容",
                                value = state.recordInput,
                                onValueChange = viewModel::onRecordInputChange,
                                placeholder = "输入内容后点右侧按钮添加",
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.addRecord() },
                            modifier = Modifier.padding(top = 22.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp, vertical = 8.dp,
                            ),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("添加", fontSize = 12.sp)
                        }
                    }

                    if (state.records.isEmpty()) {
                        Text(
                            "还没有记录",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.records.forEachIndexed { index, record ->
                            RecordRow(
                                index = index + 1,
                                text = record,
                                onDelete = { viewModel.deleteRecord(index) },
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.summarizeRecords() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("🔗 汇总到「本周已完成工作梳理」", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            SectionCard(title = "周报内容", icon = "🗒️", hint = "支持多行，每行一条") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LabeledField(
                        label = "本周已完成工作梳理",
                        value = state.doneWorks,
                        onValueChange = viewModel::onDoneWorksChange,
                        placeholder = "",
                        singleLine = false,
                        minLines = 5,
                        trailingIcon = {
                            if (state.doneWorks.isNotEmpty()) {
                                IconButton(onClick = { confirmClearDone = true }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "清空本周已完成工作梳理",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                        },
                    )
                    LabeledField(
                        label = "下周工作计划",
                        value = state.planWorks,
                        onValueChange = viewModel::onPlanWorksChange,
                        placeholder = "",
                        singleLine = false,
                        minLines = 4,
                        trailingIcon = {
                            if (state.planWorks.isNotEmpty()) {
                                IconButton(onClick = { confirmClearPlan = true }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "清空下周工作计划",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        item {
            SectionCard(title = "预览区", icon = "👁️", hint = "最终 Excel 效果") {
                PreviewTable(
                    title = state.previewTitle,
                    timeRange = state.previewTimeRange,
                    doneWorks = state.previewDone,
                    planWorks = state.previewPlan,
                )
            }
        }

        item {
            Button(
                onClick = { confirmGenerate = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !state.generating,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (state.generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("生成中…", fontSize = 14.sp)
                } else {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("生成加密 ZIP 下载", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // 第一步：确认是否生成
    if (confirmGenerate) {
        AlertDialog(
            onDismissRequest = { confirmGenerate = false },
            title = { Text("确认生成加密 ZIP？") },
            text = {
                Column {
                    Text("将生成 Excel 并打包为 AES-256 加密的 ZIP，保存到系统「下载」目录。")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "文件名：${state.fileName.ifEmpty { "-" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "解压密码：${state.password.ifEmpty { "-" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmGenerate = false
                    viewModel.generate { outcome ->
                        when (outcome) {
                            is GenerateOutcome.Success -> {
                                // 旧版行为：生成成功时文件名自动进剪贴板
                                MailHelper.copyToClipboard(context, outcome.fileName)
                                if (autoSendMail) {
                                    // 记住了「每次都直接发」→ 不再询问
                                    if (preferredMailPackage.isNotEmpty()) {
                                        sendGeneratedMail(
                                            context, outcome, preferredMailPackage, mailRecipient,
                                        )
                                    } else {
                                        pendingMail = outcome
                                        showMailPicker = true
                                    }
                                    Toast.makeText(
                                        context,
                                        "已生成并保存到「下载」目录，文件名已复制",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    // 第二步：问用户要不要直接拉起邮箱
                                    mailChoiceFor = outcome
                                }
                            }
                            is GenerateOutcome.Failure -> errorDialog = outcome.message
                        }
                    }
                }) { Text("确认生成") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGenerate = false }) { Text("取消") }
            },
        )
    }

    // 第二步：确认是否拉起邮箱 App 发送
    mailChoiceFor?.let { result ->
        // 弹窗内的收件人输入（默认值取已保存的）
        var recipientInput by remember(result) { mutableStateOf(mailRecipient) }
        AlertDialog(
            onDismissRequest = { mailChoiceFor = null },
            title = { Text("已生成，是否直接拉起邮箱 App 发送？") },
            text = {
                Column {
                    Text(
                        "已保存到系统「下载」目录，并存入「历史报告」。",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "文件名：${result.fileName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "解压密码：${result.password}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = recipientInput,
                        onValueChange = { recipientInput = it },
                        label = { Text("收件人邮箱", fontSize = 12.sp) },
                        placeholder = { Text("如：leader@example.com", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                    Text(
                        "填了会保存下来，下次自动带上；也可以在「我的」页修改。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "可以现在就把压缩包作为附件带入邮箱 App；也可以只把文件保存到本地，自己再发。",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberChoice,
                            onCheckedChange = { rememberChoice = it },
                        )
                        Text(
                            "记住我的选择，以后每次生成后都直接拉起邮箱 App 发送",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    mailChoiceFor = null
                    // 收件人地址持久化保存
                    settings.setMailRecipient(recipientInput)
                    if (rememberChoice) settings.setAutoSendMail(true)
                    if (preferredMailPackage.isNotEmpty()) {
                        sendGeneratedMail(context, result, preferredMailPackage, recipientInput)
                    } else {
                        // 还没选过邮箱 App → 先选 App，选完立刻发送
                        pendingMail = result
                        showMailPicker = true
                    }
                }) { Text("是，拉起邮箱") }
            },
            dismissButton = {
                TextButton(onClick = {
                    mailChoiceFor = null
                    // 收件人地址同样保存下来
                    settings.setMailRecipient(recipientInput)
                    if (rememberChoice) settings.setAutoSendMail(false)
                }) { Text("否，只保存到本地") }
            },
        )
    }

    // 首次生成：选择要使用的邮箱 App，选完立即发送
    if (showMailPicker) {
        MailPickerDialog(
            apps = mailApps,
            onDismiss = { showMailPicker = false; pendingMail = null },
            onSelect = { pkg ->
                settings.setPreferredMailPackage(pkg)
                showMailPicker = false
                val pending = pendingMail
                pendingMail = null
                if (pending != null) {
                    // ⚠️ 这里要读 StateFlow 的当前值，不能读 Compose state ——
                    //    刚刚 setMailRecipient() 写入的值还没触发重组，state 里还是旧的
                    sendGeneratedMail(context, pending, pkg, settings.mailRecipient.value)
                }
            },
        )
    }

    // 清空两个文本框的二次确认
    if (confirmClearDone) {
        AlertDialog(
            onDismissRequest = { confirmClearDone = false },
            title = { Text("清空「本周已完成工作梳理」？") },
            text = { Text("这个文本框里已输入的内容会被全部清空，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearDone = false
                    viewModel.clearDoneWorks()
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDone = false }) { Text("取消") }
            },
        )
    }

    if (confirmClearPlan) {
        AlertDialog(
            onDismissRequest = { confirmClearPlan = false },
            title = { Text("清空「下周工作计划」？") },
            text = { Text("这个文本框里已输入的内容会被全部清空，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearPlan = false
                    viewModel.clearPlanWorks()
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearPlan = false }) { Text("取消") }
            },
        )
    }

    errorDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text("提示") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { errorDialog = null }) { Text("知道了") } },
        )
    }
}

/** 顶部品牌栏 */
@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "工作报告生成器",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "自动生成加密周报 ZIP · 一键复制文件名 · 自动发送邮件",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 工作记录行 */
@Composable
private fun RecordRow(index: Int, text: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("$index", fontSize = 10.sp, color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 预览表格（对应导出的 Excel 结构）。
 *
 * ⚠️ 这里**只是预览**，不影响导出的 xlsx 内容。列宽比例直接取自 XlsxWriter 的真实列宽，
 *    行高、对齐方式也对齐 Excel 的样式定义，保证「所见即所得」。
 *
 * Excel 的真实列宽（单位：字符）：
 *   20.71484375, 29.8125, 26.36328125, 19.97265625, 21.46484375, 13.83203125
 * 归一化后作为预览里 6 列的宽度占比。
 */
private val PREVIEW_COL_WEIGHTS: FloatArray = floatArrayOf(
    20.71484375f, 29.8125f, 26.36328125f, 19.97265625f, 21.46484375f, 13.83203125f,
).let { raw ->
    val total = raw.sum()
    FloatArray(raw.size) { raw[it] / total }
}

private val PREVIEW_HEADERS = listOf(
    "本周工作时间",
    "本周已完成工作梳理",
    "下周工作计划",
    "客户（及用户）反馈",
    "项目经理评级",
    "其他",
)

@Composable
private fun PreviewTable(
    title: String,
    timeRange: String,
    doneWorks: String,
    planWorks: String,
) {
    val border = MaterialTheme.colorScheme.outline
    val headerBg = Color(0xFFB5C6EA)
    val ink = Color(0xFF1F2329)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, border, RoundedCornerShape(6.dp)),
    ) {
        // 第 1 行：标题（Excel 里 A1:F1 合并，水平+垂直居中）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.ifEmpty { "-" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        PreviewDivider(border)

        // 第 2 行：表头（Excel 里黑体 10pt、填充 B5C6EA、水平垂直居中）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg),
        ) {
            PREVIEW_HEADERS.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(PREVIEW_COL_WEIGHTS[index])
                        .border(0.5.dp, border)
                        .padding(horizontal = 1.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 8.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        PreviewDivider(border)

        // 第 3、4 行：数据区（Excel 里 A3:A4 … F3:F4 六列全部纵向合并）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // A 列：仿宋 9pt、左对齐、垂直居中、不换行
            PreviewCell(
                text = timeRange,
                weight = PREVIEW_COL_WEIGHTS[0],
                border = border,
                fontSize = 8.5.sp,
                wrapText = false,
                centerVertically = true,
            )
            // B / C 列：仿宋 9pt、左对齐、换行
            PreviewCell(doneWorks, PREVIEW_COL_WEIGHTS[1], border, 8.5.sp)
            PreviewCell(planWorks, PREVIEW_COL_WEIGHTS[2], border, 8.5.sp)
            // D / E / F 列：仿宋 11pt、左对齐、换行（内容留空，供以后扩展）
            PreviewCell("", PREVIEW_COL_WEIGHTS[3], border, 8.5.sp)
            PreviewCell("", PREVIEW_COL_WEIGHTS[4], border, 8.5.sp)
            PreviewCell("", PREVIEW_COL_WEIGHTS[5], border, 8.5.sp)
        }
    }
}

/** 预览表格里的细分隔线 */
@Composable
private fun PreviewDivider(border: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(border),
    )
}

@Composable
private fun RowScope.PreviewCell(
    text: String,
    weight: Float,
    border: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    wrapText: Boolean = true,
    centerVertically: Boolean = false,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(132.dp)
            .border(0.5.dp, border)
            .padding(horizontal = 3.dp, vertical = 4.dp),
        contentAlignment = if (centerVertically) Alignment.Center else Alignment.TopStart,
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
            maxLines = if (wrapText) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (centerVertically) Modifier.fillMaxWidth() else Modifier,
        )
    }
}

/**
 * 拉起邮箱 App 发送刚生成的周报。
 *
 * 附件是加密 ZIP，主题用报告标题，正文带上解压密码，
 * 收件人取「我的」页配置好的邮箱（没配就不填）。
 */
private fun sendGeneratedMail(
    context: android.content.Context,
    result: GenerateOutcome.Success,
    packageName: String,
    recipient: String,
) {
    // 邮件只填主题、收件人、附件，正文留空
    // 主题与压缩包名保持一致（旧版也是用 attach.getName() 当主题）
    val ok = MailHelper.launchWithAttachment(
        context = context,
        packageName = packageName.ifEmpty { null },
        file = result.file,
        subject = result.fileName,
        body = "",
        recipient = recipient.trim(),
    )
    if (!ok) {
        android.widget.Toast.makeText(
            context,
            "未能拉起邮件应用，可在「我的」页重新选择邮箱 App",
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }
}

/** 选择要使用的邮箱 App（首次生成时弹出） */
@Composable
private fun MailPickerDialog(
    apps: List<MailApp>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择发送邮件的应用") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "之后每次生成都会用这个应用发送，可在「我的」页随时更换。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (apps.isEmpty()) {
                    Text(
                        "没有检测到邮箱类应用。请先安装 QQ邮箱 / Gmail / 网易邮箱 / Outlook 等，再回来生成。",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    apps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onSelect(app.packageName) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                app.label,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
