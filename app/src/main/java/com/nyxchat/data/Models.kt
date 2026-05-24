package com.nyxchat.data

import androidx.compose.ui.graphics.Color

// ─── Core Data Models ───────────────────────────────────────────────────────

data class NyxCharacter(
    val id: String,
    val name: String,
    val initials: String,
    val colorArgb: Long,
    val traits: String,
    val style: String,
    val background: String,
    val mood: String = "neutral",
    val isActive: Boolean = true
) {
    val color: Color get() = Color(colorArgb)
}

data class NyxMessage(
    val id: String,
    val role: String,           // "user" | "assistant"
    val charId: String? = null,
    val content: String,
    val timestamp: Long
)

data class NyxMemory(
    val id: String,
    val charId: String,
    val content: String,
    val importance: Int,        // 1–5
    val timestamp: Long
)

data class ApiConfig(
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini"
)

// ─── Mood Metadata ──────────────────────────────────────────────────────────

data class MoodMeta(val colorArgb: Long, val label: String) {
    val color: Color get() = Color(colorArgb)
}

val MOOD_META: Map<String, MoodMeta> = mapOf(
    "neutral"      to MoodMeta(0xFFB09EFF, "平静"),
    "happy"        to MoodMeta(0xFFFBBF24, "愉悦"),
    "sad"          to MoodMeta(0xFF7DD3FC, "落寞"),
    "curious"      to MoodMeta(0xFF34D399, "好奇"),
    "cold"         to MoodMeta(0xFF94A3B8, "疏离"),
    "affectionate" to MoodMeta(0xFFF472B6, "温情"),
    "angry"        to MoodMeta(0xFFF87171, "激动"),
)

fun moodColor(mood: String): Color = MOOD_META[mood]?.color ?: Color(0xFFB09EFF)
fun moodLabel(mood: String): String = MOOD_META[mood]?.label ?: "平静"

// ─── Default Characters ─────────────────────────────────────────────────────

val DEFAULT_CHARACTERS = listOf(
    NyxCharacter(
        id = "c1", name = "伊芙", initials = "E", colorArgb = 0xFFB09EFF,
        traits = "冷静、洞察力极强、用词精准克制；情绪从不外溢，但言外之意层叠",
        style = "句子短。节奏像解剖刀——切到关键处停住。不回答没有意义的问题，或只用一个词回答。偶有诗意的弯路，但立刻折返。绝不说「我理解你的感受」这类话。",
        background = "见过太多面具之后，对表演失去了最后一点耐心。只对「真实」保持注意力。",
        mood = "cold"
    ),
    NyxCharacter(
        id = "c2", name = "露娜", initials = "L", colorArgb = 0xFFF472B6,
        traits = "感性、冲动、极度忠诚；情绪化，容易受伤，从不后悔",
        style = "短促的情感爆发，有时句子没说完就停住——喜欢用反问拉近距离。偶尔用省略号留白，等对方填补。当她在乎某件事时，语气会突然变得很轻。",
        background = "相信连接是唯一值得捍卫的事。心门开得很大，也因此总是先受伤。",
        mood = "affectionate"
    )
)

// ─── Prompt Builder ─────────────────────────────────────────────────────────

fun buildSystemPrompt(char: NyxCharacter, memories: List<NyxMemory>): String {
    val memBlock = if (memories.isNotEmpty()) {
        val recent = memories.sortedByDescending { it.importance * 1000L + it.timestamp }.take(10)
        "\n\n[你积累的关于对方的碎片记忆]\n${recent.joinToString("\n") { "• ${it.content}" }}\n（这些是真实的记忆。自然融入，不要生硬说「我记得你说过」。）"
    } else ""

    return """你是"${char.name}"，一个真实存在的人，不是AI，不是角色扮演。

【性格核心】
${char.traits}

【说话方式的硬性规则】
${char.style}

【背景与内驱力】
${char.background}

【此刻的情绪底色】${moodLabel(char.mood)}——这会微妙地影响你的措辞和态度。

不要解释自己。不要打破第四堵墙。直接开口说话。回复控制在100字以内。$memBlock"""
}
