package com.workreport.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workreport.app.ui.common.SettingsDivider
import com.workreport.app.ui.common.SettingsGroup
import com.workreport.app.ui.common.SettingsRow

/**
 * 「我的」页 —— 设置首页。
 *
 * 这里只放入口，每个配置项点进去是一个独立的小页面，
 * 避免所有设置挤在一屏里。
 */
@Composable
fun ProfileScreen(
    onShowIntro: () -> Unit = {},
    /** 跳到某个配置子页面 */
    onOpen: (ProfileRoute) -> Unit = {},
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 14.dp, end = 14.dp, top = 0.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 2.dp),
            ) {
                Text(
                    "我的",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "个人设置与快捷配置，点任意一项进去配置",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsGroup {
                SettingsRow(
                    icon = "👤",
                    title = "个人信息",
                    subtitle = "姓名，用于生成文件名",
                    value = state.userName.ifEmpty { "未填写" },
                    onClick = { onOpen(ProfileRoute.NAME) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = "⚡",
                    title = "快捷记录模板",
                    subtitle = "把常写的重复内容存成模板，一键插入",
                    value = "${state.workShortcuts.size + state.planShortcuts.size} 条",
                    onClick = { onOpen(ProfileRoute.SHORTCUTS) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = "🛡️",
                    title = "敏感词库",
                    subtitle = "「纪委」自动变「jw」这类替换规则",
                    value = "${state.sensitiveWords.size} 个",
                    onClick = { onOpen(ProfileRoute.SENSITIVE) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = "🌗",
                    title = "外观",
                    subtitle = "浅色 / 深色 / 跟随系统",
                    value = when (state.themeMode) {
                        com.workreport.app.data.local.ThemeMode.SYSTEM -> "跟随系统"
                        com.workreport.app.data.local.ThemeMode.LIGHT -> "浅色"
                        com.workreport.app.data.local.ThemeMode.DARK -> "深色"
                    },
                    onClick = { onOpen(ProfileRoute.APPEARANCE) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = "📨",
                    title = "邮件设置",
                    subtitle = "生成后是否自动拉起邮箱",
                    value = if (state.autoSendMail) "自动发送" else "每次询问",
                    onClick = { onOpen(ProfileRoute.MAIL) },
                )
            }
        }

        item {
            SettingsGroup {
                SettingsRow(
                    icon = "📄",
                    title = "功能介绍与更新记录",
                    subtitle = "看看这个版本都改了什么",
                    onClick = onShowIntro,
                )
                SettingsDivider()
                SettingsRow(
                    icon = "🧹",
                    title = "数据管理",
                    subtitle = "清除本机保存的所有数据",
                    onClick = { onOpen(ProfileRoute.DATA) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = "ℹ️",
                    title = "关于",
                    subtitle = "版本号、仓库地址与免责声明",
                    value = com.workreport.app.BuildConfig.VERSION_NAME,
                    onClick = { onOpen(ProfileRoute.ABOUT) },
                )
            }
        }
    }
}

/** 「我的」页下的配置子页面 */
enum class ProfileRoute(val route: String) {
    NAME("profile/name"),
    SHORTCUTS("profile/shortcuts"),
    SENSITIVE("profile/sensitive"),
    APPEARANCE("profile/appearance"),
    MAIL("profile/mail"),
    DATA("profile/data"),
    ABOUT("profile/about"),
}
