package com.zaijian.zhoumuyun.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zaijian.zhoumuyun.data.db.dao.CharacterIdentityDao
import com.zaijian.zhoumuyun.data.db.dao.MemoryCandidateDao
import com.zaijian.zhoumuyun.data.db.dao.MemoryDao
import com.zaijian.zhoumuyun.data.db.dao.MessageDao
import com.zaijian.zhoumuyun.data.db.dao.WorldEventDao
import com.zaijian.zhoumuyun.data.db.entity.CharacterIdentityEntity
import com.zaijian.zhoumuyun.data.db.entity.MemoryCandidateEntity
import com.zaijian.zhoumuyun.data.db.entity.MemoryEntity
import com.zaijian.zhoumuyun.data.db.entity.MemoryFtsEntity
import com.zaijian.zhoumuyun.data.db.entity.MessageEntity
import com.zaijian.zhoumuyun.data.db.entity.WorldEventEntity

/**
 * 再见周慕云 · Room 数据库
 *
 * 版本历史：
 *   v1（Phase 7）：messages / world_events / character_identity
 *   v2（Phase 8）：+ memories / memories_fts / memory_candidates
 *
 * Phase 8 新增表：
 * - memories：长期记忆主表（含 FTS 关键词字段）
 * - memories_fts：FTS4 全文检索虚拟表（与 memories 同步写入）
 * - memory_candidates：记忆候选层（Event → Candidate → Memory 管道的中间状态）
 */
@Database(
    entities = [
        MessageEntity::class,
        WorldEventEntity::class,
        CharacterIdentityEntity::class,
        MemoryEntity::class,
        MemoryFtsEntity::class,
        MemoryCandidateEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun worldEventDao(): WorldEventDao
    abstract fun characterIdentityDao(): CharacterIdentityDao
    abstract fun memoryDao(): MemoryDao
    abstract fun memoryCandidateDao(): MemoryCandidateDao

    companion object {
        private const val DB_NAME = "zaijian_world.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration v1 → v2：新增 Memory Engine 三张表。
         *
         * FTS4 虚拟表必须用 CREATE VIRTUAL TABLE 语法，
         * Room 的 autoMigrate 不支持 FTS，手写 Migration 是唯一方式。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. memories 主表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `memories` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `characterId` INTEGER NOT NULL,
                        `domain` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `importance` INTEGER NOT NULL,
                        `keywords` TEXT NOT NULL,
                        `sourceEventId` TEXT,
                        `isCore` INTEGER NOT NULL DEFAULT 0,
                        `projectId` TEXT,
                        `accessCount` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastAccessedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_characterId` ON `memories` (`characterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_characterId_domain` ON `memories` (`characterId`, `domain`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_characterId_importance` ON `memories` (`characterId`, `importance`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_characterId_isCore` ON `memories` (`characterId`, `isCore`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_projectId` ON `memories` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_createdAt` ON `memories` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_lastAccessedAt` ON `memories` (`lastAccessedAt`)")

                // 2. memories_fts FTS4 虚拟表（TOKENIZER_UNICODE61 支持中文）
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `memories_fts`
                    USING fts4(
                        `content` TEXT,
                        `keywords` TEXT,
                        tokenize=unicode61
                    )
                """.trimIndent())

                // 3. memory_candidates 候选表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `memory_candidates` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `characterId` INTEGER NOT NULL,
                        `sourceEventId` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `score` INTEGER NOT NULL,
                        `domain` TEXT NOT NULL,
                        `projectId` TEXT,
                        `isProcessed` INTEGER NOT NULL DEFAULT 0,
                        `resultMemoryId` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_candidates_characterId` ON `memory_candidates` (`characterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_candidates_sourceEventId` ON `memory_candidates` (`sourceEventId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_candidates_isProcessed` ON `memory_candidates` (`isProcessed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_candidates_createdAt` ON `memory_candidates` (`createdAt`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    // fallbackToDestructiveMigration 仅开发阶段保留，正式发版前移除
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
