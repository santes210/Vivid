package com.vivid.app.util

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tracker de analytics para Reels.
 *
 * Metricas:
 *   - views: cuenta unica por usuario (no se incrementa cada vez)
 *   - watchTime: segundos totales de visualizacion (suma)
 *   - completedViews: vistas donde el usuario vio >=80% del reel
 *
 * Uso:
 *   val tracker = ReelAnalyticsTracker("reel_abc123", durationMs = 15000)
 *   tracker.start()       // cuando ExoPlayer empieza a reproducir
 *   tracker.complete()    // cuando el usuario vio el final
 *   tracker.cancel()      // cuando se scrollea a otro reel
 */
class ReelAnalyticsTracker(
    private val reelId: String,
    private val durationMs: Long
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var trackingJob: Job? = null
    private var startTimeMs: Long = 0
    private var watchTimeSeconds: Int = 0
    private var hasCompleted: Boolean = false

    /**
     * Empieza a contar watch-time. Se actualiza cada 2 segundos.
     */
    fun start() {
        startTimeMs = System.currentTimeMillis()
        trackingJob?.cancel()
        trackingJob = scope.launch {
            while (true) {
                delay(2000)
                val elapsed = System.currentTimeMillis() - startTimeMs
                val seconds = (elapsed / 1000).toInt()
                if (seconds > watchTimeSeconds) {
                    watchTimeSeconds = seconds
                    if (watchTimeSeconds % 5 == 0) {
                        // Flush cada 5 segundos para no abusar de Firestore
                        flushWatchTime()
                    }
                }
            }
        }
    }

    /**
     * Marca el reel como completado y sube las metricas finales.
     */
    fun complete() {
        if (!hasCompleted) {
            hasCompleted = true
            scope.launch {
                try {
                    firestore.collection("reels").document(reelId).update(
                        "completedViews", FieldValue.increment(1)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "complete failed", e)
                }
            }
        }
        flushWatchTime()
    }

    /**
     * Cancela el tracking sin marcar como completo (ej: scroll away).
     */
    fun cancel() {
        trackingJob?.cancel()
        trackingJob = null
        flushWatchTime()
    }

    /**
     * Sube el watch-time acumulado. No bloquea.
     */
    private fun flushWatchTime() {
        if (watchTimeSeconds == 0) return
        val toFlush = watchTimeSeconds
        watchTimeSeconds = 0
        scope.launch {
            try {
                firestore.collection("reels").document(reelId).update(
                    "totalWatchTimeSec", FieldValue.increment(toFlush.toLong())
                )
                Log.d(TAG, "Flushed $toFlush s for reel $reelId")
            } catch (e: Exception) {
                Log.w(TAG, "flush failed", e)
            }
        }
    }

    /**
     * Sube una vista unica. Llamar una vez por usuario por reel.
     */
    fun trackView(userId: String) {
        scope.launch {
            try {
                firestore.collection("reels").document(reelId)
                    .collection("viewers")
                    .document(userId)
                    .set(mapOf(
                        "viewedAt" to System.currentTimeMillis(),
                        "watchedSeconds" to (watchTimeSeconds)
                    ))
                firestore.collection("reels").document(reelId).update(
                    "viewsCount", FieldValue.increment(1)
                )
            } catch (e: Exception) {
                Log.w(TAG, "trackView failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "ReelAnalytics"
    }
}
