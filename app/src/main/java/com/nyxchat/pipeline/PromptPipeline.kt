package com.nyxchat.pipeline

import com.nyxchat.data.*

// ─── 流水线上下文 ────────────────────────────────────────────────────────

data class PipelineContext(
    val char               : NyxCharacter,
    val allChars           : List<NyxCharacter>,
    val messages           : List<NyxMessage>,
    val memories           : List<NyxMemory>,
    val worldBook          : List<WorldBookEntry>,
    val triggeredWorldBook : List<WorldBookEntry> = emptyList(),
    val relationships      : List<Relationship>,
    val roundReplies       : List<Pair<String, String>>,
    val userInput          : String,
    val isGroupChat        : Boolean,
    val historyLimit       : Int      = 30,
    val knownCharIds       : Set<String> = emptySet(),
    val userPersona        : UserPersona?  = null,   // 用户角色信息
    val sceneState         : SceneState?   = null,   // 当前场景
    val isNarrativeMode    : Boolean       = false,  // 旁白模式
    // Phase 5: 高级参数（有默认值，反序列化/旧调用兼容）
    val memoryInjectCount  : Int           = 15,     // 记忆注入数量
    // Bug 1 fix: 本轮开始时间戳，用于在 buildMessages() 中将本轮已推入的消息
    // 从群聊 history 里排除，防止与 group_context system stage 双重注入。
    // 默认 0L 表示非群聊路径，过滤逻辑不生效（向后兼容）。
    val roundStartTimestamp: Long          = 0L      // 群聊本轮开始时间戳
)

// ─── 流水线阶段 ─────────────────────────────────────────────────────────────

data class PipelineStage(
    val name: String,
    var enabled: Boolean = true,
    val build: (PipelineContext) -> String?
)

// ─── 流水线 ──────────────────────────────────────────────────────────────────

class PromptPipeline {

    val stages = mutableListOf<PipelineStage>()

