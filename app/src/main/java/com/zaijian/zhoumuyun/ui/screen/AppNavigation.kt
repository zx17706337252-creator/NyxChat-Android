package com.zaijian.zhoumuyun.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zaijian.zhoumuyun.ui.theme.AnimDuration
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme

// ─────────────────────────────────────────────────────────────
//  Navigation routes
// ─────────────────────────────────────────────────────────────

sealed class AppRoute(val route: String) {
    object Splash          : AppRoute("splash")
    object World           : AppRoute("world")
    object Characters      : AppRoute("characters")
    object Tasks           : AppRoute("tasks")
    object Profile         : AppRoute("profile")
    object Chat            : AppRoute("chat/{characterId}") {
        fun createRoute(id: Int) = "chat/$id"
    }
    object CharacterDetail : AppRoute("character_detail/{characterId}") {
        fun createRoute(id: Int) = "character_detail/$id"
    }
}

// Bottom nav tabs (root destinations only)
private val bottomNavRoutes = listOf(
    AppRoute.World.route,
    AppRoute.Characters.route,
    AppRoute.Tasks.route,
    AppRoute.Profile.route,
)

// Detail pages (slide-in/out transitions)
private val detailRoutes = listOf(
    "chat/",
    "character_detail/",
)

private data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

private val bottomNavItems = listOf(
    BottomNavItem(AppRoute.World.route,      Icons.Outlined.Home,        "公馆"),
    BottomNavItem(AppRoute.Characters.route, Icons.Outlined.MenuBook,    "书架"),
    BottomNavItem(AppRoute.Tasks.route,      Icons.Outlined.CheckCircle, "任务"),
    BottomNavItem(AppRoute.Profile.route,    Icons.Outlined.Person,      "我"),
)

// ─────────────────────────────────────────────────────────────
//  Transition helpers  （设计规范 §18）
//
//  Tab 切换   → crossfade 150ms
//  进入详情页  → slideInRight + fadeIn 250ms
//  返回        → slideOutRight + fadeOut 200ms
// ─────────────────────────────────────────────────────────────

/** 判断目标路由是否是详情页（需要 slide 动画） */
private fun String?.isDetailRoute() =
    detailRoutes.any { prefix -> this?.startsWith(prefix) == true }

// ─── Enter specs ────────────────────────────────────────────

/** Tab 切换：crossfade（仅 fade，无 scale） */
private val tabEnter = fadeIn(tween(AnimDuration.fast))

/** 进入详情页：从右侧滑入 + 淡入 */
private val detailEnter =
    slideInHorizontally(tween(AnimDuration.pageSwitch)) { it / 5 } +
    fadeIn(tween(AnimDuration.pageSwitch))

// ─── Exit specs ─────────────────────────────────────────────

/** Tab 切换：crossfade */
private val tabExit = fadeOut(tween(AnimDuration.fast))

/** 从 Tab 跳到详情页时，Tab 页轻微淡出（不滑出，避免晕眩） */
private val tabToDetailExit = fadeOut(tween(AnimDuration.fast))

/** 返回：向右滑出 + 淡出 */
private val detailPopExit =
    slideOutHorizontally(tween(AnimDuration.pageSwitch - 50)) { it / 5 } +
    fadeOut(tween(AnimDuration.pageSwitch - 50))

/** 详情页本身退出（push 到另一详情时） */
private val detailExit =
    slideOutHorizontally(tween(AnimDuration.pageSwitch)) { -it / 8 } +
    fadeOut(tween(AnimDuration.pageSwitch))

// ─────────────────────────────────────────────────────────────
//  Bottom nav helper composables
// ─────────────────────────────────────────────────────────────

