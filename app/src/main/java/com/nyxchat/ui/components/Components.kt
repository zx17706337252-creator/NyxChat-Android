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
// Redesigned with premium layering:
//  • Outer glow ring (character color, very soft)
//  • Middle border with gradient shimmer (top-lighter, bottom-darker)
//  • Inner frosted background
//  • Mood dot with a subtle halo

@Composable
fun Portrait(
    char: NyxCharacter,
    size: Dp = 44.dp,
    isTyping: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val nyx      = LocalNyxColors.current
    val moodCol  by animateColorAsState(moodColor(char.mood), tween(800), label = "mood")
    val charCol   = char.color
    val hexShape  = remember { HexagonShape() }

    Box(
        Modifier
            .size(size + 4.dp)   // extra room for the outer glow ring
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // ── Outer glow ring (very subtle) ──────────────────────────────
        Box(
            Modifier
                .size(size + 4.dp)
                .clip(hexShape)
                .background(
                    Brush.radialGradient(
                        listOf(charCol.copy(0.18f), Color.Transparent)
                    )
                )
        )

        // ── Main hexagon ───────────────────────────────────────────────
        Box(
            Modifier
                .size(size)
                .clip(hexShape)
                // Inner gradient: top-left lighter for "lit surface" feel
                .background(
                    Brush.linearGradient(
                        0f   to charCol.copy(0.22f),
                        0.45f to charCol.copy(0.10f),
                        1f   to nyx.Layer1.copy(alpha = 0.96f),
                        start = Offset(0f, 0f),
                        end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                // Top-edge shimmer border to simulate light hitting the edge
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        0f   to charCol.copy(0.75f),
                        0.4f to charCol.copy(0.35f),
                        1f   to charCol.copy(0.08f),
                        start = Offset(0f, 0f),
                        end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = hexShape
                ),
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
                Text(
                    char.initials,
                    color = charCol.copy(alpha = 0.95f),
                    fontSize = (size.value * .35f).sp,
                    fontFamily = CinzelFamily,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Mood indicator dot ─────────────────────────────────────────
        // Two layers: outer halo + inner solid dot
        Box(Modifier.align(Alignment.BottomEnd)) {
            // Soft halo
            Box(
                Modifier
                    .size(size * .30f)
                    .clip(CircleShape)
                    .background(moodCol.copy(alpha = 0.25f))
            )
            // Solid center
            Box(
                Modifier
                    .size(size * .20f)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(moodCol)
                    .border(1.dp, nyx.Background, CircleShape)
            )
        }
    }
}

// ─── Message Bubble ──────────────────────────────────────────────────────────

// ─── SummaryBubble ────────────────────────────────────────────────────────────
// 历史摘要消息专用气泡：居中显示，视觉上区别于普通对话消息
@Composable
private fun SummaryBubble(msg: NyxMessage) {
    val nyx = LocalNyxColors.current
    val content = msg.content.removePrefix("【历史摘要】").trim()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                // Layered: background gradient + border + inner padding
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        0f to nyx.Layer3.copy(alpha = 0.80f),
                        1f to nyx.Layer2.copy(alpha = 0.90f)
                    )
                )
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        0f to nyx.BorderMid,
                        1f to nyx.BorderSubtle
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Decorative divider with fade
            Box(
                Modifier
                    .width(80.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, nyx.BorderMid, Color.Transparent)
                        )
                    )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "本章小结",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = nyx.AccentSoft.copy(alpha = 0.7f),
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                content,
                fontSize = 12.sp,
                color = nyx.TextSecond,
                lineHeight = 19.sp,
                fontStyle = FontStyle.Italic
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width(80.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, nyx.BorderSubtle, Color.Transparent)
                        )
                    )
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

    val nyx    = LocalNyxColors.current
    val isUser = msg.role == "user"
    val char   = chars.find { it.id == msg.charId }
    val charCol = char?.color ?: nyx.Accent

    // User bubbles: top-right corner is sharp; char bubbles: top-left is sharp
    val topS = if (isUser) 18.dp else 4.dp
    val topE = if (isUser) 4.dp else 18.dp
    val shape = RoundedCornerShape(topStart = topS, topEnd = topE, bottomStart = 18.dp, bottomEnd = 18.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) 56.dp else 0.dp,
                end   = if (isUser) 0.dp else 56.dp,
                bottom = 14.dp
            ),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // ── Character avatar (left side) ──────────────────────────────
        if (!isUser) {
            char?.let {
                Portrait(it, 36.dp)
                Spacer(Modifier.width(9.dp))
            } ?: Spacer(Modifier.width(45.dp))
        }

        Column(
            Modifier.widthIn(max = 268.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Character name label
            if (!isUser && char != null) {
                Text(
                    char.name,
                    color = charCol.copy(alpha = 0.90f),
                    fontSize = 10.sp,
                    fontFamily = CinzelFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 5.dp, start = 6.dp)
                )
            }

            // ── The bubble itself ──────────────────────────────────────
            // Layer 1: Drop shadow (painted behind via drawBehind)
            // Layer 2: Gradient background fill
            // Layer 3: Top-edge shimmer border (simulates light on the rim)
            Box(
                Modifier
                    .clip(shape)
                    // Elevation shadow effect via background blur with offset
                    .drawBehind {
                        // Soft shadow — offset down-right, color is char/accent at low alpha
                        val shadowColor = if (isUser) nyx.AccentDeep.copy(0.35f) else charCol.copy(0.20f)
                        drawRoundRect(
                            color = shadowColor,
                            topLeft = Offset(3f, 5f),
                            size = Size(size.width, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
                        )
                    }
                    // Gradient background fill
                    .background(
                        if (isUser)
                            Brush.linearGradient(
                                0f   to nyx.AccentDeep.copy(alpha = 0.38f),
                                0.5f to nyx.Accent.copy(alpha = 0.18f),
                                1f   to nyx.UserBubbleGrad2,
                                start = Offset(0f, 0f),
                                end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        else
                            Brush.linearGradient(
                                0f   to charCol.copy(alpha = 0.13f),
                                0.6f to charCol.copy(alpha = 0.06f),
                                1f   to nyx.Layer1.copy(alpha = 0.95f),
                                start = Offset(0f, 0f),
                                end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                    )
                    // Outer border with gradient shimmer
                    .border(
                        width = 0.75.dp,
                        brush = if (isUser)
                            Brush.linearGradient(
                                0f   to nyx.AccentBright.copy(0.60f),
                                0.5f to nyx.Accent.copy(0.35f),
                                1f   to nyx.AccentDeep.copy(0.15f),
                                start = Offset(0f, 0f),
                                end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        else
                            Brush.linearGradient(
                                0f   to charCol.copy(0.45f),
                                0.5f to charCol.copy(0.20f),
                                1f   to charCol.copy(0.05f),
                                start = Offset(0f, 0f),
                                end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            ),
                        shape = shape
                    )
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            ) {
                val isNarrative = msg.content.startsWith("[旁白：") && msg.content.endsWith("]")
                val displayText = if (isNarrative)
                    msg.content.removePrefix("[旁白：").removeSuffix("]")
                else msg.content

                if (isNarrative) {
                    MarkdownText(
                        text      = displayText,
                        color     = nyx.TextSecond,
                        fontSize  = 14.sp,
                        lineHeight = 22.sp,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    StyledContent(
                        text      = displayText,
                        charColor = if (isUser) nyx.AccentSoft else charCol
                    )
                }
                if (showCursor) {
                    val blink = rememberInfiniteTransition(label = "cursor")
                    val alpha by blink.animateFloat(0f, 1f,
                        infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "blink")
                    Text(
                        "▍",
                        color = (chars.find { it.id == msg.charId }?.color ?: nyx.Accent).copy(alpha),
                        fontSize = 14.sp
                    )
                }
            }

            // Timestamp
            Text(
                formatTime(msg.timestamp),
                color = nyx.TextDim.copy(alpha = 0.75f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 4.dp, start = 6.dp, end = 6.dp)
            )
        }

        // ── User avatar (right side) ───────────────────────────────────
        if (isUser) {
            Spacer(Modifier.width(9.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    // Gradient background matching user bubble palette
                    .background(
                        Brush.linearGradient(
                            listOf(nyx.AccentDeep.copy(0.28f), nyx.AccentPill)
                        )
                    )
                    .border(
                        width = 0.75.dp,
                        brush = Brush.verticalGradient(
                            listOf(nyx.AccentBright.copy(0.5f), nyx.Accent.copy(0.15f))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) { Text("✦", color = nyx.AccentSoft.copy(alpha = 0.85f), fontSize = 12.sp) }
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
// Premium version: character-color gradient bubble with elevated shadow

@Composable
fun TypingIndicator(char: NyxCharacter) {
    val nyx   = LocalNyxColors.current
    val mc    = moodColor(char.mood)
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Portrait(char, 36.dp, isTyping = true)
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier
                .clip(shape)
                .drawBehind {
                    drawRoundRect(
                        color = char.color.copy(0.18f),
                        topLeft = Offset(2f, 4f),
                        size = Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
                    )
                }
                .background(
                    Brush.linearGradient(
                        0f   to char.color.copy(alpha = 0.14f),
                        0.6f to char.color.copy(alpha = 0.06f),
                        1f   to nyx.Layer1.copy(alpha = 0.95f),
                        start = Offset(0f, 0f),
                        end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .border(
                    width = 0.75.dp,
                    brush = Brush.linearGradient(
                        0f   to char.color.copy(0.45f),
                        0.5f to char.color.copy(0.18f),
                        1f   to char.color.copy(0.04f),
                        start = Offset(0f, 0f),
                        end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = shape
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
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
    val nyx = LocalNyxColors.current
    val borderMid = nyx.BorderMid

    Box(
        Modifier
            .fillMaxWidth()
            // Elevation shadow at top — drawn behind via drawBehind
            .drawBehind {
                // Top separator: gradient line
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, borderMid.copy(alpha = 0.6f), Color.Transparent)
                    ),
                    start = Offset(0f, 0f), end = Offset(size.width, 0f),
                    strokeWidth = 0.75.dp.toPx()
                )
                // Inner glow line just below the separator
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, nyx.AccentGlow.copy(0.3f), Color.Transparent)
                    ),
                    start = Offset(size.width * 0.1f, 1.dp.toPx()),
                    end   = Offset(size.width * 0.9f, 1.dp.toPx()),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
            // Frosted glass background with gradient depth
            .background(
                Brush.verticalGradient(
                    0f   to nyx.Layer2.copy(alpha = 0.95f),
                    0.4f to nyx.Layer1.copy(alpha = 0.98f),
                    1f   to nyx.Layer1
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
                    if (active) nyx.AccentSoft else nyx.TextDim,
                    tween(280), label = "nav_color_${item.route}"
                )
                val pillWidth by androidx.compose.animation.core.animateDpAsState(
                    if (active) 28.dp else 0.dp,
                    tween(280, easing = FastOutSlowInEasing),
                    label = "pill_${item.route}"
                )

                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onNavigate(item.route) }
                        .padding(vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Active indicator pill — gradient, animated width
                    Box(
                        Modifier
                            .height(2.5.dp)
                            .width(pillWidth)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(nyx.AccentDeep, nyx.AccentSoft)
                                )
                            )
                    )

                    Text(
                        item.label,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = itemColor,
                        letterSpacing = 0.8.sp
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
