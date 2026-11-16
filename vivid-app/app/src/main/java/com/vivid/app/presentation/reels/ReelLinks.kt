package com.vivid.app.presentation.reels

/** Share document references, never signed media URLs (which bypass future privacy checks). */
object ReelLinks {
    private val idPattern = Regex("[A-Za-z0-9_-]{1,128}")
    private val linkPattern = Regex("vivid://reel/([A-Za-z0-9_-]{1,128})")
    fun create(id: String): String {
        require(idPattern.matches(id)) { "Invalid reel identifier" }
        return "vivid://reel/$id"
    }
    fun parse(link: String): String? = linkPattern.matchEntire(link)?.groupValues?.get(1)
    fun postFromMessage(text: String): String? = text.lineSequence().map { it.trim() }
        .mapNotNull { Regex("vivid://post/([A-Za-z0-9_-]{1,128})").matchEntire(it)?.groupValues?.get(1) }.firstOrNull()
    fun fromMessage(text: String): String? = text.lineSequence().map { it.trim() }
        .mapNotNull(::parse).firstOrNull()
}
