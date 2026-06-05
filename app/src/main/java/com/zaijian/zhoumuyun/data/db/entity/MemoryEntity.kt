package com.zaijian.zhoumuyun.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

// ─────────────────────────────────────────────────────────────
//  MemoryDomain — 记忆所属领域
// ─────────────────────────────────────────────────────────────

enum class MemoryDomain {
    PERSONAL,   // 用户偏好、习惯、关系、情绪模式
    WORK,       // 项目知识、任务经验、工作流程
    WORLD,      // 角色行为、角色互动、世界事件、关系演化
}

// ─────────────────────────────────────────────────────────────
//  MemoryEntity — 长期记忆主表
//
//  设计原则（§6）：
//  - Memory 不是聊天记录，是经过筛选、压缩、抽象后的长期信息
//  - 禁止直接由聊天记录写入，必须经过 MemoryCandidate 层
//  - importance 1-5：
//      1 = 直接丢弃  2 = 保留 7 天  3 = 长期记忆
//      4 = 长期保存  5(isCore) = 永不自动删除
// ─────────────────────────────────────────────────────────────

@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["characterId"]),
        Index(value = ["characterId", "domain"]),
        Index(value = ["characterId", "importance"]),
        Index(value = ["characterId", "isCore"]),
        Index(value = ["projectId"]),
        Index(value = ["createdAt"]),
        Index(value = ["lastAccessedAt"]),
    ]
)
data class MemoryEntity(
    @PrimaryKey val id: String,

    /** 所属角色 ID（1-9） */
    val characterId: Int,

    /** 记忆所属领域 */
    val domain: String,         // MemoryDomain.name

    /** 记忆内容（提炼后的自然语言描述） */
    val content: String,

    /** 重要度 1-5 */
    val importance: Int,

    /**
     * 关键词列表（空格分隔字符串，同步写入 FTS4 虚拟表）。
     * FTS4 检索依赖此字段，写入时需同步更新 memories_fts。
     */
    val keywords: String,       // 空格分隔，如 "银发 角色 偏好"

    /** 来源 WorldEvent ID，可追溯到具体事件 */
    val sourceEventId: String?,

    /** 是否为 Core Memory（importance=5，永不自动删除，每次都注入 Prompt） */
    val isCore: Boolean = false,

    /**
     * 关联项目 ID（可空）。
     * ★ v3 新增：Project Memory 归属 MemoryDomain.WORK 且此字段非空。
     */
    val projectId: String? = null,

    /** 被 Prompt 检索召回的次数（影响 FinalScore 权重） */
    val accessCount: Int = 0,

    val createdAt: Long,
    val updatedAt: Long,

    /** 最后一次被 Prompt 召回的时间（用于计算 recency_score） */
    val lastAccessedAt: Long,
)

// ─────────────────────────────────────────────────────────────
//  MemoryFtsEntity — FTS4 全文检索虚拟表
//
//  与 memories 主表同步写入，通过 rowid 关联。
//  仅存储用于检索的字段：content + keywords。
//  查询示例：SELECT * FROM memories_fts WHERE memories_fts MATCH :query
// ─────────────────────────────────────────────────────────────

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "memories_fts")
data class MemoryFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val content: String,
    val keywords: String,
)

// ─────────────────────────────────────────────────────────────
//  MemoryCandidateEntity — 记忆候选层
//
//  所有记忆必须先进入此表，经过打分后再决定是否写入 memories。
//  禁止直接向 memories 写入，必须经过 MemoryEngine.process()。
//
//  候选分数规则：
//  score 1 → 直接丢弃（不写入 memories）
//  score 2 → 写入，importance=2（7天后可删）
//  score 3-5 → 写入，importance=score
// ─────────────────────────────────────────────────────────────

@Entity(
    tableName = "memory_candidates",
    indices = [
        Index(value = ["characterId"]),
        Index(value = ["sourceEventId"]),
        Index(value = ["isProcessed"]),
        Index(value = ["createdAt"]),
    ]
)
data class MemoryCandidateEntity(
    @PrimaryKey val id: String,

    /** 所属角色 ID */
    val characterId: Int,

    /** 触发此候选的 WorldEvent ID */
    val sourceEventId: String,

    /** 候选记忆内容（原始文本，尚未提炼） */
    val content: String,

    /** 候选分数 1-5（1=丢弃，2-5=写入对应 importance） */
    val score: Int,

    /** 记忆领域 */
    val domain: String,         // MemoryDomain.name

    /**
     * 关联项目 ID（可空）。
     * ★ v3 新增：Project 相关事件产生的候选带此字段。
     */
    val projectId: String? = null,

    /** 是否已处理（写入 memories 或丢弃） */
    val isProcessed: Boolean = false,

    /** 处理后生成的 Memory ID（如果 score > 1） */
    val resultMemoryId: String? = null,

    val createdAt: Long,
)
