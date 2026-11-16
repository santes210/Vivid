package com.vivid.app.util

/** Source-time selection translated to the exported (possibly trimmed) video. */
internal object VideoCoverTiming {
    fun exportedTime(sourceTimeMs: Long, trimStartMs: Long, trimEndMs: Long): Long {
        val start = if (trimStartMs >= 0 && trimEndMs > trimStartMs) trimStartMs else 0L
        return (sourceTimeMs.coerceAtLeast(0L) - start).coerceAtLeast(0L)
    }

    fun frameTime(requestedMs: Long?, durationMs: Long): Long {
        val duration = durationMs.coerceAtLeast(0L)
        return (requestedMs ?: if (duration > 2000) 1000L else duration / 2)
            .coerceIn(0L, (duration - 1).coerceAtLeast(0L))
    }
}
