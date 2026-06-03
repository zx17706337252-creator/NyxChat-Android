package com.nyxchat.pipeline

import com.nyxchat.data.*
import org.json.JSONObject

// ─── Turn Scheduler ───────────────────────────────────────────────────────────
// Decides which characters speak, in what order, and whether to stay silent

data class TurnDecision(
    val speakers: List<String>,          // charIds in speaking order
    val silent: List<String>,            // charIds staying silent this round
    val targets: Map<String, String?>    // charId -> who they're responding to (null = user)
)

object TurnScheduler {

    // 会话调度阈值常量
    private const val SILENCE_THRESHOLD = 30f
    // Bug 3 fix: FALLBACK_SPEAKER_LIMIT 原先声明但从未使用（死代码），已移除。
    // 回退逻辑在 scheduleHeuristic() 末尾通过 if (speakers.isEmpty()) 处理。

    // Fast heuristic scheduling (no API call, uses personality rules)
    fun scheduleHeuristic(
        activeChars: List<NyxCharacter>,
        userInput: String,
        lastRoundSpeakers: List<String>,
        // Bug 4 fix: 连续沉默轮数上限。超过此轮数的角色强制纳入 speakers，
        // 避免内向/冷漠角色因阈值过滤而永久沉默、"怎么叫也不发言"。
        silentCharsThisRound: Map<String, Int> = emptyMap(),
        maxSilentRounds: Int = 3
    ): TurnDecision {
        if (activeChars.isEmpty()) return TurnDecision(emptyList(), emptyList(), emptyMap())
        if (activeChars.size == 1) return TurnDecision(
            listOf(activeChars[0].id), emptyList(), mapOf(activeChars[0].id to null)
        )

        // Score each character's "urgency" to respond
        val scores = activeChars.associate { char ->
            var score = 50f

            // Mood influence
            score += when (char.mood) {
                "affectionate" -> 20f   // warm chars speak up
                "curious"      -> 15f
                "angry"        -> 25f   // angry chars react fast
                "cold"         -> -15f  // cold chars hold back
                "sad"          -> -10f
                else           -> 0f
            }

            // Spoke last round? Back off slightly
            if (char.id in lastRoundSpeakers) score -= 10f

            // Traits signal introversion/extroversion (simple keyword check)
            val traits = char.traits.lowercase()
            if ("内向" in traits || "寡言" in traits || "沉默" in traits) score -= 20f
            if ("活跃" in traits || "外向" in traits || "冲动" in traits) score += 15f

            char.id to score
        }

        // Sort by score descending; below threshold = silent
        val sorted = scores.entries.sortedByDescending { it.value }
        val speakers = sorted.filter { it.value >= SILENCE_THRESHOLD }.map { it.key }
        val silent   = sorted.filter { it.value < SILENCE_THRESHOLD }.map { it.key }

        // If everyone is too low, at least one speaks
        var finalSpeakers = if (speakers.isEmpty()) listOf(sorted.first().key) else speakers

        // Bug 4 fix: 对超过 maxSilentRounds 轮沉默的角色强制纳入 speakers，
        // 防止内向/冷漠角色（如 cold + 沉默特征，得分 15 < 30）永久沉默。
        val forcedSpeakers = silent.filter { id ->
            (silentCharsThisRound[id] ?: 0) >= maxSilentRounds
        }
        if (forcedSpeakers.isNotEmpty()) {
            finalSpeakers = (finalSpeakers + forcedSpeakers).distinct()
        }

        // Simple targeting: first speaker addresses user, subsequent speakers may address a prior speaker
        val targets = finalSpeakers.mapIndexed { i, id ->
            val target = if (i == 0) null
                         else finalSpeakers.getOrNull(i - 1)  // respond to the char before them
            id to target
        }.toMap()

        return TurnDecision(finalSpeakers, silent, targets)
    }

    // AI-powered scheduling (costs ~80 tokens, better results)
    suspend fun scheduleWithAI(
        activeChars: List<NyxCharacter>,
        recentMessages: List<NyxMessage>,
        userInput: String,
        // Bug 4 fix: 接收外部传入的沉默轮次 map，供 heuristic fallback 使用
        silentCharsMap: Map<String, Int> = emptyMap(),
        callAI: suspend (List<Map<String, String>>) -> String
    ): TurnDecision {
        // Bug 4 fix: 从 recentMessages 推导上一轮发言者，用于 heuristic fallback 中的
        // 连续发言惩罚（-10 分），避免同一角色在 AI fallback 路径下也连续霸屏。
        // 取最后一条 user 消息之后的所有 assistant 消息的 charId 即为上一轮发言角色。
        val lastUserIdx = recentMessages.indexOfLast { it.role == "user" }
        val lastRoundSpeakers = if (lastUserIdx >= 0)
            recentMessages.drop(lastUserIdx + 1)
                .filter { it.role == "assistant" }
                .mapNotNull { it.charId }
                .distinct()
        else emptyList()

        val charList = activeChars.joinToString("\n") { c ->
            "- ${c.name}（${c.id}）：${c.traits.take(50)}，情绪：${moodLabel(c.mood)}"
        }
        val recentCtx = recentMessages.takeLast(6).joinToString("\n") { m ->
            if (m.role == "user") "用户：${m.content}"
            else "${activeChars.find { it.id == m.charId }?.name ?: "角色"}：${m.content}"
        }

        val prompt = """以下角色正在群聊中：
$charList

最近对话：
$recentCtx

用户刚说：$userInput

判断哪些角色会回应这条消息，用什么顺序，是否有角色保持沉默（沉默也是有效选择）。
考虑每个角色的性格——内向角色不轻易开口，愤怒的角色反应快，冷静的角色选择时机。

只返回JSON，不要其他内容：
{
  "speakers": ["charId1", "charId2"],
  "silent": ["charId3"],
  "targets": {"charId1": null, "charId2": "charId1"}
}
targets中null表示回应用户，字符串表示回应另一个角色。"""

        return try {
            val response = callAI(listOf(mapOf("role" to "user", "content" to prompt)))
            val clean = response.replace("```json", "").replace("```", "").trim()
            val json  = JSONObject(clean)

            val speakerArr = json.getJSONArray("speakers")
            val silentArr  = json.optJSONArray("silent")
            val targetsObj = json.optJSONObject("targets")

            val speakers = (0 until speakerArr.length()).map { speakerArr.getString(it) }
                .filter { id -> activeChars.any { it.id == id } }
            val silent   = if (silentArr != null) (0 until silentArr.length()).map { silentArr.getString(it) } else emptyList()
            val targets  = speakers.associateWith { id ->
                targetsObj?.optString(id)?.takeIf { it != "null" && it.isNotBlank() }
            }

            if (speakers.isEmpty()) scheduleHeuristic(activeChars, userInput, lastRoundSpeakers, silentCharsMap)
            else TurnDecision(speakers, silent, targets)
        } catch (e: Exception) {
            // Bug fix ④: CancellationException 是协程取消信号，必须重新抛出；
            // 原代码吞掉后执行 heuristic 调度，被取消的协程仍会返回结果继续运行。
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Fall back to heuristic on JSON parse / network error
            scheduleHeuristic(activeChars, userInput, lastRoundSpeakers, silentCharsMap)
        }
    }
}
