package com.vivid.app.presentation.messages

import com.google.firebase.firestore.FirebaseFirestoreException

enum class MediaOpenFailure { SESSION, ACCESS, SIGNING, NETWORK, CONTENT }
internal class MediaOpenException(val reason: MediaOpenFailure) : Exception(reason.name)

internal fun mediaOpenFailure(error: Exception): MediaOpenFailure = when (error) {
    is MediaOpenException -> error.reason
    is FirebaseFirestoreException -> firestoreMediaOpenFailure(error.code.value())
    is java.io.IOException -> MediaOpenFailure.NETWORK
    else -> MediaOpenFailure.CONTENT
}

/**
 * Firestore uses canonical gRPC status numbers. Keep this mapping independent of
 * Firebase's Code enum: its static initializer uses android.util.SparseArray,
 * which cannot run in plain JVM unit tests. The Android adapter is tested in androidTest.
 */
internal fun firestoreMediaOpenFailure(code: Int): MediaOpenFailure = when (code) {
    16 -> MediaOpenFailure.SESSION // UNAUTHENTICATED
    7 -> MediaOpenFailure.ACCESS // PERMISSION_DENIED
    14, 4 -> MediaOpenFailure.NETWORK // UNAVAILABLE, DEADLINE_EXCEEDED
    else -> MediaOpenFailure.CONTENT
}
