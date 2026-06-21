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
 * Esto permite subir fotos SIN necesitar Firebase Storage ni cuenta bancaria.
 */
object ImageCompressor {

    private const val MAX_WIDTH = 800
    private const val MAX_HEIGHT = 800
    private const val COMPRESS_QUALITY = 70
    private const val MAX_SIZE_BYTES = 900_000 // ~900KB, dejando margen para el documento

    /**
     * Comprime una imagen desde Uri y la devuelve como Base64.
     * Si aún es muy grande, reduce más la calidad iterativamente.
     */
    fun compressToBase64(uri: Uri, context: android.content.Context): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                // Paso 1: Obtener dimensiones originales
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, options)
                input.reset()

                // Paso 2: Calcular sample size
                options.inSampleSize = calculateInSampleSize(
                    options.outWidth, options.outHeight, MAX_WIDTH, MAX_HEIGHT
                )
                options.inJustDecodeBounds = false

                // Paso 3: Decodificar bitmap con sample
                val bitmap = BitmapFactory.decodeStream(input, null, options)
                    ?: return null

                // Paso 4: Escalar al tamaño exacto si es necesario
                val scaledBitmap = if (
                    bitmap.width > MAX_WIDTH || bitmap.height > MAX_HEIGHT
                ) {
                    val ratio = kotlin.math.min(
                        MAX_WIDTH.toFloat() / bitmap.width,
                        MAX_HEIGHT.toFloat() / bitmap.height
                    )
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * ratio).toInt(),
                        (bitmap.height * ratio).toInt(),
                        true
                    )
                } else {
                    bitmap
                }

                // Paso 5: Comprimir a JPEG con calidad iterativa
                compressIteratively(scaledBitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

    private fun compressIteratively(bitmap: Bitmap): String {
        var quality = COMPRESS_QUALITY
        var base64String: String

        while (quality >= 10) {
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
            val bytes = byteArrayOutputStream.toByteArray()

            if (bytes.size <= MAX_SIZE_BYTES) {
                base64String = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                return base64String
            }
            quality -= 15
        }

        // Si aún es muy grande, reducimos el bitmap más
        val reducedWidth = (bitmap.width * 0.5).toInt()
        val reducedHeight = (bitmap.height * 0.5).toInt()
        val reducedBitmap = Bitmap.createScaledBitmap(bitmap, reducedWidth, reducedHeight, true)
        val baos = ByteArrayOutputStream()
        reducedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
    }

    /**
     * Decodifica Base64 de vuelta a Bitmap.
     */
    fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            val bytes = android.util.Base64.decode(base64String, android.util.Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Devuelve un placeholder de bitmap (color sólido) para casos de error.
     */
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

    /**
     * Obtiene dimensiones aproximadas de una imagen sin cargarla completa.
     */
    fun getImageInfo(uri: Uri, context: android.content.Context): ImageInfo {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            ImageInfo(options.outWidth, options.outHeight)
        } ?: ImageInfo(0, 0)
    }

    data class ImageInfo(val width: Int, val height: Int)
}
