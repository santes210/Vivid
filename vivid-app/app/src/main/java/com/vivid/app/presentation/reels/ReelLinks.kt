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
    private val messageLinkPattern = Regex("vivid://(reel|post)/([A-Za-z0-9_-]{1,128})")
    // Optional notes precede the actual shared reference. A link inside the
    // note must not replace the final attached post/reel card.
    private fun messageReference(text: String): MatchResult? = text.lineSequence()
        .mapNotNull { messageLinkPattern.matchEntire(it.trim()) }.lastOrNull()
    fun postFromMessage(text: String): String? = messageReference(text)
        ?.takeIf { it.groupValues[1] == "post" }?.groupValues?.get(2)
    fun fromMessage(text: String): String? = messageReference(text)
        ?.takeIf { it.groupValues[1] == "reel" }?.groupValues?.get(2)
}
