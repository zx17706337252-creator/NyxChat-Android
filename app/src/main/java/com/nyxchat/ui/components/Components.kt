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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nyxchat.ui.components.MarkdownText
import com.nyxchat.data.*
import com.nyxchat.ui.theme.*
import java.io.File

// ─── Portrait ────────────────────────────────────────────────────────────────

@Composable
fun Portrait(
    char: NyxCharacter,
    size: Dp = 44.dp,
    isTyping: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val moodCol by animateColorAsState(moodColor(char.mood), tween(800), label = "mood")
    val charCol = char.color
    val glowAnim by animateFloatAsState(
        if (isTyping) 0.7f else 0.3f,
        if (isTyping) infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
        else tween(700), label = "glow"
    )

    Box(
        Modifier.size(size).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // Glow rings behind
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.toPx() / 2, size.toPx() / 2)
            val r = size.toPx() / 2f
            repeat(3) { i ->
                drawCircle(moodCol.copy(alpha = glowAnim * 0.22f * (3 - i)),
                    radius = r + ((i + 1) * 5.dp.toPx()), center = c)
            }
        }

        // Circle clip
        Box(
            Modifier.fillMaxSize().clip(CircleShape)
                .background(charCol.copy(0.12f))
                .border(1.5.dp, charCol.copy(0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (char.hasAvatar) {
                AsyncImage(
                    model = File(char.avatarPath),
                    contentDescription = char.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(char.initials, color = charCol, fontSize = (size.value * .36f).sp,
                    fontFamily = CinzelFamily, fontWeight = FontWeight.SemiBold)
            }
        }

        // Mood dot
        Box(
            Modifier.align(Alignment.BottomEnd)
                .size(size * .27f).clip(CircleShape)
                .background(moodCol)
                .border(1.5.dp, NyxColors.Background, CircleShape)
        )
    }
}

// ─── Message Bubble ──────────────────────────────────────────────────────────

// ─── SummaryBubble ────────────────────────────────────────────────────────────
// 历史摘要消息专用气泡：居中显示，视觉上区别于普通对话消息
@Composable
private fun SummaryBubble(msg: NyxMessage) {
    val content = msg.content.removePrefix("【历史摘要】").trim()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NyxColors.Layer2)
                .border(0.5.dp, NyxColors.BorderSubtle, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "── 本章小结 ──",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = NyxColors.TextDim,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                content,
                fontSize = 12.sp,
                color = NyxColors.TextSecond,
                lineHeight = 18.sp,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
fun MessageBubble(msg: NyxMessage, chars: List<NyxCharacter>, showCursor: Boolean = false) {
    // 历史摘要消息单独渲染，不进入普通气泡流程
    if (msg.isSummary) {
        SummaryBubble(msg)
        return
    }

    val isUser = msg.role == "user"
    val char   = chars.find { it.id == msg.charId }
    val charCol = char?.color ?: NyxColors.Accent

    val topS = if (isUser) 16.dp else 3.dp
    val topE = if (isUser) 3.dp else 16.dp
    val shape = RoundedCornerShape(topStart = topS, topEnd = topE, bottomStart = 16.dp, bottomEnd = 16.dp)

    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            char?.let {
                Portrait(it, 34.dp)
                Spacer(Modifier.width(8.dp))
            } ?: Spacer(Modifier.width(42.dp))
        }

        Column(
            Modifier.widthIn(max = 272.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (!isUser && char != null) {
                Text(
                    char.name,
                    color = charCol.copy(0.85f), fontSize = 10.sp,
                    fontFamily = CinzelFamily, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )
            }

            // Bubble
            Box(
                Modifier.clip(shape)
                    .background(
                        if (isUser) NyxColors.UserBubbleBg
                        else charCol.copy(0.07f)
                    )
                    .border(
                        0.5.dp,
                        if (isUser) NyxColors.UserBubbleBorder else charCol.copy(0.2f),
                        shape
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val isNarrative = msg.content.startsWith("[旁白：") && msg.content.endsWith("]")
                val displayText = if (isNarrative)
                    msg.content.removePrefix("[旁白：").removeSuffix("]")
                else msg.content

                MarkdownText(
                    text      = displayText,
                    color     = if (isNarrative) NyxColors.TextSecond else NyxColors.TextPrimary,
                    fontSize  = if (isNarrative) 14.sp else 15.5.sp,
                    lineHeight = 25.sp,
                    fontStyle = if (isNarrative) FontStyle.Italic else FontStyle.Normal
                )
                if (showCursor) {
                    val blink = rememberInfiniteTransition(label = "cursor")
                    val alpha by blink.animateFloat(0f, 1f,
                        infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "blink")
                    Text("▍", color = (chars.find { it.id == msg.charId }?.color ?: NyxColors.Accent).copy(alpha),
                        fontSize = 14.sp)
                }
            }

            Text(
                formatTime(msg.timestamp),
                color = NyxColors.TextDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 3.dp, horizontal = 4.dp)
            )
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(NyxColors.Accent.copy(0.1f))
                    .border(0.5.dp, NyxColors.Accent.copy(0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("✦", color = NyxColors.Accent.copy(0.6f), fontSize = 11.sp) }
        }
    }
}

// ─── Typing Indicator ────────────────────────────────────────────────────────

@Composable
fun TypingIndicator(char: NyxCharacter) {
    val mc    = moodColor(char.mood)
    val shape = RoundedCornerShape(topStart = 3.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)

    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Portrait(char, 34.dp, isTyping = true)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(shape)
                .background(char.color.copy(0.07f))
                .border(0.5.dp, char.color.copy(0.2f), shape)
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            MoodTypingDots(mood = char.mood, color = mc)
        }
    }
}

