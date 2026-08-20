package com.vivid.app.util

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File

/**
 * Caché de videos/audio para ExoPlayer (media3) con TTL de 7 días.
 *
 * Los reels, stories de video y música de posts se reproducían SIEMPRE desde
 * B2 (streaming) — cada visita re-descargaba el archivo completo. Con este
 * caché, la primera reproducción guarda el archivo en disco y las siguientes
 * se sirven localmente hasta que:
 *   - el usuario borra el caché, o
 *   - pasan 7 días desde la última vez que se usó (evictor LRU).
 */
@UnstableApi
object VideoCacheManager {

    private const val TAG = "VideoCacheManager"
    private const val CACHE_DIR_NAME = "vivid_video_cache"
    private const val MAX_CACHE_BYTES = 500L * 1024L * 1024L
    private const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var ttlChecked = false

    private fun ensureFreshCache(context: Context) {
        if (ttlChecked) return
        ttlChecked = true

        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) return

        val lastModified = cacheDir.lastModified()
        if (System.currentTimeMillis() - lastModified > CACHE_TTL_MS) {
            android.util.Log.d(TAG, "Caché de video con más de 7 días, limpiando para regenerar")
            clearCache(context)
        }
    }

    /** Devuelve el SimpleCache compartido por la app. */
    fun getCache(context: Context): SimpleCache {
        ensureFreshCache(context)
        simpleCache?.let { return it }

        synchronized(this) {
            simpleCache?.let { return it }

            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)

            return SimpleCache(
                cacheDir,
                evictor,
                StandaloneDatabaseProvider(context)
            ).also { simpleCache = it }
        }
    }

    /** True si la URL es remota y por tanto se puede cachear. */
    fun isCacheable(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

    /**
     * Clave que ignora el Authorization de las URLs firmadas de B2.
     */
    private fun cacheKeyFor(url: String): String = url.substringBefore('?')

    private val strippedQueryCacheKeyFactory =
        androidx.media3.datasource.cache.CacheKeyFactory { uri ->
            cacheKeyFor(uri.toString())
        }

    /**
     * Elimina del dispositivo el recurso reproducido por ExoPlayer.
     *
     * Solo toca cacheDir de Vivid; nunca Fotos/Galería ni almacenamiento externo.
     */
    fun removeCachedMedia(context: Context, uri: String) {
        if (!isCacheable(uri)) return
        runCatching {
            getCache(context).removeResource(cacheKeyFor(uri))
        }
    }

    /** Crea una MediaSource usando caché. */
    fun buildCachedMediaSource(context: Context, uri: String): MediaSource {
        val cache = getCache(context)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val upstreamFactory = DefaultDataSource.Factory(
            context,
            httpDataSourceFactory
        )

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(strippedQueryCacheKeyFactory)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ProgressiveMediaSource.Factory(cacheDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))
    }

    /** Tamaño total del caché de video/audio. */
    fun cacheSizeBytes(context: Context): Long {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) return 0L

        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /** Borra todo el caché de video/audio privado de Vivid. */
    fun clearCache(context: Context) {
        synchronized(this) {
            try {
                simpleCache?.release()
            } catch (_: Exception) {
            }
            simpleCache = null
        }

        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        runCatching {
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        }
    }
}
