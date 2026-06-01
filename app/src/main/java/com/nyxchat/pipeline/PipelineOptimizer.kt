package com.nyxchat.pipeline

import com.nyxchat.data.NyxCharacter
import com.nyxchat.data.NyxMemory
import com.nyxchat.data.NyxMessage

/**
 * PipelineOptimizer
 *
 * 无副作用的纯函数工具集，集中实现文档里的三类优化：
 *
 *  1. Token 预算截断历史  — selectHistoryByBudget()
 *  2. 外貌条件注入       — shouldInjectAppearance()

 *
 * 附加：
 *  - estimateTokens()         中文感知 token 估算（供 CharactersScreen 显示）
 *  - selectMemoriesForInject() 替换原有 importance>=3 过滤，使用衰减分排序结果

 *  - buildSilentCharSummary() 群聊沉默角色简化摘要
 *  - isMentioned()            @mention 检测（沉默角色临时升级触发条件）
 */
object PipelineOptimizer {

    // ── 预算常量 ────────────────────────────────────────────────────────────

    /** 系统 Prompt 可用 token 上限 */
    const val SYSTEM_BUDGET = 6_000

    /**
     * 历史消息可用 token 上限。
     * 固定条数截断的问题：同样 20 条，10-token 的「继续」和 400-token 的长描写差 40 倍。
     * 改用 token 预算后截断更精确。
     */
    const val HISTORY_BUDGET = 20_000

    /**
     * 注入记忆条数默认上限。
     * Bug 2 fix: 原来 selectMemoriesForInject() 硬编码 .take(MEMORY_INJECT_LIMIT=10)，
     * 导致 ctx.memories 最多只有 10 条，Phase 5 用户可调的 memoryInjectCount（5–50）
     * 在 pipeline stage 里 take(ctx.memoryInjectCount) 永远取不到超过 10 条的结果。
     * 修复方案：函数改为接收 limit 参数，调用方（主对话路径）传入 _memoryInjectCount.value；
     * Worker / debug 路径不传参时沿用此默认值。
     */
    const val MEMORY_INJECT_LIMIT = 15  // Phase 1 对齐值，Worker 路径的默认注入上限

    /** 活跃窗口：最近这么多条消息永远不参与截断 */
    const val PROTECTED_TAIL = 20

    // ── 外貌关键词表 ────────────────────────────────────────────────────────

    private val APPEARANCE_KEYWORDS = setOf(
        // 外貌直述
        "外貌", "长相", "样子", "好看", "漂亮", "美丽", "帅气", "颜值",
        // 服装
        "穿", "衣服", "裙子", "上衣", "衣着", "穿着", "装扮", "打扮", "服装", "换衣", "换装",
        // 身体部位
        "头发", "发色", "发型", "眼睛", "瞳色", "瞳孔", "肤色", "皮肤",
        "身材", "体型", "胸", "腰", "腿", "手", "脸", "嘴", "鼻",
        // 场景触发
        "镜子", "倒影", "照镜", "看起来", "看上去", "描述一下", "什么样子", "长什么",
        // 英文兜底
        "appearance", "look", "looks", "wear", "wearing", "dress", "hair", "eyes", "outfit"
    )

