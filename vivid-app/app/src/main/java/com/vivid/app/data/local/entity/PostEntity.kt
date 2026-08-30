package com.vivid.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vivid.app.util.Hashtags

/**
 * Cache Room de un post (feed offline + fallback offline de Explorar).
 *
 * `hashtags` guarda los tags serializados con comas de ambos lados
 * (ver [Hashtags.joinForCache]) para poder filtrar por tag exacto en SQL.
 */
@Immutable
@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val userProfilePicture: String,
    val imageUrl: String = "",
    val imageBase64: String = "",
    val caption: String,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val timestamp: Long,
    val isLiked: Boolean = false,
    val storageKey: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val isVideo: Boolean = false,
    val musicTitle: String = "",
    val musicArtist: String = "",
    val musicAssetFile: String = "",
    val musicUrl: String = "",
    val musicStorageKey: String = "",
    val hashtags: String = "",
    val cachedAt: Long = System.currentTimeMillis()
)

/** Entity → modelo de UI (reutilizado por feed y explore). */
fun PostEntity.toPostData() = com.vivid.app.presentation.feed.PostData(
    id = id,
    userId = userId,
    username = username,
    userProfilePicture = userProfilePicture,
    imageUrl = imageUrl,
    imageBase64 = imageBase64,
    storageKey = storageKey,
    videoUrl = videoUrl,
    thumbnailUrl = thumbnailUrl,
    isVideo = isVideo,
    caption = caption,
    likesCount = likesCount,
    commentsCount = commentsCount,
    timestamp = timestamp,
    isLiked = isLiked,
    musicTitle = musicTitle,
    musicArtist = musicArtist,
    musicAssetFile = musicAssetFile,
    musicUrl = musicUrl,
    musicStorageKey = musicStorageKey,
    hashtags = Hashtags.splitFromCache(hashtags)
)

/** Modelo de UI → entity de cache (posts traídos por Explorar). */
fun com.vivid.app.presentation.feed.PostData.toCachedEntity(cachedAt: Long = System.currentTimeMillis()) = PostEntity(
    id = id,
    userId = userId,
    username = username,
    userProfilePicture = userProfilePicture,
    imageUrl = imageUrl,
    imageBase64 = imageBase64,
    caption = caption,
    likesCount = likesCount,
    commentsCount = commentsCount,
    timestamp = timestamp,
    isLiked = isLiked,
    storageKey = storageKey,
    videoUrl = videoUrl,
    thumbnailUrl = thumbnailUrl,
    isVideo = isVideo,
    musicTitle = musicTitle,
    musicArtist = musicArtist,
    musicAssetFile = musicAssetFile,
    musicUrl = musicUrl,
    musicStorageKey = musicStorageKey,
    hashtags = Hashtags.joinForCache(hashtags),
    cachedAt = cachedAt
)
