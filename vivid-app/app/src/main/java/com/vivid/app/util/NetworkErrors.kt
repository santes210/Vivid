package com.vivid.app.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Timeout por defecto para las operaciones de red de la UI (20 segundos). */
const val VIVID_NETWORK_TIMEOUT_MS = 20_000L

/**
 * Lanzada cuando una operación de red supera el timeout. Es IOException, así
 * que cualquier `catch (Exception)` existente la captura sin cambios.
 */
class NetworkTimeoutException(
    val tag: String,
    val timeoutMs: Long,
    cause: TimeoutCancellationException
) : IOException("$tag excedió el timeout de ${timeoutMs / 1000}s", cause)

/**
 * Envuelve un bloque de red con un timeout. Si se supera, lanza
 * [NetworkTimeoutException], que la UI traduce a un mensaje con reintento
 * vía [Throwable.toUserFacingMessage].
 *
 * Ejemplo:
 * ```
 * val snapshot = withNetworkTimeout("feed.loadPosts") { query.get().await() }
 * ```
 */
suspend fun <T> withNetworkTimeout(tag: String, block: suspend () -> T): T =
    withNetworkTimeout(tag, VIVID_NETWORK_TIMEOUT_MS, block)

suspend fun <T> withNetworkTimeout(tag: String, timeoutMs: Long, block: suspend () -> T): T {
    return try {
        withTimeout(timeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        throw NetworkTimeoutException(tag, timeoutMs, e)
    }
}

/**
 * ¿El error (o alguna de sus causas) es un problema de conectividad?
 * Cubre: sin DNS, conexión rechazada, timeout de socket, timeout propio de la
 * app, Firebase sin red y Firestore UNAVAILABLE (servidor inalcanzable).
 */
fun Throwable.isConnectivityError(): Boolean {
    val chain = generateSequence<Throwable>(this) { it.cause }.toList()
    return chain.any {
        it is UnknownHostException ||
            it is ConnectException ||
            it is SocketTimeoutException ||
            it is NetworkTimeoutException ||
            it is FirebaseNetworkException ||
            (it is FirebaseFirestoreException && it.code == FirebaseFirestoreException.Code.UNAVAILABLE)
    }
}

/**
 * Mensaje consistente para el usuario, igual en todas las pantallas:
 * - timeout → "tardó demasiado" (se puede reintentar)
 * - sin conexión → "sin conexión" (se puede reintentar cuando vuelva la red)
 * - cualquier otra cosa → el texto fallback de la pantalla.
 */
fun Throwable.toUserFacingMessage(fallback: String): String = when {
    this is NetworkTimeoutException || this is SocketTimeoutException ->
        "La conexión tardó demasiado. Revisa tu internet e inténtalo de nuevo."
    isConnectivityError() ->
        "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    else -> fallback
}
