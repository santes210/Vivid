package com.vivid.app.presentation.profile

import org.junit.Assert.*
import org.junit.Test

class ProfileWebsiteTest {
    @Test fun validHttpsLinkAndEmptyValueAreAccepted() {
        assertEquals("", validProfileWebsite("  "))
        assertEquals("https://example.com/about?q=1", validProfileWebsite(" https://example.com/about?q=1 "))
    }
    @Test fun dangerousSchemesCredentialsAndMalformedLinksAreRejected() {
        listOf("javascript:alert(1)", "intent://example", "file:///private/data", "http://example.com",
            "https://user:password@example.com", "https://", "https://example.com/a b",
            "https://example.com:99999").forEach { assertNull(it, validProfileWebsite(it)) }
    }
}
