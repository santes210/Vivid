package com.vivid.app.presentation.messages

import com.google.firebase.firestore.FirebaseFirestoreException

enum class MediaOpenFailure { SESSION, ACCESS, SIGNING, NETWORK, CONTENT }
internal class MediaOpenException(val reason: MediaOpenFailure) : Exception(reason.name)

internal fun mediaOpenFailure(error: Exception): MediaOpenFailure = when (error) {
    is MediaOpenException -> error.reason
    is FirebaseFirestoreException -> when (error.code) {
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> MediaOpenFailure.SESSION
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> MediaOpenFailure.ACCESS
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> MediaOpenFailure.NETWORK
        else -> MediaOpenFailure.CONTENT
    }
    is java.io.IOException -> MediaOpenFailure.NETWORK
    else -> MediaOpenFailure.CONTENT
}
