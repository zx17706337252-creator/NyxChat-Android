package com.zaijian.zhoumuyun.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zaijian.zhoumuyun.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    /** 获取指定角色的历史消息（按时间升序，最多 limit 条） */
    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getByCharacter(characterId: Int, limit: Int = 100): List<MessageEntity>

    /** 实时观察指定角色的消息列表（UI 层用） */
    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY createdAt ASC")
    fun observeByCharacter(characterId: Int): Flow<List<MessageEntity>>

    /** 获取最近 N 条（跨角色，用于 Event 上下文） */
    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLatest(limit: Int = 20): List<MessageEntity>

    @Query("DELETE FROM messages WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Int)

    @Query("SELECT COUNT(*) FROM messages WHERE characterId = :characterId")
    suspend fun countByCharacter(characterId: Int): Int
}
