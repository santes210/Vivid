package com.vivid.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Utilidad para comprimir imágenes antes de convertirlas a Base64.
 * Firestore tiene un límite de 1MB por documento, así que comprimimos
 * la imagen para que quepa como texto Base64.
 */
object ImageCompressor {

    private const val MAX_WIDTH = 720
    private const val MAX_HEIGHT = 720
    private const val INITIAL_COMPRESS_QUALITY = 72
    private const val MIN_COMPRESS_QUALITY = 20
    private const val MAX_BINARY_SIZE_BYTES = 450_000

    /**
     * Comprime una imagen desde Uri y la devuelve como Base64.
     * Abre el contenido una sola vez, lo lee como bytes y luego trabaja en memoria
     * para evitar fallos por `InputStream.reset()` no soportado.
     */
    fun compressToBase64(uri: Uri, context: android.content.Context): String? {
        return try {
            val imageBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: return null

            if (imageBytes.isEmpty()) return null

            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    originalWidth = bounds.outWidth,
                    originalHeight = bounds.outHeight,
                    reqWidth = MAX_WIDTH,
                    reqHeight = MAX_HEIGHT
                )
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
                ?: return null

            val scaledBitmap = scaleBitmapIfNeeded(bitmap)
            compressIteratively(scaledBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_WIDTH && bitmap.height <= MAX_HEIGHT) return bitmap

        val ratio = kotlin.math.min(
            MAX_WIDTH.toFloat() / bitmap.width,
            MAX_HEIGHT.toFloat() / bitmap.height
        )

        val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun calculateInSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (originalHeight > reqHeight || originalWidth > reqWidth) {
            val halfHeight = originalHeight / 2
            val halfWidth = originalWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun compressIteratively(initialBitmap: Bitmap): String? {
        var bitmap = initialBitmap
        var quality = INITIAL_COMPRESS_QUALITY
        var attempts = 0

        while (attempts < 6) {
            while (quality >= MIN_COMPRESS_QUALITY) {
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
                val bytes = byteArrayOutputStream.toByteArray()

                if (bytes.size <= MAX_BINARY_SIZE_BYTES) {
                    return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
                quality -= 10
            }

            val reducedWidth = (bitmap.width * 0.75f).toInt().coerceAtLeast(180)
            val reducedHeight = (bitmap.height * 0.75f).toInt().coerceAtLeast(180)

            if (reducedWidth == bitmap.width && reducedHeight == bitmap.height) break

            bitmap = Bitmap.createScaledBitmap(bitmap, reducedWidth, reducedHeight, true)
            quality = INITIAL_COMPRESS_QUALITY
            attempts++
        }

        val fallback = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, MIN_COMPRESS_QUALITY, fallback)
        val fallbackBytes = fallback.toByteArray()
        if (fallbackBytes.isEmpty() || fallbackBytes.size > MAX_BINARY_SIZE_BYTES) return null

        return android.util.Base64.encodeToString(fallbackBytes, android.util.Base64.NO_WRAP)
    }

    fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            val bytes = android.util.Base64.decode(base64String, android.util.Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    fun createPlaceholderBitmap(width: Int = 400, height: Int = 400): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val canvas = android.graphics.Canvas(this)
            canvas.drawColor(android.graphics.Color.parseColor("#2D2D3A"))
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#55556A")
                textSize = 60f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("Vivid", width / 2f, height / 2f + 20f, paint)
        }
    }

    fun getImageInfo(uri: Uri, context: android.content.Context): ImageInfo {
        return try {
            val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ImageInfo(0, 0)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            ImageInfo(options.outWidth, options.outHeight)
        } catch (_: Exception) {
            ImageInfo(0, 0)
        }
    }

    data class ImageInfo(val width: Int, val height: Int)
}
