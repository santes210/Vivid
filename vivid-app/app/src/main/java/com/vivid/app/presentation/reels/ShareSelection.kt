package com.vivid.app.presentation.reels

import java.text.Normalizer

/** Pure helpers shared by the picker, retry button and message renderer. */
object ShareSelection {
    const val MAX_NOTE_LENGTH = 500

    fun matches(name: String, query: String): Boolean {
        fun fold(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
        return fold(name).contains(fold(query.trim()))
    }

    fun payload(base: String, note: String): String = note.trim().take(MAX_NOTE_LENGTH)
        .let { if (it.isBlank()) base else "$it\n\n$base" }

    fun noteFromMessage(text: String): String? {
        val separator = text.lastIndexOf("\n\n")
        if (separator < 0) return null
        val lastParagraph = text.substring(separator + 2)
        if (ReelLinks.fromMessage(lastParagraph) == null && ReelLinks.postFromMessage(lastParagraph) == null) return null
        return text.substring(0, separator).trim().takeIf { it.isNotBlank() }
    }

    fun retryable(selected: Set<String>, statuses: Map<String, ReelShareStatus>): Set<String> =
        selected.filterTo(linkedSetOf()) { statuses[it] == ReelShareStatus.FAILED }
}
