package com.vivid.app.util

import java.text.Normalizer

/**
 * Plegado de texto para búsqueda: minúsculas + sin diacríticos.
 *
 * `María`, `maria` y `MARÍA` pliegan a `maria`, así la búsqueda de personas,
 * hashtags y captions coincide aunque el usuario escriba sin tildes (o con
 * tildes de más). Es la misma idea que [Hashtags.normalize] pero para texto
 * libre: no recorta símbolos ni exige letras, solo pliega.
 *
 * Dónde vive cada valor en Firestore:
 *  - `usernameLower` / `displayNameLower` / `caption`: `lowercase()` simple
 *    (compat con documentos y consultas existentes).
 *  - `usernameFold` / `displayNameFold` / `captionFold`: este plegado. Las
 *    consultas del buscador van contra estos campos y caen a los `*Lower`
 *    para los documentos antiguos que aún no los tienen.
 */
object TextFold {

    private val DIACRITICS = "\\p{Mn}+".toRegex()
    private val WHITESPACE = "\\s+".toRegex()

    /**
     * Minúsculas, sin marcas diacríticas, con espacios colapsados y sin
     * espacios de borde. Así "hola  MUNDO " pliega a "hola mundo" y las
     * frases comparan bien en el buscador.
     */
    fun fold(raw: String): String {
        if (raw.isEmpty()) return ""
        val collapsed = raw.trim().replace(WHITESPACE, " ").lowercase()
        val nfd = Normalizer.normalize(collapsed, Normalizer.Form.NFD)
        return nfd.replace(DIACRITICS, "")
    }
}
