package com.vivid.shared.util

import kotlin.math.max

/**
 * Utilidades de red compartidas: detección de errores, reintentos, etc.
 */
object NetworkUtils {

    /**
     * Determina si un error es recuperable (se puede reintentar).
     */
    fun isRecoverableError(throwable: Throwable): Boolean {
        val message = throwable.message?.lowercase() ?: return false
        return message.contains("timeout") ||
            message.contains("network") ||
            message.contains("unavailable") ||
            message.contains("connection")
    }

    /**
     * Calcula el delay de un reintento exponencial con jitter.
     * @param attempt Número de intento (0-based).
     * @param baseDelayMs Delay base en milisegundos.
     * @param maxDelayMs Delay máximo en milisegundos.
     */
    fun exponentialBackoff(
        attempt: Int,
        baseDelayMs: Long = 1000L,
        maxDelayMs: Long = 30000L
    ): Long {
        val delay = baseDelayMs * (1L shl max(0, attempt))
        return delay.coerceAtMost(maxDelayMs)
    }
}
