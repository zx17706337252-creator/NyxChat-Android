package com.zaijian.zhoumuyun.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object Palette {
    val Ink900   = Color(0xFF2C2118)
    val Ink600   = Color(0xFF7A6A56)
    val Ink300   = Color(0xFFBEAE98)
    val White    = Color(0xFFFFFFFF)

    val Cream      = Color(0xFFF5F0E8)
    val Parchment  = Color(0xFFFBF7F0)
    val Border     = Color(0xFFE0D4C0)
    val AccentSoft = Color(0xFFF0E8D8)

    val Night         = Color(0xFF12100A)
    val NightCard     = Color(0xFF1E1A12)
    val NightElevated = Color(0xFF2A2418)
    val NightBorder   = Color(0xFF3A3020)
    val NightText     = Color(0xFFEDE8DE)
    val NightTextSub  = Color(0xFF8A7E68)

    val Gold     = Color(0xFFC4A46A)
    val GoldSoft = Color(0xFFF0E8D0)

    val Online  = Color(0xFF6BCB8B)
    val Idle    = Color(0xFFF6C858)
    val Focused = Color(0xFF8FA8C9)
    val Offline = Color(0xFFBCC3CE)

    val TaskActive = Color(0xFF5B9CF6)
    val TaskPaused = Color(0xFFF6C858)
    val TaskDone   = Color(0xFF6BCB8B)
    val TaskFailed = Color(0xFFF47067)

    // M3 scheme bridge colors
    val Slate = Color(0xFF8A9FB5)
    val Snow  = Color(0xFFFBF7F0)
    val Paper = Color(0xFFF5F0E8)

    // 角色专属色（与角色人设强绑定，保留）
    val Difa     = Color(0xFF8A9FB5)
    val Luna     = Color(0xFFB49AC6)
    val Yifu     = Color(0xFF9CC2AE)
    val Youxi    = Color(0xFF8FA8C9)
    val Suofiya  = Color(0xFFA8A8C8)
    val GuLan    = Color(0xFFC4A882)
    val MingMei  = Color(0xFFC89AA3)
    val MoWanning = Color(0xFFE3B17A)
    val JiangFan = Color(0xFF9BAAB8)
}

@Immutable
data class AppColors(
    val bgBase: Color,
    val bgCard: Color,
    val bgElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val accent: Color,
    val accentSoft: Color,
    val statusActive: Color,
    val statusIdle: Color,
    val statusFocused: Color,
    val statusOffline: Color,
    val taskActive: Color,
    val taskPaused: Color,
    val taskDone: Color,
    val taskFailed: Color,
    val isDark: Boolean,
)

val LightColors = AppColors(
    bgBase        = Palette.Cream,
    bgCard        = Palette.Parchment,
    bgElevated    = Palette.Parchment,
    border        = Palette.Border,
    textPrimary   = Palette.Ink900,
    textSecondary = Palette.Ink600,
    textDisabled  = Palette.Ink300,
    accent        = Palette.Gold,
    accentSoft    = Palette.AccentSoft,
    statusActive  = Palette.Online,
    statusIdle    = Palette.Idle,
    statusFocused = Palette.Focused,
    statusOffline = Palette.Offline,
    taskActive    = Palette.TaskActive,
    taskPaused    = Palette.TaskPaused,
    taskDone      = Palette.TaskDone,
    taskFailed    = Palette.TaskFailed,
    isDark        = false,
)

val DarkColors = AppColors(
    bgBase        = Palette.Night,
    bgCard        = Palette.NightCard,
    bgElevated    = Palette.NightElevated,
    border        = Palette.NightBorder,
    textPrimary   = Palette.NightText,
    textSecondary = Palette.NightTextSub,
    textDisabled  = Palette.Ink600,
    accent        = Palette.Gold,
    accentSoft    = Palette.Gold.copy(alpha = 0.15f),
    statusActive  = Palette.Online,
    statusIdle    = Palette.Idle,
    statusFocused = Palette.Focused,
    statusOffline = Palette.Offline,
    taskActive    = Palette.TaskActive,
    taskPaused    = Palette.TaskPaused,
    taskDone      = Palette.TaskDone,
    taskFailed    = Palette.TaskFailed,
    isDark        = true,
)

val LocalAppColors = staticCompositionLocalOf { LightColors }
