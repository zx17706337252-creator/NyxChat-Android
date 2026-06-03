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
// Design philosophy: deep space-noir with warm violet undertones.
// Backgrounds use very dark desaturated purples (not pure black) for depth.
// Surfaces are layered with subtle elevation via slight lightness steps.
// Accent is a rich violet-indigo, never oversaturated — complemented by
// a warm rose-gold highlight. Gradients are directional for dimensionality.

object NyxColors {
    // ── Dark palette ─────────────────────────────────────────────────────
    // Base layers: deep indigo-charcoal, not pure black — warmer and more refined
    val Background       = Color(0xFF080610)   // near-black with indigo tint
    val Layer1           = Color(0xFF0F0C1E)   // card floors, nav bar
    val Layer2           = Color(0xFF171426)   // elevated cards, bubbles
    val Layer3           = Color(0xFF1F1B32)   // modals, inputs
    val Layer4           = Color(0xFF28253F)   // tooltips, hover states

    // Frosted glass tint for overlays
    val GlassTint        = Color(0xCC0F0C1E)   // 80% Layer1 for backdrop blur feel

    // Borders use a cool-violet at various opacities
    val BorderSubtle     = Color(0x12A594F9)   // barely-there separator
    val BorderMid        = Color(0x28A594F9)   // card outlines
    val BorderHi         = Color(0x50A594F9)   // focused / selected
    val BorderGlow       = Color(0x18C4B5FD)   // very soft outer glow stroke

    // Surface edge highlight — top-edge "glass" shimmer
    val EdgeHighlight    = Color(0x14FFFFFF)
    val EdgeHighlight2   = Color(0x08FFFFFF)   // even softer, bottom/side edges

    // Text hierarchy
    val TextPrimary      = Color(0xFFEDE8F8)   // slightly warm white
    val TextSecond       = Color(0xFFB0A8CC)   // secondary — muted lavender
    val TextDim          = Color(0xFF6A607E)   // timestamps, captions
    val TextDisabled     = Color(0xFF3F3855)

    // Accent: rich violet-indigo — not electric, more like deep amethyst
    val Accent           = Color(0xFF8B5CF6)   // primary interactive
    val AccentBright     = Color(0xFFA78BFA)   // hover / active highlight
    val AccentSoft       = Color(0xFFC4B5FD)   // chip text, secondary accents
    val AccentGlow       = Color(0x308B5CF6)   // shadow glow
    val AccentPill       = Color(0x228B5CF6)   // pill background
    val AccentDeep       = Color(0xFF6D28D9)   // gradient dark end

    // User bubble: warm indigo-blue tint
    val UserBubbleBg     = Color(0x168B5CF6)
    val UserBubbleBorder = Color(0x348B5CF6)
    val UserBubbleGrad1  = Color(0xFF110E22)   // gradient start
    val UserBubbleGrad2  = Color(0xFF0D0B1C)   // gradient end

    // Semantic
    val Success          = Color(0xFF34D399)
    val Error            = Color(0xFFFC8181)
    val Warning          = Color(0xFFFBBF24)

    // ── Light palette ─────────────────────────────────────────────────────
    val LightBackground   = Color(0xFFF2EEF9)
    val LightLayer1       = Color(0xFFFAF8FE)
    val LightLayer2       = Color(0xFFF3EDF9)
    val LightLayer3       = Color(0xFFEAE1F4)
    val LightLayer4       = Color(0xFFDED3EE)
    val LightBorderSubtle = Color(0x1E8B5CF6)
    val LightBorderMid    = Color(0x3C8B5CF6)
    val LightBorderHi     = Color(0x708B5CF6)
    val LightTextPrimary  = Color(0xFF1E1540)
    val LightTextSecond   = Color(0xFF4E4568)
    val LightTextDim      = Color(0xFF8E85A5)
    val LightUserBubbleBg = Color(0x148B5CF6)
    val LightUserBubbleBorder = Color(0x308B5CF6)
}

// ─── Palette data class ───────────────────────────────────────────────────────

data class NyxColorPalette(
    val Background       : Color,
    val Layer1           : Color,
    val Layer2           : Color,
    val Layer3           : Color,
    val Layer4           : Color,
    val GlassTint        : Color,
    val BorderSubtle     : Color,
    val BorderMid        : Color,
    val BorderHi         : Color,
    val BorderGlow       : Color,
    val EdgeHighlight    : Color,
    val EdgeHighlight2   : Color,
    val TextPrimary      : Color,
    val TextSecond       : Color,
    val TextDim          : Color,
    val TextDisabled     : Color,
    val Accent           : Color,
    val AccentBright     : Color,
    val AccentSoft       : Color,
    val AccentGlow       : Color,
    val AccentPill       : Color,
    val AccentDeep       : Color,
    val Success          : Color,
    val Error            : Color,
    val Warning          : Color,
    val UserBubbleBg     : Color,
    val UserBubbleBorder : Color,
    val UserBubbleGrad1  : Color,
    val UserBubbleGrad2  : Color,
)

