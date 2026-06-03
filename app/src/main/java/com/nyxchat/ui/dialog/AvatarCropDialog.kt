package com.nyxchat.ui.dialog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nyxchat.ui.theme.LocalNyxColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas as ComposeCanvas

@Composable
fun AvatarCropDialog(
    uri: android.net.Uri,
    context: Context,
    charName: String,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap: Bitmap? = remember(uri) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
            val maxDim = 1200
            val w = raw.width; val h = raw.height
            if (max(w, h) > maxDim) {
                val s = maxDim.toFloat() / max(w, h)
                Bitmap.createScaledBitmap(raw, (w * s).toInt(), (h * s).toInt(), true)
            } else raw
        }.getOrNull()
    }

    if (bitmap == null) { onDismiss(); return }

    // framePx is set from BoxWithConstraints and used in the confirm crop
    var framePx by remember { mutableFloatStateOf(0f) }
    var scale   by remember { mutableFloatStateOf(1f) }
    var offset  by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0A0718)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "调整头像位置",
                fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextSecond,
                modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
            )

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.Black)
            ) {
                val fp = constraints.maxWidth.toFloat()
                SideEffect { framePx = fp }  // keep framePx in sync for confirm button

                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    val newScale    = (scale * zoomChange).coerceIn(0.8f, 5f)
                    val baseScale   = max(fp / bitmap.width.toFloat(), fp / bitmap.height.toFloat())
                    val es          = baseScale * newScale
                    val maxOffX     = max(0f, (bitmap.width  * es - fp) / 2f)
                    val maxOffY     = max(0f, (bitmap.height * es - fp) / 2f)
                    val proposed    = offset + panChange
                    scale  = newScale
                    offset = Offset(
                        proposed.x.coerceIn(-maxOffX, maxOffX),
                        proposed.y.coerceIn(-maxOffY, maxOffY)
                    )
                }

                // Draw bitmap with full transform control
                ComposeCanvas(
                    Modifier
                        .fillMaxSize()
                        .transformable(transformState)
                ) {
                    val baseScale = max(fp / bitmap.width.toFloat(), fp / bitmap.height.toFloat())
                    val es   = baseScale * scale
                    val dstW = (bitmap.width  * es).roundToInt()
                    val dstH = (bitmap.height * es).roundToInt()
                    val tx   = (size.width  / 2f - bitmap.width  * es / 2f + offset.x).roundToInt()
                    val ty   = (size.height / 2f - bitmap.height * es / 2f + offset.y).roundToInt()
                    drawImage(
                        image     = bitmap.asImageBitmap(),
                        dstSize   = IntSize(dstW, dstH),
                        dstOffset = IntOffset(tx, ty)
                    )
                }

                // Batch 4 Item 10: Rounded-square mask overlay (was circular)
                // cornerRadius = 12dp；裁剪框可延伸至图片边缘（cropSizePx 系数 0.96f）
                ComposeCanvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                ) {
                    val cr = 12.dp.toPx()
                    // 裁剪框边长：占满 96% 的 fp（原圆形内切 84%）
                    val boxSize = fp * 0.96f
                    val left   = (size.width  - boxSize) / 2f
                    val top    = (size.height - boxSize) / 2f
                    val rect   = Rect(left, top, left + boxSize, top + boxSize)

                    drawRect(Color(0xCC000000))
                    // 挖空圆角正方形区域
                    drawRoundRect(
                        color        = Color.Transparent,
                        topLeft      = rect.topLeft,
                        size         = rect.size,
                        cornerRadius = CornerRadius(cr),
                        blendMode    = BlendMode.Clear
                    )
                    // 主边框
                    drawRoundRect(
                        color        = Color(0x809D6FFF),
                        topLeft      = rect.topLeft,
                        size         = rect.size,
                        cornerRadius = CornerRadius(cr),
                        style        = Stroke(1.5.dp.toPx())
                    )
                    // 外发光细线
                    val outerInset = 3.dp.toPx()
                    drawRoundRect(
                        color        = Color(0x309D6FFF),
                        topLeft      = Offset(rect.left - outerInset, rect.top - outerInset),
                        size         = Size(rect.width + outerInset * 2, rect.height + outerInset * 2),
                        cornerRadius = CornerRadius(cr + outerInset),
                        style        = Stroke(1.dp.toPx())
                    )
                }

                Text(
                    "双指缩放 · 拖动调整",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(0.45f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalNyxColors.current.TextDim),
                    border = BorderStroke(0.5.dp, LocalNyxColors.current.BorderSubtle)
                ) {
                    Text("取消", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        if (framePx > 0f) {
                            runCatching { onConfirm(cropBitmap(bitmap, scale, offset, framePx)) }
                        }
                    },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalNyxColors.current.Accent.copy(0.25f),
                        contentColor   = LocalNyxColors.current.AccentSoft
                    )
                ) {
                    Text("确认裁剪", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Crop [bitmap] to a square using the user's pan/zoom state.
 *
 * [framePx]   – actual display size of the square crop box in pixels
 * [userScale] – zoom level the user applied (1f = image fills the frame)
 * [offset]    – display-pixel translation from the centered position
 */
private fun cropBitmap(bitmap: Bitmap, userScale: Float, offset: Offset, framePx: Float): Bitmap {
    val bmpW = bitmap.width.toFloat()
    val bmpH = bitmap.height.toFloat()

    // baseScale: how many bitmap pixels fit in one display pixel (at userScale=1)
    val baseScale     = max(framePx / bmpW, framePx / bmpH)
    val effectiveScale = baseScale * userScale

    // Batch 4 Item 10: box occupies 96% of the frame (was 84% for the inscribed circle)
    val cropSizePx = (framePx * 0.96f / effectiveScale).roundToInt()
        .coerceIn(32, min(bitmap.width, bitmap.height))

    // Center of the visible area in bitmap coordinates
    val centerX = (bmpW / 2f - offset.x / effectiveScale)
        .coerceIn(cropSizePx / 2f, bmpW - cropSizePx / 2f)
    val centerY = (bmpH / 2f - offset.y / effectiveScale)
        .coerceIn(cropSizePx / 2f, bmpH - cropSizePx / 2f)

    val left = (centerX - cropSizePx / 2f).roundToInt().coerceIn(0, bitmap.width  - cropSizePx)
    val top  = (centerY - cropSizePx / 2f).roundToInt().coerceIn(0, bitmap.height - cropSizePx)
    val safe = cropSizePx.coerceAtMost(min(bitmap.width - left, bitmap.height - top))

    return Bitmap.createBitmap(bitmap, left, top, safe, safe)
}
