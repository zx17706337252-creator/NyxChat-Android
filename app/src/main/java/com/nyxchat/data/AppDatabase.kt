package com.nyxchat.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ─── Entities ─────────────────────────────────────────────────────────────────

@Entity(
    tableName = "messages",
    // P2-B fix: 补充 charId 索引。
    // countRecentForChar 按 charId + role 过滤，无索引时全表扫描；
    // 消息数万条时每次生日检查 / ProactiveWorker 都要全扫，加索引后降为 O(log n)。
    indices = [Index("sessionId"), Index("timestamp"), Index("charId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String = "default",
    val role: String,
    val charId: String?,
    val content: String,
    val timestamp: Long,
    val isGroup: Int = 1,
    val isSummary: Int = 0   // 1 = 本条是历史摘要，由自动压缩生成
)

@Entity(
    tableName = "memories",
    indices = [Index("charId"), Index("importance"), Index("timestamp")]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val charId: String,
    val content: String,
    val importance: Int,
    val timestamp: Long,
    val type: String = "Public"   // 步骤13：记忆类型标签
)

// ─── Mappers ──────────────────────────────────────────────────────────────────

fun MessageEntity.toDomain() = NyxMessage(id, role, charId, content, timestamp, sessionId, isGroup == 1, isSummary == 1)
fun NyxMessage.toEntity()    = MessageEntity(id, sessionId, role, charId, content, timestamp, if (isGroup) 1 else 0, if (isSummary) 1 else 0)
fun MemoryEntity.toDomain()  = NyxMemory(id, charId, content, importance, timestamp, type)
fun NyxMemory.toEntity()     = MemoryEntity(id, charId, content, importance, timestamp, type)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getForSession(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentForSession(sessionId: String, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: MessageEntity)

    // Update content of existing message (used for streaming)
    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND id NOT IN (SELECT id FROM messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trimSession(sessionId: String, keep: Int)

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId AND isSummary = 1")
    suspend fun countSummariesForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE charId = :charId AND role = 'assistant'")
    suspend fun countForCharacter(charId: String): Int

    @Query("SELECT MAX(timestamp) FROM messages WHERE charId = :charId")
    suspend fun lastMessageTime(charId: String): Long?

    // Bug fix: 跨 session 检查角色最近消息，用于生日去重（原 _messages.value 只含当前 session）
    @Query("SELECT COUNT(*) FROM messages WHERE charId = :charId AND role = 'assistant' AND timestamp > :sinceMs")
    suspend fun countRecentForChar(charId: String, sinceMs: Long): Int

    // P1-B fix: 旧版无 sessionId 参数的 searchMessages / searchMessagesFiltered 已废弃，
    // Repository 层全部改用下方带 sessionId 的版本，旧方法已删除，消除死代码。

    // Step 5 fix (Bug 5): 在查询层直接用 sessionId 过滤，避免把所有会话消息拉进内存
    // 再由 ViewModel 二次过滤。消息数量大时可节省大量内存和 Cursor 解析耗时。
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND content LIKE :pattern ORDER BY timestamp DESC")
    suspend fun searchMessagesInSession(sessionId: String, pattern: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND content LIKE :pattern AND charId = :charId ORDER BY timestamp DESC")
    suspend fun searchMessagesInSessionFiltered(sessionId: String, pattern: String, charId: String): List<MessageEntity>
}

@Dao
interface MemoryDao {

    // Phase 1: limit 默认值 50→200，与 pruneForChar 上限同步
    @Query("""
        SELECT * FROM memories WHERE charId = :charId
        ORDER BY (importance * 1.0 / (1.0 + CAST(((:nowMs - timestamp) / 86400000) AS REAL) * 0.05)) DESC
        LIMIT :limit
    """)
    suspend fun getForCharDecayed(charId: String, nowMs: Long, limit: Int = 200): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY importance DESC, timestamp DESC LIMIT :limit")
    suspend fun getTop(limit: Int): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<MemoryEntity>)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memories WHERE charId = :charId")
    suspend fun deleteForChar(charId: String)

    // Phase 1: keep 默认值 50→200，与 getForCharDecayed 上限同步
    @Query("DELETE FROM memories WHERE charId = :charId AND id NOT IN (SELECT id FROM memories WHERE charId = :charId ORDER BY (importance * 1.0 / (1.0 + CAST(((:nowMs - timestamp) / 86400000) AS REAL) * 0.05)) DESC LIMIT :keep)")
    suspend fun pruneForChar(charId: String, nowMs: Long, keep: Int = 200)
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [MessageEntity::class, MemoryEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "nyx_chat_db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
        }

        // ─── Migrations ──────────────────────────────────────────────────────────

        // V1 → V2: 新增 isGroup 列（默认 1）
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isGroup INTEGER NOT NULL DEFAULT 1")
            }
        }

        // V2 → V3: 新增 isSummary 列（默认 0）
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isSummary INTEGER NOT NULL DEFAULT 0")
            }
        }

        // V3 → V4: 新增 memories 表
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `memories` (
                        `id` TEXT NOT NULL,
                        `charId` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `importance` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `type` TEXT NOT NULL DEFAULT 'Public',
                        PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_charId` ON `memories` (`charId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_importance` ON `memories` (`importance`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_timestamp` ON `memories` (`timestamp`)")
            }
        }

        // V4 → V5: 新增 charId 索引到 messages 表
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_charId` ON `messages` (`charId`)")
            }
        }

        // V5 → V6: 新增 sessionId 索引到 messages 表
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_sessionId` ON `messages` (`sessionId`)")
            }
        }
    }
}
