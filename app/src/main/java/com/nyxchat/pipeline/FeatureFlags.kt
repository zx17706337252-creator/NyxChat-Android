package com.nyxchat.pipeline

/**
 * 功能开关——出问题时改为 false 即可回滚，不需要 revert 代码。
 */
object FeatureFlags {
    const val MEMORY_DECAY_INJECTION  = true   // 记忆衰减注入（替代固定 top10）
    const val APPEARANCE_CONDITIONAL  = true   // 外貌按关键词条件注入
    const val TOKEN_BUDGET_HISTORY    = true   // Token Budget 截断（替代固定条数）
    const val GROUP_TIERED_INJECTION  = true   // 群聊角色三档注入
}
