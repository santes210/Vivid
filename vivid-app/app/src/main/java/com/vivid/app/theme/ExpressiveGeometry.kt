package com.vivid.app.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Vivid — Expressive Geometry.
 *
 * Librería de formas "vivas" de Material 3 Expressive. No son solo esquinas redondeadas:
 * incluyen superelipses (squircles) con curvatura continua, estrellas, ráfagas, pétalos,
 * corazones, festones (scallops), diamantes y píldoras. Cada una se puede usar como
 * [Shape] en `Surface`, `clip(...)`, `Button`, `Card`, etc.
 *
 * Inspiración: la identidad "squircle" de PixelPlayer / M3 Expressive — siluetas suaves,
 * continuas y con personalidad, no cajas redondeadas genéricas.
 */

/** Superelipse (squircle). Exponente 4 = "clásico M3"; 2 = círculo; 6+ = casi cuadrado. */
class SquircleShape(
    private val exponent: Float = 4f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val hw = size.width / 2f
        val hh = size.height / 2f
        val n = exponent
        val steps = 96
        var first = true
        for (i in 0..steps) {
            val t = (2.0 * PI * i / steps).toFloat()
            val c = cos(t)
            val s = sin(t)
            val x = cx + hw * sign(c) * abs(c).pow(2f / n)
            val y = cy + hh * sign(s) * abs(s).pow(2f / n)
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Estrella de N puntas (polígono estrellado).
 * @param points     número de puntas
 * @param innerRatio relación radio interno/externo (0.3 = puntiaguda, 0.6 = suave/redondeada)
 */
class StarShape(
    private val points: Int = 5,
    private val innerRatio: Float = 0.42f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = min(size.width, size.height) / 2f
        val inner = outer * innerRatio
        val n = points * 2
        var first = true
        for (i in 0 until n) {
            val r = if (i % 2 == 0) outer else inner
            val a = (PI * i / points - PI / 2).toFloat()
            val x = cx + cos(a) * r
            val y = cy + sin(a) * r
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Ráfaga / sol (burst): estrella con puntas más numerosas y finas, ideal para
 * acentos decorativos, "sparkles" y estados de energía.
 */
class BurstShape(
    private val rays: Int = 12,
    private val innerRatio: Float = 0.35f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = min(size.width, size.height) / 2f
        val inner = outer * innerRatio
        val n = rays * 2
        var first = true
        for (i in 0 until n) {
            val r = if (i % 2 == 0) outer else inner
            val a = (PI * i / rays - PI / 2).toFloat()
            val x = cx + cos(a) * r
            val y = cy + sin(a) * r
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Pétalo / gota (teardrop): una punta redondeada y una cónica. Muy expresiva para
 * badges, indicadores y acentos de marca.
 * @param orientation 0 = punta abajo, 1 = punta arriba, 2 = punta derecha, 3 = izquierda
 */
class TeardropShape(
    private val orientation: Int = 0
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        // Aproximamos una gota con dos curvas cúbicas desde la punta.
        when (orientation % 4) {
            0 -> { // punta abajo
                path.moveTo(w / 2f, h)                    // punta
                path.cubicTo(w * 0.12f, h * 0.62f, w * 0.06f, h * 0.28f, w * 0.28f, h * 0.08f)
                path.cubicTo(w * 0.40f, -h * 0.03f, w * 0.60f, -h * 0.03f, w * 0.72f, h * 0.08f)
                path.cubicTo(w * 0.94f, h * 0.28f, w * 0.88f, h * 0.62f, w / 2f, h)
            }
            1 -> { // punta arriba
                path.moveTo(w / 2f, 0f)                    // punta
                path.cubicTo(w * 0.12f, h * 0.38f, w * 0.06f, h * 0.72f, w * 0.28f, h * 0.92f)
                path.cubicTo(w * 0.40f, h * 1.03f, w * 0.60f, h * 1.03f, w * 0.72f, h * 0.92f)
                path.cubicTo(w * 0.94f, h * 0.72f, w * 0.88f, h * 0.38f, w / 2f, 0f)
            }
            2 -> { // punta derecha
                path.moveTo(w, h / 2f)
                path.cubicTo(w * 0.62f, h * 0.12f, w * 0.28f, h * 0.06f, w * 0.08f, h * 0.28f)
                path.cubicTo(-w * 0.03f, h * 0.40f, -w * 0.03f, h * 0.60f, w * 0.08f, h * 0.72f)
                path.cubicTo(w * 0.28f, h * 0.94f, w * 0.62f, h * 0.88f, w, h / 2f)
            }
            else -> { // punta izquierda
                path.moveTo(0f, h / 2f)
                path.cubicTo(w * 0.38f, h * 0.12f, w * 0.72f, h * 0.06f, w * 0.92f, h * 0.28f)
                path.cubicTo(w * 1.03f, h * 0.40f, w * 1.03f, h * 0.60f, w * 0.92f, h * 0.72f)
                path.cubicTo(w * 0.72f, h * 0.94f, w * 0.38f, h * 0.88f, 0f, h / 2f)
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Corazón expresivo. Ideal para "Me gusta", acciones de marcado y acentos de marca.
 */
class HeartShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        // Trayectoria única y continua: hoyuelo superior → lóbulo izq → centro →
        // lóbulo der → punta inferior → cierre.
        path.moveTo(w / 2f, h * 0.34f)
        // Lóbulo izquierdo (sube y rodea por la izquierda)
        path.cubicTo(w * 0.08f, h * 0.02f, w * 0.16f, -h * 0.16f, w * 0.38f, h * 0.04f)
        path.cubicTo(w * 0.52f, h * 0.14f, w * 0.50f, h * 0.32f, w * 0.50f, h * 0.32f)
        // Lóbulo derecho (simétrico)
        path.cubicTo(w * 0.50f, h * 0.32f, w * 0.48f, h * 0.14f, w * 0.62f, h * 0.04f)
        path.cubicTo(w * 0.84f, -h * 0.16f, w * 0.92f, h * 0.02f, w / 2f, h * 0.96f)
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Diamante / rombo (superelipse rotada con curvatura): forma "gema" expresiva.
 * @param exponent curvatura de los lados
 */
class DiamondShape(
    private val exponent: Float = 3f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        val n = exponent
        path.moveTo(w / 2f, 0f)                       // punta superior
        // lado superior derecho
        for (i in 1..8) {
            val t = i / 8f
            val x = w / 2f + (w / 2f) * t.pow(n)
            val y = h / 2f * (1f - (1f - t).pow(1f / n))
            path.lineTo(x, y)
        }
        path.lineTo(w, h / 2f)                        // punta derecha
        // lado inferior derecho
        for (i in 1..8) {
            val t = i / 8f
            val x = w / 2f + (w / 2f) * (1f - t).pow(n)
            val y = h / 2f + (h / 2f) * t.pow(1f / n)
            path.lineTo(x, y)
        }
        path.lineTo(w / 2f, h)                        // punta inferior
        // lado inferior izquierdo
        for (i in 1..8) {
            val t = i / 8f
            val x = w / 2f - (w / 2f) * (1f - t).pow(n)
            val y = h / 2f + (h / 2f) * t.pow(1f / n)
            path.lineTo(x, y)
        }
        path.lineTo(0f, h / 2f)                       // punta izquierda
        // lado superior izquierdo
        for (i in 1..8) {
            val t = i / 8f
            val x = w / 2f - (w / 2f) * t.pow(n)
            val y = h / 2f * (1f - (1f - t).pow(1f / n))
            path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Festón / scallop: borde ondulado (como una cortina o un estante de olas).
 * Se usa para detalles decorativos, tarjetas hero y "shelves" expresivos.
 * @param waves   número de ondas
 * @param onTop   si las ondas van arriba (true) o abajo (false)
 */
class ScallopShape(
    private val waves: Int = 5,
    private val onTop: Boolean = true
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        val arcWidth = w / waves
        if (onTop) {
            path.moveTo(0f, h)
            path.lineTo(w, h)
            // ondas en la parte superior, de derecha a izquierda
            for (i in 0 until waves) {
                val x = w - i * arcWidth
                val cx = x - arcWidth / 2f
                val cy = 0f
                path.lineTo(x, 0f)
                path.quadraticBezierTo(cx, cy - h * 0.4f, x - arcWidth, 0f)
            }
        } else {
            path.moveTo(0f, 0f)
            path.lineTo(w, 0f)
            for (i in 0 until waves) {
                val x = w - i * arcWidth
                val cx = x - arcWidth / 2f
                val cy = h
                path.lineTo(x, h)
                path.quadraticBezierTo(cx, cy + h * 0.4f, x - arcWidth, h)
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Diente de sierra (sawtooth) en un borde: filo zigzag expresivo.
 */
class SawtoothShape(
    private val teeth: Int = 6,
    private val onTop: Boolean = true
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        val toothW = w / teeth
        if (onTop) {
            path.moveTo(0f, h)
            path.lineTo(w, h)
            for (i in 0 until teeth) {
                val x = w - i * toothW
                path.lineTo(x, 0f)
                path.lineTo(x - toothW / 2f, h * 0.5f)
            }
            path.lineTo(0f, h)
        } else {
            path.moveTo(0f, 0f)
            path.lineTo(w, 0f)
            for (i in 0 until teeth) {
                val x = w - i * toothW
                path.lineTo(x, h)
                path.lineTo(x - toothW / 2f, h * 0.5f)
            }
            path.lineTo(0f, 0f)
        }
        path.close()
        return Outline.Generic(path)
    }
}
