package com.vivid.app.presentation.profile

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test

class ProfileSharingTest {
    @Test fun `profile links round trip exact identifiers`() {
        assertEquals("uid_123-abc", ProfileLinks.parse(ProfileLinks.create("uid_123-abc")))
    }

    @Test fun `malformed profile references are rejected`() {
        listOf("vivid://profile/", "vivid://profile/a/b", "vivid://profile/a?token=x",
            "https://profile/a", "vivid://profile/../a", "vivid://profile/a#fragment").forEach {
            assertNull(it, ProfileLinks.parse(it))
        }
        try { ProfileLinks.create("a/b"); fail("Expected invalid identifier") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun `QR contains only the profile reference`() {
        val link = ProfileLinks.create("user123")
        val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 300, 300)
        val pixels = IntArray(300 * 300) { if (matrix[it % 300, it / 300]) 0xff000000.toInt() else 0xffffffff.toInt() }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(300, 300, pixels)))
        assertEquals(link, MultiFormatReader().decode(bitmap).text)
    }

    @Test fun `collection names normalize whitespace and allow unfiling`() {
        assertEquals("Viajes 2026", normalizedSavedCollection("  Viajes   2026  "))
        assertEquals("", normalizedSavedCollection("  "))
        try { normalizedSavedCollection("x".repeat(41)); fail("Expected length limit") }
        catch (_: IllegalArgumentException) { }
    }
}
