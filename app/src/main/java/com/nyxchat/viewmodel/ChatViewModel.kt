package com.nyxchat.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.nyxchat.NyxApp
import com.nyxchat.data.*
import com.nyxchat.data.NyxRepository
import com.nyxchat.notification.ProactiveWorker
import com.nyxchat.pipeline.*
import com.nyxchat.services.TtsService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class ChatViewModel @Inject constructor(
    app: Application,
    private val repo: NyxRepository
) : AndroidViewModel(app) {

    private val tts      = TtsService(app)
    // Phase-2 fix: 引用进程级单例，与 Worker 路径共享同一实例
    private val pipeline = defaultPipeline
    private val gson     = Gson()

    companion object {
        // Bug fix: 正则只编译一次，不在每个流式 token 到达时重新创建
        private val SENTENCE_END_REGEX = Regex("[。！？.!?]")
        // Bug 5 fix (part 1): 防御群聊 context 中模型学到 "名字：回复" 格式后
        // 在 charId 找不到时退化成 "null：内容" 的输出前缀，统一剥离。
        private val NULL_PREFIX_REGEX   = Regex("""^null[：:]\s*""", RegexOption.IGNORE_CASE)
        private val NARRATION_REGEX    = Regex("""^\[旁白：(.+)]$""")
        // 步骤10a：提取 AI 输出的情绪标记 [mood:xxx]
        private val MOOD_REGEX         = Regex("""\[mood:(\w+)\]""")
        /** 导入文档时每个 WorldBook 分片的最大字符数 */
        private const val DOC_IMPORT_CHUNK_SIZE = 1500
    }

    // ── State (Opt 1: private MutableStateFlow + public read-only StateFlow) ─────
    private val _characters    = MutableStateFlow<List<NyxCharacter>>(emptyList())
    val characters: StateFlow<List<NyxCharacter>>   = _characters.asStateFlow()
    private val _messages      = MutableStateFlow<List<NyxMessage>>(emptyList())
    val messages: StateFlow<List<NyxMessage>>       = _messages.asStateFlow()
    private val _memories      = MutableStateFlow<List<NyxMemory>>(emptyList())
    val memories: StateFlow<List<NyxMemory>>        = _memories.asStateFlow()
    private val _worldBook     = MutableStateFlow<List<WorldBookEntry>>(emptyList())
    val worldBook: StateFlow<List<WorldBookEntry>>  = _worldBook.asStateFlow()
    private val _relationships = MutableStateFlow<List<Relationship>>(emptyList())
    val relationships: StateFlow<List<Relationship>> = _relationships.asStateFlow()
    private val _sessions      = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>>      = _sessions.asStateFlow()
    private val _activeSession = MutableStateFlow("default")
    val activeSession: StateFlow<String>            = _activeSession.asStateFlow()
    private val _userPersona   = MutableStateFlow(UserPersona())
    val userPersona: StateFlow<UserPersona>         = _userPersona.asStateFlow()
    private val _sceneState    = MutableStateFlow(SceneState())
    val sceneState: StateFlow<SceneState>           = _sceneState.asStateFlow()
    private val _narrativeMode = MutableStateFlow(false)
    val narrativeMode: StateFlow<Boolean>           = _narrativeMode.asStateFlow()
    private val _enableParticles = MutableStateFlow(true)
    val enableParticles: StateFlow<Boolean>         = _enableParticles.asStateFlow()

    // Batch 3 Item 9: 聊天背景图路径，空字符串表示使用默认背景
    private val _chatBackground = MutableStateFlow(repo.loadChatBackground())
    val chatBackground: StateFlow<String>           = _chatBackground.asStateFlow()

    // ── 补全缺失的 StateFlow ──────────────────────────────────────────────
    private val _apiConfig = MutableStateFlow(repo.loadApiConfig())
    val apiConfig: StateFlow<ApiConfig> = _apiConfig.asStateFlow()

    private val _ttsConfig = MutableStateFlow(repo.loadTtsConfig())
    val ttsConfig: StateFlow<TtsConfig> = _ttsConfig.asStateFlow()

    private val _isDarkMode = MutableStateFlow(repo.loadDarkMode())
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    /** 已标记为"小说片段"的消息 id 集合 */
    private val _fragmentIds = MutableStateFlow<Set<String>>(emptySet())

    // Bug 3 fix: 关系日志版本计数器。
    // RelationshipScreen 用 remember(logTick) 订阅此值，任何一次日志写入
    // 都会让计数器 +1，触发 Compose 重组，日志折叠区无需退出页面即可实时刷新。
    private val _relLogTick = MutableStateFlow(0)
    val relLogTick: StateFlow<Int> = _relLogTick.asStateFlow()
    val fragmentIds: StateFlow<Set<String>> = _fragmentIds.asStateFlow()

    private val _isTtsPlaying  = MutableStateFlow(false)
    val isTtsPlaying: StateFlow<Boolean> = _isTtsPlaying.asStateFlow()
    private val _ttsCurrentText = MutableStateFlow("")
    val ttsCurrentText: StateFlow<String> = _ttsCurrentText.asStateFlow()
    private val _typingCharId   = MutableStateFlow<String?>(null)
    val typingCharId: StateFlow<String?> = _typingCharId.asStateFlow()
    private val _groupMode      = MutableStateFlow(false)
    val groupMode: StateFlow<Boolean> = _groupMode.asStateFlow()
    private val _selectedCharId = MutableStateFlow<String?>(null)
    val selectedCharId: StateFlow<String?> = _selectedCharId.asStateFlow()
    private val _error          = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _isStreaming    = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    // 搜索
    private val _searchResults = MutableStateFlow<List<NyxMessage>>(emptyList())
    val searchResults: StateFlow<List<NyxMessage>> = _searchResults.asStateFlow()

    private var streamingJob: kotlinx.coroutines.Job? = null
    // Phase-1 fix: epoch 计数器，每次 sendMessage 递增。
    // finally 块在执行 saveCharacters 前先比对 epoch，若值已变（说明新一轮发送已启动）
    // 则跳过本次落盘，避免旧协程的 finally 覆盖新协程在内存中写入的角色状态。
    private var streamingEpoch: Int = 0
    private val memoryQueue     = Channel<Triple<String, String, String>>(Channel.BUFFERED)
    private val wbLastTriggered = mutableMapOf<String, Int>()
    // Opt 4: 有界队列，防止长对话时 TTS 无限积压；DROP_OLDEST 确保播放的是最新消息
    private val ttsQueue        = Channel<Triple<String, String, String>>(capacity = 3, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    private val _pipelineStages = MutableStateFlow(pipeline.stages.toList())
    val pipelineStages: StateFlow<List<PipelineStage>> = _pipelineStages.asStateFlow()

    // ── 步骤7：Prompt 预览面板 ─────────────────────────────────────────────
    private val _debugStages = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val debugStages: StateFlow<List<Pair<String, String>>> = _debugStages.asStateFlow()

    // ── 步骤9a：世界书触发 ID 集合 ─────────────────────────────────────────
    private val _lastTriggeredWbIds = MutableStateFlow<Set<String>>(emptySet())
    val lastTriggeredWbIds: StateFlow<Set<String>> = _lastTriggeredWbIds.asStateFlow()

    // ── 步骤8：记忆注入预览（charId → 有效记忆列表）─────────────────────────
    private val _effectiveMemsPreview = MutableStateFlow<Map<String, List<NyxMemory>>>(emptyMap())
    val effectiveMemsPreview: StateFlow<Map<String, List<NyxMemory>>> = _effectiveMemsPreview.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────
    init {
        // Opt: SharedPreferences 初次读取可能触发磁盘 I/O（JSON 反序列化），
        // 将较大的批量加载移到 IO 线程，避免主线程 StrictMode 违规。
        viewModelScope.launch(Dispatchers.IO) {
            val chars      = repo.loadCharacters()
            val wb         = repo.loadWorldBook()
            val rels       = repo.loadRelationships()
            val sess       = repo.loadSessions()
            val persona    = repo.loadUserPersona()
            val scene      = repo.loadSceneState()
            val particles  = repo.loadParticlesEnabled()
            // P2-A fix: 消息和记忆也在此协程内顺序加载，保证 checkBirthdays() 调用时
            // _characters.value 与 _messages.value 均已赋值，彻底消除竞争条件。
            // 原来的第二个独立协程与此协程并行，极高概率在 _characters.value 仍为
            // 空列表时就调用了 checkBirthdays()，导致当天生日消息静默跳过。
            val msgs       = repo.loadMessages("default")
            val mems       = repo.loadAllMemories()
            // 切回 Main 线程统一更新 StateFlow
            withContext(Dispatchers.Main) {
                _characters.value    = chars
                _worldBook.value     = wb
                _relationships.value = rels
                _sessions.value      = sess
                _userPersona.value   = persona
                _sceneState.value    = scene
                _enableParticles.value = particles
                _messages.value      = msgs
                _memories.value      = mems
                _selectedCharId.value = chars.firstOrNull { it.isActive }?.id
                // Bug fix: 主动调度必须在 chars 加载完毕后执行（原来在 IO 协程外，chars 为空时直接跳过）
                chars.filter { it.proactiveConfig.enabled }
                    .forEach { ProactiveWorker.scheduleNext(app, it) }
            }
            // P2-A fix: withContext(Main) 返回后 _characters.value 已确定赋值。
            // checkBirthdays() 内部的 withContext(Dispatchers.IO) 可在当前 IO 协程中正常嵌套。
            checkBirthdays()
        }
        viewModelScope.launch {
            for ((charId, userText, reply) in memoryQueue) {
                val ext = runCatching {
                    val existing = _memories.value.filter { it.charId == charId }
                    // Bug fix: 使用 StateFlow 缓存值，避免每次重读磁盘（多余 IO 且可能读到旧值）
                    repo.extractMemories(_apiConfig.value, charId, userText, reply, existing)
                }.getOrDefault(emptyList())
                if (ext.isNotEmpty()) addMemories(ext)
            }
        }
        viewModelScope.launch {
            for ((text, voiceId, mood) in ttsQueue) {
                _isTtsPlaying.value = true; _ttsCurrentText.value = text
                try { tts.speak(text, voiceId, _ttsConfig.value, mood) }
                finally { _isTtsPlaying.value = false; _ttsCurrentText.value = "" }
            }
        }
        // Bug 6 fix: 监听 ProactiveWorker 投递的 TTS 请求，转入 ttsQueue 统一播放。
        // 这样 stopTts() 可以中断 Proactive 音频，也不会和对话 TTS 叠播。
        viewModelScope.launch {
            (app as? NyxApp)?.proactiveTtsChannel?.collect { (text, voiceId, mood) ->
                ttsQueue.trySend(Triple(text, voiceId, mood))
            }
        }
    }

    /** 深色模式：切换并持久化 */
    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        repo.saveDarkMode(enabled)
    }

    // ── 历史压缩 ──────────────────────────────────────────────────────────
    private val _isCompressing    = MutableStateFlow(false)
    val isCompressing: StateFlow<Boolean> = _isCompressing.asStateFlow()
    private val _lastCompressInfo = MutableStateFlow<String?>(null)
    val lastCompressInfo: StateFlow<String?> = _lastCompressInfo.asStateFlow()
    // Phase 2-B: 压缩阈值（初始值从存储读取，默认 100）
    private val _compressTrigger = MutableStateFlow(repo.loadCompressTrigger())
    val compressTrigger: StateFlow<Int> = _compressTrigger.asStateFlow()

    /** Phase 2-B: 更新压缩阈值并持久化 */
    fun setCompressTrigger(v: Int) {
        val clamped = v.coerceIn(20, 200)
        _compressTrigger.value = clamped
        repo.saveCompressTrigger(clamped)
    }

    // Phase 5: 高级参数
    private val _wbScanDepth       = MutableStateFlow(repo.loadWbScanDepth())
    val wbScanDepth: StateFlow<Int> = _wbScanDepth.asStateFlow()
    private val _wbMaxEntries      = MutableStateFlow(repo.loadWbMaxEntries())
    val wbMaxEntries: StateFlow<Int> = _wbMaxEntries.asStateFlow()
    private val _memoryInjectCount = MutableStateFlow(repo.loadMemoryInjectCount())
    val memoryInjectCount: StateFlow<Int> = _memoryInjectCount.asStateFlow()

    /** Phase 5: 世界书扫描深度（6–30） */
    fun setWbScanDepth(v: Int) {
        val c = v.coerceIn(6, 30); _wbScanDepth.value = c; repo.saveWbScanDepth(c)
    }
    /** Phase 5: 世界书触发条目上限（1–30） */
    fun setWbMaxEntries(v: Int) {
        val c = v.coerceIn(1, 30); _wbMaxEntries.value = c; repo.saveWbMaxEntries(c)
    }
    /** Phase 5: 记忆注入数量（5–50） */
    fun setMemoryInjectCount(v: Int) {
        val c = v.coerceIn(5, 50); _memoryInjectCount.value = c; repo.saveMemoryInjectCount(c)
    }

    /**
     * 手动触发当前会话的历史压缩。
     * 自动压缩在每条 AI 回复后后台静默触发（超过阈值才执行）。
     */
    fun compressCurrentSession() {
        viewModelScope.launch {
            val sessId = _activeSession.value
            _isCompressing.value = true
            // 手动触发：5 条以上即可生成本章小结
            val result = repo.compressSessionHistory(sessId, _apiConfig.value, minCount = 5)
            _isCompressing.value = false
            if (result != null) {
                _messages.value = repo.loadMessages(sessId)
                _lastCompressInfo.value = "✅ 本章小结已生成"
            } else {
                _lastCompressInfo.value = "当前消息数不足（至少5条）"
            }
            // Step 3 fix (Bug 3): 状态提示在 4 秒后自动清除，避免永久残留在
            // 设置页面让用户误以为压缩流程卡住。4 秒足够用户看清提示内容。
            kotlinx.coroutines.delay(4_000)
            _lastCompressInfo.value = null
        }
    }

    /** Step 3 fix: UI 也可以主动清除提示（例如用户手动关闭 Snackbar）。 */
    fun clearCompressInfo() { _lastCompressInfo.value = null }

    // ── Pipeline ──────────────────────────────────────────────────────────
    fun toggleStage(name: String, enabled: Boolean) {
        pipeline.toggle(name, enabled)
        _pipelineStages.value = pipeline.stages.toList()
    }
    fun setParticlesEnabled(v: Boolean) { _enableParticles.value = v; repo.saveParticlesEnabled(v) }
    /** Batch 3 Item 9: 设置聊天区自定义背景图路径并持久化 */
    fun setChatBackground(path: String) { _chatBackground.value = path; repo.saveChatBackground(path) }

    // ── 补全缺失的函数 ─────────────────────────────────────────────────────

    /** 保存 API 配置并同步 StateFlow */
    fun saveApiConfig(cfg: ApiConfig) {
        _apiConfig.value = cfg
        repo.saveApiConfig(cfg)
    }

    /** 保存 TTS 配置并同步 StateFlow */
    fun saveTtsConfig(cfg: TtsConfig) {
        _ttsConfig.value = cfg
        repo.saveTtsConfig(cfg)
    }

    /** 测试 API 连接，返回是否成功 */
    suspend fun testConnection(cfg: ApiConfig): Boolean = runCatching {
        repo.callAI(cfg, listOf(mapOf("role" to "user", "content" to "reply: ok")), 5)
        true
    }.getOrDefault(false)

    /** 切换消息的"小说片段"标记 */
    fun toggleFragment(msgId: String) {
        _fragmentIds.value = _fragmentIds.value.toMutableSet().also { set ->
            if (!set.add(msgId)) set.remove(msgId)
        }
    }

    /** 将所有已标记片段拼接成小说文本 */
    fun exportAsNovel(): String {
        val ids = _fragmentIds.value
        if (ids.isEmpty()) return ""
        return _messages.value
            .filter { it.id in ids }
            .joinToString("\n\n") { msg ->
                val charName = _characters.value.find { it.id == msg.charId }?.name
                if (charName != null) "$charName：${msg.content}" else msg.content
            }
    }

    // ── Sessions ──────────────────────────────────────────────────────────
    fun createSession(title: String = "新对话") {
        val activeCharIds = _characters.value.filter { it.isActive }.map { it.id }
        val session = ChatSession(UUID.randomUUID().toString(), title, characterIds = activeCharIds)
        _sessions.value = _sessions.value + session
        repo.saveSessions(_sessions.value)
        switchSession(session.id)
    }

    fun switchSession(sessionId: String) {
        viewModelScope.launch {
            val prevId = _activeSession.value
            val prevCharIds = _characters.value.filter { it.isActive }.map { it.id }
            _sessions.value = _sessions.value.map {
                if (it.id == prevId) it.copy(characterIds = prevCharIds) else it
            }
            repo.saveSessions(_sessions.value)

            _activeSession.value = sessionId
            _messages.value = repo.loadMessages(sessionId)
            _lastTriggeredWbIds.value = emptySet()   // Bug 3 fix: 切换会话时清空触发指示，避免显示上一会话的 WB 状态
            wbLastTriggered.clear()                  // Bug C fix: 清空 WB 冷却索引——旧会话的消息计数在新会话中无意义，
            // 若不清空，旧 session 中被触发过的条目（如 lastIdx=50）在新 session 消息数较少时
            // (messages.size - 50) < cooldownMsgs，条目会被错误地锁定在冷却中无法触发

            val target = _sessions.value.find { it.id == sessionId }
            if (target != null && target.characterIds.isNotEmpty()) {
                val restored = _characters.value.map { it.copy(isActive = it.id in target.characterIds) }
                _characters.value = restored
                repo.saveCharacters(restored)
                _selectedCharId.value = restored.firstOrNull { it.isActive }?.id
            }
        }
    }

    fun deleteSession(sessionId: String) {
        if (sessionId == "default") return
        viewModelScope.launch {
            repo.clearSession(sessionId)
            _sessions.value = _sessions.value.filter { it.id != sessionId }
            repo.saveSessions(_sessions.value)
            if (_activeSession.value == sessionId) switchSession("default")
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        _sessions.value = _sessions.value.map { if (it.id == sessionId) it.copy(title = newTitle) else it }
        repo.saveSessions(_sessions.value)
    }

    private fun touchSession() {
        val sid = _activeSession.value
        _sessions.value = _sessions.value.map {
            if (it.id == sid) it.copy(lastMessageAt = System.currentTimeMillis()) else it
        }
        repo.saveSessions(_sessions.value)
    }

    // ── Characters ────────────────────────────────────────────────────────
    fun updateCharacter(c: NyxCharacter) {
        _characters.value = _characters.value.map { if (it.id == c.id) c else it }
        repo.saveCharacters(_characters.value)
        if (c.proactiveConfig.enabled) ProactiveWorker.scheduleNext(getApplication(), c)
        else ProactiveWorker.cancel(getApplication(), c.id)
    }
    fun addCharacter(c: NyxCharacter) {
        _characters.value = _characters.value + c; repo.saveCharacters(_characters.value)
        if (_selectedCharId.value == null) _selectedCharId.value = c.id
    }
    fun deleteCharacter(id: String) {
        ProactiveWorker.cancel(getApplication(), id)
        _characters.value = _characters.value.filter { it.id != id }
        repo.saveCharacters(_characters.value)
        if (_selectedCharId.value == id)
            _selectedCharId.value = _characters.value.firstOrNull { it.isActive }?.id
        viewModelScope.launch {
            repo.clearMemoriesForChar(id)
            _memories.value = _memories.value.filter { it.charId != id }
            invalidateMemsPreview(id)   // Step 1 fix: 角色删除时同步清除其预览缓存
        }
    }
    fun setCharacterAvatar(charId: String, uri: Uri) {
        val path = repo.copyImageToInternal(uri, "avatar_$charId.jpg")
        _characters.value.find { it.id == charId }?.let { updateCharacter(it.copy(avatarPath = path)) }
    }
    fun saveCroppedAvatar(charId: String, bitmap: android.graphics.Bitmap) {
        val path = repo.saveBitmapToInternal(bitmap, "avatar_$charId.jpg")
        _characters.value.find { it.id == charId }?.let { updateCharacter(it.copy(avatarPath = path)) }
    }
    fun setCharacterBackground(charId: String, uri: Uri) {
        val path = repo.copyImageToInternal(uri, "bg_$charId.jpg")
        _characters.value.find { it.id == charId }?.let { updateCharacter(it.copy(backgroundPath = path)) }
    }
    fun selectChar(id: String) {
        _selectedCharId.value = id
        _lastTriggeredWbIds.value = emptySet()   // Bug 3 fix: 切换角色时清空触发指示
    }
    fun toggleGroupMode()       { _groupMode.value = !_groupMode.value }
    fun clearError()            { _error.value = null }

    // ── Messages ──────────────────────────────────────────────────────────
    fun deleteMessage(msgId: String) {
        viewModelScope.launch {
            repo.deleteMessage(msgId)
            _messages.value = _messages.value.filter { it.id != msgId }
        }
    }
    fun searchMessages(query: String, charId: String? = null) {
        viewModelScope.launch {
            _searchResults.value = if (query.isBlank()) emptyList()
            else {
                // Step 5 fix (Bug 5): 搜索已在 DB 层按 sessionId 过滤，
                // 不再需要 raw.filter { it.sessionId == _activeSession.value } 的内存二次过滤。
                val sessId = _activeSession.value
                if (charId != null) repo.searchMessagesFiltered(query, charId, sessId)
                else                repo.searchMessages(query, sessId)
            }
        }
    }
    fun clearSearch() { _searchResults.value = emptyList() }

    // ── Memories ──────────────────────────────────────────────────────────
    fun deleteMemory(id: String) {
        viewModelScope.launch {
            // Step 1 fix: 在删除前先拿到 charId，删除后失效该角色的预览缓存
            val charId = _memories.value.find { it.id == id }?.charId
            repo.deleteMemory(id)
            _memories.value = _memories.value.filter { it.id != id }
            if (charId != null) invalidateMemsPreview(charId)
        }
    }
    fun clearCharMemories(cid: String) {
        viewModelScope.launch {
            repo.clearMemoriesForChar(cid)
            _memories.value = _memories.value.filter { it.charId != cid }
            invalidateMemsPreview(cid)  // Step 1 fix
        }
    }
    fun updateMemory(memory: NyxMemory) {
        viewModelScope.launch {
            repo.updateMemory(memory)
            _memories.value = _memories.value.map { if (it.id == memory.id) memory else it }
            invalidateMemsPreview(memory.charId)  // Step 1 fix
        }
    }
    fun addManualMemory(charId: String, text: String) {
        val mem = NyxMemory(UUID.randomUUID().toString(), charId, text, 5, System.currentTimeMillis())
        viewModelScope.launch {
            repo.insertMemories(listOf(mem))
            _memories.value = _memories.value + mem
            invalidateMemsPreview(charId)  // Step 1 fix
        }
    }
    private suspend fun addMemories(new: List<NyxMemory>) {
        repo.insertMemories(new)
        val charIds = new.map { it.charId }.distinct()
        val refreshed = charIds.flatMap { repo.loadMemoriesForChar(it) }
        _memories.value = _memories.value.filter { it.charId !in charIds } + refreshed
        // Step 1 fix: AI 自动提取记忆后，同样需要失效相关角色的预览缓存
        charIds.forEach { invalidateMemsPreview(it) }
    }

    // ── Persona / Scene ───────────────────────────────────────────────────
    fun saveUserPersona(p: UserPersona)  { _userPersona.value = p; repo.saveUserPersona(p) }
    fun saveSceneState(s: SceneState)    { _sceneState.value = s; repo.saveSceneState(s) }
    fun toggleNarrativeMode()            { _narrativeMode.value = !_narrativeMode.value }

    // ── World Book ────────────────────────────────────────────────────────
    fun saveWorldBook(e: List<WorldBookEntry>)  { _worldBook.value = e; repo.saveWorldBook(e) }
    fun addWorldBookEntry(e: WorldBookEntry)    = saveWorldBook(_worldBook.value + e)
    fun updateWorldBookEntry(e: WorldBookEntry) = saveWorldBook(_worldBook.value.map { if (it.id == e.id) e else it })
    fun deleteWorldBookEntry(id: String)        = saveWorldBook(_worldBook.value.filter { it.id != id })
    private fun markWorldBookTriggered(ids: List<String>) {
        val n = _messages.value.size
        ids.forEach { wbLastTriggered[it] = n }
        // Bug fix: 清理已被删除的世界书条目对应的冷却索引，防止 map 无限增长
        val validIds = _worldBook.value.map { it.id }.toSet()
        wbLastTriggered.keys.retainAll(validIds)
        // Bug 2 fix: 同步回写 WorldBookEntry.lastTriggeredMsgIndex 并持久化到磁盘。
        // 原来冷却仅靠内存 wbLastTriggered 维持，App 重启后 map 清空，所有条目回退到
        // 默认值 -999，导致冷却在每次重启后全部失效。现在以消息计数为标尺写入数据模型，
        // triggerWorldBook 重启后仍能正确读取 lastTriggeredMsgIndex 判断冷却。
        // saveWorldBook / storage.save 均为普通函数，在主线程调用安全（apply() 异步提交）。
        if (ids.isNotEmpty()) {
            val idSet = ids.toSet()
            val updated = _worldBook.value.map { entry ->
                if (entry.id in idSet) entry.copy(lastTriggeredMsgIndex = n) else entry
            }
            _worldBook.value = updated
            repo.saveWorldBook(updated)
        }
    }

    // ── Relationships ─────────────────────────────────────────────────────
    // 从文件导入：读取文本后生成 sticky WorldBookEntry（迁移自原 KnowledgeScreen）
    fun importDocumentAsWorldBook(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { repo.readTextDocument(uri) }
                ?: run { _error.value = "无法读取文件"; return@launch }
            val filename = withContext(Dispatchers.IO) { repo.getFilenameFromUri(uri) }
            val chunks = text.trim().chunked(DOC_IMPORT_CHUNK_SIZE)
            chunks.forEachIndexed { i, chunk ->
                addWorldBookEntry(WorldBookEntry(
                    id       = UUID.randomUUID().toString(),
                    title    = "$filename${if (chunks.size > 1) "（${i+1}/${chunks.size}）" else ""}",
                    content  = chunk,
                    keywords = emptyList(),
                    sticky   = true
                ))
            }
        }
    }
    private fun saveRelationship(rel: Relationship) {
        val updated = _relationships.value.filter { it.id != rel.id } + rel
        _relationships.value = updated; repo.saveRelationships(updated)
    }
    /**
     * 手动调整后保存。传入空 delta 触发一次 applyDelta()，
     * 让 stage 棘轮和亲密地板在手动滑块调整后也能重算，
     * 而不是直接覆盖掉逻辑状态。
     */
    fun saveRelationshipManual(rel: Relationship) = saveRelationship(rel.applyDelta(RelationshipDelta()))

    /**
     * 设置指定角色与用户之间的分手开关（仅在 stage >= 4 时由 UI 暴露）。
     * 持久化到磁盘，下次对话的 applyDelta 和 Prompt 注入都会读取最新值。
     */
    fun setAllowBreakup(charId: String, enabled: Boolean) {
        val relId    = "${charId}_${USER_PSEUDO_ID}"
        val existing = _relationships.value.find { it.id == relId }
            ?: newUserCharRelationship(charId)
        saveRelationship(existing.copy(allowBreakup = enabled))
    }
    // Bug #6 fix：原来统一用 Relationship() 默认构造（affection=0.5f），
    // 用户↔角色方向应使用 newUserCharRelationship()（affection=0.10f）。
    // Step 3 给 UserCharCard 加了重置按钮后此 bug 变活跃，必须在此之前修复。
    fun resetRelationship(fromId: String, toId: String) =
        saveRelationship(
            if (toId == USER_PSEUDO_ID) newUserCharRelationship(fromId)
            else Relationship("${fromId}_${toId}", fromId, toId)
        )

    // ── 步骤12：读取关系事件日志（RelationshipScreen 用）─────────────────
    fun loadRelationshipLog(charId: String): List<RelationshipLogEntry> =
        repo.loadRelationshipLog(charId)

    /**
     * Bug 3 fix: 私有包装，写入日志后让 _relLogTick + 1，通知 RelationshipScreen 刷新。
     * Bug 4 fix: 外部调用时传入 toCharId，使日志条目携带关系对端信息，
     *            RelationshipCard 可据此过滤，避免用户↔角色的变化混入角色间卡片。
     */
    private fun appendRelationshipLog(charId: String, entry: RelationshipLogEntry) {
        repo.appendRelationshipLog(charId, entry)
        _relLogTick.value++
    }

    /**
     * Bug 7 fix: 批量版本——对同一 charId 只触发一次 SharedPreferences 序列化。
     * 每轮对话收集完所有条目后统一调用，_relLogTick 也只递增一次。
     */
    private fun appendRelationshipLogBatch(charId: String, entries: List<RelationshipLogEntry>) {
        if (entries.isEmpty()) return
        repo.appendRelationshipLogBatch(charId, entries)
        _relLogTick.value++
    }

    // ── Character export ──────────────────────────────────────────────────
    fun exportCharacterJson(char: NyxCharacter): String = gson.toJson(char)
    fun shareCharacter(context: android.content.Context, char: NyxCharacter) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, exportCharacterJson(char))
            putExtra(Intent.EXTRA_SUBJECT, "永恒之家 角色：${char.name}")
        }
        context.startActivity(Intent.createChooser(intent, "分享角色 ${char.name}"))
    }

    // ── TTS ───────────────────────────────────────────────────────────────
    fun playTts(text: String, charId: String) {
        val char = _characters.value.find { it.id == charId } ?: return
        viewModelScope.launch { ttsQueue.send(Triple(text, char.voiceId, char.mood)) }
    }
    fun stopTts() { tts.stopCurrent(); _isTtsPlaying.value = false }

    // ── Streaming ─────────────────────────────────────────────────────────
    fun stopStreaming() {
        streamingJob?.cancel(); streamingJob = null
        _isStreaming.value = false; _typingCharId.value = null
    }
    fun clearChat() {
        viewModelScope.launch {
            repo.clearSession(_activeSession.value)
            _messages.value = emptyList()

            // Step 2 fix (Bug 2 & 6): 清空聊天时同步重置世界书冷却状态。
            //
            // 问题根因：clearSession() 只清了消息记录，但两处冷却状态没有归零：
            //   ① wbLastTriggered（内存 Map）— 条目存着"第 N 条消息时触发过"
            //   ② WorldBookEntry.lastTriggeredMsgIndex（持久化）— 同上
            // 清空后消息数归 0，triggerWorldBook 计算 (0 - N) < cooldownMsgs，
            // 认为仍在冷却期，所有设置过冷却的条目在新对话中长时间无法触发。
            //
            // 修复方案：
            //   ① 清空内存 Map（最快，立即生效）
            //   ② 把所有条目的 lastTriggeredMsgIndex 重置为 -999（默认"从未触发"），
            //      并持久化到磁盘，保证 App 重启后冷却也不会残留。
            wbLastTriggered.clear()

            val resetWb = _worldBook.value.map { entry ->
                if (entry.lastTriggeredMsgIndex > WB_ENTRY_NEVER_TRIGGERED) entry.copy(lastTriggeredMsgIndex = WB_ENTRY_NEVER_TRIGGERED) else entry
            }
            // 只有真正有变化时才写 StateFlow + 磁盘，避免无谓序列化
            if (resetWb != _worldBook.value) {
                _worldBook.value = resetWb
                repo.saveWorldBook(resetWb)
            }
        }
    }

    fun regenerateLast() {
        val lastAssistant = _messages.value.lastOrNull { it.role == "assistant" } ?: return
        val lastUser      = _messages.value.lastOrNull { it.role == "user" }      ?: return
        viewModelScope.launch {
            // Bug fix: 先删除旧的助手消息 AND 旧的用户消息，再重新发送
            // 原来只删了助手消息，sendMessage 又插了一条新用户消息，导致历史中有两条相同用户消息
            repo.deleteMessage(lastAssistant.id)
            repo.deleteMessage(lastUser.id)
            _messages.value = _messages.value.filter { it.id != lastAssistant.id && it.id != lastUser.id }
            // Opt 7: 正则精确匹配旁白格式，避免内容含中括号时误删
            val rawText = NARRATION_REGEX.find(lastUser.content)?.groupValues?.get(1) ?: lastUser.content
            sendMessage(rawText)
        }
    }

    // ── Birthday check ────────────────────────────────────────────────────
    private suspend fun checkBirthdays() {
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"))
        val cfg   = _apiConfig.value          // 使用 StateFlow，避免重复 IO
        if (cfg.apiKey.isBlank()) return
        val sinceMs = System.currentTimeMillis() - 86_400_000L
        _characters.value.filter { it.isActive && it.birthday == today }.forEach { char ->
            // Bug fix: 原 _messages.value.any{} 只能检查当前 session 的消息。
            // 切换 session 后生日消息对当前列表不可见 → alreadySent=false → 重复发送。
            // 改为跨所有 session 的 DB 查询：24h 内该角色有任何助手消息就不再发。
            val alreadySent = withContext(Dispatchers.IO) {
                repo.db.messageDao().countRecentForChar(char.id, sinceMs) > 0
            }
            if (!alreadySent) {
                val prompt = "今天是${char.name}的生日（或某个特别的纪念日）！请用${char.name}的口吻发送一条生日/纪念日相关的消息。"
                // Bug #2 fix：原裸 callAI 绕过了 PromptPipeline，角色设定/记忆/关系/世界书全部缺失，
                // 导致生日消息"不像这个角色"、不知道和用户的关系、无法触发世界书条目。
                // 改为走完整 Pipeline，保证生日消息与正常对话保持同等上下文质量。
                val charMems = repo.loadMemoriesDecayed(char.id).let {
                    PipelineOptimizer.selectMemoriesForInject(it, _memoryInjectCount.value)
                }
                val history  = _messages.value.takeLast(repo.estimateHistoryLimit(cfg))
                val charRels = _relationships.value.filter {
                    it.fromCharId == char.id || it.toCharId == char.id
                }
                val birthdayCtx = PipelineContext(
                    char               = char,
                    allChars           = listOf(char),
                    messages           = history,
                    memories           = charMems,
                    worldBook          = _worldBook.value,
                    triggeredWorldBook = emptyList(),
                    relationships      = charRels,
                    roundReplies       = emptyList(),
                    userInput          = prompt,
                    isGroupChat        = false,
                    historyLimit       = history.size,
                    knownCharIds       = emptySet(),
                    userPersona        = _userPersona.value,
                    sceneState         = _sceneState.value,
                    isNarrativeMode    = _narrativeMode.value,
                    memoryInjectCount  = _memoryInjectCount.value
                )
                // 复用 ViewModel 顶部的 pipeline 单例，避免每次生日触发时重复分配
                // 约 10 个 PipelineStage lambda 对象（原代码错误地调用 buildDefaultPipeline()
                // 而非复用已有的 pipeline 字段，与整体设计不一致）。
                val apiMsgs = pipeline.buildMessages(birthdayCtx)
                val rawReply = runCatching {
                    repo.callAI(cfg, apiMsgs, 200, 0.9f)
                }.getOrNull() ?: return@forEach
                // 剥离 [mood:xxx] 和 null: 前缀，与正常对话路径保持一致
                val reply = NULL_PREFIX_REGEX.replaceFirst(
                    rawReply.replace(MOOD_REGEX, "").trim(), ""
                ).trimStart()
                // Bug fix: checkBirthdays() 运行在 IO dispatcher（init 协程中），
                // push() 的 _messages.value = _messages.value + m 是非原子读改写，
                // 若与 Main 线程的 sendMessage() 并发执行会导致消息丢失。
                // 切回 Main 线程后再写 StateFlow，保持与其他所有写入路径一致。
                withContext(Dispatchers.Main) {
                    push(NyxMessage(UUID.randomUUID().toString(), "assistant", char.id, reply, System.currentTimeMillis(), _activeSession.value))
                }
            }
        }
    }

    // ── sendMessage ───────────────────────────────────────────────────────
    fun sendMessage(text: String) {
        val active  = _characters.value.filter { it.isActive }
        val targets = if (_groupMode.value) active else listOfNotNull(active.find { it.id == _selectedCharId.value } ?: active.firstOrNull())
        if (targets.isEmpty()) { _error.value = "没有可用角色"; return }

        // 直接从 StateFlow 读取，不再用局部变量遮蔽
        val currentApiConfig = _apiConfig.value
        if (currentApiConfig.apiKey.isBlank()) { _error.value = "请先在设置填入 API 密钥"; return }

        // Bug fix: 先取消上一个还在运行的流式请求再启动新请求。
        // 原代码直接覆盖 streamingJob 引用，旧协程以孤儿形式继续运行，
        // 与新协程并发修改 _messages / _typingCharId / _isStreaming，
        // 导致重复消息、状态闪烁，极端情况下并发写 DB。
        streamingJob?.cancel()
        // Phase-1 fix: 每次发送前递增 epoch，使旧协程的 finally 能感知到自己已过期。
        val myEpoch = ++streamingEpoch

        // Bug fix: 在第一个 suspend 点（push 用户消息）之前就置 true，
        // 防止 push() 内部 withContext(IO) 挂起期间 Main 线程重组、
        // 用户再次点击发送（canSend 仍依赖 typingCharId，但 isStreaming 可作为额外屏障）。
        _isStreaming.value = true

        streamingJob = viewModelScope.launch {
            // Bug fix: 旧协程的 finally { _isStreaming.value = false } 在主线程事件队列里
            // 异步执行，可能在本协程体启动后才跑到，把我们在 launch 前设好的 true 覆盖成 false。
            // 在协程体最开头再设一次 true，确保无论旧 finally 何时执行都不会影响当前流程。
            _isStreaming.value = true
            _error.value = null
            val sessionId = _activeSession.value
            push(NyxMessage(UUID.randomUUID().toString(), "user", null, text, System.currentTimeMillis(), sessionId, isGroup = _groupMode.value))
            touchSession()

            val triggeredWB = triggerWorldBook(_messages.value, _worldBook.value, text,
                maxNonStickyEntries = _wbMaxEntries.value,
                scanDepth           = _wbScanDepth.value,
                inMemoryIndex       = wbLastTriggered)
            if (triggeredWB.isNotEmpty()) markWorldBookTriggered(triggeredWB.map { it.id })
            _lastTriggeredWbIds.value = triggeredWB.map { it.id }.toSet()

            val ordered: List<NyxCharacter>
            val targetMap: Map<String, String?>
            if (_groupMode.value && targets.size > 1) {
                val decision = TurnScheduler.scheduleWithAI(targets, _messages.value.takeLast(8), text)
                    { msgs -> repo.callAI(currentApiConfig, msgs, 200, 0.3f) }
                ordered   = decision.speakers.mapNotNull { id -> targets.find { it.id == id } }
                targetMap = decision.targets
            } else { ordered = targets; targetMap = targets.associate { it.id to null } }

            val roundReplies = mutableListOf<Pair<String, String>>()

            // Bug fix (moved above launch): _isStreaming is now set to true before the launch call,
            // so the flag is visible immediately and the line below is removed to avoid redundancy.
            try {
            for (char in ordered) {
                _typingCharId.value = char.id
                try {
                    val charMems = if (FeatureFlags.MEMORY_DECAY_INJECTION) {
                        repo.loadMemoriesDecayed(char.id).let {
                            // Bug 2 fix: 传入用户设置的注入数量，解锁 Phase 5 记忆条数控制
                            PipelineOptimizer.selectMemoriesForInject(it, _memoryInjectCount.value)
                        }
                    } else {
                        // 回退：按重要度降序取前 N 条，不做时间衰减
                        repo.loadMemoriesDecayed(char.id)
                    }
                    val history = if (FeatureFlags.TOKEN_BUDGET_HISTORY) {
                        PipelineOptimizer.selectHistoryByBudget(_messages.value)
                    } else {
                        _messages.value.takeLast(repo.estimateHistoryLimit(_apiConfig.value))
                    }

                    val charRels = _relationships.value.filter { it.fromCharId == char.id || it.toCharId == char.id }
                    val targetId = targetMap[char.id]
                    val relevantReplies = if (targetId != null)
                        roundReplies.filter { (n, _) -> n == _characters.value.find { it.id == targetId }?.name }
                    else roundReplies.toList()

                    val knownCharIds = if (_groupMode.value)
                        _characters.value.filter { it.isActive }.map { it.id }.toSet() else emptySet()

                    val injectAppearance = FeatureFlags.APPEARANCE_CONDITIONAL &&
                        PipelineOptimizer.shouldInjectAppearance(text)

                    // 步骤10b：情绪惯性 — 在构建 PipelineContext 之前对 currentMood 执行步进
                    // targetMood 为空（旧数据未初始化）时退化为无步进
                    val moodTarget = char.targetMood.takeIf { it.isNotBlank() } ?: char.mood
                    // Bug 4 fix: Gson 反序列化旧 JSON 时缺失的 Float 字段默认为 Java 的 0f，而非
                    // Kotlin 声明的 0.7f。旧角色 emotionalStability == 0f 会导致情绪每轮立即跳变，
                    // 惯性完全失效。此处对未初始化值（< 0.01f）做兜底，使用设计默认值 0.7f。
                    val stability = if (char.emotionalStability < 0.01f) 0.7f else char.emotionalStability
                    val updatedChar = if (char.mood != moodTarget) {
                        if (Random.nextFloat() > stability) char.copy(mood = moodTarget)
                        else char
                    } else char

                    val charForCtx = if (injectAppearance) updatedChar else updatedChar.copy(appearance = "")

                    val allCharsForCtx = if (_groupMode.value) {
                        _characters.value.filter { it.isActive }.map { c ->
                            when {
                                c.id == char.id -> updatedChar
                                // GROUP_TIERED_INJECTION 关闭时对所有角色用完整卡片
                                !FeatureFlags.GROUP_TIERED_INJECTION -> c
                                PipelineOptimizer.isMentioned(text, c.name) -> c
                                else -> c.copy(traits = PipelineOptimizer.buildSilentCharSummary(c),
                                    style = "", background = "", speakingExamples = "", constraints = "")
                            }
                        }
                    } else listOf(updatedChar)

                    val ctx = PipelineContext(
                        char               = charForCtx,
                        allChars           = allCharsForCtx,
                        messages           = history,
                        memories           = charMems,
                        worldBook          = _worldBook.value,
                        triggeredWorldBook = triggeredWB,
                        relationships      = charRels,
                        roundReplies       = relevantReplies,
                        userInput          = text,
                        isGroupChat        = _groupMode.value,
                        historyLimit       = history.size,
                        knownCharIds       = knownCharIds,
                        userPersona        = _userPersona.value,
                        sceneState         = _sceneState.value,
                        isNarrativeMode    = _narrativeMode.value,
                        memoryInjectCount  = _memoryInjectCount.value
                    )

                    // Phase 1: max_tokens 由角色级 replyLength 控制，不再硬编码 1200
                    val stream = repo.streamAI(currentApiConfig, pipeline.buildMessages(ctx), charForCtx.replyLength.maxTokens, char.temperature)
                    val replyBuilder = StringBuilder()
                    var buffer = ""

                    stream.collect { delta ->
                        if (!_isStreaming.value) return@collect
                        buffer += delta
                        if (buffer.length >= 4 || delta.contains(SENTENCE_END_REGEX)) {
                            replyBuilder.append(buffer); buffer = ""
                        }
                    }
                    if (buffer.isNotEmpty()) replyBuilder.append(buffer)
                    _typingCharId.value = null

                    val replyText = NULL_PREFIX_REGEX.replaceFirst(
                        replyBuilder.toString().trim(), ""
                    ).trimStart()
                    if (replyText.isNotBlank()) {
                        // PersonaValidator 已移除：不再拦截重试
                        val finalReply = replyText

                        // 步骤10a：提取并剥离 [mood:xxx] 标记
                        val moodMatch = MOOD_REGEX.find(finalReply)
                        val extractedMood = moodMatch?.groupValues?.getOrNull(1)
                        val cleanReply = if (moodMatch != null)
                            finalReply.replace(moodMatch.value, "").trimEnd()
                        else finalReply

                        push(NyxMessage(UUID.randomUUID().toString(), "assistant", char.id, cleanReply,
                            System.currentTimeMillis(), sessionId, isGroup = _groupMode.value))
                        roundReplies.add(char.name to cleanReply)
                        memoryQueue.trySend(Triple(char.id, text, cleanReply))
                        if (_ttsConfig.value.enabled && _ttsConfig.value.autoPlay)
                            ttsQueue.send(Triple(cleanReply, updatedChar.voiceId, updatedChar.mood))

                        // Bug 3+4 fix: only update timestamp in memory; batch-save after the loop
                        // 步骤10：同步更新 currentMood（步进结果）和 targetMood（AI 新指定）
                        _characters.value = _characters.value.map {
                            if (it.id == char.id) it.copy(
                                lastActiveAt = System.currentTimeMillis(),
                                mood         = updatedChar.mood,
                                targetMood   = extractedMood ?: it.targetMood
                            ) else it
                        }
                    }
                } catch (e: Exception) {
                    // Bug fix: CancellationException 是协程取消信号，必须重新抛出；
                    // 让外层 finally 处理 isStreaming 重置，而非在此静默吞掉。
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // 普通 API 错误：仅记录错误，继续处理队列中剩余角色
                    _error.value = e.message
                    _typingCharId.value = null
                }
            } // end for (char in ordered)

            } finally {
                // Bug fix: 无论成功/失败/取消，都重置 isStreaming 标志
                _isStreaming.value = false
                _typingCharId.value = null
                // Bug 1 fix: 角色状态（mood/lastActiveAt/targetMood）在流式循环中已写入内存；
                // 协程被 cancel() 时 finally 之后的代码不执行，必须在此持久化，
                // 否则取消时内存修改静默丢失，重启后从磁盘加载到旧值。
                // saveCharacters 是普通函数（非 suspend），在 finally 中调用安全。
                //
                // Phase-1 fix: epoch 守卫，防止旧协程的 finally 覆盖新一轮发送已写入的角色状态。
                // 场景：用户快速连发 → 旧协程被 cancel() → 新协程已启动并更新了 _characters.value
                //       → 旧协程的 finally 此刻才执行，若无守卫会用「旧协程视角的最终值」
                //         再覆盖一次磁盘，将新协程刚写入的 mood/lastActiveAt/targetMood 抹去。
                // 只有 epoch 未变（本协程仍是当前活跃轮次）时才落盘；
                // 若 epoch 已变，新协程自己的 finally 会负责持久化，此处安全跳过。
                if (streamingEpoch == myEpoch) {
                    repo.saveCharacters(_characters.value)
                }
            }

            // 关系追踪和自动压缩只在正常完成时执行（协程取消时不会到达此处）

            // 关系追踪 — Bug 2 fix: 传入 existing 做增量更新，而不是每次覆盖为 0.5f
            val exchangeLines = buildList {
                add("用户：$text")
                roundReplies.forEach { (name, reply) -> add("${name}：$reply") }
            }
            if (exchangeLines.size >= 2) {
                val updates = RelationshipTracker.track(exchangeLines, _characters.value, _relationships.value)
                // ── 步骤12：对比更新前后，生成关系事件日志 ──────────────────────
                // Bug 7 fix: 原来对同一 charId 最多有 8 次独立 load→save，
                // 现在先收集本轮所有条目，最后一次性批量写入（每个 charId 只做 1 次 I/O）。
                val pendingLogs = mutableMapOf<String, MutableList<RelationshipLogEntry>>()

                updates.forEach { newRel ->
                    val oldRel = _relationships.value.find { it.id == newRel.id }
                    if (oldRel != null) {
                        val charEntries = pendingLogs.getOrPut(newRel.fromCharId) { mutableListOf() }
                        val nowMs = System.currentTimeMillis()
                        val dimDeltas = listOf(
                            "trust"       to (newRel.trust       - oldRel.trust),
                            "affection"   to (newRel.affection   - oldRel.affection),
                            "tension"     to (newRel.tension     - oldRel.tension),
                            "respect"     to (newRel.respect     - oldRel.respect),
                            "dependency"  to (newRel.dependency  - oldRel.dependency),
                            "jealousy"    to (newRel.jealousy    - oldRel.jealousy),
                            "suppression" to (newRel.suppression - oldRel.suppression)
                        )
                        dimDeltas.forEach { (dim, delta) ->
                            if (kotlin.math.abs(delta) > 0.025f) {
                                val dimCn = when (dim) {
                                    "trust"       -> "信任"
                                    "affection"   -> "亲密"
                                    "tension"     -> "张力"
                                    "respect"     -> "尊重"
                                    "dependency"  -> "依赖"
                                    "jealousy"    -> "嫉妒"
                                    "suppression" -> "压抑"
                                    else          -> dim
                                }
                                val sign = if (delta > 0f) "+" else ""
                                charEntries.add(RelationshipLogEntry(
                                    ts       = nowMs,
                                    charId   = newRel.fromCharId,
                                    toCharId = newRel.toCharId,
                                    dim      = dim,
                                    delta    = delta,
                                    summary  = "$dimCn $sign${(delta * 100).toInt()}%"
                                ))
                            }
                        }
                        // 关系阶段变化日志（stage 棘轮推进时单独记录，方便回溯关系里程碑）
                        if (newRel.stage != oldRel.stage) {
                            charEntries.add(RelationshipLogEntry(
                                ts       = nowMs,
                                charId   = newRel.fromCharId,
                                toCharId = newRel.toCharId,
                                dim      = "stage",
                                delta    = (newRel.stage - oldRel.stage).toFloat(),
                                summary  = "关系阶段 ${stageLabel(oldRel.stage)} → ${stageLabel(newRel.stage)}"
                            ))
                        }
                    }
                    saveRelationship(newRel)
                }

                // 批量写入：每个 charId 只执行一次 load + save（Bug 7 fix 核心）
                pendingLogs.forEach { (charId, entries) ->
                    appendRelationshipLogBatch(charId, entries)
                }
            }

            // 自动历史压缩（后台静默，不阻塞消息流）
            if (repo.shouldCompress(sessionId)) {
                viewModelScope.launch {
                    val result = repo.compressSessionHistory(sessionId, currentApiConfig)
                    if (result != null) {
                        _messages.value = repo.loadMessages(sessionId)
                        // Bug fix: 压缩后消息总数骤降，wbLastTriggered 里保存的旧索引（如 65）
                        // 远大于新消息数（如 26），导致 (newCount - oldIdx) < 0 < cooldownMsgs，
                        // 所有设了冷却的世界书条目永久锁死、再也无法触发。
                        // 修复策略与 clearChat() 完全一致：同时清空内存索引和持久化索引。
                        wbLastTriggered.clear()
                        val resetWb = _worldBook.value.map { entry ->
                            if (entry.lastTriggeredMsgIndex > WB_ENTRY_NEVER_TRIGGERED) entry.copy(lastTriggeredMsgIndex = WB_ENTRY_NEVER_TRIGGERED) else entry
                        }
                        if (resetWb != _worldBook.value) {
                            _worldBook.value = resetWb
                            repo.saveWorldBook(resetWb)
                        }
                    }
                }
            }
        }
    }

    fun maybeGreet(charId: String) {
        val char = _characters.value.find { it.id == charId } ?: return
        // 问候语只在当前 session 没有任何该角色消息时发送（session 级，与生日检查不同）。
        // 此处有意只检查当前 session：切换 session 就是开新对话，允许再次问候。
        if (char.greeting.isBlank() || _messages.value.any { it.charId == charId }) return
        viewModelScope.launch {
            push(NyxMessage(UUID.randomUUID().toString(), "assistant", charId,
                char.greeting, System.currentTimeMillis(), _activeSession.value, false))
        }
    }

    private suspend fun push(m: NyxMessage) {
        repo.insertMessage(m); _messages.value = _messages.value + m
    }

    // ── 步骤7：构建 debug prompt stages ──────────────────────────────────
    fun buildDebugPrompt() {
        val char = _characters.value.find { it.id == _selectedCharId.value }
            ?: _characters.value.firstOrNull { it.isActive } ?: return
        viewModelScope.launch {
            val charMems = repo.loadMemoriesDecayed(char.id).let {
                PipelineOptimizer.selectMemoriesForInject(it, _memoryInjectCount.value)
            }
            val triggeredWB = triggerWorldBook(_messages.value, _worldBook.value, "",
                maxNonStickyEntries = _wbMaxEntries.value,
                scanDepth           = _wbScanDepth.value,
                inMemoryIndex       = wbLastTriggered)
            val ctx = PipelineContext(
                char               = char,
                allChars           = _characters.value.filter { it.isActive },
                messages           = _messages.value.takeLast(repo.estimateHistoryLimit(_apiConfig.value)),
                memories           = charMems,
                worldBook          = _worldBook.value,
                triggeredWorldBook = triggeredWB,
                relationships      = _relationships.value.filter { it.fromCharId == char.id || it.toCharId == char.id },
                roundReplies       = emptyList(),
                userInput          = "",
                isGroupChat        = _groupMode.value,
                historyLimit       = repo.estimateHistoryLimit(_apiConfig.value),
                knownCharIds       = emptySet(),
                userPersona        = _userPersona.value,
                sceneState         = _sceneState.value,
                isNarrativeMode    = _narrativeMode.value,
                memoryInjectCount  = _memoryInjectCount.value
            )
            _debugStages.value = pipeline.buildStagesDebug(ctx)
        }
    }

    // ── 步骤8：加载某角色的有效注入记忆（预览用）────────────────────────
    fun loadEffectiveMems(charId: String) {
        // 已有缓存时直接返回，避免每次调用都触发 IO 和 Map 无限增长（Bug #8 fix 原意保留）。
        // 缓存失效由 invalidateMemsPreview() 在所有写记忆的路径上统一触发（Step 1 fix）。
        if (_effectiveMemsPreview.value.containsKey(charId)) return
        viewModelScope.launch {
            val mems = repo.loadMemoriesDecayed(charId, limit = 8).let {
                PipelineOptimizer.selectMemoriesForInject(it)
            }
            _effectiveMemsPreview.value = _effectiveMemsPreview.value + (charId to mems)
        }
    }

    /**
     * Step 1 fix: 记忆预览缓存失效辅助函数。
     * 从 Map 中移除指定 charId 的条目；下次 loadEffectiveMems() 调用时会重新从 DB 加载。
     * 所有写记忆的路径（add / update / delete / clear / AI自动提取 / 角色删除）都必须调用此函数，
     * 否则预览界面会永久停留在首次打开时的快照。
     */
    private fun invalidateMemsPreview(charId: String) {
        _effectiveMemsPreview.value = _effectiveMemsPreview.value - charId
    }

    /** 关系阶段数字 → 中文标签（日志和 UI 共用） */
    private fun stageLabel(stage: Int): String = when (stage) {
        0 -> "陌生"; 1 -> "初识"; 2 -> "熟悉"
        3 -> "暧昧"; 4 -> "恋人"; 5 -> "深恋"
        else -> "深恋"
    }

    // Bug 4 fix: 释放 TTS 持有的 OkHttpClient 线程池，避免 ViewModel 销毁后资源泄漏
    override fun onCleared() {
        super.onCleared()
        tts.destroy()
    }
}
