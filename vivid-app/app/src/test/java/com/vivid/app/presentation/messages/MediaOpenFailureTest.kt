package com.vivid.app.presentation.messages

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaOpenFailureTest {
    @Test fun `permission denied is not reported as a network failure`() {
        assertEquals(MediaOpenFailure.ACCESS, firestoreMediaOpenFailure(7))
    }

    @Test fun `session and transient Firestore failures are distinguished`() {
        assertEquals(MediaOpenFailure.SESSION, firestoreMediaOpenFailure(16))
        assertEquals(MediaOpenFailure.NETWORK, firestoreMediaOpenFailure(14))
        assertEquals(MediaOpenFailure.NETWORK, firestoreMediaOpenFailure(4))
    }

    @Test fun `missing content and unknown codes use the safe generic message`() {
        assertEquals(MediaOpenFailure.CONTENT, firestoreMediaOpenFailure(5))
        assertEquals(MediaOpenFailure.CONTENT, firestoreMediaOpenFailure(-1))
        assertEquals(MediaOpenFailure.CONTENT, firestoreMediaOpenFailure(999))
    }

    @Test fun `signing errors retain their stage`() {
        assertEquals(MediaOpenFailure.SIGNING, mediaOpenFailure(MediaOpenException(MediaOpenFailure.SIGNING)))
        assertEquals(MediaOpenFailure.NETWORK, mediaOpenFailure(java.io.IOException("offline")))
    }
}
