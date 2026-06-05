package com.zaijian.zhoumuyun.data.memory

import com.zaijian.zhoumuyun.data.db.entity.EventType
import com.zaijian.zhoumuyun.data.db.entity.MemoryCandidateEntity
import com.zaijian.zhoumuyun.data.db.entity.MemoryDomain
import com.zaijian.zhoumuyun.data.db.entity.MemoryEntity
import com.zaijian.zhoumuyun.data.db.entity.WorldEventEntity
import com.zaijian.zhoumuyun.data.repository.EventRepository
import com.zaijian.zhoumuyun.data.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Memory Engine（Phase 8）
 *
 * 管道：Message → Event → MemoryCandidate → Memory
 *
 * 核心原则（§6）：
 * - Memory 不是聊天记录，禁止直接将聊天文本写入 memories 表
 * - 所有记忆必须经过 Candidate 层：先评分，再决定是否晋升
 * - 触发条件（§6.7）：MESSAGE / PROJECT_UPDATED / PROJECT_MILESTONE /
 *   TASK_COMPLETED / RELATIONSHIP_CHANGED / WORLD_SIMULATION
 *
 * 当前阶段（Phase 8）实现：
 * - 规则引擎提取关键事实（不依赖 LLM）
 * - 对话结束后批量处理候选（不阻塞 UI）
 * - Phase 9 可接入 LLM 做智能摘要提炼
 *
 * 四层演化方向（§6.2）：
 * Fact → Pattern → Preference → Trait
 * 当前 Phase 8 实现 Fact 层，后续版本升级。
 */
