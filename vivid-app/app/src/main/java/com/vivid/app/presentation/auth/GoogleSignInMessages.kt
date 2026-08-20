package com.vivid.app.presentation.auth

/**
 * Mensajes de error del login con Google, extraídos para poder testearlos
 * sin Credential Manager ni Firebase Auth en el JVM.
 *
 * Los fallos típicos NO son del usuario: SHA-1 del APK no registrado,
 * Web client ID ausente, o Firebase rechazando el idToken. Antes se
 * pintaba "Correo o contraseña incorrectos" / un genérico "error de
 * Google sign-in" y no había forma de diagnosticarlo.
 */
internal object GoogleSignInMessages {

    const val MISSING_WEB_CLIENT =
        "Google Sign-In no está configurado: agrega tu Web client ID en " +
            "Firebase (Authentication → Google) y vuelve a descargar google-services.json."

    const val NO_ACTIVITY =
        "No se pudo abrir el selector de cuentas de Google."

    const val NO_ACCOUNTS =
        "No hay ninguna cuenta de Google disponible en este dispositivo. " +
            "Agrega una en Ajustes y vuelve a intentarlo."

    const val EMPTY_TOKEN = "Google no devolvió un token válido."

    const val UNREADABLE_TOKEN = "Google devolvió una respuesta que no se pudo leer."

    const val UNEXPECTED_CREDENTIAL = "Google devolvió un tipo de credencial inesperado."

    /**
     * Traduce un fallo de Credential Manager a un mensaje accionable.
     *
     * [type] es [androidx.credentials.exceptions.GetCredentialException.type].
     * [message] es el mensaje crudo (a menudo "16: Developer console is not
     * set up correctly" o "10: ").
     */
    fun fromCredentialManager(type: String, message: String?): String {
        val detail = message?.trim()?.takeIf { it.isNotEmpty() }
        val haystack = listOf(type, detail.orEmpty()).joinToString(" ").lowercase()

        val hint = when {
            isDeveloperConsoleError(haystack) ->
                "El SHA-1 del APK no está registrado en Firebase Console " +
                    "(Configuración del proyecto → Tus apps → Huellas del certificado) " +
                    "o el Web client ID no es de este proyecto."
            "network" in haystack || "timeout" in haystack || "unable to resolve" in haystack ->
                "Revisa tu conexión e inténtalo de nuevo."
            "interrupted" in haystack ->
                "Se interrumpió el inicio de sesión. Vuelve a tocarlo."
            else ->
                "Verifica en Firebase Console que el login con Google esté habilitado " +
                    "y que el SHA-1 del APK instalado esté registrado."
        }

        return if (detail != null) {
            "No se pudo iniciar sesión con Google ($detail). $hint"
        } else {
            "No se pudo iniciar sesión con Google. $hint"
        }
    }

    /**
     * True cuando el primer intento no encontró cuentas y conviene
     * reintentar con la otra hoja de Google, en vez de pintar un error.
     */
    fun isNoCredentialFailure(type: String, message: String?): Boolean {
        val haystack = listOf(type, message.orEmpty()).joinToString(" ").lowercase()
        if (isDeveloperConsoleError(haystack)) return false
        return "nocredential" in haystack.replace("_", "") ||
            "no credential" in haystack ||
            "cannot find a matching credential" in haystack ||
            "no credentials available" in haystack
    }

    /**
     * Mapeo de excepciones de Firebase Auth SOLO para el login con Google.
     *
     * No reutiliza el mapeo de email/contraseña: ahí
     * FirebaseAuthInvalidCredentialsException significa "contraseña
     * equivocada", y con un idToken de Google significa que Firebase
     * RECHAZÓ el token (casi siempre SHA-1 / Web client ID).
     */
    fun fromFirebase(exceptionName: String, message: String?): String {
        return when (exceptionName) {
            "FirebaseAuthInvalidCredentialsException" ->
                "Google devolvió un token que Firebase rechazó. Verifica en Firebase Console " +
                    "(Authentication → Google) que el SHA-1 del APK instalado esté registrado " +
                    "y que el Web client ID del google-services.json sea de este proyecto."
            "FirebaseAuthUserCollisionException" ->
                "Esa cuenta de Google usa un correo ya registrado con correo y contraseña. " +
                    "Entra con ese método para vincularla."
            "FirebaseAuthInvalidUserException" ->
                "La cuenta de Google está deshabilitada."
            "FirebaseNetworkException" ->
                "No se pudo conectar con Firebase. Revisa tu internet o intenta de nuevo en unos minutos."
            else -> message?.takeIf { it.isNotBlank() }
                ?: "No se pudo iniciar sesión con Google."
        }
    }

    private fun isDeveloperConsoleError(haystack: String): Boolean {
        return "developer console" in haystack ||
            "developer_error" in haystack ||
            "developer error" in haystack ||
            haystack.contains(" 16:") ||
            haystack.contains(": 16") ||
            haystack.contains("[16]") ||
            haystack.contains(" 10:") ||
            haystack.contains(": 10") ||
            haystack.contains("api exception: 10") ||
            haystack.contains("apiexception: 10")
    }
}
