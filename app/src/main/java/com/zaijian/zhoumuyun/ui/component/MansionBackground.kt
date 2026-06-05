package com.zaijian.zhoumuyun.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.zaijian.zhoumuyun.R
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme

// ─────────────────────────────────────────────────────────────
//  MansionBackground — 公馆建筑背景
//
//  【接入插图】
//  准备好两张图后放入 res/drawable：
//    mansion_day.png   — 白天版（对应图1）
//    mansion_night.png — 夜晚版（对应图3）
//  然后将 USE_ILLUSTRATION 改为 true，取消下方注释即可。
// ─────────────────────────────────────────────────────────────

private const val USE_ILLUSTRATION = true

@Composable
fun MansionBackground(modifier: Modifier = Modifier) {
    val isDark = ZaijianTheme.colors.isDark

    if (USE_ILLUSTRATION) {
        val res = if (isDark) R.drawable.mansion_night else R.drawable.mansion_day
        Image(
            painter      = painterResource(res),
            contentDescription = null,
            modifier     = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Canvas(modifier = modifier.fillMaxSize()) {
            drawAtmosphericBackground(isDark)
            drawMansionSilhouette(isDark)
            if (isDark) drawNightAtmosphere() else drawDaytimeAtmosphere()
        }
    }
}

private fun DrawScope.drawAtmosphericBackground(isDark: Boolean) {
    if (isDark) {
        drawRect(brush = Brush.verticalGradient(listOf(
            Color(0xFF0C0E18), Color(0xFF141018), Color(0xFF1A1610),
        )))
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(Color(0x20E8D8A0), Color(0x00E8D8A0)),
                center = Offset(size.width * 0.82f, size.height * 0.08f),
                radius = size.width * 0.35f,
            ),
            radius = size.width * 0.35f,
            center = Offset(size.width * 0.82f, size.height * 0.08f),
        )
    } else {
        drawRect(brush = Brush.verticalGradient(listOf(
            Color(0xFFB8D4E8), Color(0xFFD8E8F0), Color(0xFFEEE8DC), Color(0xFFF5F0E8),
        )))
    }
}

private fun DrawScope.drawDaytimeAtmosphere() {
    drawCircle(
        brush  = Brush.radialGradient(
            listOf(Color(0x30FFE8A0), Color(0x00FFE8A0)),
            center = Offset(size.width * 0.15f, size.height * 0.05f),
            radius = size.width * 0.5f,
        ),
        radius = size.width * 0.5f,
        center = Offset(size.width * 0.15f, size.height * 0.05f),
    )
    drawRect(brush = Brush.verticalGradient(
        listOf(Color(0x00C8D8A8), Color(0x28C8D8A8)),
        startY = size.height * 0.72f, endY = size.height,
    ))
}

private fun DrawScope.drawNightAtmosphere() {
    drawRect(brush = Brush.verticalGradient(
        listOf(Color(0x00C8A060), Color(0x18C8A060)),
        startY = size.height * 0.40f, endY = size.height * 0.80f,
    ))
    drawStars()
    drawCrescent()
}

private fun DrawScope.drawStars() {
    val stars = listOf(
        0.08f to 0.04f, 0.18f to 0.09f, 0.30f to 0.03f, 0.42f to 0.07f,
        0.55f to 0.02f, 0.67f to 0.11f, 0.75f to 0.05f, 0.88f to 0.08f,
        0.12f to 0.16f, 0.36f to 0.14f, 0.60f to 0.17f, 0.80f to 0.13f,
        0.22f to 0.22f, 0.48f to 0.19f, 0.70f to 0.21f, 0.92f to 0.18f,
    )
    stars.forEachIndexed { i, (rx, ry) ->
        val alpha  = if (i % 3 == 0) 0.70f else if (i % 3 == 1) 0.45f else 0.28f
        val radius = if (i % 4 == 0) 1.8f else 1.2f
        drawCircle(color = Color(0xFFE8E0C8).copy(alpha = alpha), radius = radius,
            center = Offset(size.width * rx, size.height * ry))
    }
}

private fun DrawScope.drawCrescent() {
    drawCircle(color = Color(0xFFE8D8A8).copy(alpha = 0.85f), radius = 22f,
        center = Offset(size.width * 0.80f, size.height * 0.08f))
    drawCircle(color = Color(0xFF0C0E18), radius = 18f,
        center = Offset(size.width * 0.80f + 10f, size.height * 0.08f - 6f))
}

