package com.vivid.app.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Compresión de videos usando la librería `android-transcoder` (Otalia Studios),
 * wrapper ligero sobre MediaCodec (GPU-accelerated, ~200KB APK vs 30-50MB FFmpeg).
 *
 * Problema anterior: solo seteaba bitrate, no resolución. Si el video original
 * era 720p a 3.5Mbps, comprimir a 3.5Mbps de nuevo no reduce nada (20MB -> 17MB).
 *
 * Fix 2026-08-08:
 * - Ahora usa atMost(720,1280) que fuerza downscale si el video es más grande.
 * - Bitrates más agresivos: 0.8Mbps dataSaver, 1.2-1.5Mbps estándar, 2.5Mbps HD max.
 * - Default hdUploads = false para que el estándar comprima fuerte.
 * - Si el output es más grande que el input, se usa el input (evita inflar).
 */
object VideoCompressor {

    private const val TAG = "VideoCompressor"

    /**
     * Comprime el video y devuelve el path del archivo MP4 resultante
     * en el cacheDir. Si falla, devuelve el original.
     */
    suspend fun compress(
        context: Context,
        inputUri: Uri,
        onProgress: (Int) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "reel_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()
        onProgress(5)

        try {
            Log.d(TAG, "Comprimiendo ${inputUri.lastPathSegment ?: "video"}")
            onProgress(10)

            val isHd = com.vivid.app.util.SettingsManager.hdUploadsEnabled
            val isDataSaver = com.vivid.app.util.SettingsManager.dataSaverMode

            // Bitrates optimizados para TikTok-like
            // 20MB original (30s, ~5Mbps) -> con 1.2Mbps = 4.5MB (77% reducción)
            val targetBitrate = when {
                isDataSaver -> 600_000L   // 0.6 Mbps - ultra ligero 480p
                isHd -> 2_500_000L        // 2.5 Mbps - HD pero 720p max
                else -> 1_200_000L        // 1.2 Mbps - estándar balanceado (default)
            }

            val targetAudioBitrate = when {
                isDataSaver -> 64_000L
                else -> 96_000L
            }

            // Resolución máxima según modo
            val (maxWidth, maxHeight) = when {
                isDataSaver -> 480 to 854   // 480p vertical
                else -> 720 to 1280         // 720p vertical (TikTok/IG standard)
            }

            Log.d(TAG, "Target: ${maxWidth}x$maxHeight @ ${targetBitrate/1000}kbps (HD=$isHd, Saver=$isDataSaver)")

            // Estrategia que asegura downscale si es necesario + bitrate
            val videoStrategy = DefaultVideoStrategy.atMost(maxWidth, maxHeight)
                .bitRate(targetBitrate)
                .frameRate(30) // cappa al input frameRate
                .build()

            val audioStrategy = DefaultAudioStrategy.Builder()
                .bitRate(targetAudioBitrate)
                .channels(DefaultAudioStrategy.CHANNELS_AS_INPUT)
                .sampleRate(DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT)
                .build()

            val resultPath = suspendCancellableCoroutine<String> { cont ->
                val future = Transcoder.into(outputFile.absolutePath)
                    .addDataSource(context, inputUri)
                    .setVideoTrackStrategy(videoStrategy)
                    .setAudioTrackStrategy(audioStrategy)
                    .setListener(object : TranscoderListener {
                        override fun onTranscodeProgress(progress: Double) {
                            val pct = (15 + progress * 70).toInt().coerceIn(15, 90)
                            onProgress(pct)
                        }

                        override fun onTranscodeCompleted(successCode: Int) {
                            Log.d(TAG, "Compresión OK code=$successCode size=${outputFile.length() / 1024}KB (orig aprox calculada en log anterior)")
                            onProgress(100)
                            if (cont.isActive) cont.resume(outputFile.absolutePath)
                        }

                        override fun onTranscodeCanceled() {
                            Log.w(TAG, "Compresión cancelada")
                            if (cont.isActive) cont.resumeWithException(RuntimeException("Transcode canceled"))
                        }

                        override fun onTranscodeFailed(exception: Throwable) {
                            Log.e(TAG, "Compresión falló", exception)
                            if (cont.isActive) cont.resumeWithException(exception)
                        }
                    })
                    .transcode()

                cont.invokeOnCancellation { future.cancel(true) }
            }

            val output = File(resultPath)
            if (output.exists() && output.length() > 0) {
                // Si el comprimido quedó MÁS GRANDE que el original (puede pasar si original ya es 480p a bajo bitrate), usa original
                val inputSize = getInputSize(context, inputUri)
                Log.d(TAG, "InputSize=${inputSize/1024}KB -> OutputSize=${output.length()/1024}KB")
                if (inputSize > 0 && output.length() > inputSize * 1.1) { // 10% tolerancia
                    Log.w(TAG, "Comprimido más grande que original, usando original")
                    val fallback = File(context.cacheDir, "reel_orig_${System.currentTimeMillis()}.mp4")
                    copyToCache(context, inputUri, fallback)
                    fallback.absolutePath
                } else {
                    resultPath
                }
            } else {
                Log.w(TAG, "Compresión no produjo output, subiendo original")
                copyToCache(context, inputUri, outputFile)
                outputFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error comprimiendo — fallback a original", e)
            copyToCache(context, inputUri, outputFile)
            outputFile.absolutePath
        }
    }

    private fun getInputSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun copyToCache(context: Context, src: Uri, dst: File) {
        context.contentResolver.openInputStream(src)?.use { input ->
            dst.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
