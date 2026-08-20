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

/**
 * Recorta video entre startMs y endMs — FIX 2026-08-09: más robusto, no crashea.
 */
@UnstableApi
object VideoTrimmer {

    private const val TAG = "VideoTrimmer"

    suspend fun trim(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): String = withContext(Dispatchers.IO) {
        try {
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            if (endMs <= startMs) {
                Log.w(TAG, "end <= start, fallback")
                return@withContext copyOriginal(context, inputUri, outputFile).absolutePath
            }

            Log.d(TAG, "Trimming ${startMs}..${endMs} ms")

            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs.coerceAtLeast(0))
                .setEndPositionMs(endMs)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clipping)
                .build()
            val edited = EditedMediaItem.Builder(mediaItem).build()
            // Constructor directo retirado en Media3 1.11: conservar ambas pistas
            // mantiene el audio sincronizado en el video recortado.
            val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited))
            val composition = Composition.Builder(sequence).build()

            val resultPath = suspendCancellableCoroutine<String> { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        val size = outputFile.length()
                        Log.d(TAG, "Trim OK ${endMs - startMs}ms -> ${size / 1024}KB")
                        if (size > 1024) {
                            if (cont.isActive) cont.resume(outputFile.absolutePath)
                        } else {
                            Log.w(TAG, "Trim output pequeño, fallback")
                            if (cont.isActive) cont.resume(copyOriginal(context, inputUri, outputFile).absolutePath)
                        }
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Log.e(TAG, "Trim error, fallback a original", exportException)
                        if (cont.isActive) {
                            cont.resume(copyOriginal(context, inputUri, outputFile).absolutePath)
                        }
                    }
                }

                val transformer = try {
                    Transformer.Builder(context)
                        .addListener(listener)
                        .build()
                } catch (e: Exception) {
                    Log.e(TAG, "Builder falló: ${e.message}")
                    if (cont.isActive) cont.resume(copyOriginal(context, inputUri, outputFile).absolutePath)
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation { transformer.cancel() }
                try {
                    transformer.start(composition, outputFile.absolutePath)
                } catch (e: Exception) {
                    Log.e(TAG, "start falló: ${e.message}", e)
                    if (cont.isActive) cont.resume(copyOriginal(context, inputUri, outputFile).absolutePath)
                }
            }

            resultPath
        } catch (e: Exception) {
            Log.e(TAG, "Trim falló — fallback", e)
            try {
                copyOriginal(context, inputUri, outputFile).absolutePath
            } catch (e2: Exception) {
                Log.e(TAG, "copyOriginal falló", e2)
                inputUri.path ?: outputFile.absolutePath
            }
        }
    }

    private fun copyOriginal(context: Context, src: Uri, dst: File): File {
        return try {
            dst.parentFile?.mkdirs()
            if (src.scheme == "file") {
                val srcFile = File(src.path ?: "")
                if (srcFile.exists()) {
                    srcFile.copyTo(dst, overwrite = true)
                    return dst
                }
            }
            context.contentResolver.openInputStream(src)?.use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            dst
        } catch (e: Exception) {
            Log.e(TAG, "copyOriginal falló: ${e.message}", e)
            dst
        }
    }
}
