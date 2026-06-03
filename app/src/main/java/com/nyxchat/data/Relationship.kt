package com.nyxchat.data

// ─── Relationship Matrix ──────────────────────────────────────────────────────
// Tracks bidirectional relationship state between every pair of characters

/** 用户在关系系统中的伪 ID，用于构造 角色↔用户 的 Relationship 记录 */
const val USER_PSEUDO_ID = "user"

/**
 * 创建 角色↔用户 关系的初始值。
 * 使用低起点（affection=0.10）确保阶段从 0（陌生）开始推进，
 * 而不是跳过早期阶段。不能使用 Relationship() 默认值（affection=0.5f），
 * 因为那会让棘轮在第一次 applyDelta 就直接锁到 stage 2。
 */
fun newUserCharRelationship(charId: String): Relationship = Relationship(
    id           = "${charId}_${USER_PSEUDO_ID}",
    fromCharId   = charId,
    toCharId     = USER_PSEUDO_ID,
    trust        = 0.15f,
    affection    = 0.10f,
    tension      = 0.0f,
    respect      = 0.25f,
    dependency   = 0.10f,
    jealousy     = 0.0f,
    suppression  = 0.50f,
    stage        = 0,
    allowBreakup = false
)

// ─── Stage computation ────────────────────────────────────────────────────────

/**
 * 根据 affection/trust 计算原始阶段值（纯函数，不含棘轮逻辑）。
 * 棘轮（只增不减）由 applyDelta() 中的 maxOf() 保证。
 */
private fun computeStage(affection: Float, trust: Float): Int = when {
    affection > 0.88f && trust > 0.78f -> 5  // 深恋
    affection > 0.72f && trust > 0.62f -> 4  // 恋人
    affection > 0.55f && trust > 0.45f -> 3  // 暧昧
    affection > 0.38f && trust > 0.30f -> 2  // 熟悉
    affection > 0.20f                  -> 1  // 初识
    else                               -> 0  // 陌生
}

/**
 * 亲密度地板：stage + allowBreakup 共同决定感情的最低下限。
 * stage >= 4 且未允许分手时：地板 0.58f，角色即便争吵也明确"还在感情里"。
 * stage >= 4 且允许分手时：无地板，感情可自由下滑直至可分手。
 */
private fun affectionFloor(stage: Int, allowBreakup: Boolean): Float = when {
    stage >= 4 && !allowBreakup -> 0.58f
    stage >= 4 &&  allowBreakup -> 0.0f
    stage == 3                  -> 0.42f
    stage == 2                  -> 0.27f
    stage == 1                  -> 0.12f
    else                        -> 0.0f
}

// ─── Data model ───────────────────────────────────────────────────────────────

data class Relationship(
    val id: String,                      // "${fromCharId}_${toCharId}"
    val fromCharId: String,
    val toCharId: String,
    val trust: Float = 0.5f,             // 0.0 (no trust) – 1.0 (absolute trust)
    val affection: Float = 0.5f,         // 0.0 (cold) – 1.0 (deep affection)
    val tension: Float = 0.0f,           // 0.0 (calm) – 1.0 (explosive tension)
    val respect: Float = 0.5f,           // 0.0 (contempt) – 1.0 (deep respect)
    // ── 步骤11：扩展维度（4 → 7）────────────────────────────────────────────
    val dependency: Float = 0.5f,        // 0.0 (独立) – 1.0 (高度依赖)；影响主动消息语气
    val jealousy: Float = 0.0f,          // 0.0 (无) – 1.0 (强烈嫉妒/独占)；多角色场景下影响语气
    val suppression: Float = 0.5f,       // 0.0 (完全压抑) – 1.0 (完全释放)；注入后模型演出克制感
    val summary: String = "",            // one-sentence current state
    val updatedAt: Long = System.currentTimeMillis(),
    // ── 关系阶段 & 分手开关 ───────────────────────────────────────────────────
    val stage: Int = 0,                  // 0陌生 1初识 2熟悉 3暧昧 4恋人 5深恋，棘轮只增不减
    val allowBreakup: Boolean = false    // 分手开关；stage >= 4 时由 UI 暴露给用户
) {
    /**
     * 按顺序执行四步：
     * ① 叠加 delta，coerceIn(0,1)
     * ② 棘轮：stage 只取 max（只增不减）
     * ③ 施加亲密地板（stage + allowBreakup 共同决定）
     * ④ 复燃检测：allowBreakup=true 且 affection 升破 0.50 → 自动关闭分手开关
     */
    fun applyDelta(delta: RelationshipDelta): Relationship {
        // ①
        var newTrust       = (trust       + delta.trustDelta).coerceIn(0f, 1f)
        var newAffection   = (affection   + delta.affectionDelta).coerceIn(0f, 1f)
        var newTension     = (tension     + delta.tensionDelta).coerceIn(0f, 1f)
        var newRespect     = (respect     + delta.respectDelta).coerceIn(0f, 1f)
        var newDependency  = (dependency  + delta.dependencyDelta).coerceIn(0f, 1f)
        var newJealousy    = (jealousy    + delta.jealousyDelta).coerceIn(0f, 1f)
        var newSuppression = (suppression + delta.suppressionDelta).coerceIn(0f, 1f)

        // ② 棘轮
        val rawStage = computeStage(newAffection, newTrust)
        val newStage = maxOf(stage, rawStage)

        // ③ 亲密地板
        val floor = affectionFloor(newStage, allowBreakup)
        newAffection = newAffection.coerceAtLeast(floor)

        // ④ 复燃检测
        val newAllowBreakup = if (allowBreakup && newStage >= 4 && newAffection > 0.50f) {
            false  // 感情回暖，自动关闭分手开关
        } else {
            allowBreakup
        }

        return copy(
            trust        = newTrust,
            affection    = newAffection,
            tension      = newTension,
            respect      = newRespect,
            dependency   = newDependency,
            jealousy     = newJealousy,
            suppression  = newSuppression,
            stage        = newStage,
            allowBreakup = newAllowBreakup,
            summary      = delta.summary.ifBlank { summary },
            updatedAt    = System.currentTimeMillis()
        )
    }
}

data class RelationshipDelta(
    val trustDelta: Float = 0f,
    val affectionDelta: Float = 0f,
    val tensionDelta: Float = 0f,
    val respectDelta: Float = 0f,
    // 步骤11：扩展维度 delta（AI 不自动追踪，手动调整用）
    val dependencyDelta: Float = 0f,
    val jealousyDelta: Float = 0f,
    val suppressionDelta: Float = 0f,
    val summary: String = ""
)


