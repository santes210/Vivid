package com.vivid.app.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Recorta audio a un segmento de máximo 15s (o el que el usuario elija).
 * Usa Media3 Transformer con ClippingConfiguration — funciona para mp3, m4a, wav, ogg, etc.
 *
 * Flujo:
 *   inputUri (canción completa del dispositivo o asset copiado a cache)
 *   -> trim con startMs / endMs
 *   -> outputFile.mp3/m4a (según input)
 */
@UnstableApi
object AudioTrimmer {

    private const val TAG = "AudioTrimmer"

    suspend fun trimAudio(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): String = withContext(Dispatchers.IO) {
        if (outputFile.exists()) outputFile.delete()
        if (endMs <= startMs) {
            Log.w(TAG, "end <= start, fallback a original")
            return@withContext copyOriginal(context, inputUri, outputFile).absolutePath
        }

        try {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs.coerceAtLeast(0))
                .setEndPositionMs(endMs)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clipping)
                .build()

            val edited = EditedMediaItem.Builder(mediaItem).build()
            val composition = Composition.Builder(EditedMediaItemSequence(edited)).build()

            val result = suspendCancellableCoroutine<String> { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Log.d(TAG, "Audio trim OK ${endMs - startMs}ms -> ${outputFile.length()/1024}KB")
                        if (cont.isActive) cont.resume(outputFile.absolutePath)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Log.e(TAG, "Audio trim error", exportException)
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                }

                val transformer = Transformer.Builder(context)
                    .addListener(listener)
                    .build()

                cont.invokeOnCancellation { transformer.cancel() }
                transformer.start(composition, outputFile.absolutePath)
            }

            if (File(result).exists() && File(result).length() > 0) result else copyOriginal(context, inputUri, outputFile).absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Trim audio falló, fallback a original", e)
            copyOriginal(context, inputUri, outputFile).absolutePath
        }
    }

    /**
     * Obtiene duración de un audio/video en ms.
     * Reutilizable para UI de recorte.
     */
    fun getDurationMs(context: Context, uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            dur
        } catch (_: Exception) { 0L }
    }

    private fun copyOriginal(context: Context, src: Uri, dst: File): File {
        try {
            context.contentResolver.openInputStream(src)?.use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "copyOriginal falló", e)
        }
        return dst
    }
}
