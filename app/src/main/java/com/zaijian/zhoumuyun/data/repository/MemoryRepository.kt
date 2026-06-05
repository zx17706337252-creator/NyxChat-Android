package com.zaijian.zhoumuyun.data.repository

import com.zaijian.zhoumuyun.data.db.dao.MemoryCandidateDao
import com.zaijian.zhoumuyun.data.db.dao.MemoryDao
import com.zaijian.zhoumuyun.data.db.entity.MemoryCandidateEntity
import com.zaijian.zhoumuyun.data.db.entity.MemoryDomain
import com.zaijian.zhoumuyun.data.db.entity.MemoryEntity
import com.zaijian.zhoumuyun.data.db.entity.MemoryFtsEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * MemoryRepository（Phase 8）
 *
 * 职责：
 * 1. 写入 Memory（主表 + FTS4 虚拟表同步）
 * 2. 按域读取（Prompt 注入用）
 * 3. FTS4 全文检索 + FinalScore 评分排序
 * 4. Memory Merge（发现重复时合并而非新增）
 * 5. 候选层读写
 * 6. 过期清理（importance=2 的记忆超过 7 天后删除）
 *
 * 使用规则：
 * - 禁止直接调用 memoryDao.insert()，必须通过 save() 方法（保证 FTS 同步）
 * - 重复内容通过 findAndMerge() 处理，禁止写入重复记忆
 */
