package com.vivid.app.util

import android.content.Context
import coil3.ImageLoader
import com.vivid.app.data.local.VividDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestiona el caché local de la app con TTL de 7 días.
 *
 * Almacena cuándo se cacheó por última vez cada tipo de contenido
 * (posts, stories, reels) y provee limpieza real de:
 *  - Base de datos Room
 *  - Caché de disco de Coil (imágenes)
 *  - Archivos temporales
 */
object VividCacheManager {

    data class CacheBreakdown(
        val databaseBytes: Long = 0L,
        val imageBytes: Long = 0L,
        val mediaBytes: Long = 0L,
        val temporaryBytes: Long = 0L
    ) {
        val totalBytes: Long get() = databaseBytes + imageBytes + mediaBytes + temporaryBytes
    }

    internal fun isTemporaryFile(file: File): Boolean = temporaryPrefixes.any(file.name::startsWith)

    private val temporaryPrefixes = listOf(
        "story_", "reel_", "post_music_", "compressed_", "trimmed_",
        "watermarked_", "wm_", "thumb_", "voice_"
    )

    private const val PREFS_NAME = "vivid_cache_control"
    private const val KEY_POSTS_CACHED_AT = "posts_cached_at"
    private const val KEY_STORIES_CACHED_AT = "stories_cached_at"
    private const val KEY_REELS_CACHED_AT = "reels_cached_at"

    /** 7 días en milisegundos */
    const val CACHE_TTL_MS: Long = 7L * 24L * 60L * 60L * 1000L

    // ── Control de expiración ──

    fun arePostsFresh(context: Context): Boolean {
        return isFresh(context, KEY_POSTS_CACHED_AT)
    }

    fun areStoriesFresh(context: Context): Boolean {
        return isFresh(context, KEY_STORIES_CACHED_AT)
    }

    fun areReelsFresh(context: Context): Boolean {
        return isFresh(context, KEY_REELS_CACHED_AT)
    }

    fun markPostsCached(context: Context) {
        markCached(context, KEY_POSTS_CACHED_AT)
    }

    fun markStoriesCached(context: Context) {
        markCached(context, KEY_STORIES_CACHED_AT)
    }

    fun markReelsCached(context: Context) {
        markCached(context, KEY_REELS_CACHED_AT)
    }

    private fun isFresh(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCached = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        return lastCached > 0 && (now - lastCached) < CACHE_TTL_MS
    }

    private fun markCached(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(key, System.currentTimeMillis())
            .apply()
    }

    fun resetCacheTimestamps(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_POSTS_CACHED_AT)
            .remove(KEY_STORIES_CACHED_AT)
            .remove(KEY_REELS_CACHED_AT)
            .apply()
    }

    // ── Cálculo de tamaño ──

    /**
     * Calcula el tamaño real de todos los cachés combinados en MB.
     */
    suspend fun cacheBreakdown(context: Context): CacheBreakdown = withContext(Dispatchers.IO) {
        val db = context.getDatabasePath("vivid_database")
        val database = sequenceOf(db, File("${db.path}-wal"), File("${db.path}-shm"))
            .filter { it.isFile }.sumOf { it.length() }
        val imageDir = context.cacheDir.resolve("vivid_image_cache")
        val images = if (imageDir.exists()) imageDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        val media = VideoCacheManager.cacheSizeBytes(context)
        val temporary = context.cacheDir.walkTopDown()
            .filter { it.isFile && temporaryPrefixes.any(it.name::startsWith) }
            .sumOf { it.length() }
        CacheBreakdown(database, images, media, temporary)
    }

    suspend fun calculateCacheSizeMB(context: Context): Float =
        (cacheBreakdown(context).totalBytes / (1024f * 1024f)).coerceAtLeast(0f)

    /** Borra únicamente medios temporales; conserva mensajes y contenido offline. */
    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    suspend fun clearMediaCaches(context: Context, imageLoader: ImageLoader) = withContext(Dispatchers.IO) {
        imageLoader.diskCache?.clear()
        VideoCacheManager.clearCache(context)
        deleteTemporaryFiles(context)
    }

    /** Borra el contenido offline de Room, explícitamente separado del caché multimedia. */
    suspend fun clearOfflineContent(database: VividDatabase) = withContext(Dispatchers.IO) {
        database.postDao().clearPosts()
        database.storyDao().clearStories()
        database.reelDao().clearReels()
    }

    /** Limpia TODOS los cachés y datos offline (acción avanzada). */
    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    suspend fun clearAllCaches(
        context: Context,
        database: VividDatabase,
        imageLoader: ImageLoader
    ) = withContext(Dispatchers.IO) {
        // 1. Room: limpiar todas las tablas de contenido cacheados
        database.postDao().clearPosts()
        database.storyDao().clearStories()
        database.reelDao().clearReels()
        database.messageDao().clearAllMessages()
        database.chatDao().clearAllChats()
        database.userDao().clearAllUsers()

        // 2. Caché de disco de Coil
        imageLoader.diskCache?.clear()

        // 3. Caché de video/audio de ExoPlayer
        VideoCacheManager.clearCache(context)

        // 4. Archivos temporales
        val cacheDir = context.cacheDir
        if (cacheDir.exists()) {
            cacheDir.walkTopDown()
                .filter { it.isFile && (
                    it.name.startsWith("story_") ||
                    it.name.startsWith("reel_") ||
                    it.name.startsWith("post_music_") ||
                    it.name.startsWith("compressed_") ||
                    it.name.startsWith("trimmed_") ||
                    it.name.startsWith("watermarked_") ||
                    it.name.startsWith("wm_") ||
                    it.name.startsWith("thumb_")
                ) }
                .forEach { it.delete() }
        }

        // 5. Resetear timestamps de caché
        resetCacheTimestamps(context)
    }

    /**
     * Limpia solo los archivos temporales (no Room ni Coil).
     */
    suspend fun clearTempFilesOnly(context: Context) = withContext(Dispatchers.IO) {
        deleteTemporaryFiles(context)
    }

    private fun deleteTemporaryFiles(context: Context) {
        context.cacheDir.walkTopDown()
            .filter { it.isFile && temporaryPrefixes.any(it.name::startsWith) }
            .forEach { it.delete() }
    }
}
