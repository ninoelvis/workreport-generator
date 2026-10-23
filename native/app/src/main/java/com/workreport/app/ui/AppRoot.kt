package com.workreport.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.workreport.app.ServiceLocator
import com.workreport.app.ui.history.HistoryScreen
import com.workreport.app.ui.home.HomeScreen
import com.workreport.app.ui.intro.IntroScreen
import com.workreport.app.ui.profile.ProfileAboutPage
import com.workreport.app.ui.profile.ProfileAppearancePage
import com.workreport.app.ui.profile.ProfileDataPage
import com.workreport.app.ui.profile.ProfileMailPage
import com.workreport.app.ui.profile.ProfileNamePage
import com.workreport.app.ui.profile.ProfileRoute
import com.workreport.app.ui.profile.ProfileScreen
import com.workreport.app.ui.profile.ProfileSensitivePage
import com.workreport.app.ui.profile.ProfileShortcutsPage

/** 三个主 Tab */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "首页", Icons.Filled.Description),
    HISTORY("history", "历史报告", Icons.Filled.History),
    PROFILE("profile", "我的", Icons.Filled.Person),
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
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = destination.label)
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onOpenShortcuts = { navController.navigate(ProfileRoute.SHORTCUTS.route) },
                )
            }
            composable(TopLevelDestination.HISTORY.route) {
                HistoryScreen()
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
            composable(ProfileRoute.DATA.route) {
                ProfileDataPage(onBack = { navController.popBackStack() })
            }
            composable(ProfileRoute.ABOUT.route) {
                ProfileAboutPage(onBack = { navController.popBackStack() })
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
