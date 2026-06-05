package com.zaijian.zhoumuyun.data.prompt

import com.zaijian.zhoumuyun.data.db.entity.CharacterIdentityEntity
import com.zaijian.zhoumuyun.data.db.entity.MemoryEntity
import com.zaijian.zhoumuyun.data.model.CharacterConfig
import com.zaijian.zhoumuyun.data.provider.LLMMessage

/**
 * Prompt Orchestration Layer
 *
 * 六层架构（按设计方案 §11）：
 * | Layer    | Token上限 | 状态          |
 * |----------|----------|--------------|
 * | Identity | 1500     | ✅ Phase 7    |
 * | State    | 500      | 🔲 Phase 9   |
 * | Memory   | 1000     | ✅ Phase 8    |
 * | World    | 1000     | 🔲 Phase 10  |
 * | Task     | 1500     | 🔲 Phase 11  |
 * | Output   | 500      | ✅ Phase 7    |
 *
 * Phase 8 新增：Memory Layer（Core Memory + Relevant Memory）。
 */
object PromptOrchestrator {

    /**
     * 组装 System Prompt（Phase 8 版本）。
     * 包含 Identity Layer + Memory Layer + Output 约束。
     *
     * @param character        角色配置（内存中，含 identityConfig）
     * @param identityEntity   从数据库读取的 Identity（优先级高于 CharacterConfig 默认值）
     * @param coreMemories     Core Memory 列表（isCore=true，最多 5 条，每次必须注入）
     * @param relevantMemories 相关记忆（FTS 检索召回，最多 10 条，按对话内容动态变化）
     * @param userName         用户昵称（可为空，默认"你"）
     */
    fun buildSystemPrompt(
        character: CharacterConfig,
        identityEntity: CharacterIdentityEntity?,
        coreMemories: List<MemoryEntity> = emptyList(),
        relevantMemories: List<MemoryEntity> = emptyList(),
        userName: String = "你",
    ): String {
        // identityEntity 不为空时，数据库值优先；否则用内存默认值
        val persona = identityEntity?.persona
            ?.takeIf { it.isNotEmpty() }
            ?: character.identityConfig.persona

        val speechStyle = identityEntity?.speechStyle
            ?.takeIf { it.isNotEmpty() }
            ?: character.identityConfig.speechStyle

        val attitudeToUser = identityEntity?.attitudeToUser
            ?.takeIf { it.isNotEmpty() }
            ?: character.identityConfig.attitudeToUser

        val customSystemPrompt = identityEntity?.customSystemPrompt
            ?: character.identityConfig.customSystemPrompt

        // customSystemPrompt 非空时：Identity 完全替换，但 Memory Layer 仍注入
        val identityBlock = if (!customSystemPrompt.isNullOrEmpty()) {
            customSystemPrompt
        } else if (persona.isEmpty() && speechStyle.isEmpty()) {
            buildDefaultIdentity(character.name, userName)
        } else {
            buildIdentityBlock(
                name           = character.name,
                persona        = persona,
                speechStyle    = speechStyle,
                attitudeToUser = attitudeToUser,
                boundaries     = character.identityConfig.boundaries,
                coreBeliefs    = character.identityConfig.coreBeliefs,
                userName       = userName,
            )
        }

        // Memory Layer（§11.4）
        val memoryBlock = buildMemoryBlock(coreMemories, relevantMemories)

        // Output 约束
        val outputBlock = OUTPUT_CONSTRAINTS

        return buildString {
            append(identityBlock)
            if (memoryBlock.isNotEmpty()) {
                appendLine()
                appendLine()
                append(memoryBlock)
            }
            appendLine()
            appendLine()
            append(outputBlock)
        }
    }

    /**
     * 将消息历史转为 LLMMessage 列表（供 Provider.chat() 使用）。
     * 保留最近 N 条（设计方案 §6.8：Conversation ≤8）。
     */
    fun buildMessageHistory(
        messages: List<Pair<String, String>>,  // Pair(role, content)
        maxTurns: Int = 8,
    ): List<LLMMessage> {
        return messages
            .takeLast(maxTurns * 2)  // 每轮含用户和角色各一条
            .map { (role, content) -> LLMMessage(role, content) }
    }

    // ── Memory Layer 构建（§11.4）────────────────────────────

    /**
     * 构建 Memory Layer 文本块。
     *
     * 格式：
     * ```
     * 核心记忆（必须记住）：
     * 1. [core memory]
     * ...（最多 5 条，isCore=true 的记忆，永远注入）
     *
     * 相关记忆（本次对话相关）：
     * 1. [relevant memory]
     * ...（最多 10 条，按检索评分召回）
     * ```
     *
     * 无记忆时返回空字符串（不注入空块，节省 Token）。
     */
    private fun buildMemoryBlock(
        coreMemories: List<MemoryEntity>,
        relevantMemories: List<MemoryEntity>,
    ): String {
        if (coreMemories.isEmpty() && relevantMemories.isEmpty()) return ""

        return buildString {
            if (coreMemories.isNotEmpty()) {
                appendLine("核心记忆（必须记住）：")
                coreMemories.take(5).forEachIndexed { i, m ->
                    appendLine("${i + 1}. ${m.content}")
                }
            }

            if (relevantMemories.isNotEmpty()) {
                if (coreMemories.isNotEmpty()) appendLine()
                appendLine("相关记忆（本次对话相关）：")
                // 排除与 Core 重复的条目
                val coreIds = coreMemories.map { it.id }.toSet()
                relevantMemories
                    .filter { it.id !in coreIds }
                    .take(10)
                    .forEachIndexed { i, m ->
                        appendLine("${i + 1}. ${m.content}")
                    }
            }
        }.trimEnd()
    }

    // ── Identity Layer 构建 ───────────────────────────────────

    private fun buildDefaultIdentity(characterName: String, userName: String): String = """
你是$characterName。

请用自然、有温度的方式与${userName}对话。保持角色一致，不要破坏第四堵墙。
不要提及你是 AI，不要提及模型名称。

回复长度：自然对话节奏，不过度简短也不过度冗长。
语言：中文。
    """.trimIndent()

    private fun buildIdentityBlock(
        name: String,
        persona: String,
        speechStyle: String,
        attitudeToUser: String,
        boundaries: List<String>,
        coreBeliefs: List<String>,
        userName: String,
    ): String = buildString {
        appendLine("你是$name。")
        appendLine()
        if (persona.isNotEmpty()) {
            appendLine(persona)
            appendLine()
        }
        if (speechStyle.isNotEmpty()) {
            appendLine("你说话的方式：$speechStyle")
            appendLine()
        }
        if (attitudeToUser.isNotEmpty()) {
            appendLine("你对${userName}的态度：$attitudeToUser")
            appendLine()
        }
        if (boundaries.isNotEmpty()) {
            appendLine("你绝对不会：")
            boundaries.forEach { appendLine("- $it") }
            appendLine()
        }
        if (coreBeliefs.isNotEmpty()) {
            appendLine("你相信：")
            coreBeliefs.forEach { appendLine("- $it") }
        }
    }.trimEnd()

    // ── Output 约束（固定不变）────────────────────────────────

    private const val OUTPUT_CONSTRAINTS = """不要提及你是 AI，不要提及模型名称，不要破坏第四堵墙。
回复语言：中文。
如果工具执行了某个操作，用第一人称表达结果，不暴露工具或 Agent 的存在。"""
}
