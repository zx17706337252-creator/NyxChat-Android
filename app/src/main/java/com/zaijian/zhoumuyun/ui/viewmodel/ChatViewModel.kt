package com.zaijian.zhoumuyun.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaijian.zhoumuyun.data.db.AppDatabase
import com.zaijian.zhoumuyun.data.db.entity.MessageEntity
import com.zaijian.zhoumuyun.data.memory.MemoryEngine
import com.zaijian.zhoumuyun.data.model.DefaultCharacters
import com.zaijian.zhoumuyun.data.model.StatusType
import com.zaijian.zhoumuyun.data.prompt.PromptOrchestrator
import com.zaijian.zhoumuyun.data.provider.LLMConfig
import com.zaijian.zhoumuyun.data.provider.LLMMessage
import com.zaijian.zhoumuyun.data.provider.ProviderManager
import com.zaijian.zhoumuyun.data.repository.EventRepository
import com.zaijian.zhoumuyun.data.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

// ─────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val streamingContent: String = "",   // 正在流式生成的内容（打字机效果）
    val error: String? = null,
    val isApiKeyMissing: Boolean = false,
)

data class ChatMessage(
    val id: String,
    val role: String,       // "user" | "assistant"
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

/**
 * ChatViewModel（Phase 8）
 *
 * 职责：
 * 1. 从 Room 加载历史消息（Flow 实时监听）
 * 2. 发送消息 → 写 DB → 记录 MESSAGE 事件 → 调用 LLM → 流式更新 UI
 * 3. LLM 回复完成后写 DB + 记录 MESSAGE 事件
 * 4. API Key 未配置时提示用户去设置页
 * 5. 【Phase 8 新增】对话结束后触发 MemoryEngine 提取记忆
 * 6. 【Phase 8 新增】构建 Prompt 时注入 Core Memory + Relevant Memory
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db           = AppDatabase.getInstance(application)
    private val messageDao   = db.messageDao()
    private val eventRepo    = EventRepository(db.worldEventDao())
    private val identityDao  = db.characterIdentityDao()
    private val memoryRepo   = MemoryRepository(db.memoryDao(), db.memoryCandidateDao())
    private val memoryEngine = MemoryEngine(memoryRepo, eventRepo)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentCharacterId: Int = -1
    private var observeJob: Job? = null
    private var replyJob: Job? = null

    // ── 初始化 ───────────────────────────────────────────────

    fun init(characterId: Int) {
        if (currentCharacterId == characterId) return
        currentCharacterId = characterId
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            messageDao.observeByCharacter(characterId).collectLatest { entities ->
                val messages = entities.map { it.toChatMessage() }
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    // ── 发送消息 ─────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (currentCharacterId < 0) return

        val provider = ProviderManager.instance.activeProvider
        if (provider == null) {
            _uiState.update { it.copy(isApiKeyMissing = true) }
            return
        }

        replyJob?.cancel()
        replyJob = viewModelScope.launch {
            // 1. 存储用户消息
            val userMsgId = UUID.randomUUID().toString()
            val userMsg = MessageEntity(
                id          = userMsgId,
                characterId = currentCharacterId,
                role        = "user",
                content     = text,
                createdAt   = System.currentTimeMillis(),
            )
            messageDao.insert(userMsg)

            // 2. 记录 MESSAGE 事件（用户发消息）
            val userEventId = eventRepo.appendMessageEvent(
                actorId     = "user",
                targetId    = currentCharacterId.toString(),
                payloadJson = JSONObject().apply {
                    put("messageId", userMsgId)
                    put("preview", text.take(50))
                }.toString(),
            )
            messageDao.insert(userMsg.copy(eventId = userEventId))

            // 3. 设置 typing 状态
            _uiState.update { it.copy(isTyping = true, streamingContent = "", error = null) }

            try {
                // 4. 获取 Memory（Phase 8 新增）
                val coreMemories     = memoryRepo.getCoreMemories(currentCharacterId)
                val relevantMemories = memoryRepo.searchRelevant(currentCharacterId, text, limit = 10)

                // 5. 组装 Prompt（Phase 8：注入 Memory Layer）
                val character = DefaultCharacters.firstOrNull { it.id == currentCharacterId }
                    ?: return@launch
                val identityEntity = identityDao.getById(currentCharacterId)
                val systemPrompt = PromptOrchestrator.buildSystemPrompt(
                    character        = character,
                    identityEntity   = identityEntity,
                    coreMemories     = coreMemories,
                    relevantMemories = relevantMemories,
                )

                // 6. 记录 Memory 被访问（更新 accessCount + lastAccessedAt）
                launch(Dispatchers.IO) {
                    (coreMemories + relevantMemories).forEach {
                        memoryRepo.recordAccess(it.id)
                    }
                }

                // 7. 取最近 8 轮历史
                val history = messageDao.getByCharacter(currentCharacterId, limit = 17)
                val llmMessages = history
                    .dropLast(1)  // 排除刚插入的用户消息（已在流程中）
                    .takeLast(16)
                    .map { LLMMessage(it.role, it.content) } + LLMMessage("user", text)

                // 8. 流式调用 LLM
                var fullReply = ""
                val config = LLMConfig(
                    model       = "",  // ProviderManager 内部用 defaultModel
                    maxTokens   = 1000,
                    temperature = 0.8f,
                    stream      = true,
                )
                provider.chat(llmMessages, systemPrompt, config).collect { delta ->
                    fullReply += delta
                    _uiState.update { it.copy(streamingContent = fullReply) }
                }

                // 9. 流式结束：存储角色回复
                val assistantMsgId = UUID.randomUUID().toString()
                val assistantMsg = MessageEntity(
                    id          = assistantMsgId,
                    characterId = currentCharacterId,
                    role        = "assistant",
                    content     = fullReply,
                    createdAt   = System.currentTimeMillis(),
                )
                messageDao.insert(assistantMsg)

                // 10. 记录 MESSAGE 事件（角色回复）
                val assistantEventId = eventRepo.appendMessageEvent(
                    actorId     = currentCharacterId.toString(),
                    targetId    = "user",
                    payloadJson = JSONObject().apply {
                        put("messageId", assistantMsgId)
                        put("preview", fullReply.take(50))
                    }.toString(),
                )
                messageDao.insert(assistantMsg.copy(eventId = assistantEventId))

                // 11. 对话结束 → PRESENCE_CHANGED 事件
                eventRepo.appendPresenceChangedEvent(
                    characterId = currentCharacterId,
                    statusType  = StatusType.ACTIVE.name,
                    trigger     = "conversation_ended",
                )

                _uiState.update { it.copy(isTyping = false, streamingContent = "") }

                // 12. 【Phase 8 新增】后台触发 MemoryEngine 提取记忆
                //     在独立协程运行，不阻塞 UI，用户感知不到
                launch(Dispatchers.IO) {
                    memoryEngine.onConversationTurn(
                        characterId    = currentCharacterId,
                        userMessage    = text,
                        assistantReply = fullReply,
                        userEventId    = userEventId,
                    )
                }

            } catch (e: Exception) {
                val errMsg = when {
                    e.message?.contains("API Key") == true -> "API Key 无效，请检查设置"
                    else -> "好像出了点问题，稍后再试？"
                }
                _uiState.update {
                    it.copy(isTyping = false, streamingContent = "", error = errMsg)
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearApiKeyMissingFlag() = _uiState.update { it.copy(isApiKeyMissing = false) }

    // ── 扩展 ────────────────────────────────────────────────

    private fun MessageEntity.toChatMessage() = ChatMessage(
        id        = id,
        role      = role,
        content   = content,
        createdAt = createdAt,
    )
}
