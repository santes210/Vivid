package com.vivid.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Acciones que pertenecen a Vivid y no a una metáfora genérica del sistema.
 * Los vectores usan viewport 24, trazo redondeado de 1.8 y el mismo peso óptico.
 */
object VividIcons {
    val Create: ImageVector by lazy {
        ImageVector.Builder(
            name = "VividCreate",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 3.75f)
                lineTo(17f, 3.75f)
                cubicTo(18.79f, 3.75f, 20.25f, 5.21f, 20.25f, 7f)
                lineTo(20.25f, 17f)
                cubicTo(20.25f, 18.79f, 18.79f, 20.25f, 17f, 20.25f)
                lineTo(7f, 20.25f)
                cubicTo(5.21f, 20.25f, 3.75f, 18.79f, 3.75f, 17f)
                lineTo(3.75f, 7f)
                cubicTo(3.75f, 5.21f, 5.21f, 3.75f, 7f, 3.75f)
                close()
                moveTo(12f, 7.75f)
                lineTo(12f, 16.25f)
                moveTo(7.75f, 12f)
                lineTo(16.25f, 12f)
            }
        }.build()
    }
}
