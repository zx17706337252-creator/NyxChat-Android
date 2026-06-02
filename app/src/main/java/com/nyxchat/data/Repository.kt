package com.nyxchat.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object HttpClientHolder {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

suspend fun <T> withRetry(maxRetries: Int = 3, initialDelay: Long = 1000L,
                           backoffFactor: Double = 2.0, block: suspend () -> T): T {
    var lastEx: Exception? = null
    var delay = initialDelay
    repeat(maxRetries) { attempt ->
        try { return block() } catch (e: Exception) {
            // Bug 3 fix: CancellationException 是协程取消信号，必须重新抛出，不能重试
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Opt 6: 永久性错误（认证失败、请求格式错误）无需重试，直接抛出
            val msg = e.message ?: ""
            if (msg.contains("401") || msg.contains("403") || msg.contains("400")) throw e
            // Bug fix: 429 Too Many Requests 是限速，应等待更长时间再重试，
            // 而不是用默认的 1s 指数退避——那会立即触发更多 429。
            if (msg.contains("429")) {
                lastEx = e
                if (attempt < maxRetries - 1) kotlinx.coroutines.delay(8_000L)
                return@repeat
            }
            lastEx = e
            if (attempt < maxRetries - 1) { kotlinx.coroutines.delay(delay); delay = (delay * backoffFactor).toLong() }
        }
    }
    throw requireNotNull(lastEx)
}

// Bug fix: StorageManager 由外部（DI）注入，而非在构造函数内自行创建，
// 确保整个 App 只有一个 EncryptedSharedPreferences 实例（MasterKey 只初始化一次）。
class NyxRepository(context: Context, private val storage: StorageManager = StorageManager.getInstance(context.applicationContext)) {
    private val appContext = context.applicationContext
    private val gson       = Gson()
    internal val db        by lazy { AppDatabase.getInstance(appContext) }
    private val http       = HttpClientHolder.client

    companion object {
        private const val HISTORY_GEMINI_2 = 200; private const val HISTORY_GEMINI = 150
        private const val HISTORY_GPT_4O = 150;   private const val HISTORY_GPT_4 = 80
        private const val HISTORY_GPT_3_5 = 30;   private const val HISTORY_DEEPSEEK_V4 = 150
        private const val HISTORY_DEEPSEEK = 80;  private const val HISTORY_CLAUDE_3 = 200
        private const val HISTORY_CLAUDE = 120;   private const val HISTORY_MISTRAL_LARGE = 80
        private const val HISTORY_MISTRAL = 40;   private const val HISTORY_LLAMA_3 = 30
        private const val HISTORY_LLAMA = 20;     private const val HISTORY_QWEN = 120
        private const val HISTORY_DEFAULT = 60

        // ── 历史压缩参数 ─────────────────────────────────────────────────
        // COMPRESS_TRIGGER: Phase 2 提升为用户可调值（存储键 "compress_trigger"），
        //   默认 100（Phase 1 的值），范围 20–200，步进 10。
        //   const val 已降级为普通 val，用 companion 保留向后兼容引用。
        val COMPRESS_TRIGGER = 100          // 保留供 SettingsScreen 显示默认值
        const val COMPRESS_BATCH   = 40
    }

    // Phase 2-B: 压缩阈值动态读写
    fun loadCompressTrigger(): Int = storage.load("compress_trigger", 100)
    fun saveCompressTrigger(v: Int) = storage.save("compress_trigger", v.coerceIn(20, 200))

    // Phase 5: 高级参数持久化
    fun loadWbScanDepth(): Int      = storage.load("wb_scan_depth",        12)
    fun saveWbScanDepth(v: Int)     = storage.save("wb_scan_depth",        v.coerceIn(6, 30))
    fun loadWbMaxEntries(): Int     = storage.load("wb_max_entries",       10)
    fun saveWbMaxEntries(v: Int)    = storage.save("wb_max_entries",       v.coerceIn(1, 30))
    fun loadMemoryInjectCount(): Int  = storage.load("memory_inject_count", 15)
    fun saveMemoryInjectCount(v: Int) = storage.save("memory_inject_count", v.coerceIn(5, 50))

    // 厂商重构：优先读取 ApiConfig.historyLimit（内置厂商已精确设定），
    // null 时才走字符串匹配兜底（自定义厂商 / 旧版数据兼容）。
    fun estimateHistoryLimit(cfg: ApiConfig): Int =
        cfg.historyLimit ?: estimateHistoryLimit(cfg.model)

    // P1-A fix: 统一转小写后比较，彻底消除大小写歧义。
    // 原来 "deepseek-v4" in model 无法匹配官方名称 deepseek-V3.2（大写 V），
    // 导致 V3.2 历史上限落到兜底的 40 条（HISTORY_DEEPSEEK）而非正确的 70 条。
    // 新增 "deepseek-v3" 分支与 v4 共享同一上限（方案文档确认 V3.2 = 70 条）。
    fun estimateHistoryLimit(model: String): Int {
        val m = model.lowercase()
        return when {
            "gemini-2" in m        -> HISTORY_GEMINI_2
            "gemini" in m          -> HISTORY_GEMINI
            "gpt-4o" in m          -> HISTORY_GPT_4O
            "gpt-4" in m           -> HISTORY_GPT_4
            "gpt-3.5" in m         -> HISTORY_GPT_3_5
            "deepseek-v4" in m     -> HISTORY_DEEPSEEK_V4
            "deepseek-v3" in m     -> HISTORY_DEEPSEEK_V4  // V3.2 与 v4 同等限制
            "deepseek" in m        -> HISTORY_DEEPSEEK
            "claude-3" in m        -> HISTORY_CLAUDE_3
            "claude" in m          -> HISTORY_CLAUDE
            "mistral-large" in m   -> HISTORY_MISTRAL_LARGE
            "mistral" in m         -> HISTORY_MISTRAL
            "llama3" in m          -> HISTORY_LLAMA_3
            "llama" in m           -> HISTORY_LLAMA
            "qwen" in m            -> HISTORY_QWEN
            else                   -> HISTORY_DEFAULT
        }
    }

    // ── StorageManager ─────────────────────────────────────────────────────
    // Bug 1 fix: 旧版 JSON 中没有 replyLength 字段时，Gson 将枚举反序列化为 null，
    // 绕过 Kotlin 非空约束，导致 ChatViewModel / PromptPipeline / CharactersScreen 三处 NPE。
    // 在 Repository 层统一兜底：一处修复，覆盖全部下游调用链。
    fun loadCharacters(): List<NyxCharacter> =
        storage.load("characters", DEFAULT_CHARACTERS).map { c ->
            @Suppress("UNNECESSARY_SAFE_CALL")
            c.copy(replyLength = c.replyLength ?: ReplyLength.Medium)
        }
    fun saveCharacters(v: List<NyxCharacter>)      = storage.save("characters", v)
    // Minor 2 fix: matchMode 与 replyLength 同款问题——旧世界书条目的 matchMode 反序列化为 null，
    // triggerWorldBook() 中的 when(entry.matchMode) 会 NPE。同步修复。
    fun loadWorldBook(): List<WorldBookEntry> =
        storage.load("worldbook", DEFAULT_WORLD_BOOK).map { e ->
            @Suppress("UNNECESSARY_SAFE_CALL")
            e.copy(matchMode = e.matchMode ?: MatchMode.ANY)
        }
    fun saveWorldBook(v: List<WorldBookEntry>)      = storage.save("worldbook", v)
    fun loadRelationships(): List<Relationship>     = storage.load("relations", emptyList<Relationship>())
    fun saveRelationships(v: List<Relationship>)    = storage.save("relations", v)
    fun loadSessions(): List<ChatSession>           = storage.load("sessions", listOf(ChatSession("default", "默认对话")))
    fun saveSessions(v: List<ChatSession>)          = storage.save("sessions", v)
    fun loadUserPersona(): UserPersona              = storage.load("user_persona", UserPersona())
    fun saveUserPersona(v: UserPersona)             = storage.save("user_persona", v)
    fun loadSceneState(): SceneState                = storage.load("scene_state", SceneState())
    fun saveSceneState(v: SceneState)               = storage.save("scene_state", v)
    // Bug 4 fix: subscriptionKey / alibabaDashscopeKey / volcengineToken 为敏感字段，
    // 原 storage.save/load 使用普通 SharedPreferences（明文）。
    // 修复后：非敏感配置走普通存储，三个密钥走 EncryptedSharedPreferences。
    // 迁移兼容：首次 load 时若检测到旧整体 JSON，自动迁移到新格式，并写入迁移标记避免重复执行。
    fun loadTtsConfig(): TtsConfig {
        val migrated = storage.loadBoolean("tts_config_migrated", false)
        if (!migrated) {
            // 尝试读取旧整体 JSON（明文存储）
            val legacy = storage.load("tts_config", TtsConfig())
            saveTtsConfig(legacy)                              // 写入新分离格式（密钥加密）
            storage.saveBoolean("tts_config_migrated", true)  // 标记迁移完成，避免重复执行
            return legacy
        }
        val meta = storage.load("tts_config_meta", TtsConfig())
        return meta.copy(
            subscriptionKey     = storage.loadSecureString("tts_sub_key"),
            alibabaDashscopeKey = storage.loadSecureString("tts_ali_key"),
            volcengineToken     = storage.loadSecureString("tts_vol_token")
        )
    }

    fun saveTtsConfig(v: TtsConfig) {
        // 非敏感字段：普通存储（密钥字段置空，不写入明文）
        storage.save("tts_config_meta", v.copy(
            subscriptionKey     = "",
            alibabaDashscopeKey = "",
            volcengineToken     = ""
        ))
        // 敏感字段：EncryptedSharedPreferences 加密存储
        storage.saveSecureString("tts_sub_key",   v.subscriptionKey)
        storage.saveSecureString("tts_ali_key",   v.alibabaDashscopeKey)
        storage.saveSecureString("tts_vol_token", v.volcengineToken)
    }

    fun loadDarkMode(): Boolean                     = storage.loadBoolean("dark_mode", true)
    fun saveDarkMode(v: Boolean)                    = storage.saveBoolean("dark_mode", v)

    // 步骤4：里程碑基线（key 含 charId，避免多角色互相污染）
    fun loadMilestoneBaseline(charId: String): Map<String, Float> =
        storage.load("milestone_baseline_$charId", emptyMap<String, Float>())
    fun saveMilestoneBaseline(charId: String, v: Map<String, Float>) =
        storage.save("milestone_baseline_$charId", v)

    // 步骤6：话题去重记录
    fun loadProactiveTopics(charId: String): List<String> =
        storage.load("proactive_topics_$charId", emptyList<String>())
    fun saveProactiveTopics(charId: String, v: List<String>) =
        storage.save("proactive_topics_$charId", v)

    // 步骤12：关系事件日志
    fun loadRelationshipLog(charId: String): List<RelationshipLogEntry> =
        storage.load("rel_log_$charId", emptyList<RelationshipLogEntry>())
    fun appendRelationshipLog(charId: String, entry: RelationshipLogEntry) {
        val existing = loadRelationshipLog(charId).takeLast(99)   // 最多保留 100 条
        storage.save("rel_log_$charId", existing + entry)
    }
    /**
     * Bug 7 fix: 批量追加日志条目。
     * 原来每轮对话对同一 charId 最多有 8 次独立的 load→append→save，
     * 每次都完整地序列化+反序列化同一个 JSON 数组，低端机上会有轻微卡顿。
     * 批量版本对同一 charId 只执行一次 load 和一次 save，减少 7 次冗余 I/O。
     */
    fun appendRelationshipLogBatch(charId: String, entries: List<RelationshipLogEntry>) {
        if (entries.isEmpty()) return
        val existing = loadRelationshipLog(charId)
        // 保留最近 100 条：先取旧列表的末尾（100 - 新增数量），再拼上新条目
        val keepCount = (100 - entries.size).coerceAtLeast(0)
        storage.save("rel_log_$charId", existing.takeLast(keepCount) + entries)
    }
    fun loadParticlesEnabled(): Boolean             = storage.loadBoolean("enable_particles", true)
    fun saveParticlesEnabled(v: Boolean)            = storage.saveBoolean("enable_particles", v)
    // Batch 3 Item 9: 聊天区全局背景图路径（空字符串 = 无自定义背景）
    fun loadChatBackground(): String                = storage.load("chat_background_path", "")
    fun saveChatBackground(path: String)            = storage.save("chat_background_path", path)

    fun loadApiConfig(): ApiConfig {
        val key = storage.loadSecureString("api_key")
        return ApiConfig(
            apiKey       = key,
            baseUrl      = storage.loadSecure("api_base_url", "https://api.openai.com/v1"),
            model        = storage.loadSecure("api_model", "gpt-4o-mini"),
            providerName = storage.loadSecureString("api_provider"),
            historyLimit = storage.loadSecureString("api_history_limit").toIntOrNull()
        )
    }
    fun saveApiConfig(v: ApiConfig) {
        storage.saveSecureString("api_key", v.apiKey)
        storage.saveSecure("api_base_url", v.baseUrl)
        storage.saveSecure("api_model", v.model)
        storage.saveSecureString("api_provider", v.providerName)
        storage.saveSecureString("api_history_limit", v.historyLimit?.toString() ?: "")
    }

    // ── Messages ───────────────────────────────────────────────────────────
    suspend fun insertMessage(m: NyxMessage) = withContext(Dispatchers.IO) { db.messageDao().insert(m.toEntity()) }
    suspend fun appendMessage(m: NyxMessage) = insertMessage(m)
    suspend fun loadMessages(sessionId: String): List<NyxMessage> = withContext(Dispatchers.IO) {
        db.messageDao().getForSession(sessionId).map { it.toDomain() }
    }
    suspend fun deleteMessage(id: String) = withContext(Dispatchers.IO) { db.messageDao().deleteById(id) }
    suspend fun clearSession(sessionId: String) = withContext(Dispatchers.IO) { db.messageDao().deleteSession(sessionId) }

    // Step 5 fix (Bug 5): 改为在 DB 层就用 sessionId 过滤，消除 ViewModel 中的内存二次过滤。
    // 函数签名不变，ViewModel 的 searchMessages() 调用无需修改，只需额外传入 sessionId。
    suspend fun searchMessages(query: String, sessionId: String): List<NyxMessage> = withContext(Dispatchers.IO) {
        db.messageDao().searchMessagesInSession(sessionId, "%$query%").map { it.toDomain() }
    }
    suspend fun searchMessagesFiltered(query: String, charId: String, sessionId: String): List<NyxMessage> = withContext(Dispatchers.IO) {
        db.messageDao().searchMessagesInSessionFiltered(sessionId, "%$query%", charId).map { it.toDomain() }
    }

    // ── Memories ───────────────────────────────────────────────────────────
    suspend fun loadAllMemories(): List<NyxMemory> = withContext(Dispatchers.IO) {
        db.memoryDao().getTop(500).map { it.toDomain() }
    }
    suspend fun loadMemoriesForChar(charId: String): List<NyxMemory> = withContext(Dispatchers.IO) {
        db.memoryDao().getForCharDecayed(charId, System.currentTimeMillis(), 100).map { it.toDomain() }
    }
    // Phase 1: limit 默认值 50→200，与 pruneForChar 上限同步
    suspend fun loadMemoriesDecayed(charId: String, limit: Int = 200): List<NyxMemory> = withContext(Dispatchers.IO) {
        db.memoryDao().getForCharDecayed(charId, System.currentTimeMillis(), limit).map { it.toDomain() }
    }
    suspend fun insertMemories(mems: List<NyxMemory>) = withContext(Dispatchers.IO) {
        db.memoryDao().insertAll(mems.map { it.toEntity() })
        // Phase 1: 50→200，每角色记忆上限从约25次对话扩展到约100次；存储成本几乎为零
        mems.map { it.charId }.distinct().forEach { db.memoryDao().pruneForChar(it, 200) }
    }
    suspend fun deleteMemory(id: String) = withContext(Dispatchers.IO) { db.memoryDao().delete(id) }
    suspend fun clearMemoriesForChar(charId: String) = withContext(Dispatchers.IO) { db.memoryDao().deleteForChar(charId) }
    suspend fun updateMemory(m: NyxMemory) = withContext(Dispatchers.IO) {
        // Bug fix: 原来先 delete 再 insert，非原子操作；崩溃在两步之间会丢失记忆
        // insertAll 已使用 OnConflictStrategy.REPLACE，直接覆盖写入即可
        db.memoryDao().insertAll(listOf(m.toEntity()))
    }

    // ── File helpers ───────────────────────────────────────────────────────
    fun copyImageToInternal(uri: Uri, filename: String): String {
        val file = File(appContext.filesDir, filename)
        appContext.contentResolver.openInputStream(uri)?.use { ins -> file.outputStream().use { ins.copyTo(it) } }
        return file.absolutePath
    }
    fun saveBitmapToInternal(bitmap: Bitmap, filename: String): String {
        val file = File(appContext.filesDir, filename)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }
    fun readTextDocument(uri: Uri): String? =
        runCatching { appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
    fun getFilenameFromUri(uri: Uri): String {
        var name = "document"
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (col >= 0 && cursor.moveToFirst()) name = cursor.getString(col) ?: name
        }
        return name.substringBeforeLast(".")
    }

    // ── AI calls ───────────────────────────────────────────────────────────
    suspend fun callAI(cfg: ApiConfig, messages: List<Map<String, String>>,
                       maxTokens: Int = 800, temperature: Float = 0.85f): String =
        withContext(Dispatchers.IO) {
            withRetry {
                val req = buildRequest(cfg, buildBody(cfg.model, messages, maxTokens, temperature, false))
                // R1 fix: response.use {} 确保无论成功/失败，TCP 连接都归还连接池。
                // 原代码 !isSuccessful 时直接抛出，resp 从未关闭 → 连接泄漏。
                http.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: "{}"      // 只读一次，避免二次消费返回 null
                    if (!resp.isSuccessful) throw Exception("API ${resp.code}: ${bodyStr.take(200)}")
                    JSONObject(bodyStr)
                        .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                }
            }
        }

    // Opt 5: 用 flowOn(Dispatchers.IO) 代替散落的 withContext，线程调度更清晰
    fun streamAI(cfg: ApiConfig, messages: List<Map<String, String>>,
                 maxTokens: Int = 1200, temperature: Float = 0.85f): Flow<String> = flow {
        val call = http.newCall(buildRequest(cfg, buildBody(cfg.model, messages, maxTokens, temperature, true)))
        val resp = call.execute()
        // R2 fix: 将 isSuccessful 检查移入 try 块内，确保 finally { resp.body?.close() } 始终执行。
        // 原代码在 try 外抛出时 finally 不运行 → 每次 API 错误都泄漏一条 TCP 连接。
        try {
            if (!resp.isSuccessful) throw Exception("API ${resp.code}")
            val source = resp.body?.source()
                ?: throw Exception("响应 body 为空（HTTP ${resp.code}），请检查 API 地址是否正确")
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ") && !line.contains("[DONE]")) {
                    // Bug fix ①: 原来 runCatching 把 emit() 也包在内，
                    // 下游取消时 emit() 抛出的 CancellationException 被吞掉，
                    // 导致 OkHttp 连接在用户点击停止后仍继续读取完整响应。
                    // 修复：runCatching 仅覆盖可能失败的 JSON 解析部分；
                    // emit() 移到外部，让取消信号正常向上传播并中断循环。
                    val content = runCatching {
                        JSONObject(line.removePrefix("data: "))
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("delta").optString("content", "")
                    }.getOrDefault("")
                    if (content.isNotEmpty()) emit(content)
                }
            }
        } finally {
            resp.body?.close()   // 协程取消时也保证连接关闭，防止 OOM
        }
    }.flowOn(Dispatchers.IO)

    // ── 历史压缩 ───────────────────────────────────────────────────────────
    // 优化: 只统计非摘要消息，摘要本身不应计入压缩阈值
    // Phase 2-B: 阈值改为读取存储值，支持用户在 SettingsScreen 动态调整
    suspend fun shouldCompress(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val total    = db.messageDao().countForSession(sessionId)
        val summaries = db.messageDao().countSummariesForSession(sessionId)
        (total - summaries) >= loadCompressTrigger()
    }

    /**
     * 压缩 [sessionId] 最早的 [COMPRESS_BATCH] 条非摘要消息 → 用 AI 生成 1 条摘要。
     * 成功时返回摘要文本并修改数据库；失败/条数不足时返回 null（不修改任何数据）。
     * [minCount] 手动触发时可设较低值（默认 COMPRESS_BATCH）。
     */
    suspend fun compressSessionHistory(sessionId: String, cfg: ApiConfig, minCount: Int = COMPRESS_BATCH): String? =
        withContext(Dispatchers.IO) {
            val allMsgs = db.messageDao().getForSession(sessionId)
            val originals = allMsgs.filter { it.isSummary == 0 }
            if (originals.size < minCount) return@withContext null

            val toCompress = originals.take(COMPRESS_BATCH)
            val dialogue = toCompress.joinToString("\n") { msg ->
                val speaker = when {
                    msg.role == "user" -> "你"
                    msg.charId != null -> "角色(${msg.charId.take(8)})"
                    else               -> "助手"
                }
                "$speaker：${msg.content.take(300)}"
            }
            val promptMsgs = listOf(
                mapOf("role" to "system", "content" to
                    "你是一个会写故事的对话摘要助手。请用简洁但富有章节感的中文，将以下对话写成一则「本章小结」（200字以内），" +
                    "说明本章发生了什么、角色的情绪变化、以及值得记住的事。像小说章节结尾那样自然叙述。只输出小结本身，不加任何前缀。"),
                mapOf("role" to "user", "content" to dialogue)
            )

            return@withContext try {
                val summary = callAI(cfg, promptMsgs, maxTokens = 300, temperature = 0.3f)
                if (summary.isBlank()) return@withContext null

                val summaryMsg = NyxMessage(
                    id        = java.util.UUID.randomUUID().toString(),
                    role      = "system",
                    charId    = null,
                    content   = "【历史摘要】$summary",
                    timestamp = toCompress.first().timestamp,
                    sessionId = sessionId,
                    isGroup   = false,
                    isSummary = true
                )
                db.messageDao().insert(summaryMsg.toEntity())
                toCompress.forEach { db.messageDao().deleteById(it.id) }
                summary
            } catch (_: Exception) {
                null   // 压缩失败不报错，下次重试
            }
        }

    suspend fun extractMemories(cfg: ApiConfig, charId: String,
                                 userText: String, reply: String,
                                 existing: List<NyxMemory>): List<NyxMemory> {
        val existingSummary = existing.takeLast(10).joinToString("\n") { "- ${it.content}" }
        val prompt = """
从以下对话中提取值得长期记忆的信息，最多3条，只保留有实质意义的内容。

【优先提取】
- 承诺 / 约定（"我明天会…" / "你答应过…" / "我们说好了…"）
- 用户透露的重要个人信息（喜好、经历、身份、情感状态）
- 关系状态的明确变化（信任提升、冲突、和解、表白等）
- 角色对用户做出的承诺或用户对角色的重要请求

【忽略】寒暄、重复、无意义内容、和已有记忆重复的内容

格式（每条独立一行）：[重要度1-5] 内容
重要度：5=绝对不能忘（承诺/约定）  4=很重要  3=有参考价值  1-2=轻微
如果没有值得提取的内容，只回复"无"。

已有记忆（勿重复）：
$existingSummary

对话：
用户：$userText
角色：$reply
        """.trimIndent()
        val raw = callAI(cfg, listOf(mapOf("role" to "user", "content" to prompt)), 200, 0.3f)
        if (raw.trim() == "无" || raw.isBlank()) return emptyList()
        // P3-B fix: 原来 line.removePrefix("[").take(1) 只取第一个字符，
        // AI 若输出 [10] 则解析出 "1"（值域外），输出 [3] 内容 则正常，但实现脆弱。
        // 改用正则精确捕获数字组和内容组：\[(\d+)]\s*(.+)
        // coerceIn(1,5) 防止 AI 偶发超范围输出导致异常。
        val memLineRegex = Regex("""\[(\d+)]\s*(.+)""")
        return raw.lines().mapNotNull { line ->
            val match = memLineRegex.find(line.trim()) ?: return@mapNotNull null
            val importance = match.groupValues[1].toIntOrNull()?.coerceIn(1, 5) ?: return@mapNotNull null
            val content = match.groupValues[2].trim().ifBlank { return@mapNotNull null }
            NyxMemory(UUID.randomUUID().toString(), charId, content, importance, System.currentTimeMillis())
        }
    }

    private fun buildBody(model: String, messages: List<Map<String, String>>,
                          maxTokens: Int, temperature: Float, stream: Boolean) =
        JSONObject().apply {
            put("model", model)
            put("messages", JSONArray(messages.map { m ->
                JSONObject().apply { put("role", m["role"]); put("content", m["content"]) }
            }))
            put("max_tokens", maxTokens); put("temperature", temperature.toDouble()); put("stream", stream)
            // 通义 Qwen 混合思考模型：统一显式关闭思考，确保不消耗思考 token
            // · qwen3-x 系列（qwen3-235b-a22b / qwen3-32b 等，连字符格式）：官方默认开启思考，必须关闭
            // · qwen3.x 系列（未来可能出现的点号格式）：同上，一并覆盖
            // · qwen-plus / qwen-flash / qwen-turbo：「默认不开启」，显式传 false 保险
            // · qwen-long：专用长文本模型，不是思考模型，不传此参数
            // Bug 3 fix: 原 startsWith("qwen3.") 带点号，无法匹配已预设的 qwen3-235b-a22b / qwen3-32b
            val isQwenHybridThinking = model.startsWith("qwen3") ||
                model.startsWith("qwen-plus") ||
                model.startsWith("qwen-flash") ||
                model.startsWith("qwen-turbo")
            if (isQwenHybridThinking) {
                put("enable_thinking", false)
            }
            // DeepSeek R1（含方舟版 deepseek-r1-*）：部分部署支持 enable_thinking=false
            // 主要靠 PromptPipeline 的 output_directive stage 指示不输出思考过程
            // 此处暂不传参，避免不支持该字段的端点返回 400
        }.toString().toRequestBody("application/json".toMediaType())

    private fun buildRequest(cfg: ApiConfig, body: okhttp3.RequestBody) =
        Request.Builder().url("${cfg.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${cfg.apiKey}")
            .addHeader("Content-Type", "application/json").post(body).build()
}
