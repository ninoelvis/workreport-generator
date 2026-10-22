package com.workreport.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.workreport.app.ui.AppRoot
import com.workreport.app.ui.theme.WorkReportTheme

/**
 * 唯一的 Activity。
 *
 * 全部界面由 Compose 绘制，不存在 WebView / 网页资源。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}
