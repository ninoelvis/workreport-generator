package com.workreport.app.ui

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.workreport.app.ServiceLocator
import com.workreport.app.ui.history.HistoryScreen
import com.workreport.app.ui.home.HomeScreen
import com.workreport.app.ui.intro.IntroDialog
import com.workreport.app.ui.profile.ProfileScreen

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

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // 功能介绍页：首次启动自动弹出，之后可从「我的」页手动打开
    val settings = ServiceLocator.settings
    val introShown by settings.introShown.collectAsStateWithLifecycle()
    var showIntro by remember { mutableStateOf(false) }
    var introForced by remember { mutableStateOf(false) }

    LaunchedEffect(introShown) {
        if (!introShown) {
            showIntro = true
            introForced = true
        }
    }

    Scaffold(
        bottomBar = {
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
                HomeScreen()
            }
            composable(TopLevelDestination.HISTORY.route) {
                HistoryScreen()
            }
            composable(TopLevelDestination.PROFILE.route) {
                ProfileScreen(onShowIntro = {
                    introForced = false
                    showIntro = true
                })
            }
        }
    }

    if (showIntro) {
        IntroDialog(
            dismissOnOutsideClick = !introForced,
            onDismiss = { dontShowAgain ->
                // 首次启动看过之后就不再自动弹出；手动打开的不改这个标记
                if (introForced) settings.setIntroShown(true)
                introForced = false
                showIntro = false
                if (dontShowAgain) settings.setIntroShown(true)
            },
        )
    }
}