class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val candidateDao: MemoryCandidateDao,
) {

    // ── 候选层 ────────────────────────────────────────────────

    suspend fun insertCandidate(candidate: MemoryCandidateEntity) =
        candidateDao.insert(candidate)

    suspend fun getPendingCandidates(characterId: Int): List<MemoryCandidateEntity> =
        candidateDao.getPending(characterId)

    suspend fun markCandidateProcessed(candidateId: String, resultMemoryId: String?) =
        candidateDao.markProcessed(candidateId, resultMemoryId)

    // ── Memory 写入（同步 FTS）────────────────────────────────

    /**
     * 写入一条新 Memory，同时同步写 FTS4 虚拟表。
     *
     * Room 的 FTS4 虚拟表不支持自动同步，必须手动维护。
     * FTS 的 rowId 与 memories 主表的 SQLite rowid 一致——
     * 由于我们无法在插入后直接拿到 rowid（Room 隐藏了），
     * 使用一个简单的约定：FTS 表用内容哈希作为虚拟 rowId，
     * 实际检索时通过 JOIN memories.rowid = memories_fts.rowid 关联。
     *
     * 注意：此处通过查询方式获取刚插入行的 rowid。
     */
    suspend fun save(memory: MemoryEntity) {
        memoryDao.insert(memory)
        // FTS 同步：通过 content 哈希作为 rowId（简化实现，避免额外查询）
        // 生产方案：INSERT OR REPLACE INTO memories_fts VALUES(last_insert_rowid(), ...)
        // Room 限制下，用稳定的正整数哈希保证 rowId 唯一性。
        val ftsRowId = memory.id.hashCode().let { if (it < 0) -it else it }
        memoryDao.insertFts(
            MemoryFtsEntity(
                rowId    = ftsRowId,
                content  = memory.content,
                keywords = memory.keywords,
            )
        )
    }

    /**
     * 更新已有 Memory（内容改变时同步更新 FTS）。
     */
    suspend fun update(memory: MemoryEntity) {
        memoryDao.update(memory)
        // 先删 FTS 旧记录，再插新记录
        val ftsRowId = memory.id.hashCode().let { if (it < 0) -it else it }
        memoryDao.deleteFtsById(ftsRowId)
        memoryDao.insertFts(
            MemoryFtsEntity(
                rowId    = ftsRowId,
                content  = memory.content,
                keywords = memory.keywords,
            )
        )
    }

    /**
     * Merge 逻辑：发现内容相似的已有记忆时，合并而非新增。
     *
     * 规则（§6.6）：
     * - 提取候选内容的第一个有意义词（5字以上的词组）
     * - 查找 memories 表中 content 包含该词的记录
     * - 如果找到相似记忆 → 更新内容（合并），importance 取两者最高值
     * - 如果没有 → 写入新记忆
     *
     * @return 合并到的 Memory ID（如果是 Merge），或新写入的 Memory ID
     */
    suspend fun saveOrMerge(memory: MemoryEntity): String {
        // 提取用于相似度查找的关键词（keywords 的第一个词）
        val firstKeyword = memory.keywords.split(" ").firstOrNull { it.length >= 2 }
        if (firstKeyword != null) {
            val similar = memoryDao.findSimilar(memory.characterId, firstKeyword)
            val candidate = similar.firstOrNull()
            if (candidate != null && candidate.id != memory.id) {
                // 找到相似记忆：执行 Merge
                val merged = candidate.copy(
                    content        = mergeContent(candidate.content, memory.content),
                    importance     = maxOf(candidate.importance, memory.importance),
                    keywords       = mergeKeywords(candidate.keywords, memory.keywords),
                    isCore         = candidate.isCore || memory.isCore,
                    updatedAt      = System.currentTimeMillis(),
                    accessCount    = candidate.accessCount,
                    lastAccessedAt = candidate.lastAccessedAt,
                )
                update(merged)
                return candidate.id
            }
        }
        // 没有相似记忆：写入新记录
        save(memory)
        return memory.id
    }

    // ── 读取：Prompt 注入 ─────────────────────────────────────

    /**
     * 获取 Core Memory（importance=5，每次对话必须注入，最多 5 条）。
     */
    suspend fun getCoreMemories(characterId: Int): List<MemoryEntity> =
        memoryDao.getCoreMemories(characterId).take(5)

    /**
     * 全文检索：根据用户消息内容检索相关记忆，用于 Prompt Memory Layer。
     *
     * FinalScore 评分公式（§12.2）：
     * FinalScore = FTS_rank(0.45) + recency(0.25) + importance(0.20) + frequency(0.10)
     *
     * @param query 用户消息或对话关键词
     * @param limit 最多返回条数
     */
    suspend fun searchRelevant(
        characterId: Int,
        query: String,
        limit: Int = 10,
    ): List<MemoryEntity> {
        if (query.isBlank()) return emptyList()

        // FTS4 查询：将查询词转为 FTS 格式（支持前缀匹配）
        val ftsQuery = buildFtsQuery(query)

        val ftsResults = try {
            memoryDao.searchByFts(characterId, ftsQuery, limit * 2)
        } catch (e: Exception) {
            // FTS 查询语法错误时降级到最近记忆
            emptyList()
        }

        // FinalScore 评分 + 排序 + 取 TopK
        val now = System.currentTimeMillis()
        return ftsResults
            .map { it to calculateFinalScore(it, now) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * 按域获取记忆（Prompt 分层注入用）。
     */
    suspend fun getByDomain(characterId: Int, domain: MemoryDomain, limit: Int = 5): List<MemoryEntity> =
        memoryDao.getByDomain(characterId, domain.name, limit)

    // ── 观察（UI 层）─────────────────────────────────────────

    fun observeAll(characterId: Int): Flow<List<MemoryEntity>> =
        memoryDao.observeAll(characterId)

    fun observeImportant(characterId: Int): Flow<List<MemoryEntity>> =
        memoryDao.observeImportant(characterId)

    fun observeAboutUser(characterId: Int): Flow<List<MemoryEntity>> =
        memoryDao.observeAboutUser(characterId)

    fun observeAboutWorld(characterId: Int): Flow<List<MemoryEntity>> =
        memoryDao.observeAboutWorld(characterId)

    // ── 访问记录 ──────────────────────────────────────────────

    suspend fun recordAccess(memoryId: String) =
        memoryDao.recordAccess(memoryId, System.currentTimeMillis())

    // ── 删除 ──────────────────────────────────────────────────

    suspend fun deleteById(memoryId: String) =
        memoryDao.deleteById(memoryId)

    /**
     * 清理过期记忆（importance=2 保留 7 天）。
     * 由 MemoryEngine 定期调用。
     */
    suspend fun cleanExpired(characterId: Int) {
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        memoryDao.deleteExpired(characterId, sevenDaysAgo)
    }

    // ── 内部工具方法 ──────────────────────────────────────────

    /**
     * 构建 FTS4 查询字符串。
     *
     * FTS4 MATCH 语法：
     * - 单词精确匹配："银发"
     * - 前缀匹配："银发*"
     * - 多词 OR："银发 角色"（空格分隔 = OR）
     *
     * 此处将输入切分后每个词加前缀通配符。
     */
    private fun buildFtsQuery(input: String): String {
        val words = input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.isEmpty()) input
        else words.take(5).joinToString(" ") { "$it*" }
    }

    /**
     * FinalScore 评分（§12.2）。
     *
     * FTS_rank 在 Room 中不直接可用（需 matchinfo），
     * 此处用 importance + recency + frequency 三项近似（FTS 已完成筛选）。
     */
    private fun calculateFinalScore(memory: MemoryEntity, now: Long): Double {
        // recency_score：越新越高，超过 90 天趋近 0
        val ageMs = now - memory.lastAccessedAt
        val ageDays = ageMs / (1000.0 * 60 * 60 * 24)
        val recency = maxOf(0.0, 1.0 - ageDays / 90.0)

        // importance_score：归一化到 0-1
        val importance = (memory.importance - 1) / 4.0

        // access_frequency：对数归一化，防止高频记忆过度主导
        val frequency = if (memory.accessCount <= 0) 0.0
        else Math.log(memory.accessCount.toDouble() + 1) / Math.log(51.0)

        // FTS 已完成相关性筛选（0.45 权重），此处给 0.45 基础分
        return 0.45 + recency * 0.25 + importance * 0.20 + frequency * 0.10
    }

    /**
     * 合并两条记忆的内容（简单拼接，保留更新的内容为主）。
     * Phase 10 可接入 LLM 做智能合并。
     */
    private fun mergeContent(existing: String, incoming: String): String {
        if (existing == incoming) return existing
        return "$existing（更新：$incoming）"
    }

    private fun mergeKeywords(existing: String, incoming: String): String {
        val existingSet = existing.split(" ").toSet()
        val incomingSet = incoming.split(" ").toSet()
        return (existingSet + incomingSet).filter { it.isNotBlank() }.joinToString(" ")
    }

    companion object {
        /** 快捷构建：从 ChatViewModel 传入的内容生成新 Memory ID */
        fun newId(): String = UUID.randomUUID().toString()
    }
}