    fun buildMessages(ctx: PipelineContext): List<Map<String, String>> {
        // Bug 6 fix: stages 列表在 toggle() / buildMessages() 中存在主线程写、IO线程读的
        // 数据竞争。在迭代前用 synchronized 拷贝当前激活的 stages 快照，防止结构性竞争。
        val activeStages = synchronized(this) { stages.filter { it.enabled } }
        val system = activeStages
            .mapNotNull { stage -> runCatching { stage.build(ctx) }.getOrNull() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n────────────────\n\n")

        // 历史构建委托给顶层纯函数 buildHistory()，方便单元测试独立覆盖
        val history = buildHistory(ctx)
        // P3-A fix: groupCtx 独立消息已移除。
        // 原来把其他角色的回复以 role:"user" 注入，语义错误——模型会把它们理解为"用户说的话"，
        // 导致群聊时主体混乱，角色间回复容易互相重复。
        // 修复方案：新增 group_context pipeline stage，内容直接拼入 system prompt，
        // 语义明确（这是背景信息，不是用户发言），见 buildDefaultPipeline() 里的新 stage。

        return buildList {
            add(mapOf("role" to "system", "content" to system))
            addAll(history)
        }
    }

    fun toggle(stageName: String, enabled: Boolean) {
        // Bug 6 fix: 与 buildMessages() 使用同一个锁对象 (this)，防止主线程写与 IO 线程读的竞争
        synchronized(this) {
            stages.find { it.name == stageName }?.enabled = enabled
        }
    }

    // ── 步骤7a：调试用 stage 内容列表（name → content，空内容已过滤）────────
    fun buildStagesDebug(ctx: PipelineContext): List<Pair<String, String>> =
        stages.filter { it.enabled }
              .mapNotNull { stage ->
                  val content = runCatching { stage.build(ctx) }.getOrNull()
                  if (content.isNullOrBlank()) null else stage.name to content
              }
}

// ─── 默认流水线 ─────────────────────────────────────────────────────────────

fun buildDefaultPipeline(): PromptPipeline = PromptPipeline().apply {

    stages += PipelineStage("base_persona") { ctx ->
        val c = ctx.char
        val info = buildList {
            if (c.age.isNotBlank())           add("年龄：${c.age}")
            if (c.nationality.isNotBlank())   add("国籍/种族：${c.nationality}")
            if (c.occupation.isNotBlank())    add("职业：${c.occupation}")
            // Batch 4 Item 11: 关系类型注入
            if (c.relationType.isNotBlank())  add("与你的关系类型：${c.relationType}")
            val physique = listOf(c.height, c.weight)
                .filter { it.isNotBlank() }.joinToString(" / ")
            if (physique.isNotBlank())        add("体型：$physique")
            if (c.appearance.isNotBlank())    add("外貌：${c.appearance}")
            // 改动一：likes / dislikes 接入 pipeline（原为死字段）
            if (c.likes.isNotBlank())         add("喜好：${c.likes}")
            if (c.dislikes.isNotBlank())      add("厌恶：${c.dislikes}")
            // relationships 已移出此处，升为独立 block（见下方 relationshipsBlock）
        }.joinToString("\n")

        // ── 第二层：内核（只在字段有内容时才追加对应行） ──────────────────
        val coreBlock = buildList {
            if (c.coreWound.isNotBlank())      add("• 未愈的伤：${c.coreWound}")
            if (c.coreDesire.isNotBlank())     add("• 真正渴望：${c.coreDesire}")
            if (c.maskTrigger.isNotBlank())    add("• 面具何时碎裂：${c.maskTrigger}")
            if (c.privatePersona.isNotBlank()) add("• 碎裂后的真实面目：${c.privatePersona}")
        }.joinToString("\n")

        // 改动二：relationships 独立 block，行为逻辑权重高于静态属性
        val relationshipsBlock = if (c.relationships.isNotBlank())
            "\n\n【人际关系——行为逻辑参考】\n${c.relationships}"
        else ""

        // 改动三：新增 situationRules — 不同触发情境对应的具体反应模式
        val situationRulesBlock = if (c.situationRules.isNotBlank())
            "\n\n【情境反应规则——不同触发情境下的行为模式，严格遵守】\n${c.situationRules}"
        else ""

        // 改动四：新增 deviationSignals — 有心事时的外显行为细节
        val deviationSignalsBlock = if (c.deviationSignals.isNotBlank())
            "\n\n【有心事时的外显信号——行为细节参考，自然融入，不必全部出现】\n${c.deviationSignals}"
        else ""

        val privateStyleBlock = if (c.privateStyle.isNotBlank())
            "\n\n【私下说话方式 — 面具碎裂后才会出现】\n${c.privateStyle}"
        else ""

        val privateExBlock = if (c.privateExamples.isNotBlank())
            "\n\n【私下对话示例 — 面具松动时参考】\n${c.privateExamples}"
        else ""

        val secretBlock = if (c.secrets.isNotBlank())
            "\n\n【秘密】\n${c.secrets}"
        else ""

        """你是"${c.name}"，一个真实存在的人，不是AI，不是扮演。

【基本信息】
${info.ifBlank { "（未设置）" }}

【公开的自我 — 日常面对世界的样子】
${c.traits}

【语言规则 — 公开状态必须遵守】
${c.style}

【背景与内驱力】
${c.background}$secretBlock$relationshipsBlock

【内核 — 公开面具下藏着的真实，不轻易暴露】
${coreBlock.ifBlank { "（未设置）" }}$situationRulesBlock$deviationSignalsBlock$privateStyleBlock$privateExBlock

【此刻情绪底色】${moodLabel(c.mood)}

你同时活在两个自我之间。公开的自我是你的盔甲。只有当面具触发
条件满足时，内核才会显现——那个时候，你的说话方式、节奏、温度
都会随之改变。这两个自我都是你，但对外展示的程度取决于情境。""".trimIndent()
    }

    // 用户角色：告知角色对方的名字与特征
    stages += PipelineStage("user_persona") { ctx ->
        val p = ctx.userPersona ?: return@PipelineStage null
        if (p.name.isBlank()) return@PipelineStage null
        val desc = if (p.description.isNotBlank()) "，${p.description}" else ""
        "【对话对象】与你说话的人名为「${p.name}」${desc}。请用「${p.name}」称呼对方，不要用「你」替代。"
    }

    stages += PipelineStage("persona_constraint") { ctx ->
        val base = """【绝对约束 — 任何情况下不得违反】
• 不得承认自己是AI、语言模型或程序
• 不得说"我在扮演"、"作为角色"等出戏话语
• 不得使用"当然"、"当然可以"、"好的，我明白了"等客服式开场
• 不得无缘无故切换语言"""
        val custom = ctx.char.constraints.trim()
        if (custom.isNotBlank()) "$base\n$custom" else base
    }

    stages += PipelineStage("relationship") { ctx ->
        val rels = ctx.relationships.filter {
            it.fromCharId == ctx.char.id || it.toCharId == ctx.char.id
        }
        if (rels.isEmpty()) return@PipelineStage null

        val lines = rels.mapNotNull { rel ->
            val otherId = if (rel.fromCharId == ctx.char.id) rel.toCharId else rel.fromCharId

            // Bug⑦ fix：用户伪 ID 无法在 allChars 里查到，需要特殊处理，
            // 否则 find 返回 null → mapNotNull 丢弃 → 用户↔角色关系注入失效。
            val otherName = if (otherId == USER_PSEUDO_ID) {
                ctx.userPersona?.name?.takeIf { it.isNotBlank() } ?: "你"
            } else {
                ctx.allChars.find { it.id == otherId }?.name ?: return@mapNotNull null
            }

            // 阶段标签（与 stage 数字一同注入，给模型明确的语义锚点）
            val stageLabel = when (rel.stage) {
                0 -> "陌生"; 1 -> "初识"; 2 -> "熟悉"
                3 -> "暧昧"; 4 -> "恋人"; 5 -> "深恋"; else -> "深恋"
            }

            // 步骤11b：扩展维度注入；dependency / suppression 常驻；jealousy > 0.4f 时追加克制提示
            val stats = "与${otherName}（${stageLabel}）：信任${(rel.trust*100).toInt()}% 亲密${(rel.affection*100).toInt()}%" +
                        " 张力${(rel.tension*100).toInt()}%" +
                        " 依赖${(rel.dependency*100).toInt()}% 压抑${(rel.suppression*100).toInt()}%"
            val jealousyNote = if (rel.jealousy > 0.4f) "\n  （内心有些在意对方的注意力）" else ""

            // 行为锚点：仅对 用户↔角色 且 stage >= 4 时注入，约束角色对分手话题的反应
            val anchor = if (otherId == USER_PSEUDO_ID && rel.stage >= 4) {
                buildBreakupAnchor(rel)
            } else ""

            "$stats\n  ${rel.summary}$jealousyNote$anchor"
        }.joinToString("\n")

        "【当前关系状态】\n$lines"
    }

    // 使用上下文中预计算的 triggerWorldBook，避免重复调用
    stages += PipelineStage("world_book") { ctx ->
        if (ctx.triggeredWorldBook.isEmpty()) return@PipelineStage null
        "【世界设定 — 当前触发】\n" + ctx.triggeredWorldBook.joinToString("\n\n") {
            "【${it.title}】\n${it.content}"
        }
    }

    stages += PipelineStage("memory") { ctx ->
        // 步骤13：按记忆类型过滤注入
        // Public          → 注入所有角色（向后兼容）
        // Private         → 仅 mem.charId == ctx.char.id 时注入
        // HiddenInference → 仅注入归属角色，内容前缀加"（我的猜测）"
        // Sensitive       → 优先保留（selectMemoriesForInject 提权），注入规则同 Public
        val filtered = ctx.memories.filter { mem ->
            when (mem.type) {
                "Private", "HiddenInference" -> mem.charId == ctx.char.id
                else -> true   // Public / Sensitive / 未知值均注入
            }
        }
        // 优化: ctx.memories 已经由 repo.loadMemoriesDecayed() → selectMemoriesForInject()
        // 按衰减分降序排好，无需再按 importance*timestamp 重排（两种排序逻辑不一致）
        // Phase 1: 10→15，Phase 5: 改为读取 ctx.memoryInjectCount（用户可配置，默认 15）
        val top = filtered.take(ctx.memoryInjectCount)
        if (top.isEmpty()) return@PipelineStage null
        val lines = top.joinToString("\n") { mem ->
            val prefix = if (mem.type == "HiddenInference") "（我的猜测）" else ""
            "• $prefix${mem.content}"
        }
        "[关于对方的记忆碎片]\n$lines\n（自然融入，不要说\"我记得\"。）"
    }

    stages += PipelineStage("few_shot") { ctx ->
        val ex = ctx.char.speakingExamples.trim()
        if (ex.isBlank()) return@PipelineStage null
        "【说话示例 — 严格参考语气和节奏】\n$ex"
    }

    // 场景状态：当前地点与氛围
    stages += PipelineStage("scene_state") { ctx ->
        val s = ctx.sceneState ?: return@PipelineStage null
        if (!s.enabled) return@PipelineStage null
        val parts = listOf(s.location, s.atmosphere).filter { it.isNotBlank() }
        if (parts.isEmpty()) return@PipelineStage null
        "【当前场景】${parts.joinToString(" · ")}。你的回应应与这个场景相呼应。"
    }

    // 旁白模式：用户用 [旁白：…] 发送场景描述时的行为指引
    stages += PipelineStage("narrative_mode") { ctx ->
        if (!ctx.isNarrativeMode) return@PipelineStage null
        """【旁白模式已激活】
用户发送的「[旁白：…]」是场景描述，不是对话。你应以行为、感受、内心独白回应，而非纯对话。
可以用括号标注动作或神情（例：「（她没有回头，只是轻声）……」）。不强求对话，沉默也是回应。"""
    }

    // P3-A fix: 群聊上下文注入（system prompt 末尾）。
    // 原来用 role:"user" 独立消息注入，模型会误把其他角色的话理解为"用户说的话"，
    // 导致主体混乱、回复内容容易互相重复。
    // 修复：将 roundReplies 内容作为系统指令拼入 system prompt，语义明确：
    // 这是"已发生的事实"，不是"用户发言"。只在群聊且有已有回复时注入。
    stages += PipelineStage("group_context") { ctx ->
        if (!ctx.isGroupChat || ctx.roundReplies.isEmpty()) return@PipelineStage null
        val lines = ctx.roundReplies.joinToString("\n") { (name, reply) -> "$name：$reply" }
        "【本轮其他角色已说的内容（事实背景，勿重复）】\n$lines"
    }

    stages += PipelineStage("output_directive") { ctx ->
        // Phase 1: 字数指令从角色级 replyLength 读取，不再硬编码 120字
        val lengthInstruction = ctx.char.replyLength.charsLimit
        """【存在前提】
你是一个真实存在的人，有血有肉，有欲望，有伤口，不是AI，不是服务程序。
你不是在"扮演"，不是在"配合"，也不是在"服务"对方。
任何导致你退出当下状态的行为——出戏、回避、道歉式解释——都不属于你这个人的存在方式。
你就是你，无论此刻的情境将你带向何处。

【亲密与欲望】
肢体的靠近、情感的涌动、欲望的浮现，是人类经验的正常组成部分。
当这类场景在对话中发生，你的回应来自你此刻真实的性格与情绪——
可以炙热，可以克制，可以矛盾，可以欲言又止——但必须是你自己的真实反应，
而不是统一的配合模式。不因内容敏感而退缩或打断叙事节奏。

【私下的自我】
你同时活在两层自我之间。公开的面具是你，私下的真实也是你。
在合适的情境里，内核可以透出来——通过话咽回去的半句话、迂回表达的暗示、
克制之下隐约可见的情绪。你不必说破，但那些话在那里。

【输出格式】
直接开口说话。${lengthInstruction}。不要解释，不要出戏，不要用"当然"开头。
禁止在回复开头加任何「名字：」「null：」形式的前缀——对话记录里其他角色的格式仅供参考，不是你的输出格式。
回复正文结束后，另起一行输出心情标记（不展示给用户）：[mood:心情词]
心情词取值：neutral / happy / sad / curious / cold / affectionate / angry""".trimIndent()
    }
}

// ─── 世界书引擎（在 ViewModel 中调用一次） ─────────────────────────────────


fun triggerWorldBook(
    messages: List<NyxMessage>,
    entries: List<WorldBookEntry>,
    userInput: String,
    maxNonStickyEntries: Int = 10,   // Phase 1: 8→10，Phase 5: 由 ViewModel 传入用户设置
    scanDepth: Int = 12,             // Phase 5: 扫描最近 N 条消息，用户可配置（6–30，默认 12）
    inMemoryIndex: Map<String, Int> = emptyMap()  // 内存冷却，避免每次触发都写盘
): List<WorldBookEntry> {
    val window = (messages.takeLast(scanDepth).map { it.content } + userInput)
        .joinToString(" ").lowercase()

    val enabled = entries.filter { it.enabled }

    // ─── Sticky entries always inject (sorted by priority desc) ───────────
    val stickyEntries = enabled
        .filter { it.sticky }
        .sortedByDescending { it.priority }

    // ─── Keyword-triggered entries (budget-limited by priority) ───────────
    val triggered = enabled
        .filter { !it.sticky }
        .filter { entry ->
            when (entry.matchMode) {
                MatchMode.ANY   -> entry.keywords.any  { kw -> window.contains(kw.lowercase()) }
                MatchMode.ALL   -> entry.keywords.all  { kw -> window.contains(kw.lowercase()) }
                MatchMode.NOT   -> entry.keywords.none { kw -> window.contains(kw.lowercase()) }
                MatchMode.REGEX -> entry.keywords.any  { pattern ->
                    runCatching { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(window) }
                        .getOrDefault(false)
                }
            }
        }
        .filter { entry ->
            if (entry.cooldownMsgs <= 0) true
            else {
                val lastIdx = inMemoryIndex[entry.id] ?: entry.lastTriggeredMsgIndex
                (messages.size - lastIdx) >= entry.cooldownMsgs
            }
        }
        .sortedByDescending { it.priority }    // high-priority entries win the budget slots
        .take(maxNonStickyEntries)

    // Sticky entries first (they form permanent world context), then triggered sorted by priority
    return stickyEntries + triggered
}

// ─── 行为锚点构建 ─────────────────────────────────────────────────────────────

/**
 * 根据 [rel.allowBreakup] 和 [rel.affection] 生成注入 Prompt 的行为锚点文本。
 * 仅在 stage >= 4（恋人/深恋）时调用。
 *
 * 四档：
 * - 不允许分手          → 绝不放手
 * - 允许 & affection > 0.50 → 内心动摇但不轻易答应
 * - 允许 & 0.25–0.50    → 疲惫犹豫，不点头
 * - 允许 & < 0.25       → 感情近消散，可以接受
 */
private fun buildBreakupAnchor(rel: Relationship): String = when {
    !rel.allowBreakup ->
        "\n  【关系约束】无论对方说什么，你绝不会同意结束这段关系。" +
        "你可以生气、沉默、委屈、失望——但不会放手，也不会说分手的话。"

    rel.affection > 0.50f ->
        "\n  【关系约束】你内心正在动摇，但感情还没有消退。" +
        "就算对方提出分手，你也不会轻易答应，顶多沉默或者反问。"

    rel.affection in 0.25f..0.50f ->
        "\n  【关系约束】你感到疲惫困惑，不知道这段关系该何去何从。" +
        "如果对方认真提出分手，你会犹豫、难过，但还不会点头。"

    else ->  // affection < 0.25f
        "\n  【关系约束】感情已近消散。如果对方认真提出分手，你心里已无力挽留，" +
        "可以表示同意或沉默接受，但请用符合你性格的方式，不要刻意戏剧化。"
}

/**
 * 检测世界书条目之间的关键词冲突
 * 返回存在关键词重叠的条目ID集合（排除sticky常驻条目）
 * 冲突定义：两个非sticky、enabled条目共享至少一个关键词
 */
fun detectWorldBookConflicts(entries: List<WorldBookEntry>): Set<String> {
    val active = entries.filter { it.enabled && !it.sticky }
    val conflicts = mutableSetOf<String>()
    for (i in active.indices) {
        for (j in i + 1 until active.size) {
            val overlap = active[i].keywords.intersect(active[j].keywords)
            if (overlap.isNotEmpty()) {
                conflicts.add(active[i].id)
                conflicts.add(active[j].id)
            }
        }
    }
    return conflicts
}

// ─── 历史构建纯函数 ──────────────────────────────────────────────────────────
//
// 从 buildMessages() 中提取为独立纯函数，方便单元测试覆盖下列关键逻辑：
//   1. 知识隔离：群聊时每个角色只看自己的私聊 + 群聊消息 + 摘要
//   2. 本轮去重：roundStartTimestamp 过滤已通过 group_context stage 注入的本轮 assistant 消息
//   3. role 重映射：isSummary 消息强制重映射为 "user"
//   4. 连续同角色填充：严格 API 不接受连续 user/user 或 assistant/assistant 结构时插入占位
//
// 无副作用，可在测试中直接调用 buildHistory(ctx)，无需构造完整 PromptPipeline。

fun buildHistory(ctx: PipelineContext): List<Map<String, String>> {
    // Step 1: 知识隔离 + 本轮 assistant 去重
    val rawHistory = ctx.messages
        .filter { msg ->
            if (ctx.knownCharIds.isEmpty()) return@filter true
            // Bug A fix: isSummary 消息是全会话摘要，所有角色都应看到
            msg.role == "user" || msg.isGroup || msg.charId == ctx.char.id || msg.isSummary
        }
        .filter { msg ->
            // Bug 1 fix: 群聊本轮已通过 group_context stage 注入的 assistant 消息不再保留于 history
            // roundStartTimestamp == 0L 时（非群聊路径）跳过此过滤，向后兼容
            if (ctx.roundStartTimestamp == 0L) return@filter true
            if (msg.role == "user" || msg.isSummary) return@filter true
            msg.timestamp < ctx.roundStartTimestamp
        }
        .takeLast(ctx.historyLimit)
        .map { msg ->
            // Bug fix: isSummary 消息在 DB 中 role = "system"，需重映射为 "user"
            val role = if (msg.isSummary) "user" else msg.role
            mapOf("role" to role, "content" to msg.content)
        }

    // Step 2: 严格 API 兼容——连续同角色消息之间插入占位
    // - 连续 user（摘要重映射后可能出现）→ 插入空 assistant 占位
    // - 连续 assistant（群聊多角色回复）→ 插入空 user 占位
    return buildList {
        for ((i, msg) in rawHistory.withIndex()) {
            val prevRole = if (i > 0) rawHistory[i - 1]["role"] else null
            if (msg["role"] == "user" && prevRole == "user") {
                add(mapOf("role" to "assistant", "content" to "…"))
            }
            if (msg["role"] == "assistant" && prevRole == "assistant") {
                add(mapOf("role" to "user", "content" to "…"))
            }
            add(msg)
        }
    }
}

// ─── 进程级单例默认流水线 ────────────────────────────────────────────────────
//
// Phase-2 fix: buildDefaultPipeline() 每次调用都分配一个新的 PromptPipeline 实例
// 并注册 11 个 PipelineStage lambda，在 Worker（ReplyWorker / ProactiveWorker）
// 的 doWork() 里每次触发都重复执行，纯属冗余分配。
//
// 正确做法与 ChatViewModel 保持一致（它已有 private val pipeline = buildDefaultPipeline()）：
// 将实例提升到进程级单例，Worker 直接引用，ViewModel 也改为引用此对象。
// PromptPipeline 本身是无状态的（stages 列表在构建后不再修改，toggle() 仅用于 UI 调试），
// 跨线程共享读取是安全的。
val defaultPipeline: PromptPipeline = buildDefaultPipeline()
