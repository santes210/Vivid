package com.vivid.app.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

/** Limpia archivos de edición abandonados sin tocar datos del usuario. */
class CacheCleanupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        applicationContext.cacheDir.walkTopDown()
            .filter { it.isFile && VividCacheManager.isTemporaryFile(it) && it.lastModified() < cutoff }
            .forEach { it.delete() }
        Result.success()
    }.getOrElse { Result.retry() }
}
