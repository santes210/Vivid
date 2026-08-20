package com.vivid.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Marca de agua automática — FIX 2026-08-09: más robusto, no crashea.
 */
@UnstableApi
object VideoWatermarker {

    private const val TAG = "VideoWatermarker"

    suspend fun applyWatermark(
        context: Context,
        inputUri: Uri,
        outputFile: File
    ): String = withContext(Dispatchers.IO) {
        try {
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val logo = renderVividLogo(widthPx = 500, heightPx = 140)

            val bitmapOverlay = try {
                androidx.media3.effect.BitmapOverlay.createStaticBitmapOverlay(logo)
            } catch (e: Exception) {
                Log.w(TAG, "createStaticBitmapOverlay falló: ${e.message}")
                null
            }

            if (bitmapOverlay == null) {
                Log.w(TAG, "BitmapOverlay null, fallback")
                return@withContext copyOriginal(context, inputUri, outputFile).absolutePath
            }

            val overlayEffect = OverlayEffect(ImmutableList.of<TextureOverlay>(bitmapOverlay))

            val mediaItem = MediaItem.fromUri(inputUri)
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    androidx.media3.transformer.Effects(
                        emptyList(),
                        listOf<Effect>(overlayEffect)
                    )
                )
                .build()
            // Media3 1.11 reemplazó el constructor directo por factories de
            // secuencias; la marca de agua no debe descartar el audio original.
            val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited))
            val composition = Composition.Builder(sequence).build()

            val outputPath = suspendCancellableCoroutine<String> { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        val size = outputFile.length()
                        Log.d(TAG, "Watermark OK: ${size / 1024}KB")
                        if (size > 1024) {
                            if (cont.isActive) cont.resume(outputFile.absolutePath)
                        } else {
                            if (cont.isActive) cont.resume(copyOriginal(context, inputUri, outputFile).absolutePath)
                        }
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Log.e(TAG, "Watermark error, fallback", exportException)
                        if (cont.isActive) {
                            cont.resume(copyOriginal(context, inputUri, outputFile).absolutePath)
                        }
                    }
                }

                val transformer = try {
                    Transformer.Builder(context)
                        .addListener(listener)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
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

            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "Watermark falló — fallback", e)
            try {
                copyOriginal(context, inputUri, outputFile).absolutePath
            } catch (e2: Exception) {
                Log.e(TAG, "copyOriginal falló", e2)
                inputUri.path ?: outputFile.absolutePath
            }
        }
    }

    private fun renderVividLogo(widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint().apply {
            color = Color.argb(90, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), 24f, 24f, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            isFakeBoldText = true
            setShadowLayer(8f, 0f, 3f, Color.argb(180, 0, 0, 0))
        }
        canvas.drawText("Vivid ✦", 24f, 88f, textPaint)
        return bmp
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
