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
                            IntroBullet("填写周报", "填姓名与本周工作时间，自动算出周数、文件名与 ZIP 解压密码。")
                            IntroBullet("工作记录", "做完一项随手记一条，上报前一键汇总进「本周已完成工作梳理」。")
                            IntroBullet("生成加密 ZIP", "导出 Excel 并用密码加密压缩，保存到系统「下载」目录。")
                            IntroBullet("自动复制文件名", "生成成功的同时，文件名自动进入手机剪贴板。")
                            IntroBullet("自动发邮件", "自动拉起你设定的邮箱 App，压缩包作为附件带入写邮件界面。")
                            IntroBullet("实时预览", "随时查看最终 Excel 效果，所见即所得。")
                            IntroBullet(
                                "敏感词管理",
                                "在「我的」页可增删改敏感词库；敏感词会被替换成小写拼音首字母（如「纪委」→「jw」）。词库保存在本机，卸载软件才会清除。",
                            )
                            IntroBullet("分区清除", "「我的」页可一键清除本地设置与词库，历史报告单独保留。")
                        }
                    }

                    item {
                        IntroCard(title = "历史版本更新记录", icon = Icons.Filled.History) {
                            ChangeLogItem(
                                "v1.3.2",
                                "修复：填了收件人邮箱后，拉起邮箱 App 时收件人栏是空的。原因是 EXTRA_EMAIL 在附件通道（ACTION_SEND）下只有部分邮箱 App 会读。现在改为按目标邮箱 App 的实际能力自动选择通道——支持邮件 URI 的走 ACTION_SENDTO（收件人必定填上），Gmail 等会丢附件的仍走 ACTION_SEND 并把收件人写进 mailto 与 EXTRA_EMAIL 两处。「我的」页新增「复制收件人地址」按钮作为兜底。",
                                current = true,
                            )
                            ChangeLogItem(
                                "v1.3.1",
                                "两个内容输入框的清除按钮加上二次确认，防止误删；生成后的「是否拉起邮箱 App 发送」弹窗里新增收件人邮箱输入框，填完自动保存，下次生成直接带上（也可在「我的」页修改），确认后再选择邮箱 App。",
                            )
                            ChangeLogItem(
                                "v1.3.0",
                                "正式版发布。「本周已完成工作梳理」「下周工作计划」两个输入框各自带上独立清除按钮；生成周报改为两步确认：先确认是否生成，再确认是否直接拉起邮箱 App 发送，可勾选「以后每次都直接发送」；邮件内容精简为只带主题、收件人与附件，正文不再自动填写；「我的」页邮箱设置里新增「每次生成后直接拉起邮箱 App 发送」开关。",
                            )
                            ChangeLogItem(
                                "v1.3.0-beta2",
                                "新增首次启动功能介绍页与更新记录（之后可在「我的」页随时查看）；「我的」页新增收件人邮箱配置；生成周报后自动拉起邮箱 App 发送附件（首次会请你选择应用，之后自动记住，可随时更换）；首页预览表格按 Excel 真实列宽重新对齐。",
                            )
                            ChangeLogItem(
                                "v1.3.0-beta1",
                                "原生重写：整个 App 从 WebView 套壳改写为 Kotlin + Jetpack Compose 原生实现，不再加载任何网页资源。导出的 Excel 与加密 ZIP 与旧版逐字节一致。",
                            )
                            ChangeLogItem(
                                "v1.2.1",
                                "App 内顶栏图标也换成新的卷轴+笔图标（此前只改了桌面图标）；若桌面图标仍显示旧样式，是桌面缓存了图标，可清理桌面缓存。",
                            )
                            ChangeLogItem(
                                "v1.2.0",
                                "全新应用图标（卷轴+笔，适配 Android 自适应图标）；状态栏与导航栏颜色改为跟随页面主题。",
                            )
                            ChangeLogItem("v1.1.0", "新增敏感词管理：可增删改敏感词库，词库长期保存，保存后立即生效。")
                            ChangeLogItem("v1.0.2", "修复「报告标题」修改后预览区不实时刷新的问题。")
                            ChangeLogItem("v1.0", "正式版发布到 GitHub，首页引导页新增「源码与更新记录」。")
                            ChangeLogItem("v6", "新增首次启动功能介绍页与更新日志；提供「重置全部本地数据」入口。")
                            ChangeLogItem("v5", "界面按飞书 / 钉钉风格重做：品牌栏、信息统计卡片、浅深双主题。")
                            ChangeLogItem("v4", "邮箱选择列表只显示邮箱类应用，微信 / QQ / 微博等分享类应用不再出现。")
                            ChangeLogItem("v3", "发送邮箱可自行选择并持久化记住，之后随时可手动更换。")
                            ChangeLogItem("v2", "生成后自动复制文件名到剪贴板，并自动拉起邮箱把压缩包作为附件带入。")
                            ChangeLogItem("v1", "初版：周报内容生成、加密 ZIP 导出、保存到系统下载目录。")
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
