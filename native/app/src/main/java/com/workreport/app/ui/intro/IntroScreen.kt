package com.workreport.app.ui.intro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workreport.app.BuildConfig
import com.workreport.app.ServiceLocator
import com.workreport.app.ui.common.AppTopBar

/**
 * 功能介绍与更新记录（整页）。
 *
 * 现在是 NavHost 里的一个全屏路由，不再是 Dialog：
 * Dialog 是独立窗口，状态栏区域会露出系统灰色底，看起来"没全屏"；
 * 改成普通页面后和首页完全一致，内容直接铺到屏幕顶部。
 */
@Composable
fun IntroScreen(
    onClose: () -> Unit,
    /** 首次启动强制展示时不能随手划走 */
    forced: Boolean = false,
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    // 首次启动必须填姓名 —— 压缩包文件名要用
    var userName by remember { mutableStateOf(ServiceLocator.settings.userName.value) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶部渐变装饰，让品牌区比纯色更有层次
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        ) {
            AppTopBar(
                title = "工作报告生成器",
                subtitle = "填写本周工作内容，一键生成加密周报 ZIP 并发送邮件",
                trailing = {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            "v" + BuildConfig.VERSION_NAME,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                },
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                IntroSection(
                    title = "功能介绍",
                    icon = Icons.Filled.Star,
                    initiallyExpanded = true,
                ) {
                    IntroBullet("填姓名和时间", "自动算出周数、文件名和压缩包密码。")
                    IntroBullet("随手记工作", "做完一项记一条，汇总进「本周已完成工作梳理」。")
                    IntroBullet("快捷记录模板", "常写的重复内容存成模板，首页点一下就插进去。")
                    IntroBullet("自动暂存", "填到一半退出也不丢，下次打开还在；生成压缩包后自动清除。")
                    IntroBullet("一键生成", "导出 Excel 并加密成 ZIP，存到手机「下载」目录。")
                    IntroBullet("自动发邮件", "拉起邮箱 App，主题和压缩包附件自动带好，收件人自己填。")
                    IntroBullet("敏感词替换", "「纪委」自动变「jw」，词库自己维护。")
                    IntroBullet(
                        "脱敏预览，逐处放行",
                        "导出前就能看清哪几处被替换了，还带上下文。同一句话里出现两次「查询」时，" +
                            "可以只放行其中一处 —— 另一处照常脱敏，不会再整句一起豁免。",
                    )
                    IntroBullet("系统分享", "生成的压缩包可以直接调系统分享面板，发给任何人。")
                    IntroBullet("周报提醒", "选好星期几，到点首页顶部提醒一句，不用记着什么时候写周报。")
                    IntroBullet("配置备份", "姓名、词库、模板、提醒设置可以备份成一个加密文件，换手机时一键恢复。")
                    IntroBullet("历史报告", "随时回看，可以再下载、再发一次。")
                    IntroBullet(
                        "历史报告直接预览",
                        "点开就能看到压缩包里那份 Excel 长什么样 —— 表格、合并单元格、列宽都跟导出的完全一致，" +
                            "连以前生成的报告也能看全，不用手动解压。",
                    )
                    IntroBullet(
                        "历史报告改一改",
                        "看完可以直接「载入首页继续编辑」，改完重新生成；" +
                            "生成成功后表单会自动清空，不用手动删。",
                    )
                    IntroBullet("实时预览", "导出前先看 Excel 长什么样。")
                }
            }

            item {
                IntroSection(title = "历史版本更新记录", icon = Icons.Filled.History) {
                    ChangeLogItem(
                        "v1.4.0",
                        "脱敏预览升级成**按处放行**：同一句话里出现两次「查询」时会列成两行并带上下文，" +
                            "可以只放行其中一处，另一处照常脱敏；「全部还原」「恢复脱敏」也按处生效。" +
                            "新增系统分享面板、周报提醒（选星期几，16:00-18:00 之间提醒）、" +
                            "个人配置加密备份与恢复、快捷模板复制导入、上周计划一键接续本周工作。",
                        current = true,
                    )
                    ChangeLogItem(
                        "v1.3.11",
                        "预览表在深色模式下也看得清了（表头、标题、边框都改成跟随主题）；" +
                            "生成压缩包后自动清空「工作记录」和「周报内容」，下次直接重新填；" +
                            "历史报告新增「载入首页继续编辑」—— 看完内容可以直接改，改完重新生成。",
                    )
                    ChangeLogItem(
                        "v1.3.10",
                        "历史报告预览改成真的把压缩包解开：自动带上解压密码，读出里面那份 Excel 的完整内容，" +
                            "**以前生成的报告也能看全表格**（不只新报告）。预览区还会显示压缩包的解压密码。",
                    )
                    ChangeLogItem(
                        "v1.3.9",
                        "历史报告新增「查看内容」：点开就能直接看到压缩包里那份 Excel 的样子，" +
                            "表格、合并单元格、列宽都跟导出的一致，不用手动解压。" +
                            "卡片上也会直接显示「本周完成」的内容摘要，瞄一眼就知道写了什么。",
                    )
                    ChangeLogItem(
                        "v1.3.8",
                        "新增「快捷记录模板」：常写的重复内容存成模板，首页点一下就插入。" +
                            "「本周工作记录」和「下周工作计划」两套模板分开配置。" +
                            "「我的」页改成设置首页，每个配置项点进去单独编辑。" +
                            "引导页改为全屏展示，各分区可展开收起。",
                    )
                    ChangeLogItem(
                        "v1.3.6",
                        "新增草稿暂存：「工作记录」和「周报内容」填到一半退出 App 也不会丢，下次打开自动恢复；生成压缩包后自动清除。",
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
                        "正式版：生成与发送分两步确认；邮件只带主题、附件，正文留空。",
                    )
                    ChangeLogItem(
                        "更早",
                        "原生重写（v1.3.0-beta 起，不再使用 WebView）；再往前是 WebView 时代的 v1.0 ~ v1.2.1：敏感词管理、自适应图标、界面重做等。",
                    )
                }
            }

            item {
                IntroSection(title = "数据与隐私", icon = Icons.Filled.Lock) {
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
                IntroSection(title = "源码与更新记录", icon = Icons.Filled.Code) {
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
                // 首次启动时必须留下姓名：压缩包文件名是「姓名-2026年10月第4周工作报告.zip」
                if (forced) {
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("你的姓名", fontSize = 13.sp) },
                        placeholder = { Text("用于压缩包文件名，如：张三", fontSize = 12.5.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        // 首次启动时把姓名存下来，首页的文件名就能直接用
                        if (forced && userName.isNotBlank()) {
                            ServiceLocator.settings.setUserName(userName)
                        }
                        onClose()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    enabled = !forced || userName.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        if (forced) "我知道了，开始使用" else "关闭",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * 可展开 / 收起的分区卡片。
 */
@Composable
private fun IntroSection(
    title: String,
    icon: ImageVector,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "introSectionArrow",
    )
    val titleColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
        label = "introSectionTitle",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
            )
        }

        if (expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                content()
            }
        }
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
    Row(Modifier.padding(bottom = 10.dp)) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                version,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (current) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            desc,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