/** 导航栏选中指示点（提取为独立 composable 避免 RowScope 作用域污染） */
@Composable
private fun SelectionIndicator(visible: Boolean, accent: Color) {
    AnimatedVisibility(
        visible  = visible,
        enter    = fadeIn(tween(AnimDuration.fast)) +
                   scaleIn(tween(AnimDuration.fast), 0.5f),
        exit     = fadeOut(tween(AnimDuration.fast)) +
                   scaleOut(tween(AnimDuration.fast), 0.5f),
        modifier = Modifier.padding(top = 28.dp),
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(accent, CircleShape),
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  App scaffold with bottom navigation
// ─────────────────────────────────────────────────────────────

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val colors        = ZaijianTheme.colors
    val appType       = ZaijianTheme.typography
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route

    // 全屏页面（聊天、详情）隐藏底部导航
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        containerColor = colors.bgBase,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor  = if (colors.isDark)
                        colors.bgCard.copy(alpha = 0.88f)
                    else
                        colors.bgBase.copy(alpha = 0.88f),
                    tonalElevation = 0.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick  = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon = {
                                Box(contentAlignment = Alignment.BottomCenter) {
                                    Icon(
                                        imageVector        = item.icon,
                                        contentDescription = item.label,
                                        modifier           = Modifier.size(24.dp),
                                    )
                                    // 选中指示点：4dp 圆点 fade+scale 动画
                                    SelectionIndicator(
                                        visible = selected,
                                        accent  = colors.accent,
                                    )
                                }
                            },
                            label  = { Text(item.label, style = appType.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = colors.accent,
                                selectedTextColor   = colors.accent,
                                unselectedIconColor = colors.textSecondary,
                                unselectedTextColor = colors.textSecondary,
                                indicatorColor      = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = AppRoute.Splash.route,
            modifier         = Modifier
                .fillMaxSize()
                .padding(innerPadding),

            // ── 默认进入动画：Tab crossfade ──────────────────
            enterTransition  = {
                val target = targetState.destination.route
                if (target.isDetailRoute()) detailEnter else tabEnter
            },

            // ── 默认退出动画 ──────────────────────────────────
            exitTransition   = {
                val target = targetState.destination.route
                if (target.isDetailRoute()) tabToDetailExit else tabExit
            },

            // ── popBackStack 时的进入（详情页弹出后 Tab 重新出现）
            popEnterTransition = {
                fadeIn(tween(AnimDuration.fast))
            },

            // ── popBackStack 时的退出（详情页向右滑出）
            popExitTransition = {
                val initial = initialState.destination.route
                if (initial.isDetailRoute()) detailPopExit else tabExit
            },
        ) {
            // ── Splash ─────────────────────────────────────────
            composable(
                route           = AppRoute.Splash.route,
                enterTransition = { fadeIn(tween(AnimDuration.fast)) },
                exitTransition  = { fadeOut(tween(AnimDuration.pageSwitch)) },
            ) {
                SplashScreen(
                    onFinished = {
                        navController.navigate(AppRoute.World.route) {
                            popUpTo(AppRoute.Splash.route) { inclusive = true }
                        }
                    },
                )
            }

            // ── Root tabs ──────────────────────────────────────

            composable(AppRoute.World.route) {
                WorldScreen(
                    onNavigateToChat    = { id -> navController.navigate(AppRoute.Chat.createRoute(id)) },
                    onNavigateToProfile = { id -> navController.navigate(AppRoute.CharacterDetail.createRoute(id)) },
                    onNavigateToTasks   = { navController.navigate(AppRoute.Tasks.route) },
                )
            }

            composable(AppRoute.Characters.route) {
                CharacterScreen(
                    onNavigateToDetail = { id ->
                        navController.navigate(AppRoute.CharacterDetail.createRoute(id))
                    },
                    onNavigateToChat   = { id ->
                        navController.navigate(AppRoute.Chat.createRoute(id))
                    },
                )
            }

            composable(AppRoute.Tasks.route)   { TaskCenterScreen() }
            composable(AppRoute.Profile.route) { ProfileScreen() }

            // ── Detail pages ───────────────────────────────────

            composable(
                route     = AppRoute.Chat.route,
                arguments = listOf(navArgument("characterId") { type = NavType.IntType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("characterId") ?: return@composable
                ChatScreen(
                    characterId         = id,
                    onBack              = { navController.popBackStack() },
                    onNavigateToProfile = { charId ->
                        navController.navigate(AppRoute.CharacterDetail.createRoute(charId))
                    },
                )
            }

            composable(
                route     = AppRoute.CharacterDetail.route,
                arguments = listOf(navArgument("characterId") { type = NavType.IntType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("characterId") ?: return@composable
                CharacterDetailScreen(
                    characterId = id,
                    onBack      = { navController.popBackStack() },
                    onStartChat = { charId ->
                        navController.navigate(AppRoute.Chat.createRoute(charId))
                    },
                )
            }
        }
    }
}