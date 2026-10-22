package com.workreport.app.ui.intro

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.workreport.app.R

/**
 * 功能介绍与更新记录。
 *
 * 首次启动自动弹出；之后可在「我的」页随时再次打开。
 * 内容沿用 WebView 旧版的引导页，并补上原生版的新条目。
 */
@Composable
fun IntroDialog(
    /** 是否允许点击外部关闭（首次启动强制展示时传 false） */
    dismissOnOutsideClick: Boolean = true,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (dismissOnOutsideClick) onDismiss(dontShowAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                IntroHeader()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        IntroCard(title = "功能介绍", icon = Icons.Filled.Star) {
                            IntroBullet("填姓名和时间", "自动算出周数、文件名和压缩包密码。")
                            IntroBullet("随手记工作", "做完一项记一条，汇总进「本周已完成工作梳理」。")
                            IntroBullet("自动暂存", "填到一半退出也不丢，下次打开还在；生成压缩包后自动清除。")
                            IntroBullet("一键生成", "导出 Excel 并加密成 ZIP，存到手机「下载」目录。")
                            IntroBullet("自动发邮件", "拉起邮箱 App，主题和压缩包附件自动带好，收件人自己填。")
                            IntroBullet("敏感词替换", "「纪委」自动变「jw」，词库自己维护。")
                            IntroBullet("历史报告", "随时回看，可以再下载、再发一次。")
                            IntroBullet("实时预览", "导出前先看 Excel 长什么样。")
                        }
                    }

                    item {
                        IntroCard(title = "历史版本更新记录", icon = Icons.Filled.History) {
                            ChangeLogItem(
                                "v1.3.6",
                                "新增草稿暂存：「工作记录」和「周报内容」填到一半退出 App 也不会丢，下次打开自动恢复；生成压缩包后自动清除。",
                                current = true,
                            )
                            ChangeLogItem(
                                "v1.3.5",
                                "邮件不再自动填收件人，只带主题和附件，收件人在邮箱里自己填 —— 这样各家邮箱 App 都能正常用。首页文件名显示不全也修了。「关于」里加了免责声明。",
                            )
                            ChangeLogItem(
                                "v1.3.4",
                                "修复拉起邮箱 App 闪退、拉不起来的问题。",
                            )
                            ChangeLogItem(
                                "v1.3.3",
                                "新增发送方式切换（已随 v1.3.5 移除）。",
                            )
                            ChangeLogItem(
                                "v1.3.2",
                                "修复收件人栏空白；邮件主题改为与压缩包名一致。",
                            )
                            ChangeLogItem(
                                "v1.3.1",
                                "清除按钮加二次确认；发送弹窗里可直接填收件人。",
                            )
                            ChangeLogItem(
                                "v1.3.0",
                                "正式版：生成与发送分两步确认；邮件只带主题、收件人、附件，正文留空。",
                            )
                            ChangeLogItem(
                                "更早",
                                "原生重写（v1.3.0-beta 起，不再使用 WebView）；再往前是 WebView 时代的 v1.0 ~ v1.2.1：敏感词管理、自适应图标、界面重做等。",
                            )
                        }
                    }

                    item {
                        IntroCard(title = "数据与隐私", icon = Icons.Filled.Lock) {
                            IntroBullet("完全本地", "所有内容只保存在本机，不上传任何服务器。")
                            IntroBullet(
                                "自动保存",
                                "输入内容自动保存在本机，下次打开自动恢复；首次启动姓名为空。",
                            )
                            IntroBullet(
                                "权限最小化",
                                "只申请联网（预留）与 Android 8/9 写「下载」目录两项权限，不申请相机等无关权限。",
                            )
                        }
                    }

                    item {
                        IntroCard(title = "源码与更新记录", icon = Icons.Filled.Code) {
                            IntroBullet(
                                "仓库地址",
                                "github.com/ninoelvis/workreport-generator（原生版源码在同仓库的 native 目录）",
                            )
                            IntroBullet("自行构建", "源码可用 Android Studio 直接打开，或用 ./gradlew assembleRelease 打包。")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = dontShowAgain,
                                onCheckedChange = { dontShowAgain = it },
                            )
                            Text(
                                "不再自动显示此介绍（随时可从「我的」页重新打开）",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onDismiss(dontShowAgain) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("开始使用", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/** 顶部品牌区 */
@Composable
private fun IntroHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                "工作报告生成器",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "填写本周工作内容，一键生成加密周报 ZIP，并自动复制文件名、发送邮件",
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 卡片容器 */
@Composable
private fun IntroCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** 功能条目 */
@Composable
private fun IntroBullet(title: String, desc: String) {
    Row(Modifier.padding(bottom = 9.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                desc,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 更新记录条目 */
@Composable
private fun ChangeLogItem(version: String, desc: String, current: Boolean = false) {
    Row(Modifier.padding(bottom = 9.dp)) {
        Text(
            version,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (current) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            desc,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
