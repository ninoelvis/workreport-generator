package com.workreport.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.workreport.app.data.local.ThemeMode
import com.workreport.app.ui.AppRoot
import com.workreport.app.ui.theme.WorkReportTheme

/**
 * 唯一的 Activity。
 *
 * 全部界面由 Compose 绘制，不存在 WebView / 网页资源。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动图背景要跟 App 当前主题一致，否则深色模式下启动会先闪一下白。
        //
        // 坑：App 的深色模式是「App 内手动切换」的（ThemeMode.LIGHT / DARK / SYSTEM），
        // 系统的 values-night 跟随的是**系统**深浅色，跟不到 App 里的选择。
        // 所以这里在 installSplashScreen() 之前按设置里的主题模式，
        // 换成对应的浅色 / 深色启动主题。
        setTheme(
            if (isDarkThemeAtLaunch()) R.style.Theme_WorkReport_Splash_Dark
            else R.style.Theme_WorkReport_Splash,
        )

        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 显式指定系统栏样式：**不要**用 enableEdgeToEdge() 的默认参数。
        //
        // 坑（v1.8.0 修）：默认参数会用 `detectDarkMode` 去读**系统**的深浅色，
        // 而本 App 的深色模式是「App 内手动切换」的（ThemeMode.LIGHT / DARK / SYSTEM），
        // 系统可能一直是浅色。默认参数于是给导航条套了一层**浅色 scrim**，
        // 深色模式下底部就会浮出一条白杠。
        //
        // 这里给两套样式都传透明色，导航条/状态栏一律全透明，
        // 由 WorkReportTheme 按 App 自己的主题去设置图标明暗。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )

        // 首帧绘制完成后由系统自动移除启动图
        splash.setKeepOnScreenCondition { false }

        setContent {
            val settings = ServiceLocator.settings
            val themeMode by settings.themeMode.collectAsState()
            WorkReportTheme(themeMode = themeMode) {
                AppRoot()
            }
        }
    }

    /**
     * 启动瞬间是否应该用深色外观。
     *
     * 直接读 SharedPreferences（SettingsStore 构造时已同步读入），不依赖 Compose，
     * 所以能在 installSplashScreen() 之前拿到结果。
     */
    private fun isDarkThemeAtLaunch(): Boolean {
        val mode = ServiceLocator.settings.themeMode.value
        return when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                night == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}
