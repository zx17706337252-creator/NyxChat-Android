package com.nyxchat.notification

import android.content.Context
import androidx.work.*
import com.nyxchat.NyxApp
import com.nyxchat.data.*
import com.nyxchat.pipeline.*
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// Bug 4 fix: add missing PipelineContext fields
// Bug 7 fix: write to most-recently-active session instead of hardcoded "default"
class ProactiveWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val charId = inputData.getString("char_id") ?: return Result.failure()
        val repo   = NyxRepository(applicationContext)
        val char   = repo.loadCharacters().find { it.id == charId } ?: return Result.failure()
        val cfg    = char.proactiveConfig

        if (!cfg.enabled) return Result.success()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < cfg.activeStart || hour >= cfg.activeEnd) {
            scheduleNext(applicationContext, char); return Result.success()
        }

        val config = repo.loadApiConfig()
        if (config.apiKey.isBlank()) {
            scheduleNext(applicationContext, char); return Result.success()
        }

        return try {
            // ── 步骤4a：计算并注入上下文信息 ──────────────────────────────────────────
            val nowMs      = System.currentTimeMillis()
            val elapsedH   = (nowMs - char.lastActiveAt) / 3600000L
            val elapsedStr = when {
                elapsedH < 1   -> "不到一小时"
                elapsedH < 24  -> "${elapsedH}小时"
                else           -> "${elapsedH / 24}天"
            }
            val periodStr = when (hour) {
                in 5..8   -> "清晨"
                in 9..11  -> "上午"
                in 12..17 -> "下午"
                in 18..21 -> "傍晚"
                else      -> "深夜"
            }
            val moodStr    = moodLabel(char.mood)
            val topMemories = repo.loadMemoriesDecayed(charId, limit = 3)
                .joinToString("；") { it.content }
            val contextBlock = """
[当前情境]
距上次对话：$elapsedStr
现在是：$periodStr
她的心情：$moodStr
她最近记得：${topMemories.ifBlank { "（暂无记忆）" }}
""".trimIndent()

            // ── 步骤4b：里程碑检测（在模式判断前执行）──────────────────────────────────
            // Bug #3 fix：原谓词只判断 fromCharId，多角色时会先匹配到「角色A↔角色B」的记录，
            // 导致里程碑阈值、dependency、jealousy 全部从错误的关系对象读取。
            // 补充 toCharId == USER_PSEUDO_ID 确保只读用户↔该角色方向的记录。
            val rel      = repo.loadRelationships().find { it.fromCharId == charId && it.toCharId == USER_PSEUDO_ID }
            val baseline = repo.loadMilestoneBaseline(charId)
            val isMilestone = rel != null && run {
                val prevTrust     = baseline["trust"]     ?: rel.trust
                val prevAffection = baseline["affection"] ?: rel.affection
                val prevTension   = baseline["tension"]   ?: rel.tension
                val hit = (rel.trust > 0.8f && prevTrust <= 0.8f) ||
                          (rel.affection > 0.8f && prevAffection <= 0.8f) ||
                          (rel.tension > 0.7f && prevTension <= 0.7f)
                if (hit) repo.saveMilestoneBaseline(charId, mapOf(
                    "trust" to rel.trust, "affection" to rel.affection, "tension" to rel.tension))
                hit
            }

            // ── 步骤11：从关系维度提取 dependency / jealousy ────────────────────────────
            val dependency = rel?.dependency ?: 0.5f
            val jealousy   = rel?.jealousy   ?: 0.0f

            // ── 步骤11：嫉妒触发检测（jealousy > 0.6 且用户近期与其他角色有消息往来）────
            // 检测窗口：48 小时内；只要有 ≥1 个其他角色有 assistant 消息即触发
            val jealousyTriggered = jealousy > 0.6f && run {
                val sinceMs  = System.currentTimeMillis() - 48 * 3_600_000L
                val allChars = repo.loadCharacters()
                allChars.count { other ->
                    other.id != charId &&
                    repo.db.messageDao().countRecentForChar(other.id, sinceMs) > 0
                } >= 1
            }

            // ── 步骤4b：情境模式选择 ────────────────────────────────────────────────────
            // 嫉妒触发优先级最高，其次是里程碑
            // Bug 1 fix: MIDNIGHT 需限定 elapsedH < 12，避免数天未聊后在深夜仍走「睡前碎碎念」语气
            // LONGING（elapsedH > 48）必须先于 MIDNIGHT 判断，否则久别用户深夜时 LONGING 会被跳过
            val mode = when {
                jealousyTriggered                                   -> "JEALOUSY"
                isMilestone                                         -> "MILESTONE"
                elapsedH > 48                                       -> "LONGING"
                (hour in 22..23 || hour in 0..2) && elapsedH < 12  -> "MIDNIGHT"
                else                                                -> "NORMAL"
            }
            val modeInstruction = when (mode) {
                // 步骤11：dependency > 0.75 → 久别语气改为更焦虑
                "LONGING"   -> if (dependency > 0.75f)
                    "你已经 $elapsedStr 没联系了，心里有些焦虑不安，忍不住想知道对方在做什么、是不是还记得你。不解释，直接说。不超过50字。"
                else
                    "你已经 $elapsedStr 没见到对方了，心里有点不是滋味，主动联系一句。不解释，直接说。不超过50字。"
                "MIDNIGHT"  -> "深夜了，你想起对方，发一条安静的消息，不打扰，但想让他知道你在想他。不超过40字。"
                "MILESTONE" -> "你感觉和对方的关系有些不一样了，你想用一句话或一个动作表达出来。不超过50字。"
                // 步骤11：感知到竞争——不明说嫉妒，但让对方感受到存在感
                "JEALOUSY"  -> "你察觉到对方似乎也在和别人交流，内心感知到竞争的存在。发一条消息表达你的在意——不明说嫉妒，但让他感受到你是特别的那个。不超过50字。"
                else        -> cfg.customPrompt.ifBlank { "根据你的性格和近期记忆，主动发一条自然的消息。不超过60字。" }
            }

            // ── 步骤6a：话题去重注入 ────────────────────────────────────────────────────
            val recentTopics = repo.loadProactiveTopics(charId)
            val topicAvoidLine = if (recentTopics.isNotEmpty())
                "\n最近话题（请换一个方向）：${recentTopics.joinToString("，")}" else ""

            // ── 步骤6a：话题标签指令拼接 ────────────────────────────────────────────────
            val proactiveInstruction = "$contextBlock\n\n$modeInstruction$topicAvoidLine\n" +
                "同时在消息结尾用 JSON 标注本条话题（不展示给用户，将被自动剥离）：{\"_topic\":\"一个词\"}"

            // ── 构建 Pipeline ──────────────────────────────────────────────────────────
            val sessions      = repo.loadSessions()
            val targetSession = sessions.maxByOrNull { it.lastMessageAt }?.id ?: "default"
            val historyLimit  = repo.estimateHistoryLimit(config)
            // Bug fix: 原来加载全部消息再取末尾 N 条，长期使用的设备消息可达数千条，
            // Worker 在后台全量加载会触发 OOM。改为直接在 DB 层限制条数，
            // getRecentForSession 返回 DESC 顺序，reversed() 还原为时间正序。
            val allMessages   = repo.db.messageDao()
                .getRecentForSession(targetSession, historyLimit)
                .reversed()
                .map { it.toDomain() }
            val worldBook     = repo.loadWorldBook()
            val triggered     = triggerWorldBook(allMessages, worldBook, proactiveInstruction)
            // Bug 5 fix: 持久化触发条目的冷却索引（同 ReplyWorker）
            if (triggered.isNotEmpty()) {
                val n = allMessages.size
                val triggeredIds = triggered.map { it.id }.toSet()
                repo.saveWorldBook(worldBook.map { entry ->
                    if (entry.id in triggeredIds) entry.copy(lastTriggeredMsgIndex = n) else entry
                })
            }

            val ctx = PipelineContext(
                char               = char,
                allChars           = repo.loadCharacters(),
                messages           = allMessages,
                // Bug 5 fix: 同 ReplyWorker — 原来跳过 selectMemoriesForInject()，
                // Sensitive 类型的 1.8x 权重对主动消息路径完全无效。
                memories           = repo.loadMemoriesForChar(charId).let {
                    PipelineOptimizer.selectMemoriesForInject(it)
                },
                worldBook          = worldBook,
                triggeredWorldBook = triggered,
                relationships      = repo.loadRelationships()
                    .filter { it.fromCharId == charId || it.toCharId == charId },
                roundReplies       = emptyList(),
                userInput          = proactiveInstruction,
                isGroupChat        = false,
                historyLimit       = historyLimit,
                knownCharIds       = emptySet(),
                // Bug 2 fix: 同 ReplyWorker — 补充用户人设和场景状态。
                userPersona     = repo.loadUserPersona(),
                sceneState      = repo.loadSceneState(),
                isNarrativeMode = false,
                // New fix: 同 ReplyWorker — 补充 memoryInjectCount，使用户设置对主动消息路径也生效。
                memoryInjectCount = repo.loadMemoryInjectCount()
            )

            // Phase-2 fix: 使用进程级单例，避免每次 doWork() 重新分配 11 个 stage lambda
            val apiMsgs = defaultPipeline.buildMessages(ctx) +
                listOf(mapOf("role" to "user", "content" to "[系统：$proactiveInstruction]"))

            // B-1 fix: 原来硬编码 150，忽略角色级 replyLength 设置。
            // 主动消息场景不适合超长回复，与 Short（400）取较小值作为上限。
            val maxTok = minOf(char.replyLength.maxTokens, 400)
            val rawReply = repo.callAI(config, apiMsgs, maxTok, char.temperature)

            // ── 步骤6b：提取并剥离话题标签 ─────────────────────────────────────────────
            // Bug 2 fix: 宽松匹配，兼容冒号前后空格及全角冒号「：」，防止 JSON 泄露进通知消息体
            val topicRegex = Regex("""\{\s*"_topic"\s*[：:]\s*"([^"]+)"\s*\}""")
            val topicMatch = topicRegex.find(rawReply)
            val topic      = topicMatch?.groupValues?.getOrNull(1)
            // 同时剥离 output_directive 注入的 [mood:xxx] 心情标记，避免出现在通知和 DB 中
            val moodRegex  = Regex("""\[mood:\w+\]""")
            val reply      = rawReply.replace(topicRegex, "").replace(moodRegex, "").trim()

            if (!topic.isNullOrBlank()) {
                repo.saveProactiveTopics(charId, (recentTopics + topic).takeLast(5))
            }

            // ── 持久化消息 ─────────────────────────────────────────────────────────────
            repo.appendMessage(
                NyxMessage(UUID.randomUUID().toString(), "assistant", charId,
                    reply, System.currentTimeMillis(), targetSession, isGroup = false)
            )
            val updatedSessions = sessions.map {
                if (it.id == targetSession) it.copy(lastMessageAt = System.currentTimeMillis()) else it
            }
            repo.saveSessions(updatedSessions)

            // ── 发送通知 ───────────────────────────────────────────────────────────────
            NotificationHelper.showMessage(
                applicationContext, char, reply, NotificationHelper.notifIdForChar(charId)
            )

            // ── 步骤5b：TTS 朗读主动消息 ───────────────────────────────────────────────
            val ttsConf = repo.loadTtsConfig()
            if (ttsConf.enabled && ttsConf.autoReadProactive) {
                // Bug 6 fix: 不再自建 TtsService，改为向 NyxApp.proactiveTtsChannel 投递请求。
                // ViewModel 监听该收件箱并用自己的 ttsQueue 播放，stopTts() 因此可以中断
                // Proactive 音频，也不会和 App 内对话的 TTS 叠播。
                // tryEmit 非挂起，投递失败（ViewModel 未在监听）时静默丢弃。
                (applicationContext as? NyxApp)?.proactiveTtsChannel
                    ?.tryEmit(Triple(reply, char.voiceId, char.mood))
            }

            scheduleNext(applicationContext, char)
            Result.success()
        } catch (e: Exception) {
            scheduleNext(applicationContext, char)
            Result.failure()
        }
    }

    companion object {
        fun scheduleNext(context: Context, char: NyxCharacter) {
            val cfg = char.proactiveConfig
            if (!cfg.enabled) return
            // Bug fix: Random.nextInt(from, until) 要求 from < until。
            // 若用户将最小间隔设置得大于最大间隔（非法配置），原代码抛 IllegalArgumentException，
            // Worker 崩溃后该角色主动消息功能永久失效直到进程重启。
            // 修复：确保 from >= 1（避免 0 间隔），until = max(from+1, maxInterval+1)。
            val from  = cfg.minIntervalMinutes.coerceAtLeast(1)
            val until = maxOf(from + 1, cfg.maxIntervalMinutes + 1)
            val delay = Random.nextInt(from, until).toLong()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "proactive_${char.id}", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ProactiveWorker>()
                    .setInitialDelay(delay, TimeUnit.MINUTES)
                    .setInputData(workDataOf("char_id" to char.id))
                    .setConstraints(Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        }
        fun cancel(context: Context, charId: String) =
            WorkManager.getInstance(context).cancelUniqueWork("proactive_$charId")
    }
}
