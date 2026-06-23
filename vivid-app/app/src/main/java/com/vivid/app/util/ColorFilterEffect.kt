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
 * Filtros de color para Reels.
 *
 * Usa Media3 RgbMatrix que es GPU-accelerated:
 *   - Sepia       (tonos calidos marrones)
 *   - Blanco/Negro (luminancia pura)
 *   - Contraste   (S-curve)
 *   - Frio        (azulado)
 *   - Calido      (anaranjado)
 *
 * Las matrices son 4x5 (RGBA) como en cualquier filtro GLSL.
 * Cada fila aplica una transformacion lineal al pixel.
 */
@UnstableApi
object ColorFilterEffect {

    private const val TAG = "ColorFilter"

    /**
     * Filtros disponibles. Cada uno tiene:
     *   - id: nombre identificador
     *   - displayName: nombre que ve el usuario
     *   - matrix: matriz 4x5 para RgbMatrix
     */
    enum class Filter(
        val id: String,
        val displayName: String,
        val matrix: FloatArray
    ) {
        NONE("none", "Normal", floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
        SEPIA("sepia", "Sepia", floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
        BLACK_AND_WHITE("bw", "B&N", floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
        CONTRAST("contrast", "Contraste", floatArrayOf(
            1.5f, 0f, 0f, 0f, -50f,
            0f, 1.5f, 0f, 0f, -50f,
            0f, 0f, 1.5f, 0f, -50f,
            0f, 0f, 0f, 1f, 0f
        )),
        WARM("warm", "Calido", floatArrayOf(
            1.2f, 0f, 0f, 0f, 20f,
            0f, 1.1f, 0f, 0f, 10f,
            0f, 0f, 0.9f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
        COOL("cool", "Frio", floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 1.0f, 0f, 0f, 5f,
            0f, 0f, 1.2f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )),
        VIVID("vivid", "Vivido", floatArrayOf(
            1.3f, -0.1f, -0.1f, 0f, 0f,
            -0.1f, 1.3f, -0.1f, 0f, 0f,
            -0.1f, -0.1f, 1.3f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
        FADE("fade", "Fade", floatArrayOf(
            0.9f, 0f, 0f, 0f, 20f,
            0f, 0.9f, 0f, 0f, 20f,
            0f, 0f, 0.9f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    /**
     * Aplica el filtro al video. Devuelve path del MP4 con filtro aplicado.
     * Si falla, devuelve el original.
     */
    suspend fun apply(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        filter: Filter
    ): String = withContext(Dispatchers.IO) {
        if (filter == Filter.NONE) {
            Log.d(TAG, "Filtro NONE, copiando original")
            return@withContext copyOriginal(context, inputUri, outputFile).absolutePath
        }
        if (outputFile.exists()) outputFile.delete()

        try {
            // RgbMatrix es una INTERFAZ en Media3 1.4.1 (no un constructor que
            // recibe FloatArray). La implementamos devolviendo la matriz del filtro.
            // Como RgbMatrix extends GlEffect extends Effect, se puede usar
            // directamente como videoEffect sin RgbFilter.createMatrixEffect.
            val rgbMatrix = object : androidx.media3.effect.RgbMatrix {
                override fun getMatrix(
                    presentationTimeUs: Long,
                    useHdr: Boolean
                ): FloatArray = filter.matrix
            }

            val mediaItem = MediaItem.fromUri(inputUri)
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    androidx.media3.transformer.Effects(
                        /* audioProcessors = */ emptyList(),
                        /* videoEffects    = */ listOf(rgbMatrix)
                    )
                )
                .build()
            val composition = Composition.Builder(EditedMediaItemSequence(edited)).build()

            suspendCancellableCoroutine<String> { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, result: ExportResult) {
                        Log.d(TAG, "Filtro ${filter.id} aplicado: ${outputFile.length() / 1024} KB")
                        if (cont.isActive) cont.resume(outputFile.absolutePath)
                    }
                    override fun onError(
                        comp: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        Log.e(TAG, "Error filtro", exception)
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                }
                val transformer = Transformer.Builder(context).addListener(listener).build()
                cont.invokeOnCancellation { transformer.cancel() }
                transformer.start(composition, outputFile.absolutePath)
            }.also { path ->
                return@withContext if (File(path).length() > 0) path
                else copyOriginal(context, inputUri, outputFile).absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Filtro fallo - fallback a original", e)
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
