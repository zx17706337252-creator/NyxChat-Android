package com.nyxchat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nyxchat.data.*
import com.nyxchat.ui.theme.*

// ─── Portrait with Mood Glow ─────────────────────────────────────────────────

@Composable
fun Portrait(
    char: NyxCharacter,
    size: Dp = 44.dp,
    isTyping: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val moodColor by animateColorAsState(
        targetValue = moodColor(char.mood),
        animationSpec = tween(800),
        label = "moodColor"
    )
    val charColor = char.color

    val glowAlpha by animateFloatAsState(
        targetValue = if (isTyping) 0.55f else 0.22f,
        animationSpec = if (isTyping) infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ) else tween(600),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            val radius = size.toPx() / 2

            // Outer mood glow rings
            for (i in 3 downTo 1) {
                drawCircle(
                    color = moodColor.copy(alpha = glowAlpha * 0.28f * i),
                    radius = radius + (i * 5.dp.toPx()),
                    center = center
                )
            }

            // Portrait fill
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        charColor.copy(alpha = 0.28f),
                        charColor.copy(alpha = 0.08f)
                    ),
                    center = Offset(size.toPx() * 0.38f, size.toPx() * 0.32f),
                    radius = radius
                ),
                radius = radius - 1.dp.toPx(),
                center = center
            )

            // Border
            drawCircle(
                color = charColor.copy(alpha = 0.5f),
                radius = radius - 0.75.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Initials
        Text(
            text = char.initials,
            color = charColor,
            fontSize = (size.value * 0.36f).sp,
            fontFamily = CinzelFamily,
            fontWeight = FontWeight.SemiBold
        )

        // Mood dot
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.27f)
                .clip(CircleShape)
                .background(moodColor)
                .border(1.5.dp, NyxColors.Background, CircleShape)
        )
    }
}

// ─── Message Bubble ──────────────────────────────────────────────────────────

@Composable
fun MessageBubble(
    msg: NyxMessage,
    chars: List<NyxCharacter>
) {
    val isUser = msg.role == "user"
    val char = chars.find { it.id == msg.charId }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            if (char != null) {
                Portrait(char = char, size = 36.dp)
                Spacer(Modifier.width(8.dp))
            }
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Character name
            if (!isUser && char != null) {
                Text(
                    text = char.name.uppercase(),
                    color = char.color,
                    fontSize = 9.5.sp,
                    fontFamily = CinzelFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                )
            }

            // Bubble
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 14.dp else 3.dp,
                            topEnd = if (isUser) 3.dp else 14.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 14.dp
                        )
                    )
                    .background(
                        if (isUser) NyxColors.UserBubble else NyxColors.SurfaceHigh
                    )
                    .border(
                        width = 1.dp,
                        color = if (isUser) NyxColors.UserBubbleBorder
                                else char?.color?.copy(alpha = 0.18f) ?: NyxColors.Border,
                        shape = RoundedCornerShape(
                            topStart = if (isUser) 14.dp else 3.dp,
                            topEnd = if (isUser) 3.dp else 14.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 14.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text = msg.content,
                    color = NyxColors.TextPrimary,
                    fontSize = 15.5.sp,
                    lineHeight = 24.sp,
                    fontFamily = CrimsonProFamily
                )
            }

            // Timestamp
            Text(
                text = formatTime(msg.timestamp),
                color = NyxColors.TextDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp)
            )
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x10FFFFFF))
                    .border(1.dp, Color(0x25FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", color = NyxColors.TextDim, fontSize = 12.sp)
            }
        }
    }
}

// ─── Typing Indicator ────────────────────────────────────────────────────────

@Composable
fun TypingIndicator(char: NyxCharacter) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Portrait(char = char, size = 36.dp, isTyping = true)
        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(NyxColors.SurfaceHigh)
                .border(1.dp, char.color.copy(0.18f),
                    RoundedCornerShape(topStart = 3.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val mc = moodColor(char.mood)
                repeat(3) { i ->
                    val anim = rememberInfiniteTransition(label = "dot$i")
                    val offsetY by anim.animateFloat(
                        initialValue = 0f, targetValue = -5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(i * 150)
                        ), label = "dot$i"
                    )
                    val alpha by anim.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(i * 150)
                        ), label = "alpha$i"
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = offsetY.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(mc.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

// ─── Bottom Navigation ───────────────────────────────────────────────────────

data class NavItem(val route: String, val icon: String, val label: String)

val NAV_ITEMS = listOf(
    NavItem("chat",     "◆", "对话"),
    NavItem("chars",    "◈", "角色"),
    NavItem("memory",   "◉", "记忆"),
    NavItem("settings", "◎", "设置"),
)

@Composable
fun BottomNav(
    currentRoute: String,
    memoryCount: Int,
    onNavigate: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NyxColors.Background)
            .border(1.dp, NyxColors.Border, RoundedCornerShape(0.dp))
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NAV_ITEMS.forEach { item ->
            val isActive = currentRoute == item.route
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(item.route) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box {
                        Text(
                            text = item.icon,
                            fontSize = 17.sp,
                            color = if (isActive) NyxColors.Accent else NyxColors.TextDim
                        )
                        if (item.route == "memory" && memoryCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 5.dp, y = (-2).dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(NyxColors.Accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (memoryCount > 9) "9+" else "$memoryCount",
                                    fontSize = 7.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Text(
                        text = item.label,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isActive) NyxColors.Accent else NyxColors.TextDim,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

fun formatTime(ts: Long): String {
    val d = java.util.Date(ts)
    return String.format("%02d:%02d", d.hours, d.minutes)
}
