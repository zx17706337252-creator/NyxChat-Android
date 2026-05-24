package com.nyxchat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nyxchat.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NyxRepository(app)

    // ─── State ───────────────────────────────────────────────────────────

    private val _characters  = MutableStateFlow<List<NyxCharacter>>(emptyList())
    val characters: StateFlow<List<NyxCharacter>> = _characters.asStateFlow()

    private val _messages    = MutableStateFlow<List<NyxMessage>>(emptyList())
    val messages: StateFlow<List<NyxMessage>> = _messages.asStateFlow()

    private val _memories    = MutableStateFlow<List<NyxMemory>>(emptyList())
    val memories: StateFlow<List<NyxMemory>> = _memories.asStateFlow()

    private val _apiConfig   = MutableStateFlow(ApiConfig())
    val apiConfig: StateFlow<ApiConfig> = _apiConfig.asStateFlow()

    private val _typingCharId = MutableStateFlow<String?>(null)
    val typingCharId: StateFlow<String?> = _typingCharId.asStateFlow()

    private val _groupMode   = MutableStateFlow(false)
    val groupMode: StateFlow<Boolean> = _groupMode.asStateFlow()

    private val _selectedCharId = MutableStateFlow<String?>(null)
    val selectedCharId: StateFlow<String?> = _selectedCharId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ─── Init ────────────────────────────────────────────────────────────

    init {
        _characters.value  = repo.loadCharacters()
        _messages.value    = repo.loadMessages()
        _memories.value    = repo.loadMemories()
        _apiConfig.value   = repo.loadApiConfig()
        _selectedCharId.value = _characters.value.firstOrNull { it.isActive }?.id
    }

    // ─── Character Actions ───────────────────────────────────────────────

    fun updateCharacter(char: NyxCharacter) {
        _characters.value = _characters.value.map { if (it.id == char.id) char else it }
        repo.saveCharacters(_characters.value)
        if (_selectedCharId.value == null && char.isActive) _selectedCharId.value = char.id
    }

    fun addCharacter(char: NyxCharacter) {
        _characters.value = _characters.value + char
        repo.saveCharacters(_characters.value)
        if (_selectedCharId.value == null) _selectedCharId.value = char.id
    }

    fun deleteCharacter(id: String) {
        _characters.value = _characters.value.filter { it.id != id }
        repo.saveCharacters(_characters.value)
        if (_selectedCharId.value == id) {
            _selectedCharId.value = _characters.value.firstOrNull { it.isActive }?.id
        }
    }

    fun selectChar(id: String) { _selectedCharId.value = id }
    fun toggleGroupMode() { _groupMode.value = !_groupMode.value }
    fun clearError() { _error.value = null }
    fun clearChat() {
        _messages.value = emptyList()
        repo.saveMessages(emptyList())
    }

    // ─── Memory Actions ──────────────────────────────────────────────────

    fun deleteMemory(id: String) {
        _memories.value = _memories.value.filter { it.id != id }
        repo.saveMemories(_memories.value)
    }

    fun clearCharMemories(charId: String) {
        _memories.value = _memories.value.filter { it.charId != charId }
        repo.saveMemories(_memories.value)
    }

    private fun addMemories(newMems: List<NyxMemory>) {
        val all = (_memories.value + newMems)
            .groupBy { it.charId }
            .flatMap { (_, mems) ->
                mems.sortedByDescending { it.importance.toLong() * 1_000_000 + it.timestamp }
                    .take(50)
            }
        _memories.value = all
        repo.saveMemories(_memories.value)
    }

    // ─── Settings ────────────────────────────────────────────────────────

    fun saveApiConfig(config: ApiConfig) {
        _apiConfig.value = config
        repo.saveApiConfig(config)
    }

    suspend fun testConnection(config: ApiConfig): Boolean {
        return try {
            repo.callAI(config, listOf(mapOf("role" to "user", "content" to "reply: ok")), 5)
            true
        } catch (e: Exception) {
            _error.value = e.message
            false
        }
    }

    // ─── Send Message ────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        val activeChars = _characters.value.filter { it.isActive }
        val targets = if (_groupMode.value) {
            activeChars
        } else {
            activeChars.filter { it.id == _selectedCharId.value }
        }
        if (targets.isEmpty()) {
            _error.value = "没有可用的角色，请先在角色页启用角色"
            return
        }
        if (_apiConfig.value.apiKey.isBlank()) {
            _error.value = "请先在设置页填入 API Key"
            return
        }

        viewModelScope.launch {
            _error.value = null

            // 1. Add user message
            val userMsg = NyxMessage(
                id = UUID.randomUUID().toString(),
                role = "user",
                content = text,
                timestamp = System.currentTimeMillis()
            )
            appendMessage(userMsg)

            val thisRoundReplies = mutableListOf<Pair<String, String>>() // name, content

            // 2. Each character responds sequentially
            for (char in targets) {
                _typingCharId.value = char.id
                try {
                    val charMems = _memories.value.filter { it.charId == char.id }
                    val systemPrompt = buildSystemPrompt(char, charMems)

                    // Build conversation history (last 24 msgs)
                    val history = _messages.value.takeLast(24).map {
                        mapOf("role" to it.role, "content" to it.content)
                    }.toMutableList()

                    // Group-chat awareness: inject what earlier chars said this round
                    if (thisRoundReplies.isNotEmpty()) {
                        val ctx = thisRoundReplies.joinToString("\n") { "${it.first}说：${it.second}" }
                        history.add(mapOf(
                            "role" to "user",
                            "content" to "[刚才其他角色的回应]\n$ctx\n[现在轮到你回应用户的问题]"
                        ))
                    }

                    val messages = buildList {
                        add(mapOf("role" to "system", "content" to systemPrompt))
                        addAll(history)
                    }

                    val reply = repo.callAI(_apiConfig.value, messages)

                    val charMsg = NyxMessage(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        charId = char.id,
                        content = reply,
                        timestamp = System.currentTimeMillis()
                    )
                    appendMessage(charMsg)
                    thisRoundReplies.add(char.name to reply)

                    // Background: extract memories
                    launch {
                        val extracted = repo.extractMemories(_apiConfig.value, char.id, text, reply)
                        if (extracted.isNotEmpty()) addMemories(extracted)
                    }

                    // Background: infer mood every 3 char messages
                    val charMsgCount = _messages.value.count { it.charId == char.id }
                    if (charMsgCount % 3 == 0) {
                        launch {
                            val recentExchange = _messages.value.takeLast(8)
                                .filter { it.role == "user" || it.charId == char.id }
                                .joinToString("\n") { m ->
                                    if (m.role == "user") "用户：${m.content}" else "${char.name}：${m.content}"
                                }
                            val newMood = repo.inferMood(_apiConfig.value, char.id, recentExchange)
                            if (newMood != null && newMood != char.mood) {
                                updateCharacter(char.copy(mood = newMood))
                            }
                        }
                    }

                } catch (e: Exception) {
                    val errMsg = NyxMessage(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        charId = char.id,
                        content = "[错误: ${e.message}]",
                        timestamp = System.currentTimeMillis()
                    )
                    appendMessage(errMsg)
                    _error.value = e.message
                }
            }

            _typingCharId.value = null
        }
    }

    private fun appendMessage(msg: NyxMessage) {
        _messages.value = _messages.value + msg
        repo.saveMessages(_messages.value)
    }
}
