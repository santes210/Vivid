package com.vivid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivid.app.data.local.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY createdAt DESC")
    fun getAllStories(): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE expiresAt > :now ORDER BY createdAt DESC")
    suspend fun getActiveStoriesOnce(now: Long): List<StoryEntity>

    @Query("SELECT * FROM stories ORDER BY createdAt DESC")
    suspend fun getStoriesOnce(): List<StoryEntity>

    @Query("SELECT * FROM stories WHERE userId = :userId ORDER BY createdAt DESC")
    fun getStoriesByUser(userId: String): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE id = :storyId LIMIT 1")
    suspend fun getStoryById(storyId: String): StoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<StoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStory(story: StoryEntity)

    @Query("DELETE FROM stories WHERE expiresAt <= :now")
    suspend fun deleteExpiredStories(now: Long)

    @Query("DELETE FROM stories")
    suspend fun clearStories()

    @Query("SELECT MAX(cachedAt) FROM stories")
    suspend fun getLastCachedAt(): Long?
}