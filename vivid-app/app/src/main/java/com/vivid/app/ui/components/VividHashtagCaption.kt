package com.vivid.app.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.vivid.app.util.Hashtags
import com.vivid.app.util.SettingsManager

/**
 * Caption con hashtags tocables: cada `#tag` del texto se pinta con el color
 * primario y abre Explorar filtrado por ese tag ([onHashtagClick]).
 *
 * Los hashtags se detectan con la MISMA regex que los extrae para Firestore
 * y para el cache de Room ([Hashtags.REGEX]), así lo que se ve clickeable es
 * exactamente lo que se indexa.
 *
 * `ClickableText` está depreciado en favor de `Text` + `LinkAnnotation` en
 * BOMs recientes, pero es la API estable en toda la matriz que soporta Vivid
 * y no tiene costo de runtime; cuando el min BOM suba, migrar es mecánico.
 */
@Composable
fun VividHashtagCaption(
    caption: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onHashtagClick: (String) -> Unit = {}
) {
    // Se respeta el filtro de lenguaje ofensivo del feed sobre el texto plano;
    // los tokens #tag quedan intactos (el filtro trabaja palabras, no símbolos).
    val filtered = SettingsManager.filterOffensiveWords(caption)
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface

    val annotated = remember(filtered, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            Hashtags.REGEX.findAll(filtered).forEach { match ->
                if (match.range.first > cursor) append(filtered.substring(cursor, match.range.first))
                val tag = match.value.removePrefix("#").lowercase()
                withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
                    append(match.value)
                }
                // La búsqueda en el click usa el rango de caracteres del tag.
                addStringAnnotation(
                    tag = HASHTAG_ANNOTATION,
                    annotation = tag,
                    start = match.range.first,
                    end = match.range.last
                )
                cursor = match.range.last + 1
            }
            if (cursor < filtered.length) append(filtered.substring(cursor))
        }
    }

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        maxLines = maxLines,
        overflow = overflow,
        onClick = { charOffset ->
            // ClickableText entrega el offset del carácter tocado: las
            // anotaciones que lo contienen son exactamente los hashtags.
            annotated
                .getStringAnnotations(HASHTAG_ANNOTATION, charOffset, charOffset)
                .firstOrNull()
                ?.item
                ?.let(onHashtagClick)
        }
    )
}

private const val HASHTAG_ANNOTATION = "hashtag"
