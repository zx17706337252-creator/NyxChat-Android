package com.zaijian.zhoumuyun.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zaijian.zhoumuyun.data.db.entity.MemoryCandidateEntity
import com.zaijian.zhoumuyun.data.db.entity.MemoryEntity
import com.zaijian.zhoumuyun.data.db.entity.MemoryFtsEntity
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────
//  MemoryDao — 长期记忆主表 + FTS4 虚拟表
// ─────────────────────────────────────────────────────────────

@Dao
interface MemoryDao {

    // ── 写入 ──────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Update
    suspend fun update(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(fts: MemoryFtsEntity)

    /**
     * 更新 FTS 虚拟表（先删后插，因为 UPDATE 在 FTS 表无效）。
     * 通过 rowId 定位并替换。
     */
    @Query("DELETE FROM memories_fts WHERE rowid = :rowId")
    suspend fun deleteFtsById(rowId: Int)

    // ── 读取：Core Memory（永远注入 Prompt）────────────────────

    @Query("""
        SELECT * FROM memories
        WHERE characterId = :characterId AND isCore = 1
        ORDER BY updatedAt DESC
    """)
    suspend fun getCoreMemories(characterId: Int): List<MemoryEntity>

    @Query("""
        SELECT * FROM memories
        WHERE characterId = :characterId AND isCore = 1
        ORDER BY updatedAt DESC
    """)
    fun observeCoreMemories(characterId: Int): Flow<List<MemoryEntity>>

    // ── 读取：按 domain 获取（Prompt 分域注入）────────────────

    @Query("""
        SELECT * FROM memories
        WHERE characterId = :characterId AND domain = :domain
        ORDER BY importance DESC, lastAccessedAt DESC
        LIMIT :limit
    """)
    suspend fun getByDomain(characterId: Int, domain: String, limit: Int = 5): List<MemoryEntity>

    // ── 读取：UI 展示（全部 / 重要 / 关于我 / 关于他）─────────

    @Query("""
        SELECT * FROM memories
        WHERE characterId = :characterId
        ORDER BY importance DESC, updatedAt DESC
    """)
    fun observeAll(characterId: Int): Flow<List<MemoryEntity>>

    @Query("""
        SELECT * FROM memories
        WHERE characterId = :characterId AND importance >= 4
        ORDER BY updatedAt DESC
    """)
    fun observeImportant(characterId: Int): Flow<List<MemoryEntity>>

    @Query("""
        SELECT * FROM memories
        WHERE characterId = :characterId AND domain = 'PERSONAL'
        ORDER BY updatedAt DESC
    """)
    fun observeAboutUser(characterId: Int): Flow<List<MemoryEntity>>

    @Query("""
        SELECT * FROM memories
        WHERE characterId = :characterId AND domain = 'WORLD'
        ORDER BY updatedAt DESC
    """)
    fun observeAboutWorld(characterId: Int): Flow<List<MemoryEntity>>

    // ── FTS4 全文检索 ─────────────────────────────────────────

    /**
     * 全文检索：从 FTS 表找匹配的 rowId，再 JOIN 主表取完整数据。
     *
     * 说明：Room FTS4 的 MATCH 查询返回 rowid，
     * 此 rowId 对应 memories 表的 rowid（SQLite 内置行号）。
     * 通过子查询关联两张表。
     *
     * 注意：FTS4 MATCH 使用简单查询，中文用 TOKENIZER_UNICODE61 分词。
     * 调用方需将查询词用 "*" 包裹以支持前缀匹配（如 "永恒*"）。
     */
    @Query("""
        SELECT m.* FROM memories m
        INNER JOIN memories_fts fts ON m.rowid = fts.rowid
        WHERE fts.memories_fts MATCH :query
          AND m.characterId = :characterId
        ORDER BY m.importance DESC, m.lastAccessedAt DESC
        LIMIT :limit
    """)
    suspend fun searchByFts(characterId: Int, query: String, limit: Int = 10): List<MemoryEntity>

    // ── 更新访问记录（被 Prompt 召回时调用）──────────────────

    @Query("""
        UPDATE memories
        SET accessCount = accessCount + 1,
            lastAccessedAt = :accessedAt
        WHERE id = :memoryId
    """)
    suspend fun recordAccess(memoryId: String, accessedAt: Long)

    // ── 删除 ──────────────────────────────────────────────────

    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun deleteById(memoryId: String)

    @Query("""
        DELETE FROM memories
        WHERE characterId = :characterId
          AND importance <= 2
          AND createdAt < :expiryTimestamp
          AND isCore = 0
    """)
    suspend fun deleteExpired(characterId: Int, expiryTimestamp: Long)

    // ── 合并辅助：按内容相似度查找已有记忆 ───────────────────

    /**
     * 查找内容最接近的已有记忆（用于 Merge 判断）。
     * 使用 LIKE 做简单的关键词包含匹配；精确检索走 FTS。
     */
    @Query("""
        SELECT * FROM memories
        WHERE characterId = :characterId
          AND (content LIKE '%' || :keyword || '%')
        ORDER BY updatedAt DESC
        LIMIT 5
    """)
    suspend fun findSimilar(characterId: Int, keyword: String): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories WHERE characterId = :characterId")
    suspend fun count(characterId: Int): Int
}

// ─────────────────────────────────────────────────────────────
//  MemoryCandidateDao — 记忆候选层
// ─────────────────────────────────────────────────────────────

@Dao
interface MemoryCandidateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(candidate: MemoryCandidateEntity)

    /** 获取所有未处理的候选（MemoryEngine 批量处理用） */
    @Query("""
        SELECT * FROM memory_candidates
        WHERE characterId = :characterId AND isProcessed = 0
        ORDER BY createdAt ASC
    """)
    suspend fun getPending(characterId: Int): List<MemoryCandidateEntity>

    /** 标记候选已处理 */
    @Query("""
        UPDATE memory_candidates
        SET isProcessed = 1, resultMemoryId = :resultMemoryId
        WHERE id = :candidateId
    """)
    suspend fun markProcessed(candidateId: String, resultMemoryId: String?)

    /** 获取最近 N 条候选（调试和展示用） */
    @Query("""
        SELECT * FROM memory_candidates
        WHERE characterId = :characterId
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun getRecent(characterId: Int, limit: Int = 20): List<MemoryCandidateEntity>

    @Query("DELETE FROM memory_candidates WHERE characterId = :characterId AND isProcessed = 1")
    suspend fun deleteProcessed(characterId: Int)
}
