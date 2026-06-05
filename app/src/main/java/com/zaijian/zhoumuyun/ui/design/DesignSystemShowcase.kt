package com.zaijian.zhoumuyun.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zaijian.zhoumuyun.data.model.DefaultCharacters
import com.zaijian.zhoumuyun.ui.theme.AvatarSize
import com.zaijian.zhoumuyun.ui.theme.Palette
import com.zaijian.zhoumuyun.ui.theme.Radius
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme

// ─────────────────────────────────────────────────────────────
//  Design System Showcase
//  Opens as a standalone screen during Phase 1 development.
//  Remove or hide behind a debug flag in production.
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignSystemShowcase(modifier: Modifier = Modifier) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenHorizontal)
    ) {
        Spacer(Modifier.height(Spacing.lg))

        // ── Section: Colors ───────────────────────────────────
        SectionHeader("Color Tokens")

        val swatches = listOf(
            "bgBase"        to colors.bgBase,
            "bgCard"        to colors.bgCard,
            "bgElevated"    to colors.bgElevated,
            "border"        to colors.border,
            "textPrimary"   to colors.textPrimary,
            "textSecondary" to colors.textSecondary,
            "textDisabled"  to colors.textDisabled,
            "accent"        to colors.accent,
            "accentSoft"    to colors.accentSoft,
            "statusActive"  to colors.statusActive,
            "statusIdle"    to colors.statusIdle,
            "statusFocused" to colors.statusFocused,
            "statusOffline" to colors.statusOffline,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement   = Arrangement.spacedBy(Spacing.sm),
        ) {
            swatches.forEach { (label, color) ->
                ColorSwatch(label, color, colors.textPrimary, colors.border)
            }
        }

        SectionDivider()

        // ── Section: Character Accents ────────────────────────
        SectionHeader("Character Accent Colors")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement   = Arrangement.spacedBy(Spacing.sm),
        ) {
            DefaultCharacters.forEach { char ->
                ColorSwatch(char.name, char.accentColor, Color.White, Color.Transparent)
            }
        }

        SectionDivider()

        // ── Section: Typography ───────────────────────────────
        SectionHeader("Typography")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("titleBold · 20sp Bold",  style = type.titleBold,  color = colors.textPrimary)
            Text("cardTitle · 16sp Medium", style = type.cardTitle, color = colors.textPrimary)
            Text("navTitle · 17sp Bold",   style = type.navTitle,   color = colors.textPrimary)
            Text("body · 14sp Regular",    style = type.body,       color = colors.textPrimary)
            Text("caption · 13sp Regular", style = type.caption,    color = colors.textSecondary)
            Text("label · 11sp Regular",   style = type.label,      color = colors.textDisabled)
            Text("button · 14sp Medium",   style = type.button,     color = colors.accent)
            Text("presence · 13sp · 正在想你", style = type.presence, color = colors.textSecondary)
        }

        SectionDivider()

        // ── Section: Radius ───────────────────────────────────
        SectionHeader("Corner Radius")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(
                "xs·6" to Radius.xs,
                "sm·12" to Radius.sm,
                "md·20" to Radius.md,
                "lg·28" to Radius.lg,
            ).forEach { (label, r) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(r))
                            .background(colors.accent.copy(alpha = 0.2f))
                            .border(1.dp, colors.accent, RoundedCornerShape(r))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = type.label, color = colors.textSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.2f))
                        .border(1.dp, colors.accent, CircleShape)
                )
                Spacer(Modifier.height(4.dp))
                Text("circle", style = type.label, color = colors.textSecondary)
            }
        }

        SectionDivider()

        // ── Section: Avatar sizes ─────────────────────────────
        SectionHeader("Avatar Sizes")
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            listOf(
                "detail·80" to AvatarSize.detail,
                "mansion·52" to AvatarSize.mansion,
                "shelf·44" to AvatarSize.shelf,
                "chat·32" to AvatarSize.chat,
                "small·24" to AvatarSize.small,
            ).forEach { (label, size) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(colors.accent)
                    ) {
                        Text(
                            text  = size.value.toInt().toString(),
                            style = ZaijianTheme.typography.label,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = type.label, color = colors.textSecondary)
                }
            }
        }

        SectionDivider()

        // ── Section: Status dots ──────────────────────────────
        SectionHeader("Status Colors")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            listOf(
                "活跃"  to colors.statusActive,
                "空闲"  to colors.statusIdle,
                "专注"  to colors.statusFocused,
                "离线"  to colors.statusOffline,
            ).forEach { (label, color) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = type.label, color = colors.textSecondary)
                }
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

// ─────────────────────────────────────────────────────────────
//  Sub-components used only in this showcase
// ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        style    = ZaijianTheme.typography.cardTitle,
        color    = ZaijianTheme.colors.textPrimary,
        modifier = Modifier.padding(bottom = Spacing.sm),
    )
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(Spacing.lg))
    HorizontalDivider(color = ZaijianTheme.colors.border)
    Spacer(Modifier.height(Spacing.lg))
}

@Composable
private fun ColorSwatch(
    label: String,
    color: Color,
    textColor: Color,
    borderColor: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(color)
                .border(1.dp, borderColor, RoundedCornerShape(Radius.xs))
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text     = label,
            style    = ZaijianTheme.typography.label,
            color    = textColor.copy(alpha = 0.7f),
            modifier = Modifier.width(56.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────

@Preview(name = "Design System · Light", showBackground = true, widthDp = 390)
@Composable
private fun PreviewLight() {
    ZaijianTheme {
        DesignSystemShowcase()
    }
}

@Preview(name = "Design System · Dark", showBackground = true,
    backgroundColor = 0xFF12131A, widthDp = 390)
@Composable
private fun PreviewDark() {
    ZaijianTheme(appTheme = com.zaijian.zhoumuyun.ui.theme.AppTheme.DARK) {
        DesignSystemShowcase()
    }
}
