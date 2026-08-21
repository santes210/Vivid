package com.vivid.shared.util

/**
 * Formateo de fechas y tiempos compartido.
 * Sin dependencias de plataforma - usa kotlinx-datetime.
 */
object TimeFormatter {

    /**
     * Formatea un timestamp como "hace X minutos/horas/días".
     * Usado en feeds, chats y perfiles.
     */
    fun formatRelativeTime(timestamp: Long, now: Long = Clock.currentTimeMillis()): String {
        val diffMs = now - timestamp
        if (diffMs < 0) return "justo ahora"

        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30
        val years = days / 365

        return when {
            seconds < 60 -> "justo ahora"
            minutes < 60 -> "hace ${minutes}m"
            hours < 24 -> "hace ${hours}h"
            days < 7 -> "hace ${days}d"
            weeks < 4 -> "hace ${weeks}sem"
            months < 12 -> "hace ${months}mes"
            else -> "hace ${years}a"
        }
    }

    /**
     * Formatea milisegundos como duración de audio/video: "M:SS" o "H:MM:SS".
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "${minutes}:${seconds.toString().padStart(2, '0')}"
        }
    }

    /**
     * Formatea un conteo grande: 1500 → "1.5K", 2500000 → "2.5M".
     */
    fun formatCount(count: Int): String {
        return when {
            count < 1000 -> count.toString()
            count < 1_000_000 -> {
                val k = count / 1000.0
                if (k == k.toLong().toDouble()) "${k.toLong()}K"
                else "${"%.1f".formatLocale(k)}K"
            }
            else -> {
                val m = count / 1_000_000.0
                if (m == m.toLong().toDouble()) "${m.toLong()}M"
                else "${"%.1f".formatLocale(m)}M"
            }
        }
    }
}

/** Extensión helper para formateo numérico sin Locale (KMP). */
internal fun String.formatLocale(vararg args: Any): String {
    // Simplificación multiplataforma: reemplaza %f/%d manualmente.
    var result = this
    for (arg in args) {
        val formatted = when (arg) {
            is Double -> {
                val intPart = arg.toLong()
                val decPart = ((arg - intPart) * 10).toLong().let { kotlin.math.abs(it) }
                "$intPart.$decPart"
            }
            is Float -> {
                val d = arg.toDouble()
                val intPart = d.toLong()
                val decPart = ((d - intPart) * 10).toLong().let { kotlin.math.abs(it) }
                "$intPart.$decPart"
            }
            else -> arg.toString()
        }
        result = result.replaceFirst("%f", formatted).replaceFirst("%d", arg.toString())
    }
    return result
}
