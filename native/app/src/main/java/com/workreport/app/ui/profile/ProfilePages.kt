package com.workreport.app.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workreport.app.BuildConfig
import com.workreport.app.data.local.ThemeMode
import com.workreport.app.domain.model.SensitiveWord
import com.workreport.app.domain.model.ShortcutSlot
import com.workreport.app.domain.model.ShortcutTemplate
import com.workreport.app.ui.common.EmptyState
import com.workreport.app.ui.common.LabeledField
import com.workreport.app.ui.common.SectionCard
import com.workreport.app.ui.common.SubPageTopBar
import com.workreport.app.util.MailApp

/** 子页面统一外壳：顶部返回栏 + 内容 */
@Composable
private fun ProfileSubPage(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SubPageTopBar(title = title, onBack = onBack, subtitle = subtitle)
        content()
    }
}

/** 把 ViewModel 的提示消息用 Toast 显示出来 */
@Composable
private fun RememberMessages(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }
}

// ===================== 个人信息 =====================

@Composable
fun ProfileNamePage(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileSubPage(
        title = "个人信息",
        subtitle = "姓名会用在生成的压缩包文件名里",
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
        ) {
            item {
                SectionCard(title = "姓名", icon = "👤") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledField(
                            label = "你的姓名",
                            value = state.userName,
                            onValueChange = viewModel::onUserNameChange,
                            placeholder = "例如：张三",
                        )
                        Text(
                            "填写后自动保存。没有填的话，生成的文件名会先用「姓名」占位。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ===================== 快捷记录模板 =====================

@Composable
fun ProfileShortcutsPage(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RememberMessages(viewModel)

    var tab by remember { mutableIntStateOf(0) }
    val slot = if (tab == 0) ShortcutSlot.WORK else ShortcutSlot.PLAN
    val templates = if (tab == 0) state.workShortcuts else state.planShortcuts

    var newText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ShortcutTemplate?>(null) }

    ProfileSubPage(
        title = "快捷记录模板",
        subtitle = "两套模板互不影响，分开配置",
        onBack = onBack,
    ) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0; newText = "" },
                    text = { Text("本周工作记录", fontSize = 13.sp) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1; newText = "" },
                    text = { Text("下周工作计划", fontSize = 13.sp) },
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionCard(
                        title = slot.title,
                        icon = if (tab == 0) "📋" else "🗒️",
                        hint = "${templates.size} 条",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                slot.hint,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newText,
                                    onValueChange = { newText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("输入模板内容", fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.addShortcut(slot, newText)
                                        newText = ""
                                    },
                                    modifier = Modifier.height(48.dp),
                                    enabled = newText.isNotBlank(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("添加", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                if (templates.isEmpty()) {
                    item {
                        EmptyState(
                            icon = "⚡",
                            title = "还没有模板",
                            description = "把每周都要重复写的内容存进来，" +
                                "之后在首页点一下就能插进去，不用每次都手敲。",
                        )
                    }
                } else {
                    items(templates, key = { it.id }) { tpl ->
                        ShortcutTemplateRow(
                            template = tpl,
                            onEdit = { editing = tpl },
                            onDelete = { viewModel.removeShortcut(slot, tpl.id) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { target ->
        ShortcutEditDialog(
            initial = target,
            onDismiss = { editing = null },
            onSave = {
                viewModel.updateShortcut(slot, target.id, it)
                editing = null
            },
            onDelete = {
                viewModel.removeShortcut(slot, target.id)
                editing = null
            },
        )
    }
}

@Composable
private fun ShortcutTemplateRow(
    template: ShortcutTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onEdit)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = template.text,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
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
private fun ShortcutEditDialog(
    initial: ShortcutTemplate,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("模板内容", fontSize = 13.sp) },
                    minLines = 2,
                    shape = RoundedCornerShape(8.dp),
                )
                TextButton(onClick = onDelete) {
                    Text("删除这条模板", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===================== 敏感词库 =====================

@Composable
fun ProfileSensitivePage(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RememberMessages(viewModel)

    var showWordDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Int, SensitiveWord>?>(null) }

    ProfileSubPage(
        title = "敏感词库",
        subtitle = "${state.sensitiveWords.size} 个词 · 词库存本机",
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "替换规则", icon = "🛡️", hint = "${state.sensitiveWords.size} 个词") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "输入内容里的敏感词会被自动替换成小写拼音首字母，例如「纪委」→「jw」。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { editing = null; showWordDialog = true },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("添加敏感词", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.restoreDefaults() },
                                modifier = Modifier.height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("恢复默认", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (state.sensitiveWords.isEmpty()) {
                item {
                    EmptyState(
                        icon = "🛡️",
                        title = "词库是空的",
                        description = "添加一个试试，比如「纪委」会自动变成「jw」。",
                    )
                }
            } else {
                items(state.sensitiveWords.size) { index ->
                    val word = state.sensitiveWords[index]
                    SensitiveWordRow(
                        word = word,
                        onEdit = { editing = index to word },
                        onDelete = { viewModel.removeWord(index) },
                    )
                }
            }
        }
    }

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
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
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

// ===================== 外观 =====================

@Composable
fun ProfileAppearancePage(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProfileSubPage(
        title = "外观",
        subtitle = "可跟随系统自动切换",
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
        ) {
            item {
                SectionCard(title = "主题模式", icon = "🌗") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Text(
                            "选「跟随系统」时，手机切换深浅色模式，App 会自动跟着变。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ===================== 邮件设置 =====================

@Composable
fun ProfileMailPage(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showMailPicker by remember { mutableStateOf(false) }

    ProfileSubPage(
        title = "邮件设置",
        subtitle = "生成后自动拉起邮箱",
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
        ) {
            item {
                SectionCard(title = "发送方式", icon = "📨") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "拉起邮箱 App 时会自动带上邮件主题和压缩包附件，收件人由你在邮箱里填写。",
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
        }
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
}

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

// ===================== 数据管理 =====================

@Composable
fun ProfileDataPage(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory()),
) {
    RememberMessages(viewModel)
    var confirmClear by remember { mutableStateOf(false) }

    ProfileSubPage(
        title = "数据管理",
        subtitle = "所有数据只存在这台手机上",
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
        ) {
            item {
                SectionCard(title = "清除本地数据", icon = "🧹") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "清除后姓名、敏感词库、快捷记录模板与主题设置都会恢复默认" +
                                "（历史报告不受影响）。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { confirmClear = true },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
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
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除全部本地数据？") },
            text = { Text("姓名、敏感词库、快捷记录模板与主题设置会恢复默认，此操作不可恢复。") },
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

// ===================== 关于 =====================

@Composable
fun ProfileAboutPage(onBack: () -> Unit) {
    ProfileSubPage(
        title = "关于",
        subtitle = "工作报告生成器 · 原生版",
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "关于", icon = "ℹ️") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "工作报告生成器 · 原生版",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "版本 " + BuildConfig.VERSION_NAME,
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
                    }
                }
            }

            item {
                SectionCard(title = "免责声明", icon = "⚖️") {
                    Text(
                        "本软件为个人自用的效率工具，仅用于自动汇总与整理本人工作内容。" +
                            "使用者应自行确保所填报的工作内容真实、合法，" +
                            "不得用于伪造工作记录、隐瞒实际工作内容或任何违反单位规定及法律法规的用途。" +
                            "因使用本软件生成、填写、发送的内容产生的一切后果由使用者自行承担，" +
                            "软件作者不承担任何法律责任。",
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
