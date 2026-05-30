package com.nyxchat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nyxchat.ui.theme.NyxColors
import kotlin.math.*
import kotlin.random.Random

private data class Particle(
    val initX  : Float,   // 0..1 screen-normalized
    val initY  : Float,
    val speed  : Float,   // fraction of screen per full cycle
    val radius : Float,   // dp
    val phaseX : Float,   // horizontal sway phase
    val phaseY : Float    // vertical drift phase offset
)

/**
 * Slow-drifting ambient light particles that tint themselves with [tintColor].
 * Particles drift upward, sway gently side to side, and fade in/out near the
 * edges so the wrap-around is seamless.
 */
@Composable
fun AmbientParticles(
    modifier   : Modifier = Modifier,
    tintColor  : Color    = NyxColors.Accent,
    count      : Int      = 18
) {
    val particles = remember {
        List(count) {
            Particle(
                initX  = Random.nextFloat(),
                initY  = Random.nextFloat(),
                speed  = 0.012f + Random.nextFloat() * 0.022f,
                radius = 0.7f  + Random.nextFloat() * 2.0f,
                phaseX = Random.nextFloat() * 2f * PI.toFloat(),
                phaseY = Random.nextFloat() * 2f * PI.toFloat()
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "ambient_particles")
    // cycle: 0 → 2π over 14 s
    val cycle by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label         = "cycle"
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height

        particles.forEach { p ->
            // Upward drift: normalised position decreases over time, wraps via mod
            val progress = cycle / (2f * PI.toFloat())            // 0..1
            val driftY   = ((p.initY - progress * p.speed * 8f).mod(1f))

            // Gentle horizontal sway
            val swayX    = p.initX + sin(cycle + p.phaseX) * 0.018f

            // Fade: fully visible in the middle band, invisible near top & bottom
            val fadeEdge = (driftY * (1f - driftY) * 4f).coerceIn(0f, 1f)
            val alpha    = fadeEdge * 0.18f   // max 18 % opacity — subtle

            drawCircle(
                color  = tintColor.copy(alpha = alpha),
                radius = p.radius.dp.toPx(),
                center = Offset(swayX * w, driftY * h)
            )
        }
    }
}
