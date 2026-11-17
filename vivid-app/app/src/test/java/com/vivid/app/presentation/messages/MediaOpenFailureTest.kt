package com.vivid.app.presentation.messages

import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaOpenFailureTest {
    @Test fun `permission denied is not reported as a network failure`() {
        assertEquals(MediaOpenFailure.ACCESS, mediaOpenFailure(
            FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)))
    }
    @Test fun `signing errors retain their stage`() {
        assertEquals(MediaOpenFailure.SIGNING, mediaOpenFailure(MediaOpenException(MediaOpenFailure.SIGNING)))
        assertEquals(MediaOpenFailure.NETWORK, mediaOpenFailure(java.io.IOException("offline")))
    }
}
