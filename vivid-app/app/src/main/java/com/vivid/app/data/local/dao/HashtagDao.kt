package com.vivid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivid.app.data.local.entity.HashtagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HashtagDao {

    @Query("SELECT * FROM hashtags ORDER BY count DESC, tag ASC")
    fun observeAll(): Flow<List<HashtagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tags: List<HashtagEntity>)

    /** Elimina tags que ya no aparecen en posts recientes (antigüedad en ms). */
    @Query("DELETE FROM hashtags WHERE lastSeenAt < :threshold")
    suspend fun pruneOlderThan(threshold: Long)

    @Query("DELETE FROM hashtags")
    suspend fun clear()
}
