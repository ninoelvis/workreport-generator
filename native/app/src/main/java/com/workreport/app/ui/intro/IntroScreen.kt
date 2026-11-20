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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workreport.app.R
import com.workreport.app.BuildConfig
import com.workreport.app.ServiceLocator
import com.workreport.app.ui.common.AppTopBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Folder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast

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
            // ---------- 首次使用：挑一个存放周报的文件夹 ----------
            item {
                StoragePickerCard()
            }

            item {
                IntroSection(
                    title = "功能介绍",
                    icon = R.drawable.ic_star,
                ) {
                    IntroBullet(
                        "填一次姓名和时间",
                        "自动算出周数、压缩包文件名和解压密码，不用自己想格式。",
                    )
                    IntroBullet(
                        "记工作 → 汇总 → 生成",
                        "做完一项记一条，可上下调顺序；汇总后导出加密 ZIP 存进你选好的文件夹，" +
                            "也能一键拉起邮箱发送。",
                    )
                    IntroBullet(
                        "脱敏按处放行",
                        "敏感词自动替换；导出前看清哪几处被换了，" +
                            "同一句里重复出现时可以只放行其中一处。",
                    )
                    IntroBullet(
                        "历史报告",
                        "随时回看、直接预览 Excel（跟导出的一模一样），可再下载、再发邮件，" +
                            "也可载入首页继续改。",
                    )
                    IntroBullet(
                        "提醒与分享",
                        "选好星期几，到点提醒你写周报；压缩包可走系统分享面板发给任何人。",
                    )
                    IntroBullet(
                        "存放目录自己挑",
                        "第一次用就可以选一个自己的文件夹存周报压缩包；" +
                            "你手动拷进去的压缩包，App 也会认出来列在历史报告里。",
                    )
                    IntroBullet(
                        "预览可以放大",
                        "预览区点「放大查看」进全屏：内容不再被裁掉，" +
                            "双指捏合缩放、双击放大，左右滑动能看完每一列。",
                    )
                    IntroBullet(
                        "配置备份",
                        "姓名、词库、模板、提醒设置可加密备份，换手机时一键恢复。",
                    )
                    IntroBullet(
                        "全新图标与动效",
                        "全 App 的图标重新画成同一套矢量图，桌面图标也换了新的；" +
                            "深浅色模式自动跟随，" +
                            "底部三个标签页切换时指示条会平滑滑动、图标轻轻缩放，" +
                            "页面之间也有淡入淡出的过渡。",
                    )
                }
            }

            item {
                IntroSection(title = "历史版本更新记录", icon = R.drawable.ic_nav_history) {
                    ChangeLogItem(
                        "v1.6.0",
                        "**整套图标重做**：App 里所有带图标的地方 —— " +
                            "功能区、底部三个标签页、操作按钮、顶栏 Logo、" +
                            "空状态、列表标签、输入框标题 —— " +
                            "原来的 emoji 和系统自带图标**全部换成同一套自绘矢量图标**，" +
                            "风格统一、粗细一致。" +
                            "**桌面图标也换了新的**：标志 + WorkReport 字样，" +
                            "适配各种屏幕形状和启动器裁切，" +
                            "深色模式另有一套配色。" +
                            "**深浅色自动跟随**：图标颜色不再写死，" +
                            "跟着 App 的浅色/深色主题走，" +
                            "设计稿里「深→浅」的层次改用透明度实现，切换主题时层次照样在。" +
                            "**底部导航加了动画**：指示条在三个标签之间平滑平移，" +
                            "选中的图标放大一点、没选中的缩一点，" +
                            "文字只在选中时淡入；" +
                            "页面之间也加了轻微的滑动 + 淡入淡出过渡。" +
                            "⚠️ **导出内容、Excel 样式、压缩包文件名、解压密码一个字都没改** —— " +
                            "这一版只动界面长相和动效。",
                        current = true,
                    )
                    ChangeLogItem(
                        "v1.5.1",
                        "修三个真机反馈的问题：**「我的 → 周报存放目录」里显示的" +
                            "「当前存放位置」本来显示的是目录里第一个文件的文件名**（查错了地方），" +
                            "现在显示目录自己的名字，并补上**完整路径**，一眼看清到底选的是哪个文件夹；" +
                            "**预览区表头的高度**按 Excel 的固定行高对齐，" +
                            "「客户（及用户）反馈」这类长标题换行时不再把整行撑歪，" +
                            "六列表头一样高、一样居中；" +
                            "**「放大查看」全屏里空白的 bug** —— 横向和纵向滚动套在了同一个容器上，" +
                            "把表格宽度压成了 0，所以点开什么也看不见，" +
                            "现在改成横竖分层滚动、宽度写死，**没填内容时也能看到完整的表格样式**。" +
                            "⚠️ 导出格式、压缩包文件名、解压密码依旧一个字没改。",
                        current = false,
                    )
                    ChangeLogItem(
                        "v1.5.0",
                        "**存放目录自己挑**：第一次用就能选一个自己的文件夹存周报压缩包，" +
                            "在「我的 → 周报存放目录」里随时能改；" +
                            "你手动拷进这个文件夹的压缩包，App 也会认出来，" +
                            "在历史报告页一并列出 —— 能预览内容（文件名能推出密码就自动推，" +
                            "推不出来让你手输一次）、也能分享。" +
                            "**预览可以放大**：预览区点「放大查看」进全屏，" +
                            "长的工作梳理不再被裁掉，双指捏合缩放、双击放大，左右滑动看全六列。" +
                            "⚠️ **文件名、压缩包里的 Excel、解压密码一个字都没改** —— " +
                            "只是换个地方存、预览画得更大一点。",
                        current = false,
                    )
                    ChangeLogItem(
                        "v1.4.3",
                        "修复**列表滑到最下面后输入法自己弹出来**的问题 —— " +
                            "v1.4.2 为了让输入框被键盘挡住时能自动滚到可见处，给列表加了" +
                            "「输入法嵌套滚动」，但它的副作用是：列表已经滑到底时，" +
                            "多出来的滚动量会被它交给输入法去**唤起键盘**，" +
                            "于是一松手键盘就弹出来了。现在已经去掉这个修饰符 —— " +
                            "内容区本来就被压在键盘上沿，输入框根本不会被遮住，不需要它。" +
                            "同时加了三道**自动检查**（扫源码）锁住这个行为：" +
                            "代码里不许再出现它、页面里不许再各自加键盘避让、" +
                            "底部避让必须由 AppRoot 统一计算，以后再出类似问题会被测试直接拦下。",
                        current = false,
                    )
                    ChangeLogItem(
                        "v1.4.2",
                        "修复打字时**键盘上方多出一大块空白**的问题 —— " +
                            "页面底部原本已经被底部导航栏抬高过一次，输入法又叠了一层避让，" +
                            "等于多减了一个导航栏的高度，键盘上沿和内容区之间就空出一大条。" +
                            "现在底部避让统一按「导航栏高度和键盘高度取大值」计算，" +
                            "内容区正好停在键盘上沿，一条空白都不留。" +
                            "同时给所有输入框所在的列表补了自动滚动：被键盘挡住时会自己滚到能看见的位置。",
                        current = false,
                    )
                    ChangeLogItem(
                        "v1.4.1",
                        "引导页「功能介绍」精简成 6 条并改成**默认收起**（原来 16 条全部铺开，太长了）；" +
                            "「数据与隐私」补上了**通知权限**的说明 —— 它只用于周报提醒，" +
                            "Android 13 以上首次进首页会主动问，不开也不影响其它功能；" +
                            "修复引导页填姓名时**输入法弹出后看不见自己打的字**（底部区域会被键盘盖住），" +
                            "同时把所有输入框的字号统一提到 16sp —— 低于 16sp 时部分手机的输入法会强制放大整页，" +
                            "输入框被放大裁切，文字反而看不见。",
                        current = false,
                    )
                    ChangeLogItem(
                        "v1.4.0",
                        "脱敏预览升级成**按处放行**：同一句话里出现两次「查询」时会列成两行并带上下文，" +
                            "可以只放行其中一处，另一处照常脱敏；「全部还原」「恢复脱敏」也按处生效。" +
                            "新增系统分享面板、周报提醒（选星期几，16:00-18:00 之间提醒）、" +
                            "个人配置加密备份与恢复、快捷模板复制导入、上周计划一键接续本周工作。" +
                            "导出产物的文件名严格保持「姓名-年年月第周周工作报告.zip」，" +
                            "压缩包内的 Excel 也同名，下载、邮件附件、分享三处拿到的都是同一个名字。" +
                            "「取回下周计划」改成放进「工作记录」：一条一条分开，可以补内容、可以上下调顺序，" +
                            "点「汇总」才进「本周已完成工作梳理」（取回会清空首页现有的工作记录和周报内容）；" +
                            "修复历史报告「取回下周计划」点了首页没反应的问题（改为由首页自己接手，基准是首页当前内容）；" +
                            "修复载入早期报告继续编辑时内容被二次格式化的问题。" +
                            "历史报告的预览区和首页预览区现在**完全一致** —— 显示的就是 Excel 里那两格的原文" +
                            "（已格式化、已脱敏），预览即导出，导出格式一个字都没动；" +
                            "脱敏预览的开关换成跟整体风格统一的形式，不再突兀。",
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
                IntroSection(title = "数据与隐私", icon = R.drawable.ic_lock) {
                    IntroBullet("完全本地", "所有内容只保存在本机，不上传任何服务器。")
                    IntroBullet(
                        "自动保存",
                        "输入内容自动保存在本机，下次打开自动恢复；首次启动会要求填一次姓名，" +
                            "因为压缩包文件名要用。",
                    )
                    IntroBullet(
                        "通知权限",
                        "只用于「周报提醒」——到点弹一条「该写周报了」。" +
                            "Android 13 以上首次进首页时会主动问你要不要开，" +
                            "点「以后再说」就再也不问；不开也不影响其它功能，" +
                            "随时可以去系统设置里开。",
                    )
                    IntroBullet(
                        "权限最小化",
                        "除通知权限外，只申请 Android 8/9 写「下载」目录的权限；" +
                            "不申请相机、位置等无关权限，也不需要联网。",
                    )
                }
            }

            item {
                IntroSection(title = "源码与更新记录", icon = R.drawable.ic_code) {
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
                    // ⚠️ 键盘避让统一由 AppRoot 处理（max(导航栏高度, 输入法高度)），
                    // 这里绝不能再叠一层 insets padding，否则键盘上方会多出一段空白。
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        label = { Text("你的姓名", fontSize = 13.sp) },
                        placeholder = { Text("用于压缩包文件名，如：张三", fontSize = 12.5.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        // 字号给足 16sp —— 低于 16sp 某些系统会自己放大整页，字反而被挤出框
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
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
    @DrawableRes icon: Int,
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
                    painter = painterResource(icon),
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
                painter = painterResource(R.drawable.ic_expand_more),
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

/**
 * 首次启动时挑一个「周报存放目录」的卡片。
 *
 * 用的是系统文件选择器（SAF），不需要额外存储权限。
 * 用户不选也能继续用 —— 那就沿用系统「下载」目录，行为和以前一模一样。
 */
@Composable
private fun StoragePickerCard() {
    val context = LocalContext.current
    val storage = remember { ServiceLocator.storageLocation }
    val treeUri by storage.treeUri.collectAsStateWithLifecycle()
    val dirName by storage.displayNameFlow.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val ok = storage.select(uri)
            Toast.makeText(
                context,
                if (ok) "已选择「${storage.displayName()}」" else "没有拿到访问权限，请再选一次",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "存放周报的文件夹",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (treeUri != null) {
                "现在存到：$dirName。点下面可以换一个，" +
                    "以后在「我的 → 周报存放目录」里随时改。"
            } else {
                "挑一个你自己的文件夹，生成的周报压缩包会直接放进去，" +
                    "在文件管理器里一眼就能找到。也可以跳过，" +
                    "那会用系统「下载」目录（和以前一样）。"
            },
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { picker.launch(null) },
                shape = RoundedCornerShape(9.dp),
            ) {
                Text(if (treeUri != null) "换一个文件夹" else "选择文件夹", fontSize = 13.sp)
            }
            if (treeUri != null) {
                OutlinedButton(
                    onClick = {
                        storage.reset()
                        Toast.makeText(context, "已恢复为系统「下载」目录", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Text("用默认的", fontSize = 13.sp)
                }
            }
        }
    }
}
