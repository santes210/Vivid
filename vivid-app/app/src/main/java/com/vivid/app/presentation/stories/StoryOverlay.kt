package com.vivid.app.presentation.stories

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Typeface

/**
 * Modelo de datos para los overlays (stickers + texto) de una Story.
 *
 * Cada overlay tiene:
 *   - position: fraccion (0..1) del tamano total para soportar
 *               cualquier orientacion/reescalado
 *   - rotation: grados
 *   - scale: tamano relativo
 *
 * Para TEXTO: text + color + fontSize
 * Para STICKER: emoji
 */
sealed interface StoryOverlay {
    val id: String
    val x: Float        // 0..1
    val y: Float        // 0..1
    val rotation: Float // grados
    val scale: Float    // 0.5..2.0

    data class TextOverlay(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val x: Float = 0.5f,
        override val y: Float = 0.5f,
        override val rotation: Float = 0f,
        override val scale: Float = 1f,
        val text: String = "Texto",
        val color: Int = Color.WHITE,
        val backgroundColor: Int? = null,
        val fontSizeSp: Float = 32f,
        val fontWeight: FontWeight = FontWeight.BOLD
    ) : StoryOverlay

    data class StickerOverlay(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val x: Float = 0.5f,
        override val y: Float = 0.5f,
        override val rotation: Float = 0f,
        override val scale: Float = 1f,
        val emoji: String = "🎉",
        val color: Int = Color.WHITE
    ) : StoryOverlay
}

enum class FontWeight { NORMAL, BOLD, ITALIC }

/**
 * Renderiza una lista de overlays sobre un bitmap.
 * Usado para:
 *   - Previsualizacion en pantalla
 *   - Generar bitmap final para subir a B2
 */
object StoryOverlayRenderer {

    fun renderOverlays(
        base: Bitmap,
        overlays: List<StoryOverlay>
    ): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()

        overlays.forEach { overlay ->
            canvas.save()
            val cx = overlay.x * w
            val cy = overlay.y * h
            canvas.translate(cx, cy)
            canvas.rotate(overlay.rotation)
            canvas.scale(overlay.scale, overlay.scale)

            when (overlay) {
                is StoryOverlay.TextOverlay -> drawText(canvas, overlay, w, h)
                is StoryOverlay.StickerOverlay -> drawSticker(canvas, overlay)
            }
            canvas.restore()
        }
        return result
    }

    private fun drawText(
        canvas: Canvas,
        overlay: StoryOverlay.TextOverlay,
        canvasW: Float,
        canvasH: Float
    ) {
        // Escalar fontSize proporcional al canvas (24sp -> aprox 1/15 del ancho)
        val baseSize = canvasW / 12f
        val scaledSize = baseSize * overlay.scale

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = overlay.color
            textSize = scaledSize
            textAlign = Paint.Align.CENTER
            typeface = when (overlay.fontWeight) {
                FontWeight.BOLD -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                FontWeight.ITALIC -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                FontWeight.NORMAL -> Typeface.DEFAULT
            }
            setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
        }

        // Fondo semitransparente detras del texto (estilo IG)
        overlay.backgroundColor?.let { bgColor ->
            val bounds = Rect()
            textPaint.getTextBounds(overlay.text, 0, overlay.text.length, bounds)
            val padding = scaledSize * 0.3f
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColor
            }
            canvas.drawRoundRect(
                bounds.left - padding,
                bounds.top - padding,
                bounds.right + padding,
                bounds.bottom + padding,
                padding, padding,
                bgPaint
            )
        }

        // Texto centrado en el origen (porque hicimos translate al cx,cy)
        canvas.drawText(overlay.text, 0f, scaledSize / 3f, textPaint)
    }

    private fun drawSticker(
        canvas: Canvas,
        overlay: StoryOverlay.StickerOverlay
    ) {
        val size = 280f * overlay.scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            textAlign = Paint.Align.CENTER
        }
        // Y offset para centrar el emoji verticalmente
        canvas.drawText(overlay.emoji, 0f, size / 3f, paint)
    }
}

/**
 * Biblioteca de stickers predefinidos.
 *
 * Para anadir mas: descargar emojis Unicode o PNGs pequenos.
 * Si quieres PNGs personalizados, cambialo por:
 *   val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.sticker_xxx)
 */
object StickerLibrary {
    val categories = mapOf(
        "Reacciones" to listOf("🎉", "🔥", "❤️", "😍", "😂", "😎", "🤩", "🥳"),
        "Emociones" to listOf("🤔", "😴", "😭", "🥺", "😡", "🤯", "😱", "🤮"),
        "Simbolos" to listOf("✨", "⭐", "💫", "🌟", "💯", "🚀", "💪", "👑"),
        "Comida" to listOf("🍕", "🍔", "🍰", "🍩", "🍦", "🌮", "🍿", "🥗"),
        "Lugares" to listOf("🌍", "🏖️", "🏔️", "🌆", "🎢", "🎡", "🏰", "🗽")
    )

    fun all(): List<String> = categories.values.flatten()
}
