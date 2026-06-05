package com.zaijian.zhoumuyun.ui.component

import android.os.Build
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import com.zaijian.zhoumuyun.data.model.StatusType
import com.zaijian.zhoumuyun.data.model.dotColor
import com.zaijian.zhoumuyun.ui.theme.BreathGlowAlphaMax
import com.zaijian.zhoumuyun.ui.theme.BreathGlowAlphaMin
import com.zaijian.zhoumuyun.ui.theme.BreathScaleMax
import com.zaijian.zhoumuyun.ui.theme.BreathScaleMin
import com.zaijian.zhoumuyun.ui.theme.breathAlphaSpec
import com.zaijian.zhoumuyun.ui.theme.breathScaleSpec

// ─────────────────────────────────────────────────────────────
//  BreathingAvatar
//  设计规范 §7 + §8 — 呼吸动画 + 头像层级
// ─────────────────────────────────────────────────────────────

/**
 * @param imageUrl      头像图片 URL 或占位符
 * @param breathColor   呼吸光颜色（通常 = character.accentColor）
 * @param statusType    控制是否显示状态环和状态点
 * @param statusRingColor 状态环颜色（默认从 statusType 派生）
 * @param size          头像直径
 * @param ringWidth     状态环线宽
 * @param glowRadius    光晕模糊半径
 * @param enableBreath  是否启用呼吸动画（OFFLINE 角色可关闭）
 */
@Composable
fun BreathingAvatar(
    imageUrl: String,
    breathColor: Color,
    statusType: StatusType,
    modifier: Modifier = Modifier,
    statusRingColor: Color = breathColor.copy(alpha = 0.60f),
    size: Dp = 52.dp,
    ringWidth: Dp = 2.5.dp,
    glowRadius: Dp = 8.dp,
    enableBreath: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_breath")

    val scale by infiniteTransition.animateFloat(
        initialValue  = if (enableBreath) BreathScaleMin else 1f,
        targetValue   = if (enableBreath) BreathScaleMax else 1f,
        animationSpec = breathScaleSpec,
        label         = "breath_scale",
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = if (enableBreath) BreathGlowAlphaMin else 0f,
        targetValue   = if (enableBreath) BreathGlowAlphaMax else 0f,
        animationSpec = breathAlphaSpec,
        label         = "glow_alpha",
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        // [2] 情绪光晕 — blur glow behind avatar
        // blur() requires API 31+; on older devices fall back to a plain alpha circle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(1.35f)
                    .blur(glowRadius)
                    .background(breathColor.copy(alpha = glowAlpha), CircleShape)
            )
        } else {
            // Fallback: larger, softer circle without hardware blur
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(1.50f)
                    .background(breathColor.copy(alpha = glowAlpha * 0.5f), CircleShape)
            )
        }

        // [1] 头像图片
        AsyncImage(
            model              = imageUrl,
            contentDescription = null,
            modifier           = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale       = ContentScale.Crop,
        )

        // [3+4] 状态环 + 状态点
        if (statusType != StatusType.OFFLINE) {
            StatusRingCanvas(
                statusType  = statusType,
                ringColor   = statusRingColor,
                ringWidth   = ringWidth,
                modifier    = Modifier.fillMaxSize(),
            )
        }

        // OFFLINE：灰度遮罩 70% 不透明度 (设计规范 §10 窗口发光规则)
        if (statusType == StatusType.OFFLINE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0x80808080))  // 50% grey overlay
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  StatusRingCanvas  — 270° 状态环 + 右下角状态点
//  设计规范 §8
// ─────────────────────────────────────────────────────────────

@Composable
fun StatusRingCanvas(
    statusType: StatusType,
    ringColor: Color,
    ringWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val dotColor = statusType.dotColor()

    Canvas(modifier = modifier) {
        val stroke = ringWidth.toPx()
        val inset  = stroke / 2f

        // 270° 弧，缺口在右下角（放状态点）
        drawArc(
            color      = ringColor,
            startAngle = -225f,
            sweepAngle = 270f,
            useCenter  = false,
            style      = Stroke(width = stroke, cap = StrokeCap.Round),
            topLeft    = Offset(inset, inset),
            size       = androidx.compose.ui.geometry.Size(
                size.width - stroke,
                size.height - stroke,
            ),
        )

        // 状态点：右下角缺口处，直径约 10dp
        val dotRadius = 5.dp.toPx()
        val dotCenter = Offset(
            x = size.width  - dotRadius,
            y = size.height - dotRadius,
        )
        // 白色底衬（防止状态点与光晕混淆）
        drawCircle(color = Color.White, radius = dotRadius + 1.5f, center = dotCenter)
        drawCircle(color = dotColor,    radius = dotRadius,         center = dotCenter)
    }
}