/** Dispatches to the correct mood-specific animation */
@Composable
private fun MoodTypingDots(mood: String, color: Color) {
    when (mood) {
        "cold"         -> ColdTypingDots(color)
        "angry"        -> AngryTypingDots(color)
        "sad"          -> SadTypingDots(color)
        "affectionate" -> AffectionateTypingDots(color)
        "happy"        -> HappyTypingDots(color)
        "curious"      -> CuriousTypingDots(color)
        else           -> NeutralTypingDots(color)
    }
}

// ── Neutral: classic bounce ────────────────────────────────────────────────
@Composable
private fun NeutralTypingDots(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val inf = rememberInfiniteTransition(label = "n$i")
            val oy by inf.animateFloat(0f, -5f,
                infiniteRepeatable(tween(500, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse, StartOffset(i * 160)), label = "oy$i")
            val al by inf.animateFloat(0.25f, 1f,
                infiniteRepeatable(tween(500), RepeatMode.Reverse, StartOffset(i * 160)), label = "al$i")
            Box(Modifier.offset(y = oy.dp).size(5.dp).clip(CircleShape).background(color.copy(al)))
        }
    }
}

// ── Cold: slow deliberate scan line ───────────────────────────────────────
@Composable
private fun ColdTypingDots(color: Color) {
    val inf = rememberInfiniteTransition(label = "cold")
    val progress by inf.animateFloat(0f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 3200
                0f at 0 with LinearEasing
                1f at 2000
                1f at 2800
                0f at 2801
            }
        ), label = "scan")
    Box(
        Modifier
            .width(36.dp)
            .height(2.dp)
            .background(color.copy(0.15f))
    ) {
        Box(
            Modifier
                .width((36 * progress).dp)
                .height(2.dp)
                .background(color.copy(0.8f))
        )
    }
}

// ── Angry: rapid horizontal jitter ────────────────────────────────────────
@Composable
private fun AngryTypingDots(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val inf = rememberInfiniteTransition(label = "ag$i")
            val jx by inf.animateFloat(-2.5f, 2.5f,
                infiniteRepeatable(tween(85, easing = LinearEasing),
                    RepeatMode.Reverse, StartOffset(i * 28)), label = "jx$i")
            val al by inf.animateFloat(0.5f, 1f,
                infiniteRepeatable(tween(85), RepeatMode.Reverse, StartOffset(i * 28)), label = "al$i")
            Box(Modifier.offset(x = jx.dp).size(5.dp).clip(CircleShape).background(color.copy(al)))
        }
    }
}

