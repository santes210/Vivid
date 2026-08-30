package com.vivid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivid.app.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts ORDER BY timestamp DESC")
    suspend fun getPostsOnce(): List<PostEntity>

    @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: String): PostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPost(post: PostEntity)

    @Query("UPDATE posts SET likesCount = :likesCount, isLiked = :isLiked WHERE id = :postId")
    suspend fun updateLike(postId: String, likesCount: Int, isLiked: Boolean)

    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deletePost(postId: String)

    /** Persiste la URL firmada re-generada (al expirar la anterior). */
    @Query("UPDATE posts SET imageUrl = :url WHERE id = :postId")
    suspend fun updateImageUrl(postId: String, url: String)

    /** Persiste la URL firmada de música re-generada (al expirar la anterior). */
    @Query("UPDATE posts SET musicUrl = :url WHERE id = :postId")
    suspend fun updateMusicUrl(postId: String, url: String)

    /** Persiste la URL firmada de video re-generada (al expirar la anterior). */
    @Query("UPDATE posts SET videoUrl = :url WHERE id = :postId")
    suspend fun updateVideoUrl(postId: String, url: String)

    @Query("DELETE FROM posts")
    suspend fun clearPosts()

    @Query("SELECT MAX(cachedAt) FROM posts")
    suspend fun getLastCachedAt(): Long?

    /**
     * Posts cacheados que llevan exactamente este hashtag. `hashtags` se
     * guarda con comas de ambos lados (",arte,musica,"), así el LIKE de
     * ",tag," no produce falsos positivos (",arte," no matchea ",smart,").
     */
    @Query("SELECT * FROM posts WHERE hashtags LIKE '%,' || :tag || ',%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getPostsByHashtag(tag: String, limit: Int): List<PostEntity>
}