private fun DrawScope.drawMansionSilhouette(isDark: Boolean) {
    val cx = size.width / 2f
    val buildingTop    = size.height * 0.22f
    val buildingBottom = size.height * 0.88f
    val buildingW      = size.width * 0.80f
    val buildingLeft   = (size.width - buildingW) / 2f

    val wallColor   = if (isDark) Color(0xFF1E1A14) else Color(0xFFF0EAE0)
    val wallShade   = if (isDark) Color(0xFF18140F) else Color(0xFFE8E0D2)
    val roofColor   = if (isDark) Color(0xFF141010) else Color(0xFFD8D0C0)
    val doorColor   = if (isDark) Color(0xFF2A2010) else Color(0xFF8A7050)
    val columnColor = if (isDark) Color(0xFF2A2418) else Color(0xFFECE6DA)

    // 主楼体
    drawRect(color = wallColor, topLeft = Offset(buildingLeft, buildingTop + 36f),
        size = Size(buildingW, buildingBottom - buildingTop - 36f))

    // 楼层屋檐线
    listOf(0.42f, 0.62f).forEach { ratio ->
        drawRect(color = roofColor, topLeft = Offset(buildingLeft - 4f, size.height * ratio),
            size = Size(buildingW + 8f, 5f))
    }

    // 柱子
    listOf(0.26f, 0.38f, 0.62f, 0.74f).forEach { rx ->
        drawRect(color = columnColor, topLeft = Offset(size.width * rx - 5f, buildingTop + 38f),
            size = Size(10f, buildingBottom - buildingTop - 38f))
    }

    // 人字屋顶
    val atticW = buildingW * 0.30f
    val atticL = cx - atticW / 2f
    drawPath(path = Path().apply {
        moveTo(cx, buildingTop - 30f)
        lineTo(atticL + atticW + 20f, buildingTop + 36f)
        lineTo(atticL - 20f, buildingTop + 36f)
        close()
    }, color = roofColor)

    // 阁楼圆顶窗
    drawArc(color = doorColor.copy(alpha = 0.6f), startAngle = 180f, sweepAngle = 180f,
        useCenter = false, topLeft = Offset(cx - 16f, buildingTop - 2f), size = Size(32f, 28f))

    // 左右翼
    val wingW = buildingW * 0.15f
    drawRect(color = wallShade, topLeft = Offset(buildingLeft - wingW * 0.5f, buildingTop + 70f),
        size = Size(wingW + buildingW * 0.12f, buildingBottom - buildingTop - 70f))
    drawRect(color = wallShade,
        topLeft = Offset(buildingLeft + buildingW - wingW * 0.62f, buildingTop + 70f),
        size = Size(wingW + buildingW * 0.12f, buildingBottom - buildingTop - 70f))

    // 大门拱形
    val doorW = buildingW * 0.14f
    val doorH = buildingW * 0.22f
    val doorL = cx - doorW / 2f
    val doorT = buildingBottom - doorH
    drawRect(color = doorColor, topLeft = Offset(doorL, doorT + doorW / 2f),
        size = Size(doorW, doorH - doorW / 2f))
    drawArc(color = doorColor, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(doorL, doorT), size = Size(doorW, doorW))

    // 夜晚灯笼光晕
    if (isDark) {
        listOf(doorL - 20f, doorL + doorW + 12f).forEach { lx ->
            val lc = Offset(lx, doorT + doorH * 0.4f)
            drawCircle(brush = Brush.radialGradient(
                listOf(Color(0x60FFA040), Color(0x00FFA040)), center = lc, radius = 28f),
                radius = 28f, center = lc)
            drawCircle(color = Color(0xFFFFD090), radius = 4f, center = lc)
        }
    }

    // 台阶
    listOf(0f, 1f, 2f).forEach { i ->
        drawRect(color = if (isDark) Color(0xFF16120C) else Color(0xFFDDD6C8),
            topLeft = Offset(buildingLeft + buildingW * 0.35f - i * 10f, buildingBottom + i * 10f),
            size = Size(buildingW * 0.30f + i * 20f, 10f))
    }
}
