package com.vivid.app.presentation.messages

import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercise the real Firebase enum/exception only where Android classes exist. */
class MediaOpenFailureAndroidTest {
    @Test fun realFirestoreExceptionsUseTheExpectedMapping() {
        val cases = mapOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED to MediaOpenFailure.ACCESS,
            FirebaseFirestoreException.Code.UNAUTHENTICATED to MediaOpenFailure.SESSION,
            FirebaseFirestoreException.Code.UNAVAILABLE to MediaOpenFailure.NETWORK,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED to MediaOpenFailure.NETWORK,
            FirebaseFirestoreException.Code.NOT_FOUND to MediaOpenFailure.CONTENT
        )
        cases.forEach { (code, expected) ->
            assertEquals(expected, mediaOpenFailure(FirebaseFirestoreException("test", code)))
        }
    }
}
