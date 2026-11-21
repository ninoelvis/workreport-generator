package com.workreport.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.annotation.DrawableRes
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.workreport.app.R
import com.workreport.app.ServiceLocator
import com.workreport.app.util.BottomAvoidance
import com.workreport.app.ui.common.GlassContentHost
import com.workreport.app.ui.common.rememberGlassBackdrop
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

/**
 * 当前这次页面切换的方向。
 *
 * `AnimatedContentTransitionScope` 里：
 *   · initialState = 正在退出的那一页
 *   · targetState  = 正在进入的那一页
 * 两个 route 一起交给 [navDirection] 算方向。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageDirection(): Int =
    navDirection(
        fromRoute = initialState.destination.route,
        toRoute = targetState.destination.route,
    )

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

    /**
     * 玻璃背景层：整屏内容录制成一层，底栏从这层里采样。
     *
     * 底栏**不占布局位置**（它浮在内容之上），
     * 所以内容区的高度从头到尾都不会因为「底栏出现/消失」而变化——
     * 这就是 v1.6.0 里「进入二级页面内容瞬间下移 64dp」的根治办法。
     */
    val backdrop = rememberGlassBackdrop()

    // 底部避让 = max(系统导航条高度, 输入法高度)
    //
    // 为什么不能各页面自己写 imePadding()？
    //   AppRoot 已经把整个内容区压到键盘上沿了，
    //   如果页面再叠一层 imePadding()，等于多减一次，键盘上方会凭空多出一条空白带
    //   （v1.4.2 修过的那个 bug）。
    //
    // 注意这里取的是**系统导航条**而不是「底栏高度」：
    //   底栏现在是浮在内容之上的玻璃胶囊，不占布局，
    //   内容要从它下面穿过去才有玻璃感，所以内容区只需要避开系统导航条。
    val density = LocalDensity.current
    val bottomAvoidance = with(density) {
        val navBarPx = WindowInsets.navigationBars.getBottom(density)
        val imePx = WindowInsets.ime.getBottom(density)
        BottomAvoidance.compute(navBarPx, imePx).toDp()
    }

    Box(
        Modifier
            .fillMaxSize()
            // 整屏铺一层主题背景色。
            //
            // v1.8.0 修「深色模式底部有白条」：NavHost 因为底部避让把内容顶起来了，
            // 系统导航条那条区域**没有任何 Compose 内容绘制**，
            // 露出来的是窗口底色（themes.xml 里写死的浅色 #F5F6F7）—— 就是那条白杠。
            // 这里先把整屏铺成当前主题的背景色，那条区域就不会再露白。
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 内容层：整屏录制成玻璃背景
        GlassContentHost(backdrop) {
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.HOME.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomAvoidance),
                // 页面切换动画：轻微横向位移 + 淡入淡出
                //
                // 方向由 [navDirection] 决定，不能写死：
                //   底栏 Tab 切换走的是 navigate + popUpTo，navigation-compose 会把 isPop 重置为 false，
                //   所以 Tab 切换**永远走 enterTransition / exitTransition**。
                //   如果这里写死「从右滑入」，那么从「我的」切到「历史报告」（往左退）时方向就是反的。
                // 现在按目标 Tab 的索引算方向：往右切 = 从右滑入，往左切 = 从左滑入。
                enterTransition = {
                    val dir = pageDirection()
                    if (dir == 0) {
                        fadeIn(tween(200))
                    } else {
                        fadeIn(tween(220)) + slideInHorizontally(animationSpec = tween(280)) { full ->
                            if (dir > 0) full / 6 else -full / 6
                        }
                    }
                },
                exitTransition = {
                    val dir = pageDirection()
                    if (dir == 0) {
                        fadeOut(tween(160))
                    } else {
                        // 旧页面往反方向退一点，形成轻微视差（位移比新页面小，避免两层内容打架）
                        fadeOut(tween(180)) + slideOutHorizontally(animationSpec = tween(280)) { full ->
                            if (dir > 0) -full / 10 else full / 10
                        }
                    }
                },
                popEnterTransition = {
                    val dir = pageDirection()
                    if (dir == 0) {
                        fadeIn(tween(200))
                    } else {
                        fadeIn(tween(220)) + slideInHorizontally(animationSpec = tween(280)) { full ->
                            if (dir > 0) full / 6 else -full / 6
                        }
                    }
                },
                popExitTransition = {
                    val dir = pageDirection()
                    if (dir == 0) {
                        fadeOut(tween(160))
                    } else {
                        fadeOut(tween(180)) + slideOutHorizontally(animationSpec = tween(280)) { full ->
                            if (dir > 0) -full / 10 else full / 10
                        }
                    }
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

        /**
         * 悬浮玻璃底栏。
         *
         * 它是根 `Box` 的另一个子节点，**不占布局位置**：
         *   · 一级页面 → 完整显示
         *   · 二级页面 → 淡出并下沉（常驻悬浮，回来时浮起）
         *
         * 因为底栏不参与布局，进入二级页面时内容区高度**一点都没变**，
         * 所以再也不会出现「内容瞬间下移」的卡顿。
         */
        AnimatedBottomBar(
            backdrop = backdrop,
            destinations = TopLevelDestination.entries,
            selectedRoute = currentRoute,
            visible = showBottomBar,
            onSelect = { destination ->
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            // v1.8.0：窄胶囊整体靠右对齐（原来是通栏居中）
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
