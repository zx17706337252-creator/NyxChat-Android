package com.nyxchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// ─── Fonts ───────────────────────────────────────────────────────────────────
// Using system fonts for reliability. To enable Google Fonts instead:
//   1. Add `implementation("androidx.compose.ui:ui-text-google-fonts")` to build.gradle
//   2. Replace font_certs.xml with real certs from:
//      https://github.com/android/user-interface-samples/blob/main/DownloadableFonts
//   3. Swap FontFamily.Serif below for GoogleFont("Cinzel") / GoogleFont("Crimson Pro")

val CinzelFamily     = FontFamily.Serif   // Cinzel substitute — elegant, works offline
val CrimsonProFamily = FontFamily.Serif   // Crimson Pro substitute — literary serif

// ─── Colors ──────────────────────────────────────────────────────────────────

object NyxColors {
    // Dark mode palette
    val Background       = Color(0xFF04020C)   // slightly deeper void
    val Layer1           = Color(0xFF0C091B)   // card surface
    val Layer2           = Color(0xFF13102A)   // card content / panels
    val Layer3           = Color(0xFF1A1635)   // inputs (sunken)
    val Layer4           = Color(0xFF221E42)   // selected / hover
    val BorderSubtle     = Color(0x14B09EFF)
    val BorderMid        = Color(0x2CB09EFF)
    val BorderHi         = Color(0x58B09EFF)
    // top-edge highlight that simulates a light source from above
    val EdgeHighlight    = Color(0x18FFFFFF)
    val TextPrimary      = Color(0xFFEAE1F6)
    val TextSecond       = Color(0xFFAA9EC0)
    val TextDim          = Color(0xFF5C5475)
    val Accent           = Color(0xFF9D6FFF)
    val AccentSoft       = Color(0xFFC084FC)
    val AccentGlow       = Color(0x409D6FFF)
    val AccentPill       = Color(0x289D6FFF)   // active nav pill background
    val Success          = Color(0xFF34D399)
    val Error            = Color(0xFFF87171)
    val Warning          = Color(0xFFFBBF24)
    val UserBubbleBg     = Color(0x1A9D6FFF)
    val UserBubbleBorder = Color(0x3A9D6FFF)

    // 浅色模式调色板 - 紫色调风格
    val LightBackground   = Color(0xFFF5F0FA)  // 淡紫白背景
    val LightLayer1       = Color(0xFFFAF7FD)  // 卡片表面 - 带紫的白
    val LightLayer2       = Color(0xFFF0E8F7)  // 卡片内容区
    val LightLayer3       = Color(0xFFE8DCF2)  // 输入框 - 稍深的紫
    val LightLayer4       = Color(0xFFDCC8EB)  // 选中/悬停态
    val LightBorderSubtle = Color(0x28B09EFF)
    val LightBorderMid    = Color(0x50B09EFF)
    val LightBorderHi     = Color(0x80B09EFF)
    val LightTextPrimary  = Color(0xFF2D1B4E)  // 深紫文字
    val LightTextSecond   = Color(0xFF5A4E70)
    val LightTextDim      = Color(0xFF8E84A0)
    val LightUserBubbleBg = Color(0x1A9D6FFF)
    val LightUserBubbleBorder = Color(0x3A9D6FFF)
}

// ─── Brush helpers ────────────────────────────────────────────────────────────

/** Card face gradient: slightly lighter top edge → layer bottom */
fun cardFaceBrush() = Brush.verticalGradient(
    listOf(NyxColors.Layer2, NyxColors.Layer1)
)

/** Horizontal accent line painted on the top edge of important cards */
fun topEdgeBrush(color: androidx.compose.ui.graphics.Color = NyxColors.BorderMid) =
    Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent))

/** Radial glow centered on a card */
fun cardGlowBrush(color: androidx.compose.ui.graphics.Color) = Brush.radialGradient(
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
    MaterialTheme(
        colorScheme = if (isDarkMode) NyxDarkColorScheme else NyxLightColorScheme,
        content = content
    )
}