class MemoryEngine(
    private val memoryRepo: MemoryRepository,
    private val eventRepo: EventRepository,
) {

    // ─────────────────────────────────────────────────────────
    //  1. 对话结束后触发：从最近 EVENT 提取候选
    // ─────────────────────────────────────────────────────────

    /**
     * 对话结束时调用（由 ChatViewModel 在 AI 回复完成后触发）。
     *
     * 流程：
     * 1. 取最近的 MESSAGE 事件（本轮对话的用户消息和角色回复）
     * 2. 规则引擎提取候选记忆
     * 3. 写入 memory_candidates 表
     * 4. 立即处理候选（不等待）
     *
     * @param characterId 当前对话的角色 ID
     * @param userMessage 用户的原始消息内容
     * @param assistantReply 角色的完整回复内容
     * @param userEventId 用户消息对应的 WorldEvent ID
     */
    suspend fun onConversationTurn(
        characterId: Int,
        userMessage: String,
        assistantReply: String,
        userEventId: String,
    ) = withContext(Dispatchers.IO) {
        // 提取候选
        val candidates = extractCandidates(
            characterId    = characterId,
            userMessage    = userMessage,
            assistantReply = assistantReply,
            sourceEventId  = userEventId,
        )

        // 写入候选表
        candidates.forEach { memoryRepo.insertCandidate(it) }

        // 立即处理候选（晋升为 Memory 或丢弃）
        processPendingCandidates(characterId)
    }

    /**
     * 处理所有未处理的候选（可独立调用，如 App 启动时补处理）。
     */
    suspend fun processPendingCandidates(characterId: Int) = withContext(Dispatchers.IO) {
        val pending = memoryRepo.getPendingCandidates(characterId)
        pending.forEach { candidate -> processCandidate(candidate) }
    }

    // ─────────────────────────────────────────────────────────
    //  2. 规则引擎：从对话中提取候选
    // ─────────────────────────────────────────────────────────

    /**
     * 规则引擎提取候选。
     *
     * Phase 8 使用规则匹配（不依赖 LLM）：
     * - 用户消息：提取用户关于自己的陈述（偏好、信息、情感表达）
     * - 角色回复：提取角色的重要行为和承诺
     *
     * Phase 9/10 可替换为 LLM 摘要：
     * 将 (userMessage, assistantReply) 发给模型，
     * 要求提取 3-5 条关键事实，指定 domain 和 score。
     */
    private fun extractCandidates(
        characterId: Int,
        userMessage: String,
        assistantReply: String,
        sourceEventId: String,
    ): List<MemoryCandidateEntity> {
        val now = System.currentTimeMillis()
        val results = mutableListOf<MemoryCandidateEntity>()

        // ── 规则 1：用户自我描述（偏好/习惯/感受）────────────
        val userFact = extractUserFact(userMessage)
        if (userFact != null) {
            results.add(
                MemoryCandidateEntity(
                    id            = UUID.randomUUID().toString(),
                    characterId   = characterId,
                    sourceEventId = sourceEventId,
                    content       = userFact.content,
                    score         = userFact.score,
                    domain        = MemoryDomain.PERSONAL.name,
                    projectId     = null,
                    createdAt     = now,
                )
            )
        }

        // ── 规则 2：情感倾向（高情感强度的对话）─────────────
        val emotionFact = extractEmotionFact(userMessage, assistantReply)
        if (emotionFact != null) {
            results.add(
                MemoryCandidateEntity(
                    id            = UUID.randomUUID().toString(),
                    characterId   = characterId,
                    sourceEventId = sourceEventId,
                    content       = emotionFact.content,
                    score         = emotionFact.score,
                    domain        = MemoryDomain.PERSONAL.name,
                    projectId     = null,
                    createdAt     = now,
                )
            )
        }

        // ── 规则 3：角色承诺/约定（会产生 World Memory）─────
        val worldFact = extractWorldFact(assistantReply)
        if (worldFact != null) {
            results.add(
                MemoryCandidateEntity(
                    id            = UUID.randomUUID().toString(),
                    characterId   = characterId,
                    sourceEventId = sourceEventId,
                    content       = worldFact.content,
                    score         = worldFact.score,
                    domain        = MemoryDomain.WORLD.name,
                    projectId     = null,
                    createdAt     = now,
                )
            )
        }

        return results
    }

    // ─────────────────────────────────────────────────────────
    //  3. 候选晋升：Candidate → Memory（或丢弃）
    // ─────────────────────────────────────────────────────────

    private suspend fun processCandidate(candidate: MemoryCandidateEntity) {
        // score=1：直接丢弃
        if (candidate.score <= 1) {
            memoryRepo.markCandidateProcessed(candidate.id, null)
            return
        }

        val now = System.currentTimeMillis()
        val memory = MemoryEntity(
            id             = UUID.randomUUID().toString(),
            characterId    = candidate.characterId,
            domain         = candidate.domain,
            content        = candidate.content,
            importance     = candidate.score,
            keywords       = extractKeywords(candidate.content),
            sourceEventId  = candidate.sourceEventId,
            isCore         = candidate.score >= 5,
            projectId      = candidate.projectId,
            accessCount    = 0,
            createdAt      = now,
            updatedAt      = now,
            lastAccessedAt = now,
        )

        // saveOrMerge：有相似记忆则 Merge，否则写入新记录
        val resultId = memoryRepo.saveOrMerge(memory)

        // 标记候选已处理
        memoryRepo.markCandidateProcessed(candidate.id, resultId)

        // 写 MEMORY_CREATED 事件（Event Engine 原则：所有写操作必须产生 Event）
        eventRepo.appendMemoryEvent(
            characterId = candidate.characterId,
            memoryId    = resultId,
            isUpdate    = resultId != memory.id,   // Merge 时 resultId 是已有 Memory 的 ID
            content     = candidate.content.take(80),
        )
    }

    // ─────────────────────────────────────────────────────────
    //  4. 规则引擎子方法
    // ─────────────────────────────────────────────────────────

    private data class ExtractedFact(val content: String, val score: Int)

    /**
     * 提取用户关于自己的陈述。
     * 匹配模式：第一人称 + 偏好/状态/信息词
     */
    private fun extractUserFact(message: String): ExtractedFact? {
        val text = message.trim()
        if (text.length < 5) return null

        // 高分模式：明确的个人信息
        val highPatterns = listOf(
            "我叫", "我是", "我的名字", "我的爱好", "我喜欢", "我不喜欢",
            "我最喜欢", "我讨厌", "我害怕", "我希望", "我梦想",
            "我住在", "我在", "我的工作", "我的职业",
        )
        for (pattern in highPatterns) {
            if (text.contains(pattern)) {
                val extracted = extractSentenceContaining(text, pattern)
                if (extracted != null) return ExtractedFact(extracted, 4)
            }
        }

        // 中分模式：情感/状态表达
        val midPatterns = listOf(
            "我觉得", "我感觉", "我认为", "我想", "我需要",
            "我最近", "我今天", "我一直", "我经常", "我总是",
            "我很", "我有点", "我比较", "我有些",
        )
        for (pattern in midPatterns) {
            if (text.contains(pattern)) {
                val extracted = extractSentenceContaining(text, pattern)
                if (extracted != null) return ExtractedFact(extracted, 3)
            }
        }

        return null
    }

    /**
     * 提取对话中的情感事件（较高情感强度的互动）。
     */
    private fun extractEmotionFact(userMessage: String, assistantReply: String): ExtractedFact? {
        val combined = "$userMessage $assistantReply"

        // 高情感强度关键词
        val highEmotionKeywords = listOf(
            "谢谢你", "感谢你", "很感动", "好感动", "我爱", "我好喜欢你",
            "你真的很", "你让我", "太重要了", "忘不了",
        )
        for (kw in highEmotionKeywords) {
            if (combined.contains(kw)) {
                val content = "用户与角色之间发生了情感较深的互动：「${userMessage.take(40)}」"
                return ExtractedFact(content, 3)
            }
        }

        return null
    }

    /**
     * 提取角色做出的承诺、约定或重要陈述（World Memory）。
     */
    private fun extractWorldFact(assistantReply: String): ExtractedFact? {
        val text = assistantReply.trim()
        if (text.length < 10) return null

        val promisePatterns = listOf(
            "我会", "我会帮你", "我会记住", "下次", "我答应",
            "我保证", "我一定", "我来", "让我来",
        )
        for (pattern in promisePatterns) {
            if (text.contains(pattern)) {
                val extracted = extractSentenceContaining(text, pattern)
                if (extracted != null) return ExtractedFact(extracted, 3)
            }
        }

        return null
    }

    /**
     * 提取包含特定关键词的完整句子。
     * 用标点符号做句子边界分割。
     */
    private fun extractSentenceContaining(text: String, keyword: String): String? {
        val sentences = text.split(Regex("[。！？.!?；;，,\\n]"))
        val sentence = sentences.firstOrNull { it.contains(keyword) }?.trim()
        return if (sentence != null && sentence.length >= 4) sentence else null
    }

    /**
     * 从记忆内容提取关键词（供 FTS4 检索用）。
     *
     * 简单策略：
     * - 提取长度 >= 2 的中文词组
     * - 过滤停用词
     * - Phase 10 可用 jieba 等分词库替换
     */
    private fun extractKeywords(content: String): String {
        val stopWords = setOf(
            "的", "了", "在", "是", "我", "你", "他", "她", "它",
            "有", "和", "就", "不", "人", "都", "一", "一个",
            "也", "很", "到", "说", "要", "去", "你好", "我们",
        )
        // 提取所有 2-6 字的片段
        val keywords = mutableSetOf<String>()
        var i = 0
        while (i < content.length - 1) {
            for (len in 2..minOf(6, content.length - i)) {
                val word = content.substring(i, i + len)
                if (word !in stopWords && word.all { it.code > 127 || it.isLetterOrDigit() }) {
                    keywords.add(word)
                }
            }
            i++
        }
        return keywords.take(10).joinToString(" ")
    }
}

// ─────────────────────────────────────────────────────────────
//  EventRepository 扩展：写 Memory 相关事件
// ─────────────────────────────────────────────────────────────

suspend fun EventRepository.appendMemoryEvent(
    characterId: Int,
    memoryId: String,
    isUpdate: Boolean,
    content: String,
) {
    val type = if (isUpdate)
        com.zaijian.zhoumuyun.data.db.entity.EventType.MEMORY_UPDATED
    else
        com.zaijian.zhoumuyun.data.db.entity.EventType.MEMORY_CREATED

    append(
        com.zaijian.zhoumuyun.data.db.entity.WorldEventEntity(
            id         = UUID.randomUUID().toString(),
            type       = type.name,
            actorId    = characterId.toString(),
            targetId   = null,
            domain     = com.zaijian.zhoumuyun.data.db.entity.EventDomain.PERSONAL.name,
            projectId  = null,
            payload    = org.json.JSONObject().apply {
                put("memoryId", memoryId)
                put("preview", content)
            }.toString(),
            importance = 2,
            createdAt  = System.currentTimeMillis(),
        )
    )
}
