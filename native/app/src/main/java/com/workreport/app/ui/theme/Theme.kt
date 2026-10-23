package com.workreport.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.workreport.app.data.local.ThemeMode

private val LightColors = lightColorScheme(
    primary = FeishuBlue,
    onPrimary = ColorWhite,
    primaryContainer = FeishuBlueLight,
    onPrimaryContainer = FeishuBlue,
    background = PageBgLight,
    onBackground = TextPrimaryLight,
    surface = CardBgLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = RecordBgLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    outlineVariant = BorderLight,
    error = DangerLight,
)

private val DarkColors = darkColorScheme(
    primary = FeishuBlue,
    onPrimary = ColorWhite,
    primaryContainer = FeishuBlueLightDark,
    onPrimaryContainer = FeishuBlue,
    background = PageBgDark,
    onBackground = TextPrimaryDark,
    surface = CardBgDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = RecordBgDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = DangerDark,
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Default,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)

/**
 * Excel 预览表的专用配色。
 *
 * 预览表要还原 Excel 的观感（表头是浅蓝底），但硬编码浅色在深色模式下会
 * 出现「深底深字」看不清的情况，所以按深浅两套分别给色。
 */
data class PreviewColors(
    /** 表头行底色 */
    val headerBg: Color,
    /** 表头行文字色 */
    val headerInk: Color,
    /** 标题行（A1 合并格）文字色 */
    val titleInk: Color,
    /** 表格边框色：深色模式下默认的 outline 太淡，表格结构会糊掉 */
    val border: Color,
)

private val LightPreviewColors = PreviewColors(
    headerBg = Color(0xFFB5C6EA),
    headerInk = Color(0xFF1F2329),
    titleInk = TextPrimaryLight,
    border = BorderLight,
)

private val DarkPreviewColors = PreviewColors(
    // 深色模式用沉下去的蓝灰做表头，避免一整块亮色刺眼
    headerBg = Color(0xFF39456B),
    headerInk = Color(0xFFE6EAF6),
    titleInk = TextPrimaryDark,
    // 默认的 BorderDark（3E3E3E）在卡片底色上看不出表格轮廓，这里提亮到能看出轮廓
    border = Color(0xFF757575),
)

/** 供预览表取用的配色 */
val LocalPreviewColors = staticCompositionLocalOf { LightPreviewColors }

/**
 * 应用主题。
 *
 * @param themeMode 手动指定浅色/深色，或跟随系统
 */
@Composable
fun WorkReportTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // 系统栏保持透明，让页面背景自然延伸上去，避免出现割裂的色块
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalPreviewColors provides if (darkTheme) DarkPreviewColors else LightPreviewColors,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content,
        )
    }
}