    // ────────────────────────────────────────────────────────────────────────
    // 1. Token 估算
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 中文感知 token 估算。
     *
     * 中文/全角字符  ≈ 1.5 tokens/字（一个汉字通常对应 1–2 个 token）
     * ASCII 及其他  ≈ 0.25 tokens/字符（约 4 个字母/数字 = 1 token）
     *
     * 误差范围 ±15%，足够用于预算截断和 UI 警告显示。
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var chinese = 0
        var other   = 0
        for (ch in text) {
            when (ch) {
                in '\u4e00'..'\u9fff',   // CJK 统一汉字
                in '\u3000'..'\u303f',   // CJK 符号和标点
                in '\uff00'..'\uffef',   // 全角字母数字
                in '\u2e80'..'\u2eff',   // CJK 部首补充
                in '\u31c0'..'\u31ef'    // CJK 笔画
                -> chinese++
                else -> other++
            }
        }
        return (chinese * 1.5 + other * 0.25).toInt().coerceAtLeast(if (text.isNotEmpty()) 1 else 0)
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. 历史截断（Token 预算版）
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 按 token 预算从最新消息向前贪心选取历史，替代固定条数截断。
     *
     * 策略：
     *  - 最近 [protectedTail] 条永远保留（活跃窗口，保证上下文连贯）
     *  - 更早的消息从最近到最远逐条尝试加入，超出 [tokenBudget] 即停止
     *
     * 调用方只需把这个结果直接传给 buildMessages()，不需要再做 takeLast()。
     */
    fun selectHistoryByBudget(
        messages: List<NyxMessage>,
        tokenBudget: Int = HISTORY_BUDGET,
        protectedTail: Int = PROTECTED_TAIL
    ): List<NyxMessage> {
        if (messages.isEmpty()) return emptyList()

        val tail  = messages.takeLast(protectedTail)
        val older = messages.dropLast(protectedTail)

        var used = tail.sumOf { estimateTokens(it.content) }
        val extra = mutableListOf<NyxMessage>()

        for (msg in older.asReversed()) {          // 从最近到最远
            val cost = estimateTokens(msg.content)
            if (used + cost > tokenBudget) break   // 超出则停止
            extra.add(0, msg)
            used += cost
        }
        return extra + tail
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. 外貌条件注入
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 判断本轮用户输入是否应触发外貌描述注入。
     *
     * 外貌字段平时节省 100–300 tokens，只在用户真正问到时才注入，
     * 避免角色在换衣、镜子等场景下胡编。
     */
    fun shouldInjectAppearance(userInput: String): Boolean =
        APPEARANCE_KEYWORDS.any { kw -> userInput.contains(kw, ignoreCase = true) }

    // ────────────────────────────────────────────────────────────────────────
    // 5. 记忆注入
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 从 [getForCharDecayed] 的衰减排序结果中选取注入条目。
     *
     * 原有逻辑：importance >= 3 的全集  → 旧记忆永远占位
     * 新逻辑：取衰减分最高的前 N 条     → 旧记忆自动衰减，近期记忆自然浮上来
     *
     * 步骤13：Sensitive 记忆额外乘以权重系数 1.8，确保衰减后仍优先保留
     *
     * 调用方示例：
     *   val decayed = repo.db.memoryDao().getForCharDecayed(charId, System.currentTimeMillis())
     *   val toInject = PipelineOptimizer.selectMemoriesForInject(decayed)
     */
    fun selectMemoriesForInject(
        decayedMemories: List<NyxMemory>,  // 已按衰减分降序排列
        limit: Int = MEMORY_INJECT_LIMIT   // Bug 2 fix: 由调用方传入，主对话路径传 _memoryInjectCount.value
    ): List<NyxMemory> {
        // Bug B fix: 原实现按 importance * multiplier 重新排序，完全丢弃了 DB 返回的时间衰减分。
        // 后果：久远的高重要度记忆永远排在最前，近期低重要度记忆始终被压在后面，
        // getForCharDecayed 按衰减分排序的用意被完全推翻。
        // 修正：以与 DB 相同的衰减公式（importance / (1 + days_old * 0.05)）
        //       再乘以 Sensitive 系数，使两个维度都真实生效。
        val SENSITIVE_MULTIPLIER = 1.8
        val nowMs = System.currentTimeMillis()
        return decayedMemories
            .sortedByDescending { mem ->
                val daysOld = (nowMs - mem.timestamp) / 86_400_000.0
                val decayScore = mem.importance / (1.0 + daysOld * 0.05)
                val multiplier = if (mem.type == "Sensitive") SENSITIVE_MULTIPLIER else 1.0
                decayScore * multiplier
            }
            .take(limit)  // Bug 2 fix: 原来 .take(MEMORY_INJECT_LIMIT) 硬编码 10，现在由调用方控制
    }

    // ────────────────────────────────────────────────────────────────────────
    // 6. 群聊角色简化
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 群聊中沉默角色的单行简介，节省 60–80% tokens。
     * 被 @mention 时调用方应切回完整卡片注入。
     */
    fun buildSilentCharSummary(char: NyxCharacter): String {
        val desc = char.traits.take(60).ifBlank { char.background.take(60) }
        return "${char.name}：${desc.ifBlank { "（无描述）" }}"
    }

    /**
     * 判断消息是否 @mention 了指定角色（沉默角色升级触发条件）。
     *
     * Bug fix: 原实现 `message.contains(charName)` 对短名字存在严重误匹配：
     *   - 角色「明」会匹配「明天」「明白」「聪明」等任意含「明」的句子
     *   - 角色「露娜」（2字）会匹配「露出」「娜拉」等词
     * 修改策略：
     *   1. @mention（@角色名）始终触发，无长度限制
     *   2. 裸名匹配只对 ≥ 3 字的名字生效，避免 CJK 子串误伤
     *      （本项目角色名普遍为 2 字，需要升级时用 @mention 即可）
     */
    fun isMentioned(message: String, charName: String): Boolean {
        if (message.contains("@$charName")) return true
        // 仅对 3 字及以上名字做裸名匹配，避免 CJK 子串误匹配
        if (charName.length >= 3) return message.contains(charName)
        return false
    }
}
