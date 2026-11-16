package com.vivid.app.presentation.messages

object VideoControlMath {
    fun seekTarget(positionMs: Long, durationMs: Long, forward: Boolean): Long {
        if (durationMs <= 0) return 0
        val current = positionMs.coerceIn(0, durationMs)
        return if (forward) current + minOf(10_000L, durationMs - current)
        else (current - 10_000L).coerceAtLeast(0)
    }

    fun timeLabel(timeMs: Long): String {
        val seconds = timeMs.coerceAtLeast(0) / 1000
        val minutes = seconds / 60
        val remainder = (seconds % 60).toString().padStart(2, '0')
        return if (minutes < 60) "$minutes:$remainder"
        else "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$remainder"
    }
}
