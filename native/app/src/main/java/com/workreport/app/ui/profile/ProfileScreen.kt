package com.workreport.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workreport.app.data.local.ThemeMode
import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.ui.common.LabeledField
import com.workreport.app.ui.common.SectionCard
import com.workreport.app.util.MailApp

@Composable
fun ProfileScreen(
    onShowIntro: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showWordDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Int, SensitiveWord>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showMailPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
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
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 4.dp),
            ) {
                Text(
                    "我的",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "个人设置与敏感词库",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "基本信息", icon = "👤") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField(
                        label = "姓名",
                        value = state.userName,
                        onValueChange = viewModel::onUserNameChange,
                        placeholder = "填写后自动保存，用于生成文件名",
                    )
                }
            }
        }

        item {
            SectionCard(title = "外观", icon = "🌗", hint = "可跟随系统自动切换") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    },
                                    fontSize = 12.sp,
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "邮件设置",
                icon = "📨",
                hint = "生成后自动拉起邮箱",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField(
                        label = "收件人邮箱",
                        value = state.mailRecipient,
                        onValueChange = viewModel::onMailRecipientChange,
                        placeholder = "如：leader@example.com（留空则不自动填）",
                    )
                    Text(
                        "填写后，拉起邮箱 App 时会一并填入收件人、主题与附件。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = state.autoSendMail,
                            onCheckedChange = { viewModel.setAutoSendMail(it) },
                        )
                        Text(
                            "每次生成后直接拉起邮箱 App 发送（不再询问）",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Text(
                        "发送邮件使用的应用",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            state.preferredMailLabel.ifEmpty { "尚未选择（首次生成时会让你选）" },
                            fontSize = 13.sp,
                            color = if (state.preferredMailLabel.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { showMailPicker = true },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                if (state.preferredMailLabel.isEmpty()) "选择" else "更换",
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Text(
                        "选择后，每次点「生成加密 ZIP 下载」都会自动用它发邮件。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "敏感词库",
                icon = "🛡️",
                hint = "${state.sensitiveWords.size} 个词",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "输入内容里的敏感词会被自动替换成小写拼音首字母，例如「纪委」→「jw」。词库存本机，卸载软件才会清除。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { editing = null; showWordDialog = true },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("添加敏感词", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.restoreDefaults() },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("恢复默认", fontSize = 12.sp)
                        }
                    }

                    if (state.sensitiveWords.isEmpty()) {
                        Text(
                            "词库是空的，添加一个试试",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.sensitiveWords.forEachIndexed { index, word ->
                            SensitiveWordRow(
                                word = word,
                                onEdit = { editing = index to word },
                                onDelete = { viewModel.removeWord(index) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "数据管理", icon = "🧹") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "清除后姓名、敏感词库与主题设置都会恢复默认（历史报告不受影响）。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("清除全部本地数据", fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            SectionCard(title = "关于", icon = "ℹ️") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("工作报告生成器 · 原生版", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "版本 1.3.1",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "完全离线运行，不申请网络以外的多余权限，所有内容只存在本机。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "项目仓库：github.com/ninoelvis/workreport-generator",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onShowIntro,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("📄 查看功能介绍与更新记录", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // 敏感词新增 / 编辑弹窗
    if (showWordDialog || editing != null) {
        SensitiveWordDialog(
            initial = editing?.second,
            autoPinyin = { viewModel.autoPinyin(it) },
            onDismiss = { showWordDialog = false; editing = null },
            onConfirm = { word, pinyin ->
                val target = editing
                if (target != null) {
                    viewModel.updateWord(target.first, word, pinyin)
                } else {
                    viewModel.addWord(word, pinyin)
                }
                showWordDialog = false
                editing = null
            },
        )
    }

    if (showMailPicker) {
        MailAppPickerDialog(
            apps = state.mailApps,
            current = state.preferredMailPackage,
            onDismiss = { showMailPicker = false },
            onSelect = { pkg ->
                viewModel.setPreferredMailPackage(pkg)
                showMailPicker = false
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除全部本地数据？") },
            text = { Text("姓名、敏感词库与主题设置会恢复默认，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    confirmClear = false
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SensitiveWordRow(
    word: SensitiveWord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            word.word,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            "→",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            word.pinyin,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(56.dp),
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SensitiveWordDialog(
    initial: SensitiveWord?,
    autoPinyin: (String) -> String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var word by remember { mutableStateOf(initial?.word ?: "") }
    var pinyin by remember { mutableStateOf(initial?.pinyin ?: "") }
    var touched by remember { mutableStateOf(initial != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加敏感词" else "编辑敏感词") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField(
                    label = "敏感词",
                    value = word,
                    onValueChange = {
                        word = it
                        if (!touched) pinyin = autoPinyin(it.trim())
                    },
                    placeholder = "如：督办",
                )
                LabeledField(
                    label = "替换成的缩写（小写拼音首字母）",
                    value = pinyin,
                    onValueChange = { pinyin = it; touched = true },
                    placeholder = "如：db",
                )
                Text(
                    "留空则自动生成；修改敏感词后缩写会按规则同步重算。",
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(word, pinyin) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 选择要使用的邮箱应用 */
@Composable
private fun MailAppPickerDialog(
    apps: List<MailApp>,
    current: String,
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
                if (apps.isEmpty()) {
                    Text(
                        "没有检测到邮箱类应用。请先在手机上安装 QQ邮箱 / Gmail / 网易邮箱 / Outlook 等。",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    apps.forEach { app ->
                        val selected = app.packageName == current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { onSelect(app.packageName) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                app.label,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
