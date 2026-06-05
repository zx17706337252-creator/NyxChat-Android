package com.zaijian.zhoumuyun.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 角色 Identity 持久化表。
 * 与内存中的 CharacterIdentity 对应，一个角色一条记录。
 * Phase 7 建表，ProfileScreen → 角色管理 中可编辑。
 */
@Entity(tableName = "character_identity")
data class CharacterIdentityEntity(
    /** characterId 作为主键（1-9） */
    @PrimaryKey val characterId: Int,
    val persona: String = "",
    val speechStyle: String = "",
    val attitudeToUser: String = "",
    /** JSON 数组字符串，存储 boundaries 列表 */
    val boundariesJson: String = "[]",
    /** JSON 数组字符串，存储 coreBeliefs 列表 */
    val corebeliefsJson: String = "[]",
    /** 若非空，完全替换 Identity Layer 自动组装结果 */
    val customSystemPrompt: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
