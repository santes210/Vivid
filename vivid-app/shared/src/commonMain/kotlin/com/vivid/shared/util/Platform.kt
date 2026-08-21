package com.vivid.shared.util

/**
 * Funciones multiplataforma que cada sistema operativo implementa de forma nativa.
 *
 * Android → androidMain/Platform.kt
 * iOS     → iosMain/Platform.kt
 */
expect class Platform() {
    /** Nombre del sistema operativo: "Android" o "iOS". */
    val name: String

    /** Versión del sistema operativo. */
    val version: String

    /** Identificador único del dispositivo (para analytics no-personal). */
    val deviceId: String
}

/**
 * Reloj multiplataforma para obtener timestamps consistentes.
 */
expect object Clock {
    /** Milisegundos desde epoch (UTC). */
    fun currentTimeMillis(): Long
}

/**
 * Logger multiplataforma.
 * Android → Logcat, iOS → os_log / print.
 */
expect object VividLog {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * UUID multiplataforma.
 */
expect fun generateUUID(): String

/**
 * Codificación Base64 multiplataforma.
 */
expect fun encodeBase64(bytes: ByteArray): String
expect fun decodeBase64(encoded: String): ByteArray
