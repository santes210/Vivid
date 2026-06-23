package com.vivid.app.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Extrae un frame del video para usarlo como miniatura (poster) en el feed.
 *
 * Usa [MediaMetadataRetriever], API nativa de Android desde API 10.
 * No requiere librerías externas.
 *
 * Estrategia:
 *   - Toma un frame en el SEGUNDO 1 (evita el primer frame que suele ser negro).
 *   - Si el video es muy corto (< 1s) usa el frame del medio.
 *   - Devuelve un JPEG comprimido para que ocupe poco espacio en B2
 *     (típicamente 20-40 KB por thumbnail).
 */
object VideoThumbnailer {

    private const val TAG = "VideoThumbnailer"

    /**
     * Extrae un thumbnail del video.
     *
     * @return File con el JPEG generado, o null si no se pudo extraer.
     */
    suspend fun extract(
        context: Context,
        videoUri: Uri,
        outputFile: File,
        targetWidth: Int = 480
    ): File? = withContext(Dispatchers.IO) {
        if (outputFile.exists()) outputFile.delete()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            // Frame en el segundo 1, o a la mitad si el video es muy corto
            val timeUs = if (durationMs > 2000) 1_000_000L else (durationMs * 1000L) / 2

            val fullBitmap = retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: run {
                Log.w(TAG, "No se pudo extraer frame del video")
                return@withContext null
            }

            // Escalar al ancho objetivo manteniendo proporción
            val ratio = targetWidth.toFloat() / fullBitmap.width
            val targetHeight = (fullBitmap.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(fullBitmap, targetWidth, targetHeight, true)

            FileOutputStream(outputFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }

            fullBitmap.recycle()
            scaled.recycle()

            Log.d(TAG, "Thumbnail extraído: ${outputFile.length() / 1024} KB")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo thumbnail", e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
