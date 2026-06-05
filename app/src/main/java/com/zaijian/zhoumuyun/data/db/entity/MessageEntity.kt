package com.zaijian.zhoumuyun.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天消息表。
 * 一条记录 = 一条消息（用户或角色）。
 * 按 characterId 索引，支持快速按角色查询历史。
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["characterId"]),
        Index(value = ["characterId", "createdAt"]),
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    /** 关联角色 ID（1-9） */
    val characterId: Int,
    /** "user" 或角色 ID 字符串 */
    val role: String,
    /** 消息文本内容 */
    val content: String,
    /** 毫秒时间戳 */
    val createdAt: Long,
    /**
     * 关联的 WorldEvent ID（Phase 7 写 MESSAGE 事件后填入，
     * 当前阶段可为 null）
     */
    val eventId: String? = null,
)
