package com.zaijian.zhoumuyun.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zaijian.zhoumuyun.data.model.CharacterConfig
import com.zaijian.zhoumuyun.data.model.FloorEnum
import com.zaijian.zhoumuyun.data.model.PresenceState
import com.zaijian.zhoumuyun.data.model.StatusType
import com.zaijian.zhoumuyun.ui.theme.AvatarSize
import com.zaijian.zhoumuyun.ui.theme.RingWidth
import com.zaijian.zhoumuyun.ui.theme.Spacing
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme

// ─────────────────────────────────────────────────────────────
//  WindowCard — 公馆单个角色窗口
//  v4 — 拱形顶 Shape + 暖色调背景
// ─────────────────────────────────────────────────────────────

/** 欧式拱形窗 Shape：顶部半圆弧 + 矩形下体 */
private class ArchWindowShape(private val archFraction: Float = 0.22f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val archH = size.height * archFraction
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, archH)
            arcTo(rect = Rect(0f, 0f, size.width, archH * 2f),
                startAngleDegrees = 180f, sweepAngleDegrees = 180f, forceMoveTo = false)
            lineTo(size.width, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

private val archShape = ArchWindowShape()

@Composable
fun WindowCard(
    character: CharacterConfig,
    presence: PresenceState,
    floor: FloorEnum,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZaijianTheme.colors
    val type   = ZaijianTheme.typography

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(80), label = "window_press",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(archShape)
            .background(windowBrush(floor, character.accentColor, presence.statusType, colors.isDark))
            .pointerInput(character.id) {
                detectTapGestures(
                    onPress      = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap        = { onClick() },
                    onLongPress  = { onLongClick() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm,
                start = Spacing.xs, end = Spacing.xs),
        ) {
            BreathingAvatar(
                imageUrl     = character.avatarUrl,
                breathColor  = character.accentColor,
                statusType   = presence.statusType,
                size         = AvatarSize.mansion,
                ringWidth    = RingWidth.mansion,
                glowRadius   = when (presence.statusType) {
                    StatusType.ACTIVE -> 12.dp; StatusType.IDLE -> 6.dp
                    StatusType.FOCUSED -> 8.dp; StatusType.OFFLINE -> 0.dp
                },
                enableBreath = presence.statusType != StatusType.OFFLINE,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = character.name,
                style = type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = if (colors.isDark) Color.White.copy(0.92f) else colors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            if (presence.statusType != StatusType.OFFLINE && character.isUnlocked) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = presence.statusText,
                    style = type.label,
                    color = if (colors.isDark) Color.White.copy(0.55f) else colors.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun windowBrush(floor: FloorEnum, accent: Color, status: StatusType, isDark: Boolean): Brush {
    val base = if (isDark) when (floor) {
        FloorEnum.SECOND   -> listOf(Color(0xFF22201A), Color(0xFF1A1814))
        FloorEnum.FIRST    -> listOf(Color(0xFF1E1C16), Color(0xFF181612))
        FloorEnum.BASEMENT -> listOf(Color(0xFF181620), Color(0xFF12111A))
    } else when (floor) {
        FloorEnum.SECOND   -> listOf(Color(0xFFF5F0E6), Color(0xFFEDE6D8))
        FloorEnum.FIRST    -> listOf(Color(0xFFF2EDE0), Color(0xFFEAE2D0))
        FloorEnum.BASEMENT -> listOf(Color(0xFFEDEAF5), Color(0xFFE5E2EE))
    }
    val glow = when (status) {
        StatusType.ACTIVE  -> if (isDark) 0.22f else 0.12f
        StatusType.IDLE    -> if (isDark) 0.12f else 0.07f
        StatusType.FOCUSED -> 0.09f
        StatusType.OFFLINE -> 0f
    }
    return if (glow > 0f)
        Brush.verticalGradient(listOf(accent.copy(glow * 0.7f), accent.copy(glow), base[0], base[1]))
    else
        Brush.verticalGradient(base)
}
