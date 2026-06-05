package com.zaijian.zhoumuyun.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zaijian.zhoumuyun.data.db.entity.CharacterIdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterIdentityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(identity: CharacterIdentityEntity)

    @Query("SELECT * FROM character_identity WHERE characterId = :characterId")
    suspend fun getById(characterId: Int): CharacterIdentityEntity?

    @Query("SELECT * FROM character_identity WHERE characterId = :characterId")
    fun observeById(characterId: Int): Flow<CharacterIdentityEntity?>

    @Query("SELECT * FROM character_identity")
    suspend fun getAll(): List<CharacterIdentityEntity>
}