val darkNyxColorPalette = NyxColorPalette(
    Background       = NyxColors.Background,
    Layer1           = NyxColors.Layer1,
    Layer2           = NyxColors.Layer2,
    Layer3           = NyxColors.Layer3,
    Layer4           = NyxColors.Layer4,
    GlassTint        = NyxColors.GlassTint,
    BorderSubtle     = NyxColors.BorderSubtle,
    BorderMid        = NyxColors.BorderMid,
    BorderHi         = NyxColors.BorderHi,
    BorderGlow       = NyxColors.BorderGlow,
    EdgeHighlight    = NyxColors.EdgeHighlight,
    EdgeHighlight2   = NyxColors.EdgeHighlight2,
    TextPrimary      = NyxColors.TextPrimary,
    TextSecond       = NyxColors.TextSecond,
    TextDim          = NyxColors.TextDim,
    TextDisabled     = NyxColors.TextDisabled,
    Accent           = NyxColors.Accent,
    AccentBright     = NyxColors.AccentBright,
    AccentSoft       = NyxColors.AccentSoft,
    AccentGlow       = NyxColors.AccentGlow,
    AccentPill       = NyxColors.AccentPill,
    AccentDeep       = NyxColors.AccentDeep,
    Success          = NyxColors.Success,
    Error            = NyxColors.Error,
    Warning          = NyxColors.Warning,
    UserBubbleBg     = NyxColors.UserBubbleBg,
    UserBubbleBorder = NyxColors.UserBubbleBorder,
    UserBubbleGrad1  = NyxColors.UserBubbleGrad1,
    UserBubbleGrad2  = NyxColors.UserBubbleGrad2,
)

val lightNyxColorPalette = NyxColorPalette(
    Background       = NyxColors.LightBackground,
    Layer1           = NyxColors.LightLayer1,
    Layer2           = NyxColors.LightLayer2,
    Layer3           = NyxColors.LightLayer3,
    Layer4           = NyxColors.LightLayer4,
    GlassTint        = NyxColors.LightLayer1.copy(alpha = 0.92f),
    BorderSubtle     = NyxColors.LightBorderSubtle,
    BorderMid        = NyxColors.LightBorderMid,
    BorderHi         = NyxColors.LightBorderHi,
    BorderGlow       = NyxColors.LightBorderSubtle,
    EdgeHighlight    = NyxColors.EdgeHighlight,
    EdgeHighlight2   = NyxColors.EdgeHighlight2,
    TextPrimary      = NyxColors.LightTextPrimary,
    TextSecond       = NyxColors.LightTextSecond,
    TextDim          = NyxColors.LightTextDim,
    TextDisabled     = NyxColors.LightTextDim.copy(alpha = 0.5f),
    Accent           = NyxColors.Accent,
    AccentBright     = NyxColors.AccentBright,
    AccentSoft       = NyxColors.AccentSoft,
    AccentGlow       = NyxColors.AccentGlow,
    AccentPill       = NyxColors.AccentPill,
    AccentDeep       = NyxColors.AccentDeep,
    Success          = NyxColors.Success,
    Error            = NyxColors.Error,
    Warning          = NyxColors.Warning,
    UserBubbleBg     = NyxColors.LightUserBubbleBg,
    UserBubbleBorder = NyxColors.LightUserBubbleBorder,
    UserBubbleGrad1  = NyxColors.LightLayer2,
    UserBubbleGrad2  = NyxColors.LightLayer1,
)

/** CompositionLocal — 在任何 @Composable 中读取当前主题调色板 */
val LocalNyxColors = staticCompositionLocalOf { darkNyxColorPalette }

// ─── Brush helpers ────────────────────────────────────────────────────────────
// These produce dimensional effects: subtle top-lit card faces, edge glows,
// and directional gradients that give surfaces a physical quality.

@Composable
fun cardFaceBrush(): Brush {
    val nyx = LocalNyxColors.current
    // Slightly lighter at top (light source from above), darker at bottom
    return Brush.verticalGradient(
        0f   to nyx.Layer3,
        0.3f to nyx.Layer2,
        1f   to nyx.Layer1.copy(alpha = 0.95f)
    )
}

@Composable
fun topEdgeBrush(color: Color = LocalNyxColors.current.BorderMid) =
    Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent))

fun cardGlowBrush(color: Color) = Brush.radialGradient(
    listOf(color.copy(0.10f), color.copy(0.03f), Color.Transparent)
)

// Elevation shadow: darker inner border at bottom/sides, lighter at top
@Composable
fun elevationBrush(accentColor: Color = Color.Transparent): Brush {
    val nyx = LocalNyxColors.current
    return Brush.verticalGradient(
        0f to nyx.EdgeHighlight,
        0.5f to Color.Transparent,
        1f to nyx.Background.copy(alpha = 0.4f)
    )
}

// Frosted glass gradient for bars and overlays
@Composable
fun glassBrush(): Brush {
    val nyx = LocalNyxColors.current
    return Brush.verticalGradient(
        0f to nyx.Layer2.copy(alpha = 0.88f),
        1f to nyx.Layer1.copy(alpha = 0.96f)
    )
}

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
    tertiary     = NyxColors.AccentBright,
    background   = NyxColors.Background,
    surface      = NyxColors.Layer1,
    surfaceVariant = NyxColors.Layer2,
    onPrimary    = Color.White,
    onBackground = NyxColors.TextPrimary,
    onSurface    = NyxColors.TextPrimary,
    error        = NyxColors.Error,
)

private val NyxLightColorScheme = lightColorScheme(
    primary      = NyxColors.Accent,
    secondary    = NyxColors.AccentSoft,
    tertiary     = NyxColors.AccentBright,
    background   = NyxColors.LightBackground,
    surface      = NyxColors.LightLayer1,
    surfaceVariant = NyxColors.LightLayer2,
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
