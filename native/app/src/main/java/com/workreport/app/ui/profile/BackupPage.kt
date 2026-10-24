package com.workreport.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.workreport.app.data.backup.ConfigBackupManager
import com.workreport.app.ui.common.EmptyState
import com.workreport.app.ui.common.SectionCard
import com.workreport.app.util.DateUtils
import java.io.File
import java.util.Calendar

/**
 * 「配置备份」子页面。
 *
 * 备份范围：姓名、主题、敏感词库、两套快捷模板、邮箱偏好、周报提醒、首页草稿。
 * 不含历史报告与已生成的 ZIP。
 *
 * 备份文件是**加密 ZIP**，密码用周报那套规则（周四日期，yyyyMMdd），
 * 用户不用记新密码。
 */
@Composable
fun BackupPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var password by remember { mutableStateOf(defaultBackupPassword()) }
    var importPassword by remember { mutableStateOf("") }
    var mergeMode by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    /** 待确认的导入内容 */
    var pendingImport by remember { mutableStateOf<Pair<ConfigBackupManager.ImportOutcome, File>?>(null) }
    var pendingRollback by remember { mutableStateOf(false) }
    var hasRollback by remember { mutableStateOf(ConfigBackupManager.hasRollback()) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            status = "读不到这个文件，换一个试试"
            statusIsError = true
            return@rememberLauncherForActivityResult
        }
        val outcome = ConfigBackupManager.readSnapshot(bytes, importPassword)
        if (outcome is ConfigBackupManager.ImportOutcome.Failure) {
            status = outcome.message
            statusIsError = true
        } else {
            pendingImport = outcome to File(uri.toString())
        }
    }

    ProfileSubPage(
        title = "配置备份",
        subtitle = "只备份个人配置，不含历史报告",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(
                title = "导出备份",
                icon = "💾",
                hint = "存成加密 ZIP",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "备份里包含：姓名、主题、敏感词库、两套快捷模板、邮箱偏好、" +
                            "周报提醒设置、首页没写完的草稿。\n\n" +
                            "不包含历史报告和已生成的压缩包 —— 那些已经躺在「下载」目录里了。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "⚠️ 备份文件用密码加密，里面有敏感词库和真实工作模板，" +
                            "别明文发给别人。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备份密码", fontSize = 13.sp) },
                        placeholder = { Text("默认沿用周报密码规则", fontSize = 12.5.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        // 16sp：字号低于 16sp 时，部分 ROM 的输入法会强制放大整页，
                        // 结果是输入框被放大裁掉，看不见自己打的字
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    Button(
                        onClick = {
                            val name = ConfigBackupManager.exportToDownloads(context, password)
                            status = if (name != null) {
                                "已导出到「下载」目录：$name\n解压密码：$password"
                            } else {
                                "导出失败，请检查存储权限"
                            }
                            statusIsError = name == null
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        enabled = password.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Filled.Download, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导出备份到「下载」目录", fontSize = 13.sp)
                    }
                }
            }

            SectionCard(
                title = "导入备份",
                icon = "📥",
                hint = "从文件恢复",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备份密码", fontSize = 13.sp) },
                        placeholder = { Text("导出时用的密码", fontSize = 12.5.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "导入方式：",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mergeMode, onClick = { mergeMode = true })
                        Text(
                            "合并（推荐）：新增敏感词和模板，不动已有的",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !mergeMode, onClick = { mergeMode = false })
                        Text(
                            "覆盖：替换当前全部配置",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("application/zip")) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        enabled = importPassword.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Filled.Upload, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("选择备份文件", fontSize = 13.sp)
                    }
                }
            }

            if (hasRollback) {
                SectionCard(
                    title = "回滚",
                    icon = "↩️",
                    hint = "后悔了可以用",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "上一次导入前的配置还存着，可以一键退回去。",
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { pendingRollback = true },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Filled.Restore, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("恢复到导入前的配置", fontSize = 13.sp)
                        }
                    }
                }
            }

            status?.let { text ->
                Text(
                    text = text,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = if (statusIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (hasRollback.not() && pendingImport == null) {
                EmptyState(
                    icon = "💡",
                    title = "换手机或者重装之前",
                    description = "先导出一份备份，到新手机上导入，姓名、敏感词库和模板都能接着用。",
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // 导入前的二次确认
    pendingImport?.let { (outcome, _) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(if (mergeMode) "合并这份配置？" else "覆盖当前配置？") },
            text = {
                Text(
                    if (mergeMode) {
                        "备份里的敏感词和模板会加进来，已有的内容不受影响；" +
                            "姓名和提醒设置以备份里的为准。\n\n" +
                            "当前正在写的草稿不会被覆盖。"
                    } else {
                        "当前全部配置（姓名、敏感词库、模板、提醒设置、草稿）" +
                            "都会被这份备份替换掉。\n\n" +
                            "导入前会自动存一份快照，可以回滚。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    val result = ConfigBackupManager.apply(
                        (outcome as ConfigBackupManager.ImportOutcome.Success).snapshot,
                        mergeMode,
                    )
                    status = result.message
                    statusIsError = false
                    hasRollback = ConfigBackupManager.hasRollback()
                }) { Text("确定导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            },
        )
    }

    // 回滚的二次确认
    if (pendingRollback) {
        AlertDialog(
            onDismissRequest = { pendingRollback = false },
            title = { Text("恢复到导入前的配置？") },
            text = { Text("当前配置会被上一次导入前的那份替换掉。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRollback = false
                    val result = ConfigBackupManager.rollback()
                    status = result.message
                    statusIsError = result is ConfigBackupManager.ImportOutcome.Failure
                    hasRollback = ConfigBackupManager.hasRollback()
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollback = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 备份密码的默认值：沿用周报密码规则（本周周四日期，yyyyMMdd）。
 *
 * 用户不用记新的密码 —— 和周报压缩包一个习惯。
 */
private fun defaultBackupPassword(): String =
    DateUtils.fmtDate8(DateUtils.getThursday(DateUtils.parseDateRange(
        DateUtils.currentWeekRangeText(),
    )?.first ?: Calendar.getInstance().time))
