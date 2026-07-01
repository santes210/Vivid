package com.vivid.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Utilidad para comprimir imágenes antes de convertirlas a Base64.
 * Firestore tiene un límite de 1MB por documento, así que comprimimos
 * la imagen para que quepa como texto Base64.
 *
 * Mejorado para respetar los ajustes dinámicos de:
 *   - hdUploadsEnabled (mayor calidad, mayor tamaño máximo)
 *   - dataSaverMode (menor calidad, menor tamaño para ahorrar datos)
 */
object ImageCompressor {

    private const val MIN_COMPRESS_QUALITY = 20

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

            val isHd = com.vivid.app.util.SettingsManager.hdUploadsEnabled
            val isDataSaver = com.vivid.app.util.SettingsManager.dataSaverMode

            val reqWidth = when {
                isDataSaver -> 480
                isHd -> 1200
                else -> 720
            }
            val reqHeight = when {
                isDataSaver -> 480
                isHd -> 1200
                else -> 720
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    originalWidth = bounds.outWidth,
                    originalHeight = bounds.outHeight,
                    reqWidth = reqWidth,
                    reqHeight = reqHeight
                )
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
                ?: return null

            val scaledBitmap = scaleBitmapIfNeeded(bitmap, reqWidth, reqHeight)
            compressIteratively(scaledBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, reqWidth: Int, reqHeight: Int): Bitmap {
        if (bitmap.width <= reqWidth && bitmap.height <= reqHeight) return bitmap

        val ratio = kotlin.math.min(
            reqWidth.toFloat() / bitmap.width,
            reqHeight.toFloat() / bitmap.height
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
        val isHd = com.vivid.app.util.SettingsManager.hdUploadsEnabled
        val isDataSaver = com.vivid.app.util.SettingsManager.dataSaverMode

        val initialQuality = when {
            isDataSaver -> 45
            isHd -> 88
            else -> 72
        }

        val maxBinarySize = when {
            isDataSaver -> 150_000 // 150 KB
            isHd -> 900_000        // 900 KB
            else -> 450_000        // 450 KB
        }

        var bitmap = initialBitmap
        var quality = initialQuality
        var attempts = 0

        while (attempts < 6) {
            while (quality >= MIN_COMPRESS_QUALITY) {
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
                val bytes = byteArrayOutputStream.toByteArray()

                if (bytes.size <= maxBinarySize) {
                    return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
                quality -= 10
            }

            val reducedWidth = (bitmap.width * 0.75f).toInt().coerceAtLeast(180)
            val reducedHeight = (bitmap.height * 0.75f).toInt().coerceAtLeast(180)

            if (reducedWidth == bitmap.width && reducedHeight == bitmap.height) break

            bitmap = Bitmap.createScaledBitmap(bitmap, reducedWidth, reducedHeight, true)
            quality = initialQuality
            attempts++
        }

        val fallback = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, MIN_COMPRESS_QUALITY, fallback)
        val fallbackBytes = fallback.toByteArray()
        if (fallbackBytes.isEmpty() || fallbackBytes.size > maxBinarySize) return null

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
        val config = Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(width, height, config)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        paint.color = android.graphics.Color.GRAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
}
