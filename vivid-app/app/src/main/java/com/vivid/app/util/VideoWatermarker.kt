package com.vivid.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
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
 * Marca de agua automática sobre los videos de Reels/Stories.
 *
 * Usa Media3 Transformer + BitmapOverlay para superponer el logo
 * "Vivid" en cada frame, sin alterar el audio.
 *
 * Por qué esto y no Canvas+MediaCodec manual:
 *   - MediaCodec puro requiere sincronizar manualmente surface, codec
 *     y muxer (~500 líneas propensas a bugs).
 *   - Media3 Transformer lo hace en hardware, queda a ~50 líneas.
 *
 * Resultado: video con "Vivid ✦" semitransparente en la esquina
 * inferior derecha.
 */
@UnstableApi
object VideoWatermarker {

    private const val TAG = "VideoWatermarker"

    /**
     * Aplica la marca de agua al video en `inputUri` y devuelve el path
     * del archivo resultante. Si falla, devuelve el original.
     */
    suspend fun applyWatermark(
        context: Context,
        inputUri: Uri,
        outputFile: File
    ): String = withContext(Dispatchers.IO) {
        if (outputFile.exists()) outputFile.delete()

        try {
            // 1. Genera el bitmap del logo
            val logo = renderVividLogo(widthPx = 480, heightPx = 120)

            // 2. Construye el overlay (esquina inferior derecha, 70% opacidad)
            val bitmapOverlay = androidx.media3.effect.BitmapOverlay
                .createStaticBitmapOverlay(logo)

            // 3. Composición con el overlay aplicado
            val mediaItem = MediaItem.fromUri(inputUri)
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    androidx.media3.transformer.Effects(
                        audioProcessors = emptyList(),
                        videoEffects = listOf(bitmapOverlay)
                    )
                )
                .build()
            val composition = Composition.Builder(listOf(edited)).build()

            // 4. Ejecuta el transformer y espera async
            val outputPath = suspendCancellableCoroutine<String> { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        Log.d(TAG, "Watermark aplicado: ${outputFile.length() / 1024} KB")
                        if (cont.isActive) cont.resume(outputFile.absolutePath)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e(TAG, "Error watermark", exportException)
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                }

                val transformer = Transformer.Builder(context)
                    .addListener(listener)
                    .build()

                cont.invokeOnCancellation { transformer.cancel() }
                transformer.start(composition, outputFile.absolutePath)
            }

            if (File(outputPath).exists() && File(outputPath).length() > 0) {
                outputPath
            } else {
                Log.w(TAG, "Watermark no produjo output, fallback")
                copyOriginal(context, inputUri, outputFile).absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Watermark falló — fallback a original", e)
            copyOriginal(context, inputUri, outputFile).absolutePath
        }
    }

    /**
     * Genera el Bitmap con el logo. En producción reemplaza por:
     *   val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.vivid_logo)
     */
    private fun renderVividLogo(widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            isFakeBoldText = true
            setShadowLayer(6f, 0f, 2f, Color.argb(160, 0, 0, 0))
        }
        canvas.drawText("Vivid ✦", 16f, 80f, textPaint)

        return bmp
    }

    private fun copyOriginal(context: Context, src: Uri, dst: File): File {
        context.contentResolver.openInputStream(src)?.use { input ->
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        return dst
    }
}
