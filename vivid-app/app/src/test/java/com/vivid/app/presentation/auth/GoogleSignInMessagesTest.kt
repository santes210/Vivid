package com.vivid.app.presentation.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSignInMessagesTest {

    @Test
    fun `error 16 points to SHA-1 registration`() {
        val message = GoogleSignInMessages.fromCredentialManager(
            type = "android.credentials.GetCredentialException.TYPE_UNKNOWN",
            message = "During begin sign in, failure response from one tap: 16: Developer console is not set up correctly."
        )

        assertTrue(message.contains("SHA-1"))
        assertTrue(message.contains("Huellas del certificado"))
        assertTrue(message.contains("16:"))
    }

    @Test
    fun `api exception 10 points to SHA-1 registration`() {
        val message = GoogleSignInMessages.fromCredentialManager(
            type = "android.credentials.GetCredentialException.TYPE_UNKNOWN",
            message = "APIException: 10"
        )

        assertTrue(message.contains("SHA-1"))
    }

    @Test
    fun `network errors ask to check connection`() {
        val message = GoogleSignInMessages.fromCredentialManager(
            type = "android.credentials.GetCredentialException.TYPE_UNKNOWN",
            message = "network timeout"
        )

        assertTrue(message.contains("conexión"))
        assertFalse(message.contains("SHA-1"))
    }

    @Test
    fun `generic failure still mentions Firebase Console`() {
        val message = GoogleSignInMessages.fromCredentialManager(
            type = "something.else",
            message = null
        )

        assertTrue(message.startsWith("No se pudo iniciar sesión con Google."))
        assertTrue(message.contains("Firebase Console"))
    }

    @Test
    fun `NoCredentialException type is treated as empty picker`() {
        assertTrue(
            GoogleSignInMessages.isNoCredentialFailure(
                "androidx.credentials.TYPE_NO_CREDENTIAL",
                "No credentials available"
            )
        )
        assertTrue(
            GoogleSignInMessages.isNoCredentialFailure(
                "unknown",
                "Cannot find a matching credential"
            )
        )
    }

    @Test
    fun `developer console error is NOT treated as empty picker`() {
        assertFalse(
            GoogleSignInMessages.isNoCredentialFailure(
                "android.credentials.GetCredentialException.TYPE_UNKNOWN",
                "16: Developer console is not set up correctly."
            )
        )
    }

    @Test
    fun `firebase invalid credentials is not wrong password`() {
        val message = GoogleSignInMessages.fromFirebase(
            "FirebaseAuthInvalidCredentialsException",
            "The supplied auth credential is malformed or has expired."
        )

        assertTrue(message.contains("token que Firebase rechazó"))
        assertFalse(message.contains("contraseña"))
    }

    @Test
    fun `firebase collision explains email already registered`() {
        val message = GoogleSignInMessages.fromFirebase(
            "FirebaseAuthUserCollisionException",
            "already in use"
        )

        assertTrue(message.contains("correo ya registrado"))
    }

    @Test
    fun `unknown firebase exception falls back to its message`() {
        assertEquals(
            "boom",
            GoogleSignInMessages.fromFirebase("SomethingElse", "boom")
        )
        assertEquals(
            "No se pudo iniciar sesión con Google.",
            GoogleSignInMessages.fromFirebase("SomethingElse", null)
        )
    }
}
