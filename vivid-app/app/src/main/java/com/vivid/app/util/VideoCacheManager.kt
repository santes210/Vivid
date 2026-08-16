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
    private const val MAX_CACHE_BYTES = 500L * 1024L * 1024L // 500 MB
    private const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L // 7 días

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var ttlChecked = false

    /**
     * Verifica (una vez por sesión) que el caché no tenga más de 7 días.
     * Si caducó, se borra para que la próxima reproducción lo regenere
     * (misma política que la caducidad de las URLs firmadas de B2).
     */
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

    /** Devuelve el SimpleCache compartido (singleton por proceso). */
    fun getCache(context: Context): SimpleCache {
        ensureFreshCache(context)
        simpleCache?.let { return it }
        synchronized(this) {
            simpleCache?.let { return it }
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            // Constructor con DatabaseProvider (el de 2 args está deprecado en
            // media3 1.4.x). StandaloneDatabaseProvider crea su propia DB SQLite
            // para el índice del caché.
            return SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(context))
                .also { simpleCache = it }
        }
    }

    /** True si la URL es remota (http/https) y por tanto cacheable. */
    fun isCacheable(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

    /**
     * Clave de caché que ignora el query string.
     *
     * Backblaze B2 emite un token de autorización NUEVO cada vez que se firma
     * una URL (`.../file/bucket/key?Authorization=...`). Con la clave por
     * defecto (URL completa), el token distinto hacía que el SimpleCache
     * fallara el lookup y se re-descargara el video completo en cada sesión.
     * Ignorando el query, el mismo archivo cacheado se reutiliza mientras su
     * contenido no cambie (las claves B2 son inmutables por path).
     *
     * Se corta el string a mano en vez de usar Uri.Builder.clearQuery(),
     * que solo existe desde API 30 (minSdk es 26).
     */
    private val strippedQueryCacheKeyFactory = androidx.media3.datasource.cache.CacheKeyFactory { uri ->
        val raw = uri.toString()
        val queryIndex = raw.indexOf('?')
        if (queryIndex >= 0) raw.substring(0, queryIndex) else raw
    }

    /** Crea una MediaSource con caché para [uri]. */
    fun buildCachedMediaSource(context: Context, uri: String): MediaSource {
        val cache = getCache(context)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(strippedQueryCacheKeyFactory)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ProgressiveMediaSource.Factory(cacheDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))
    }

    /** Tamaño del directorio de caché de video en bytes. */
    fun cacheSizeBytes(context: Context): Long {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) return 0L
        return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Borra TODO el caché de video. Se llama desde el botón "Borrar caché".
     * Libera el SimpleCache y elimina el directorio.
     */
    fun clearCache(context: Context) {
        synchronized(this) {
            try {
                simpleCache?.release()
            } catch (_: Exception) {
            }
            simpleCache = null
        }
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        runCatching { if (cacheDir.exists()) cacheDir.deleteRecursively() }
    }
}
