package com.vivid.app.util

import android.content.Context
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
 * Recorta (trim) un video entre [startMs] y [endMs].
 *
 * Usa Media3 Transformer + ClippingConfiguration.
 * Es la forma "correcta" de hacer trim en Android sin re-encodear todo:
 *
 *   - Si solo cambias puntos de entrada/salida, el output mantiene
 *     la duración exacta sin perder calidad.
 *   - Reescribe el header MP4 sin tocar el bitstream.
 *
 * Útil para que el usuario recorte el inicio/final del reel antes
 * de subirlo.
 */
@UnstableApi
object VideoTrimmer {

    private const val TAG = "VideoTrimmer"

    /**
     * Recorta el video entre startMs y endMs (inclusive).
     *
     * @return path del archivo recortado. Si falla, devuelve el original.
     */
    suspend fun trim(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): String = withContext(Dispatchers.IO) {
        if (outputFile.exists()) outputFile.delete()

        // Sanity check
        if (endMs <= startMs) {
            Log.w(TAG, "trim: endMs <= startMs, fallback a original")
            return@withContext copyOriginal(context, inputUri, outputFile).absolutePath
        }

        try {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clipping)
                .build()
            val edited = EditedMediaItem.Builder(mediaItem).build()
            val composition = Composition.Builder(EditedMediaItemSequence(edited)).build()

            suspendCancellableCoroutine<String> { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        Log.d(
                            TAG,
                            "Trim OK ${endMs - startMs}ms → ${outputFile.length() / 1024} KB"
                        )
                        if (cont.isActive) cont.resume(outputFile.absolutePath)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e(TAG, "Error trim", exportException)
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                }

                val transformer = Transformer.Builder(context)
                    .addListener(listener)
                    .build()

                cont.invokeOnCancellation { transformer.cancel() }
                transformer.start(composition, outputFile.absolutePath)
            }.also { resultPath ->
                return@withContext if (File(resultPath).length() > 0) {
                    resultPath
                } else {
                    copyOriginal(context, inputUri, outputFile).absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Trim falló — fallback a original", e)
            copyOriginal(context, inputUri, outputFile).absolutePath
        }
    }

    private fun copyOriginal(context: Context, src: Uri, dst: File): File {
        context.contentResolver.openInputStream(src)?.use { input ->
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        return dst
    }
}
