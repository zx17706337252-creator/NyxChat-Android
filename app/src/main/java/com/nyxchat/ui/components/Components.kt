package com.nyxchat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import coil.compose.AsyncImage
import com.nyxchat.ui.components.MarkdownText
import com.nyxchat.data.*
import com.nyxchat.ui.theme.*
import java.io.File

// ─── HexagonShape ─────────────────────────────────────────────────────────────
// Flat-top hexagon (horizontal edges top/bottom) — 崩铁/明日方舟 card style

class HexagonShape : Shape {
    override fun createOutline(
        size: Size, layoutDirection: LayoutDirection, density: Density
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = minOf(cx, cy)
        for (i in 0 until 6) {
            val angle = (PI / 3.0 * i).toFloat()   // 0°, 60°, 120°, 180°, 240°, 300°
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

// ─── Portrait ────────────────────────────────────────────────────────────────

@Composable
fun Portrait(
    char: NyxCharacter,
    size: Dp = 44.dp,
    isTyping: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val moodCol by animateColorAsState(moodColor(char.mood), tween(800), label = "mood")
    val charCol  = char.color
    val hexShape = remember { HexagonShape() }

    Box(
        Modifier.size(size).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // Hexagon clip — no glow, clean crisp border
        Box(
            Modifier.fillMaxSize().clip(hexShape)
                .background(charCol.copy(0.12f))
                .border(1.dp, charCol.copy(0.55f), hexShape),
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

        // Mood dot — stays circular, aligned to bottom-right corner
        Box(
            Modifier.align(Alignment.BottomEnd)
                .size(size * .22f).clip(CircleShape)
                .background(moodCol)
                .border(1.dp, LocalNyxColors.current.Background, CircleShape)
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
                .background(LocalNyxColors.current.Layer2)
                .border(0.5.dp, LocalNyxColors.current.BorderSubtle, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "── 本章小结 ──",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = LocalNyxColors.current.TextDim,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                content,
                fontSize = 12.sp,
                color = LocalNyxColors.current.TextSecond,
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
    val charCol = char?.color ?: LocalNyxColors.current.Accent

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
                        if (isUser) LocalNyxColors.current.UserBubbleBg
                        else charCol.copy(0.07f)
                    )
                    .border(
                        0.5.dp,
                        if (isUser) LocalNyxColors.current.UserBubbleBorder else charCol.copy(0.2f),
                        shape
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val isNarrative = msg.content.startsWith("[旁白：") && msg.content.endsWith("]")
                val displayText = if (isNarrative)
                    msg.content.removePrefix("[旁白：").removeSuffix("]")
                else msg.content

                if (isNarrative) {
                    MarkdownText(
                        text      = displayText,
                        color     = LocalNyxColors.current.TextSecond,
                        fontSize  = 14.sp,
                        lineHeight = 22.sp,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    // Bug 6: 分层渲染 — 动作/台词/内心各自样式，段落间留间距
                    StyledContent(
                        text     = displayText,
                        charColor = charCol
                    )
                }
                if (showCursor) {
                    val blink = rememberInfiniteTransition(label = "cursor")
                    val alpha by blink.animateFloat(0f, 1f,
                        infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "blink")
                    Text("▍", color = (chars.find { it.id == msg.charId }?.color ?: LocalNyxColors.current.Accent).copy(alpha),
                        fontSize = 14.sp)
                }
            }

            Text(
                formatTime(msg.timestamp),
                color = LocalNyxColors.current.TextDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
            )
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(LocalNyxColors.current.Accent.copy(0.1f))
                    .border(0.5.dp, LocalNyxColors.current.Accent.copy(0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("✦", color = LocalNyxColors.current.Accent.copy(0.6f), fontSize = 11.sp) }
        }
    }
}

// ─── StyledContent ───────────────────────────────────────────────────────────
// Bug 6: 将动作描述(*...*) / 台词(「...」) / 内心独白（...） 分层渲染，
//        段落之间加视觉间距，使长回复不再拥挤。

@Composable
private fun StyledContent(text: String, charColor: Color) {
    val nyx = LocalNyxColors.current
    val paragraphs = text.trim().split(Regex("\\n{1,}")).filter { it.isNotBlank() }

    Column {
        paragraphs.forEachIndexed { idx, raw ->
            if (idx > 0) androidx.compose.foundation.layout.Spacer(
                Modifier.height(6.dp)
            )
            val para = raw.trim()
            when {
                // 内心独白：（...）— 小字，暗色，斜体
                para.startsWith("（") && para.endsWith("）") ->
                    Text(
                        text = para,
                        color = nyx.TextDim,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        fontFamily = CrimsonProFamily,
                        fontStyle = FontStyle.Italic
                    )

                // 动作描述：*...* — 斜体，次要色，略小
                para.startsWith("*") ->
                    androidx.compose.material3.Text(
                        text = buildInlineAnnotated(para, nyx.TextSecond, nyx.Layer2, nyx.AccentSoft),
                        color = nyx.TextSecond,
                        fontSize = 13.5.sp,
                        lineHeight = 22.sp,
                        fontFamily = CrimsonProFamily,
                        fontStyle = FontStyle.Italic
                    )

                // 台词 / 普通文本：全功能 inline markdown，角色口音色调
                else ->
                    Text(
                        text = buildInlineAnnotated(para, nyx.TextPrimary, nyx.Layer2, nyx.AccentSoft),
                        color = nyx.TextPrimary,
                        fontSize = 15.5.sp,
                        lineHeight = 25.sp,
                        fontFamily = CrimsonProFamily
                    )
            }
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
    NavItem("relations",    "◍", "关系"),
    NavItem("settings",     "◎", "设置"),
)

@Composable
fun BottomNav(currentRoute: String, onNavigate: (String) -> Unit) {
    val borderMid = LocalNyxColors.current.BorderMid   // 提前提取，drawBehind 非 Composable 上下文
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // Top separator gradient line
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, borderMid, Color.Transparent)
                    ),
                    start = Offset(0f, 0f), end = Offset(size.width, 0f),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
            .background(
                Brush.verticalGradient(
                    0f to LocalNyxColors.current.Layer1.copy(alpha = 0.95f),
                    1f to LocalNyxColors.current.Layer1
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
                    if (active) LocalNyxColors.current.AccentSoft else LocalNyxColors.current.TextDim,
                    tween(250), label = "nav_color_${item.route}"
                )

                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onNavigate(item.route) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Active pill indicator above label
                    Box(
                        Modifier
                            .height(2.5.dp)
                            .width(if (active) 20.dp else 0.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (active)
                                    Brush.horizontalGradient(listOf(LocalNyxColors.current.Accent, LocalNyxColors.current.AccentSoft))
                                else
                                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                    )

                    Text(
                        item.label,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = itemColor,
                        letterSpacing = 0.5.sp
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
