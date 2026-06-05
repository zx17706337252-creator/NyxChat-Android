package com.zaijian.zhoumuyun.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
//  Material3 color scheme bridges
//  We keep a thin M3 scheme so Compose internals (ripple,
//  surface, etc.) still work correctly.
// ─────────────────────────────────────────────────────────────
private val M3LightScheme = lightColorScheme(
    primary          = Palette.Slate,
    onPrimary        = Color.White,
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Palette.Ink900,
    background       = Palette.Snow,
    onBackground     = Palette.Ink900,
    surface          = Palette.Paper,
    onSurface        = Palette.Ink900,
    surfaceVariant   = Palette.AccentSoft,
    onSurfaceVariant = Palette.Ink600,
    outline          = Palette.Border,
)

private val M3DarkScheme = darkColorScheme(
    primary          = Palette.Slate,
    onPrimary        = Color.White,
    primaryContainer = Palette.Slate.copy(alpha = 0.20f),
    onPrimaryContainer = Palette.NightText,
    background       = Palette.Night,
    onBackground     = Palette.NightText,
    surface          = Palette.NightCard,
    onSurface        = Palette.NightText,
    surfaceVariant   = Palette.NightElevated,
    onSurfaceVariant = Palette.NightTextSub,
    outline          = Palette.NightBorder,
)

// ─────────────────────────────────────────────────────────────
//  Enum for manual theme override
// ─────────────────────────────────────────────────────────────
enum class AppTheme { LIGHT, DARK, SYSTEM }

// ─────────────────────────────────────────────────────────────
//  Theme entry point
// ─────────────────────────────────────────────────────────────
@Composable
fun ZaijianTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (appTheme) {
        AppTheme.LIGHT  -> false
        AppTheme.DARK   -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val colors     = if (useDark) DarkColors else LightColors
    val m3Scheme   = if (useDark) M3DarkScheme else M3LightScheme
    val typography = DefaultTypography
    val dimens     = AppDimens()

    CompositionLocalProvider(
        LocalAppColors     provides colors,
        LocalAppTypography provides typography,
        LocalAppDimens     provides dimens,
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            content     = content,
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Convenience accessors  (use anywhere inside ZaijianTheme)
// ─────────────────────────────────────────────────────────────
object ZaijianTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current

    val typography: AppTypography
        @Composable @ReadOnlyComposable
        get() = LocalAppTypography.current

    val dimens: AppDimens
        @Composable @ReadOnlyComposable
        get() = LocalAppDimens.current
}
