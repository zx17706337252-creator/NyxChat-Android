package com.nyxchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// ─── Fonts ───────────────────────────────────────────────────────────────────
val CinzelFamily     = FontFamily.Serif
val CrimsonProFamily = FontFamily.Serif

// ─── Raw color constants (used to build palettes) ────────────────────────────

object NyxColors {
    // Dark palette
    val Background       = Color(0xFF04020C)
    val Layer1           = Color(0xFF0C091B)
    val Layer2           = Color(0xFF13102A)
    val Layer3           = Color(0xFF1A1635)
    val Layer4           = Color(0xFF221E42)
    val BorderSubtle     = Color(0x14B09EFF)
    val BorderMid        = Color(0x2CB09EFF)
    val BorderHi         = Color(0x58B09EFF)
    val EdgeHighlight    = Color(0x18FFFFFF)
    val TextPrimary      = Color(0xFFEAE1F6)
    val TextSecond       = Color(0xFFAA9EC0)
    val TextDim          = Color(0xFF5C5475)
    val Accent           = Color(0xFF9D6FFF)
    val AccentSoft       = Color(0xFFC084FC)
    val AccentGlow       = Color(0x409D6FFF)
    val AccentPill       = Color(0x289D6FFF)
    val Success          = Color(0xFF34D399)
    val Error            = Color(0xFFF87171)
    val Warning          = Color(0xFFFBBF24)
    val UserBubbleBg     = Color(0x1A9D6FFF)
    val UserBubbleBorder = Color(0x3A9D6FFF)

    // Light palette
    val LightBackground   = Color(0xFFF5F0FA)
    val LightLayer1       = Color(0xFFFAF7FD)
    val LightLayer2       = Color(0xFFF0E8F7)
    val LightLayer3       = Color(0xFFE8DCF2)
    val LightLayer4       = Color(0xFFDCC8EB)
    val LightBorderSubtle = Color(0x28B09EFF)
    val LightBorderMid    = Color(0x50B09EFF)
    val LightBorderHi     = Color(0x80B09EFF)
    val LightTextPrimary  = Color(0xFF2D1B4E)
    val LightTextSecond   = Color(0xFF5A4E70)
    val LightTextDim      = Color(0xFF8E84A0)
    val LightUserBubbleBg = Color(0x1A9D6FFF)
    val LightUserBubbleBorder = Color(0x3A9D6FFF)
}

// ─── Palette data class ───────────────────────────────────────────────────────

data class NyxColorPalette(
    val Background       : Color,
    val Layer1           : Color,
    val Layer2           : Color,
    val Layer3           : Color,
    val Layer4           : Color,
    val BorderSubtle     : Color,
    val BorderMid        : Color,
    val BorderHi         : Color,
    val EdgeHighlight    : Color,
    val TextPrimary      : Color,
    val TextSecond       : Color,
    val TextDim          : Color,
    val Accent           : Color,
    val AccentSoft       : Color,
    val AccentGlow       : Color,
    val AccentPill       : Color,
    val Success          : Color,
    val Error            : Color,
    val Warning          : Color,
    val UserBubbleBg     : Color,
    val UserBubbleBorder : Color,
)

val darkNyxColorPalette = NyxColorPalette(
    Background       = NyxColors.Background,
    Layer1           = NyxColors.Layer1,
    Layer2           = NyxColors.Layer2,
    Layer3           = NyxColors.Layer3,
    Layer4           = NyxColors.Layer4,
    BorderSubtle     = NyxColors.BorderSubtle,
    BorderMid        = NyxColors.BorderMid,
    BorderHi         = NyxColors.BorderHi,
    EdgeHighlight    = NyxColors.EdgeHighlight,
    TextPrimary      = NyxColors.TextPrimary,
    TextSecond       = NyxColors.TextSecond,
    TextDim          = NyxColors.TextDim,
    Accent           = NyxColors.Accent,
    AccentSoft       = NyxColors.AccentSoft,
    AccentGlow       = NyxColors.AccentGlow,
    AccentPill       = NyxColors.AccentPill,
    Success          = NyxColors.Success,
    Error            = NyxColors.Error,
    Warning          = NyxColors.Warning,
    UserBubbleBg     = NyxColors.UserBubbleBg,
    UserBubbleBorder = NyxColors.UserBubbleBorder,
)

