package com.nyxchat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.nyxchat.R

// ─── Google Fonts ────────────────────────────────────────────────────────────

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val CinzelFamily = FontFamily(
    Font(googleFont = GoogleFont("Cinzel"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Cinzel"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Cinzel"), fontProvider = provider, weight = FontWeight.Bold),
)

val CrimsonProFamily = FontFamily(
    Font(googleFont = GoogleFont("Crimson Pro"), fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = GoogleFont("Crimson Pro"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Crimson Pro"), fontProvider = provider, weight = FontWeight.SemiBold),
)

// ─── Colors ──────────────────────────────────────────────────────────────────

object NyxColors {
    val Background    = Color(0xFF05030E)
    val Surface       = Color(0xFF0A0718)
    val SurfaceHigh   = Color(0xFF0F0B20)
    val Border        = Color(0x25784ED0)
    val BorderHi      = Color(0x4DA06EFF)
    val Accent        = Color(0xFF9D6FFF)
    val AccentSoft    = Color(0xFFC084FC)
    val TextPrimary   = Color(0xFFDDD4F0)
    val TextDim       = Color(0x75DDD4F0)
    val Error         = Color(0xFFF87171)
    val Success       = Color(0xFF34D399)
    val UserBubble    = Color(0x209D6FFF)
    val UserBubbleBorder = Color(0x489D6FFF)
}

// ─── Material Theme ──────────────────────────────────────────────────────────

private val NyxColorScheme = darkColorScheme(
    primary   = NyxColors.Accent,
    secondary = NyxColors.AccentSoft,
    background = NyxColors.Background,
    surface    = NyxColors.Surface,
    onPrimary  = Color.White,
    onBackground = NyxColors.TextPrimary,
    onSurface    = NyxColors.TextPrimary,
    error        = NyxColors.Error,
)

@Composable
fun NyxChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NyxColorScheme,
        content = content
    )
}
