package com.vivid.app.presentation.profile

/** Profile references, never credentials or media URLs. */
internal object ProfileLinks {
    private val identifier = Regex("[A-Za-z0-9_-]{1,128}")
    private val link = Regex("vivid://profile/([A-Za-z0-9_-]{1,128})")
    fun create(uid: String): String {
        require(identifier.matches(uid)) { "Invalid profile identifier" }
        return "vivid://profile/$uid"
    }
    fun parse(value: String): String? = link.matchEntire(value)?.groupValues?.get(1)
}

internal fun normalizedSavedCollection(value: String): String =
    value.trim().replace(Regex("\\s+"), " ").also { require(it.length <= 40 && it.none(Char::isISOControl)) }