val lightNyxColorPalette = NyxColorPalette(
    Background       = NyxColors.LightBackground,
    Layer1           = NyxColors.LightLayer1,
    Layer2           = NyxColors.LightLayer2,
    Layer3           = NyxColors.LightLayer3,
    Layer4           = NyxColors.LightLayer4,
    BorderSubtle     = NyxColors.LightBorderSubtle,
    BorderMid        = NyxColors.LightBorderMid,
    BorderHi         = NyxColors.LightBorderHi,
    EdgeHighlight    = NyxColors.EdgeHighlight,    // 高光效果两模式通用
    TextPrimary      = NyxColors.LightTextPrimary,
    TextSecond       = NyxColors.LightTextSecond,
    TextDim          = NyxColors.LightTextDim,
    Accent           = NyxColors.Accent,           // 强调色两模式通用
    AccentSoft       = NyxColors.AccentSoft,
    AccentGlow       = NyxColors.AccentGlow,
    AccentPill       = NyxColors.AccentPill,
    Success          = NyxColors.Success,
    Error            = NyxColors.Error,
    Warning          = NyxColors.Warning,
    UserBubbleBg     = NyxColors.LightUserBubbleBg,
    UserBubbleBorder = NyxColors.LightUserBubbleBorder,
)

/** CompositionLocal — 在任何 @Composable 中读取当前主题调色板 */
val LocalNyxColors = staticCompositionLocalOf { darkNyxColorPalette }

// ─── Brush helpers ────────────────────────────────────────────────────────────

@Composable
fun cardFaceBrush(): Brush {
    val nyx = LocalNyxColors.current
    return Brush.verticalGradient(listOf(nyx.Layer2, nyx.Layer1))
}

@Composable
fun topEdgeBrush(color: Color = LocalNyxColors.current.BorderMid) =
    Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent))

fun cardGlowBrush(color: Color) = Brush.radialGradient(
    listOf(color.copy(0.12f), Color.Transparent)
)

// ─── Character preset colors ──────────────────────────────────────────────────

val CHARACTER_COLORS = listOf(
    0xFFB09EFF to "薰衣紫",
    0xFFF472B6 to "玫瑰粉",
    0xFF34D399 to "薄荷绿",
    0xFF7DD3FC to "天空蓝",
    0xFFFBBF24 to "琥珀金",
    0xFFF87171 to "珊瑚红",
    0xFF94A3B8 to "月光灰",
    0xFFE879F9 to "紫晶",
    0xFF2DD4BF to "青玉",
    0xFFFF8C42 to "暖橙",
    0xFFA78BFA to "薰紫",
    0xFFF9A8D4 to "桃粉",
)

// ─── Material Theme ───────────────────────────────────────────────────────────

private val NyxDarkColorScheme = darkColorScheme(
    primary      = NyxColors.Accent,
    secondary    = NyxColors.AccentSoft,
    background   = NyxColors.Background,
    surface      = NyxColors.Layer1,
    onPrimary    = Color.White,
    onBackground = NyxColors.TextPrimary,
    onSurface    = NyxColors.TextPrimary,
    error        = NyxColors.Error,
)

private val NyxLightColorScheme = lightColorScheme(
    primary      = NyxColors.Accent,
    secondary    = NyxColors.AccentSoft,
    background   = NyxColors.LightBackground,
    surface      = NyxColors.LightLayer1,
    onPrimary    = Color.White,
    onBackground = NyxColors.LightTextPrimary,
    onSurface    = NyxColors.LightTextPrimary,
    error        = NyxColors.Error,
)

@Composable
fun NyxChatTheme(
    isDarkMode: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (isDarkMode) darkNyxColorPalette else lightNyxColorPalette
    CompositionLocalProvider(LocalNyxColors provides palette) {
        MaterialTheme(
            colorScheme = if (isDarkMode) NyxDarkColorScheme else NyxLightColorScheme,
            content = content
        )
    }
}