// ── Sad: heavy drip downward ───────────────────────────────────────────────
@Composable
private fun SadTypingDots(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val inf = rememberInfiniteTransition(label = "sd$i")
            val oy by inf.animateFloat(0f, 7f,
                infiniteRepeatable(
                    keyframes {
                        durationMillis = 2000
                        0f at 0
                        7f at 1400 with FastOutLinearInEasing
                        7f at 1999
                    },
                    RepeatMode.Restart, StartOffset(i * 380)
                ), label = "oy$i")
            val al by inf.animateFloat(0.7f, 0.15f,
                infiniteRepeatable(tween(2000), RepeatMode.Restart, StartOffset(i * 380)), label = "al$i")
            Box(Modifier.offset(y = oy.dp).size(5.dp).clip(CircleShape).background(color.copy(al)))
        }
    }
}

// ── Affectionate: gentle soft pulse ───────────────────────────────────────
@Composable
private fun AffectionateTypingDots(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val inf = rememberInfiniteTransition(label = "af$i")
            val scale by inf.animateFloat(0.55f, 1.25f,
                infiniteRepeatable(tween(900, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse, StartOffset(i * 220)), label = "sc$i")
            Box(Modifier.size((5 * scale).dp).clip(CircleShape).background(color.copy(0.65f)))
        }
    }
}

// ── Happy: quick energetic bounce ─────────────────────────────────────────
@Composable
private fun HappyTypingDots(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { i ->
            val inf = rememberInfiniteTransition(label = "hp$i")
            val oy by inf.animateFloat(0f, -7f,
                infiniteRepeatable(tween(280, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse, StartOffset(i * 90)), label = "oy$i")
            val al by inf.animateFloat(0.3f, 1f,
                infiniteRepeatable(tween(280), RepeatMode.Reverse, StartOffset(i * 90)), label = "al$i")
            Box(Modifier.offset(y = oy.dp).size(4.dp).clip(CircleShape).background(color.copy(al)))
        }
    }
}

// ── Curious: 3 dots orbiting a center point ───────────────────────────────
@Composable
private fun CuriousTypingDots(color: Color) {
    val inf = rememberInfiniteTransition(label = "cu")
    val angle by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "angle")

    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        repeat(3) { i ->
            val rad  = Math.toRadians((angle + 120.0 * i).toDouble())
            val ox   = (kotlin.math.cos(rad) * 8.0).toFloat()
            val oy   = (kotlin.math.sin(rad) * 8.0).toFloat()
            val frac = (i.toFloat() / 3f)
            Box(
                Modifier
                    .offset(x = ox.dp, y = oy.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color.copy(0.4f + frac * 0.6f))
            )
        }
    }
}

// ─── Bottom Navigation ───────────────────────────────────────────────────────

data class NavItem(val route: String, val icon: String, val label: String)
val NAV_ITEMS = listOf(
    NavItem("chat",         "◆", "对话"),
    NavItem("chars",        "◈", "角色"),
    NavItem("worldbook",    "◇", "世界书"),
    NavItem("relations",    "◍", "关系"),
    NavItem("settings",     "◎", "设置"),
)

@Composable
fun BottomNav(currentRoute: String, onNavigate: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // Top separator gradient line
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, NyxColors.BorderMid, Color.Transparent)
                    ),
                    start = Offset(0f, 0f), end = Offset(size.width, 0f),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
            .background(
                Brush.verticalGradient(
                    0f to Color(0xD00C091B),
                    1f to NyxColors.Layer1
                )
            )
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NAV_ITEMS.forEach { item ->
                val active = currentRoute == item.route
                val itemColor by animateColorAsState(
                    if (active) NyxColors.AccentSoft else NyxColors.TextDim,
                    tween(250), label = "nav_color_${item.route}"
                )

                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onNavigate(item.route) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Active pill indicator above icon
                    Box(
                        Modifier
                            .height(2.5.dp)
                            .width(if (active) 20.dp else 0.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (active)
                                    Brush.horizontalGradient(listOf(NyxColors.Accent, NyxColors.AccentSoft))
                                else
                                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                    )

                    // Icon with glow background when active
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) NyxColors.AccentPill else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.icon,
                            fontSize = 15.sp,
                            color = itemColor
                        )
                    }

                    Text(
                        item.label,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = itemColor,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }
    }
}

fun formatTime(ts: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}
