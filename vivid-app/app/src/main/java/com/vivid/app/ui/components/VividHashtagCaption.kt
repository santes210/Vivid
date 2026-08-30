package com.vivid.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import com.vivid.app.util.Hashtags
import com.vivid.app.util.SettingsManager

/**
 * Caption con `#tags` pintados con el color primary del esquema activo
 * (Material You) y, si hay [onHashtagClick], navegables.
 *
 * Los hashtags se detectan con la MISMA lógica que los extrae para
 * Firestore y para el cache de Room ([Hashtags.spans]), así lo que se
 * ve clickeable es exactamente lo que se indexa.
 *
 * El filtro de palabras ofensivas se aplica *antes* de buscar los rangos
 * para que un tag censurado no quede clicable a medias.
 */
@Composable
fun VividHashtagCaption(
    caption: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onHashtagClick: ((String) -> Unit)? = null
) {
    val filtered = remember(caption) { SettingsManager.filterOffensiveWords(caption) }
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(filtered, linkColor, onHashtagClick) {
        buildHashtagAnnotatedString(filtered, linkColor, onHashtagClick)
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * Alias usado en el visor y en previews. Misma implementación.
 */
@Composable
fun HashtagCaption(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onHashtagClick: ((String) -> Unit)? = null
) = VividHashtagCaption(
    caption = text,
    modifier = modifier,
    style = style,
    color = color,
    maxLines = maxLines,
    overflow = overflow,
    onHashtagClick = onHashtagClick
)

internal fun buildHashtagAnnotatedString(
    text: String,
    linkColor: Color,
    onHashtagClick: ((String) -> Unit)?
) = buildAnnotatedString {
    val spans = Hashtags.spans(text)
    if (spans.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    val linkStyle = SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)
    var cursor = 0
    spans.forEach { span ->
        if (span.start > cursor) append(text.substring(cursor, span.start))
        val raw = text.substring(span.start, span.endExclusive)
        if (onHashtagClick != null) {
            withLink(
                LinkAnnotation.Clickable(
                    tag = "hashtag:${span.tag}",
                    styles = TextLinkStyles(style = linkStyle),
                    linkInteractionListener = { onHashtagClick(span.tag) }
                )
            ) {
                append(raw)
            }
        } else {
            val start = length
            append(raw)
            addStyle(linkStyle, start, length)
        }
        cursor = span.endExclusive
    }
    if (cursor < text.length) append(text.substring(cursor))
}
