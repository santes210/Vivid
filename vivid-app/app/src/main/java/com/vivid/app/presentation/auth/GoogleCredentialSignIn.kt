package com.vivid.app.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Resultado de pedir un ID token de Google al sistema.
 *
 * [Cancelled] existe aparte de [Failure] porque cerrar la hoja de Google es una
 * acción normal del usuario: no debe pintar un error rojo en la pantalla.
 */
sealed interface GoogleSignInOutcome {
    data class Success(val idToken: String) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Failure(val message: String) : GoogleSignInOutcome
}

/**
 * Login con Google usando **Credential Manager** (androidx.credentials).
 *
 * Sustituye a `GoogleSignIn` / `GoogleSignInOptions` de play-services-auth, que
 * Google marcó como deprecado y está apagando: la Activity de Sign-In clásica
 * dejará de funcionar y el flujo antiguo se rompería solo.
 *
 * Flujo (el recomendado por Firebase + Credential Manager):
 *  1. [GetGoogleIdOption] con `filterByAuthorizedAccounts = true`: si el usuario
 *     ya usó una cuenta con esta app, entra de un toque.
 *  2. Si no hay ninguna cuenta autorizada (NoCredentialException) se reintenta
 *     con [GetSignInWithGoogleOption], el "botón Sign in with Google", que
 *     muestra todas las cuentas del dispositivo y permite añadir una nueva.
 *
 * El ID token resultante se cambia por una sesión de Firebase en
 * `AuthViewModel.loginWithGoogle()` (GoogleAuthProvider.getCredential).
 *
 * Nota sobre el `nonce`: no se envía a propósito. Quien valida el token es
 * Firebase en su servidor (firma + audiencia) y `GoogleAuthProvider.getCredential`
 * no reenvía el nonce en claro, así que añadirlo no aportaría verificación
 * extra. Es el mismo flujo que documenta Firebase.
 */
object GoogleCredentialSignIn {

    private const val TAG = "GoogleCredentialSignIn"

    /** Scope propio para el "clear" al cerrar sesión (fire and forget). */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Pide un ID token de Google. Debe llamarse desde una corrutina de UI: la
     * hoja del sistema se dibuja sobre la Activity que se pasa en [context].
     */
    suspend fun requestIdToken(context: Context): GoogleSignInOutcome {
        val activity = context.findActivity()
            ?: return GoogleSignInOutcome.Failure(
                "No se pudo abrir el selector de cuentas de Google."
            )

        val serverClientId = webClientIdOrEmpty(context)
        if (serverClientId.isBlank()) {
            return GoogleSignInOutcome.Failure(
                "Google Sign-In no está configurado: agrega tu Web client ID en " +
                    "Firebase y actualiza google-services.json."
            )
        }

        val credentialManager = CredentialManager.create(activity)

        // 1) Cuentas ya autorizadas en esta app: login en un toque.
        val authorizedAccounts = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(true)
            // Sin auto-select: el botón siempre muestra el selector para que se
            // pueda entrar con otra cuenta (el flujo antiguo hacía signOut()
            // antes de abrir el intent justo por esto).
            .setAutoSelectEnabled(false)
            .build()

        // requestCredential devuelve null cuando no hay ninguna credencial que ofrecer.
        requestCredential(credentialManager, activity, authorizedAccounts)
            ?.let { return it }

        // 2) Sin cuentas autorizadas: hoja completa "Sign in with Google".
        val allAccounts = GetSignInWithGoogleOption.Builder(serverClientId).build()
        return requestCredential(credentialManager, activity, allAccounts)
            ?: GoogleSignInOutcome.Failure(
                "No hay ninguna cuenta de Google disponible en este dispositivo. " +
                    "Agrega una en Ajustes y vuelve a intentarlo."
            )
    }

    /**
     * Borra el estado de credenciales al cerrar sesión.
     *
     * Es lo que recomienda la documentación de Credential Manager: deja limpio
     * el estado que los proveedores cachean para esta app, de modo que el
     * siguiente login vuelva a preguntar. No bloquea al llamante.
     */
    fun clearCredentialState(context: Context) {
        val appContext = context.applicationContext
        cleanupScope.launch {
            runCatching {
                CredentialManager.create(appContext)
                    .clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }

    /**
     * Web client ID de Google (`default_web_client_id`).
     *
     * Lo genera el plugin google-services durante el build a partir del
     * `oauth_client` con `client_type 3` de google-services.json. Se lee por
     * nombre (Resources.getIdentifier) porque el recurso puede NO existir en
     * builds que usan un google-services.json placeholder sin oauth_client
     * (PRs de forks sin acceso a secrets). Devuelve "" en ese caso y la UI
     * muestra un aviso en vez de reventar.
     */
    fun webClientIdOrEmpty(context: Context): String {
        val resources = context.resources
        val resId = resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) resources.getString(resId) else ""
    }

    // ── Internals ──────────────────────────────────────────────────────────

    /**
     * Lanza una petición concreta.
     *
     * Devuelve `null` (y solo en ese caso) cuando el sistema no tiene ninguna
     * credencial que ofrecer, para que el llamante pueda reintentar con la hoja
     * completa de cuentas.
     */
    private suspend fun requestCredential(
        credentialManager: CredentialManager,
        activity: Activity,
        option: CredentialOption
    ): GoogleSignInOutcome? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            credentialManager.getCredential(activity, request).credential.toOutcome()
        } catch (e: NoCredentialException) {
            null
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInOutcome.Cancelled
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredential falló (type=${e.type})", e)
            GoogleSignInOutcome.Failure(readableCredentialError(e))
        }
    }

    /**
     * Traduce el error del Credential Manager a algo diagnóstico.
     *
     * Los fallos típicos del flujo Google son de CONFIGURACIÓN, no de código:
     *   - SHA-1 del keystore con el que se firmó el APK no registrado en
     *     Firebase Console → la hoja de Google devuelve DEVELOPER_ERROR.
     *   - Web client ID (oauth_client client_type 3) ausente en
     *     google-services.json → no se genera `default_web_client_id`.
     * Por eso el mensaje incluye el `type` real: con él se identifica la
     * causa en un vistazo en vez de un genérico "error de Google sign-in".
     */
    private fun readableCredentialError(e: GetCredentialException): String {
        val detail = e.message?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val hint = when {
            e.type.contains("GOOGLE_ID_TOKEN", ignoreCase = true) ->
                " Revisa en Firebase Console que el SHA-1 del APK esté registrado " +
                    "en la app Android y que el Web client ID sea el del proyecto."
            else -> " Suele deberse a la configuración de Firebase (SHA-1 del APK o Web client ID)."
        }
        return "No se pudo iniciar sesión con Google [$e.type]$detail.$hint"
    }

    private fun Credential.toOutcome(): GoogleSignInOutcome {
        if (this is CustomCredential &&
            type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return try {
                val googleCredential = GoogleIdTokenCredential.createFrom(data)
                val idToken = googleCredential.idToken
                if (idToken.isBlank()) {
                    GoogleSignInOutcome.Failure("Google no devolvió un token válido.")
                } else {
                    GoogleSignInOutcome.Success(idToken)
                }
            } catch (e: GoogleIdTokenParsingException) {
                GoogleSignInOutcome.Failure("Google devolvió una respuesta que no se pudo leer.")
            }
        }
        return GoogleSignInOutcome.Failure("Google devolvió un tipo de credencial inesperado.")
    }

    /** Credential Manager necesita la Activity, no un Context cualquiera. */
    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
