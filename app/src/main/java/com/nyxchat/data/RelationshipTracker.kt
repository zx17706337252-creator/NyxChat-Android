package com.nyxchat.data

/**
 * 关系追踪器
 * 分析对话交换片段，对所有涉及的角色对返回关系增量更新列表。
 * 传入 [existing] 后在其基础上累加，而不是每次用硬编码初始值覆盖。
 */
object RelationshipTracker {

    /**
     * 分析 [exchangeLines]（"用户：xxx"、"角色名：xxx" 格式行），
     * 针对 [characters] 中每对 isActive 角色构建并返回关系更新列表。
     * [existing] 为当前已保存的关系列表，缺失时才用默认初始值。
     */
    fun track(
        exchangeLines: List<String>,
        characters: List<NyxCharacter>,
        existing: List<Relationship> = emptyList()
    ): List<Relationship> {
        if (exchangeLines.size < 2) return emptyList()

        val activeChars = characters.filter { it.isActive }
        if (activeChars.isEmpty()) return emptyList()

        // ── Bug⑦ fix：单角色模式 — 追踪 角色↔用户 关系 ─────────────────────
        // 原来 size < 2 直接返回 emptyList()，导致单角色对话的关系值永远不更新。
        if (activeChars.size == 1) {
            val char = activeChars[0]
            val combined = exchangeLines.joinToString(" ")
            val prevRel = existing.find { it.id == "${char.id}_${USER_PSEUDO_ID}" }
            // 摩擦系数注入：传入当前 stage，让越深的关系越难快速变化
            val delta = inferDelta(combined, prevRel?.stage ?: 0)
            val updated = (prevRel ?: newUserCharRelationship(char.id)).applyDelta(delta)
            return listOf(updated)
        }
        // ─────────────────────────────────────────────────────────────────────

        val updates = mutableListOf<Relationship>()

        // ── Bug 1 fix：多角色模式也需要追踪 用户↔角色 关系 ─────────────────────
        // 原来多角色分支只有角色间双重循环，用户方向完全被忽略，
        // 导致群聊模式下「我与角色的关系」区永远显示初始默认值。
        // 修复：对每个"在本轮有台词"的角色，同样计算一次用户↔角色的增量。
        val combined = exchangeLines.joinToString(" ")   // 仅供下方角色间双重循环使用
        activeChars.forEach { char ->
            // 只要该角色在本轮有一行台词（"角色名："开头），就视为交互发生
            val charReplied = exchangeLines.any { it.startsWith("${char.name}：") }
            if (charReplied) {
                // Bug #4 fix：只用「用户行 + 该角色行」计算用户↔角色增量，
                // 避免其他角色的台词内容"共沾"，影响本角色与用户的关系值。
                val charSpecificText = exchangeLines.filter { line ->
                    line.startsWith("用户：") || line.startsWith("${char.name}：")
                }.joinToString(" ")
                val prevRel = existing.find { it.id == "${char.id}_${USER_PSEUDO_ID}" }
                val delta   = inferDelta(charSpecificText, prevRel?.stage ?: 0)
                val updated = (prevRel ?: newUserCharRelationship(char.id)).applyDelta(delta)
                updates.add(updated)
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        for (i in activeChars.indices) {
            for (j in i + 1 until activeChars.size) {
                val a = activeChars[i]
                val b = activeChars[j]

                // Bug 6 fix：与 PipelineOptimizer.isMentioned() 对齐，contains 判断前加
                // 名字长度守卫（>= 3），防止「露」「明」等双字及单字短名在
                // 「暴露」「揭露」「天明」等词中误命中，触发不必要的关系更新。
                val aMentioned = exchangeLines.any { line ->
                    line.startsWith("${a.name}：") ||
                    (a.name.length >= 3 && line.contains(a.name))
                }
                val bMentioned = exchangeLines.any { line ->
                    line.startsWith("${b.name}：") ||
                    (b.name.length >= 3 && line.contains(b.name))
                }

                if (aMentioned && bMentioned) {
                    // 每个方向使用各自的 stage，摩擦系数独立计算
                    val prevAB = existing.find { it.id == "${a.id}_${b.id}" }
                    val prevBA = existing.find { it.id == "${b.id}_${a.id}" }

                    // Bug 5 fix：角色间推断只用双方自己的台词，排除用户行和第三方角色行，
                    // 避免「用户：我爱露娜」这类台词污染 露娜↔明媚 之间的关系值。
                    val abSpecificText = exchangeLines.filter { line ->
                        line.startsWith("${a.name}：") || line.startsWith("${b.name}：")
                    }.joinToString(" ")
                    val deltaAB = inferDelta(abSpecificText, prevAB?.stage ?: 0)
                    val deltaBA = inferDelta(abSpecificText, prevBA?.stage ?: 0)

                    updates.add((prevAB ?: Relationship("${a.id}_${b.id}", a.id, b.id)).applyDelta(deltaAB))
                    updates.add((prevBA ?: Relationship("${b.id}_${a.id}", b.id, a.id)).applyDelta(deltaBA))
                }
            }
        }
        return updates
    }

    /**
     * 从对话文本推断关系增量（纯启发式，无需 AI 调用），并应用摩擦系数。
     * [stage] 决定摩擦强度：阶段越深，每轮变化量越小。
     */
    private fun inferDelta(text: String, stage: Int = 0): RelationshipDelta {
        val t = text.lowercase()
        // 正向信号：亲密 / 信任 / 欢乐
        val positive  = listOf("谢谢", "感谢", "开心", "喜欢", "爱", "好的", "当然", "放心", "支持", "帮助")
        // 负向信号：冲突 / 张力
        val negative  = listOf("生气", "讨厌", "不行", "不想", "走开", "烦", "吵", "争", "怒", "失望")
        // 信任信号
        val trustWords = listOf("相信", "信任", "秘密", "答应", "承诺", "保证", "真的")

        val posCount   = positive.count  { t.contains(it) }
        val negCount   = negative.count  { t.contains(it) }
        val trustCount = trustWords.count { t.contains(it) }

        // ── suppression 自动步进 ────────────────────────────────────────────────
        // suppression 代表「情感压抑度」：0=完全压抑，1=完全释放。
        // 设计原则：压抑感随关系深入和情感流动自然松动，但以非常小的幅度缓慢变化，
        // 不应快速归零（面具碎裂是高光时刻，不是常态）。
        //
        // 释放信号：深度信任词 + 情感直白词（「想你」「心跳」「在乎」等）
        // 压抑信号：冲突或角色主动退缩（「不想说」「随便」「算了」等）
        // 摩擦：与 affection 共享 posMultiplier/negMultiplier 后再缩减至 0.3x，
        //       确保 suppression 的自动变化速率远慢于 affection。
        val releaseWords   = listOf("想你", "心跳", "在乎", "靠近", "需要你", "想说", "说出来", "放开", "表达")
        val suppressWords  = listOf("不想说", "随便", "算了", "没事", "不重要", "闭嘴", "别问", "沉默")
        val releaseCount   = releaseWords.count  { t.contains(it) }
        val suppressCount  = suppressWords.count { t.contains(it) }
        // 净释放量（正=松动，负=收紧），单步上限 ±0.015，再乘 0.3 缩减系数
        val rawSuppressionDelta = ((releaseCount - suppressCount) * 0.005f).coerceIn(-0.015f, 0.015f)

        val rawDelta = RelationshipDelta(
            affectionDelta   = ((posCount - negCount) * 0.008f).coerceIn(-0.05f, 0.05f),
            tensionDelta     = (negCount * 0.01f - posCount * 0.005f).coerceIn(-0.05f, 0.05f),
            trustDelta       = (trustCount * 0.008f - negCount * 0.005f).coerceIn(-0.05f, 0.05f),
            suppressionDelta = rawSuppressionDelta
        )
        return applyFriction(rawDelta, stage)
    }

    /**
     * 摩擦系数表：阶段越深，正向推进越慢，负向衰减也更慢（符合现实情感惰性）。
     * 负向摩擦略高于正向（"破坏比建立容易，但也没那么容易"）。
     *
     * | stage | 正向  | 负向  |
     * |-------|-------|-------|
     * | 0     | 1.00x | 1.00x |
     * | 1     | 0.70x | 0.80x |
     * | 2     | 0.50x | 0.65x |
     * | 3     | 0.35x | 0.50x |
     * | 4     | 0.20x | 0.35x |
     * | 5     | 0.12x | 0.25x |
     *
     * suppression 额外再乘 0.3x 缩减系数：确保压抑感的自动松动速率
     * 远慢于 affection，面具碎裂是高光时刻而非每轮对话都会发生的常态。
     * respect / dependency / jealousy 由手动调整控制，不施加摩擦。
     */
    private fun applyFriction(delta: RelationshipDelta, stage: Int): RelationshipDelta {
        val posMultiplier = when (stage) {
            0    -> 1.00f
            1    -> 0.70f
            2    -> 0.50f
            3    -> 0.35f
            4    -> 0.20f
            else -> 0.12f
        }
        val negMultiplier = when (stage) {
            0    -> 1.00f
            1    -> 0.80f
            2    -> 0.65f
            3    -> 0.50f
            4    -> 0.35f
            else -> 0.25f
        }
        fun scale(v: Float) = if (v >= 0f) v * posMultiplier else v * negMultiplier
        return delta.copy(
            affectionDelta   = scale(delta.affectionDelta),
            trustDelta       = scale(delta.trustDelta),
            tensionDelta     = scale(delta.tensionDelta),
            // suppression 步进幅度极小（原始值已很小），再乘 0.3 进一步压缩
            suppressionDelta = scale(delta.suppressionDelta) * 0.3f
            // respect / dependency / jealousy 由手动调整控制，不施加摩擦
        )
    }
}
