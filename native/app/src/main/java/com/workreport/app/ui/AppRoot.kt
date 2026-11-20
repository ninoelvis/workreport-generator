package com.workreport.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.workreport.app.R
import com.workreport.app.ServiceLocator
import com.workreport.app.util.BottomAvoidance
import com.workreport.app.ui.debug.DebugPage
import com.workreport.app.ui.history.HistoryScreen
import com.workreport.app.ui.home.HomeScreen
import com.workreport.app.ui.intro.IntroScreen
import com.workreport.app.ui.profile.ProfileAboutPage
import com.workreport.app.ui.profile.ProfileAppearancePage
import com.workreport.app.ui.profile.BackupPage
import com.workreport.app.ui.profile.ProfileDataPage
import com.workreport.app.ui.profile.ProfileMailPage
import com.workreport.app.ui.profile.ProfileNamePage
import com.workreport.app.ui.profile.ProfileRoute
import com.workreport.app.ui.profile.ProfileScreen
import com.workreport.app.ui.profile.ProfileSensitivePage
import com.workreport.app.ui.profile.ProfileStoragePage
import com.workreport.app.ui.profile.ProfileShortcutsPage
import com.workreport.app.ui.reminder.ReminderPage

/** 三个主 Tab */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    /** 导航图标（矢量资源），深浅色由 Compose tint 跟随主题 */
    @DrawableRes val icon: Int,
) {
    HOME("home", "首页", R.drawable.ic_nav_home),
    HISTORY("history", "历史报告", R.drawable.ic_nav_history),
    PROFILE("profile", "我的", R.drawable.ic_nav_profile),
}

/** 引导页路由 */
private const val ROUTE_INTRO = "intro"

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val settings = ServiceLocator.settings

    /** 首次启动强制展示引导页：返回键不生效，必须看完或点掉 */
    var introForced by rememberSaveable { mutableStateOf(false) }

    // 首次启动自动进入引导页
    LaunchedEffect(Unit) {
        // 已经停在引导页上时不重复导航（旋转屏幕会重跑这个副作用）
        val alreadyOnIntro = navController.currentDestination?.route == ROUTE_INTRO
        if (!settings.introShown.value && !alreadyOnIntro) {
            introForced = true
            navController.navigate(ROUTE_INTRO)
        }
    }

    /** 只有主 Tab 页面显示底部导航栏；引导页和配置子页面都是整屏的 */
    val showBottomBar = currentRoute in TopLevelDestination.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AnimatedBottomBar(
                    destinations = TopLevelDestination.entries,
                    selectedRoute = currentRoute,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        // 底部避让 = max(底部导航栏高度, 输入法高度)
        //
        // 为什么不能各页面自己写 imePadding()？
        //   Scaffold 已经用 innerPadding 把内容区抬高了一个导航栏的高度，
        //   如果页面再叠一层 imePadding()，等于把「导航栏高度」多减了一次，
        //   键盘上方就会凭空多出一条空白带（实测就是用户看到的那块灰色）。
        //
        // 取两者最大值：
        //   · 没弹键盘 → 留导航栏的位置，底部 Tab 不会被压住
        //   · 弹了键盘 → 留键盘的高度，内容正好停在键盘上沿，不多不少
        val density = LocalDensity.current
        val bottomAvoidance = with(density) {
            val navBarPx = innerPadding.calculateBottomPadding().toPx()
            val imePx = WindowInsets.ime.getBottom(density)
            BottomAvoidance.compute(navBarPx.toInt(), imePx).toDp()
        }
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomAvoidance),
            // 页面切换动画：轻微横向位移 + 淡入淡出
            enterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(animationSpec = tween(280)) { full -> full / 6 }
            },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(animationSpec = tween(280)) { full -> -full / 6 }
            },
            popExitTransition = {
                fadeOut(tween(180)) + slideOutHorizontally(animationSpec = tween(280)) { full -> full / 6 }
            },
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onOpenShortcuts = { navController.navigate(ProfileRoute.SHORTCUTS.route) },
                )
            }
            composable(TopLevelDestination.HISTORY.route) {
                HistoryScreen(
                    onGoHome = {
                        navController.navigate(TopLevelDestination.HOME.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(TopLevelDestination.PROFILE.route) {
                ProfileScreen(
                    onShowIntro = {
                        introForced = false
                        navController.navigate(ROUTE_INTRO)
                    },
                    onOpen = { route -> navController.navigate(route.route) },
                )
            }

            // ---------- 「我的」页下的配置子页面 ----------
            composable(ProfileRoute.NAME.route) {
                ProfileNamePage(onBack = { navController.popBackStack() })
            }
            composable(ProfileRoute.SHORTCUTS.route) {
                ProfileShortcutsPage(onBack = { navController.popBackStack() })
            }
            composable(ProfileRoute.SENSITIVE.route) {
                ProfileSensitivePage(onBack = { navController.popBackStack() })
            }
            composable(ProfileRoute.APPEARANCE.route) {
                ProfileAppearancePage(onBack = { navController.popBackStack() })
            }
            composable(ProfileRoute.MAIL.route) {
                ProfileMailPage(onBack = { navController.popBackStack() })
            }
            composable(ProfileRoute.REMINDER.route) {
                ReminderPage(onBack = { navController.popBackStack() })
            }
            composable(ProfileRoute.STORAGE.route) {
                ProfileStoragePage(onBack = { navController.popBackStack() })
            }
            composable(ProfileRoute.BACKUP.route) {
                BackupPage(onBack = { navController.popBackStack() })
            }
            composable(ProfileRoute.DATA.route) {
                ProfileDataPage(onBack = { navController.popBackStack() })
            }
            composable(ProfileRoute.ABOUT.route) {
                ProfileAboutPage(
                    onBack = { navController.popBackStack() },
                    // 连点版本号 7 次打开隐藏的调试页
                    onOpenDebug = { navController.navigate(ProfileRoute.DEBUG.route) },
                )
            }

            // 隐藏的调试页：入口在「关于」页连点版本号 7 次
            composable(ProfileRoute.DEBUG.route) {
                DebugPage(onBack = { navController.popBackStack() })
            }

            // ---------- 功能介绍页（整屏，不是弹窗）----------
            composable(ROUTE_INTRO) {
                // 首次启动时吞掉返回键，避免用户直接退出 App
                BackHandler(enabled = introForced) { }
                IntroScreen(
                    forced = introForced,
                    onClose = {
                        if (introForced) settings.setIntroShown(true)
                        introForced = false
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
