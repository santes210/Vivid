package com.vivid.app.presentation.profile

/** Only explicit HTTPS URLs; no credentials, intent URLs or invisible whitespace. */
internal fun validProfileWebsite(value: String): String? {
    val text = value.trim()
    if (text.isEmpty()) return ""
    if (text.length > 2048 || text.any { it.isWhitespace() || it.isISOControl() }) return null
    return runCatching {
        val uri = java.net.URI(text)
        if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && (uri.port == -1 || uri.port in 1..65535)) text else null
    }.getOrNull()
}
