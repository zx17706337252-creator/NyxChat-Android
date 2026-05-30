package com.nyxchat.notification

import android.content.Context
import androidx.work.*
import com.nyxchat.data.*
import com.nyxchat.pipeline.*
import java.util.UUID

// 修复：创建缺失的 ReplyWorker 类处理通知回复
class ReplyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val charId = inputData.getString("char_id") ?: return Result.failure()
        val notifId = inputData.getInt("notif_id", 0)
        val userMsg = inputData.getString("user_msg") ?: return Result.failure()

        val repo = NyxRepository(applicationContext)
        val char = repo.loadCharacters().find { it.id == charId } ?: return Result.failure()

        // 修复⑧：showTyping 从 NotificationReceiver（主线程）移到这里（后台线程）
        NotificationHelper.showTyping(applicationContext, char, notifId)

        val config = repo.loadApiConfig()

        // 检查 API 配置
        if (config.apiKey.isBlank()) {
            NotificationHelper.showError(applicationContext, char, "API 未配置", notifId)
            return Result.failure()
        }

        // 修复⑩：userMsgId 在 try 外声明，catch 里可回滚孤立消息
        val userMsgId = UUID.randomUUID().toString()

        return try {
            // 查找最近活跃的会话
            val sessions = repo.loadSessions()
            val targetSession = sessions.maxByOrNull { it.lastMessageAt }?.id ?: "default"

            // Bug fix: 先加载历史，再保存用户消息；最后在 apiMsgs 中显式拼接一次。
            // 原写法：先 appendMessage → 再 loadMessages（历史已含用户消息）→ 再 buildMessages（历史包含）
            // → 再显式 append → 用户消息在请求中出现两次，AI 回复混乱。
            val historyLimit = repo.estimateHistoryLimit(config)
            val allMessages = repo.loadMessages(targetSession).takeLast(historyLimit)

            // 保存用户消息（在构建 API 请求之后才写入 DB，避免被 loadMessages 重复读取）
            repo.appendMessage(
                NyxMessage(
                    id = userMsgId,          // 使用 try 外声明的 ID，便于 catch 回滚
                    role = "user",
                    content = userMsg,
                    timestamp = System.currentTimeMillis(),
                    sessionId = targetSession,
                    isGroup = false
                )
            )

            // 构建 Pipeline Context
            val worldBook = repo.loadWorldBook()
            val triggered = triggerWorldBook(allMessages, worldBook, userMsg)
            // Bug 5 fix: 持久化触发条目的冷却索引。
            // Worker 无法访问 ChatViewModel.wbLastTriggered（内存 map），
            // 直接回写 lastTriggeredMsgIndex 并存盘，重启或切 session 后冷却仍然有效。
            if (triggered.isNotEmpty()) {
                val n = allMessages.size
                val triggeredIds = triggered.map { it.id }.toSet()
                repo.saveWorldBook(worldBook.map { entry ->
                    if (entry.id in triggeredIds) entry.copy(lastTriggeredMsgIndex = n) else entry
                })
            }
            val ctx = PipelineContext(
                char = char,
                allChars = repo.loadCharacters(),
                messages = allMessages,
                // Bug 5 fix: 原来直接传入 loadMemoriesForChar() 的 100 条原始数据，
                // 跳过了 PipelineOptimizer.selectMemoriesForInject()，
                // 导致 Sensitive 类型的 1.8x 权重对通知内联回复路径完全无效。
                memories = repo.loadMemoriesForChar(charId).let {
                    PipelineOptimizer.selectMemoriesForInject(it)
                },
                worldBook = worldBook,
                triggeredWorldBook = triggered,
                relationships = repo.loadRelationships()
                    .filter { it.fromCharId == charId || it.toCharId == charId },
                roundReplies = emptyList(),
                userInput = userMsg,
                isGroupChat = false,
                historyLimit = historyLimit,
                knownCharIds = emptySet(),
                // Bug 2 fix: 原来 userPersona / sceneState / isNarrativeMode 均未传入，
                // 导致通知内联回复里角色不知道用户名字、不遵循场景和旁白模式设置。
                userPersona     = repo.loadUserPersona(),
                sceneState      = repo.loadSceneState(),
                isNarrativeMode = false   // Worker 无法读取 ViewModel 内存态，默认关闭旁白
            )

            // 构建消息并调用 AI
            val apiMsgs = buildDefaultPipeline().buildMessages(ctx) +
                listOf(mapOf("role" to "user", "content" to userMsg))

            val rawReply = repo.callAI(config, apiMsgs, 300, char.temperature)

            // 剥离 output_directive 要求的 [mood:xxx] 心情标记，避免泄露进通知和 DB
            // Bug 1 fix: 改为带捕获组的正则，便于提取 mood 值后续写入 targetMood
            val moodRegex     = Regex("""\[mood:(\w+)\]""")
            val moodMatch     = moodRegex.find(rawReply)
            val extractedMood = moodMatch?.groupValues?.getOrNull(1)
            val reply         = rawReply.replace(moodRegex, "").trim()

            // 保存助手回复
            repo.appendMessage(
                NyxMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    charId = charId,          // Bug fix: 之前缺失 charId，消息无法关联到角色
                    content = reply,
                    timestamp = System.currentTimeMillis(),
                    sessionId = targetSession,
                    isGroup = false
                )
            )

            // 更新 char.lastActiveAt；Bug 1 fix：同步写入 AI 新指定的目标心情
            val nowMs    = System.currentTimeMillis()
            val allChars = repo.loadCharacters()
            repo.saveCharacters(allChars.map { c ->
                if (c.id == charId) c.copy(
                    lastActiveAt = nowMs,
                    targetMood   = extractedMood ?: c.targetMood   // Bug 1 fix：只在 AI 输出含 [mood:xxx] 时更新
                ) else c
            })

            // 更新会话时间戳
            val updatedSessions = sessions.map {
                if (it.id == targetSession) it.copy(lastMessageAt = nowMs) else it
            }
            repo.saveSessions(updatedSessions)

            // Bug 1 fix：关系追踪 —— 通知内联回复与 App 内对话同等对待，推进关系数值
            // 复用上方已加载的 allChars，不额外 load；不写 RelationshipLogEntry（Worker 无 ViewModel 状态）
            val exchangeLines = listOf("用户：$userMsg", "${char.name}：$reply")
            val existingRels  = repo.loadRelationships()
            val relUpdates    = RelationshipTracker.track(exchangeLines, allChars, existingRels)
            if (relUpdates.isNotEmpty()) {
                val updateMap  = relUpdates.associateBy { it.id }
                val mergedRels = existingRels.map { updateMap[it.id] ?: it } +
                                 relUpdates.filter { u -> existingRels.none { e -> e.id == u.id } }
                repo.saveRelationships(mergedRels)
            }

            // 显示回复通知
            NotificationHelper.showMessage(applicationContext, char, reply, notifId)

            Result.success()
        } catch (e: Exception) {
            // 修复⑩：AI 调用失败时，回滚已写入 DB 的孤立用户消息，保持对话历史完整。
            // runCatching 包裹删除操作，防止 deleteMessage 失败时掩盖原始异常。
            runCatching { repo.deleteMessage(userMsgId) }
            NotificationHelper.showError(applicationContext, char, "回复失败: ${e.message}", notifId)
            // 不使用 Result.retry()：重试会再次执行 appendMessage(用户消息)，导致重复记录。
            Result.failure()
        }
    }
}