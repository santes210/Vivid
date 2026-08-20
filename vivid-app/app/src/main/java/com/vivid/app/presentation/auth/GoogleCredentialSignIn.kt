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
import kotlinx.coroutines.CancellationException
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
 * Google marcó como deprecado y está apagando.
 *
 * Flujo para el botón **"Continuar con Google"** (no One Tap):
 *  1. [GetSignInWithGoogleOption]: la hoja completa de cuentas. Es lo que
 *     documenta Google para un botón explícito y funciona para cuentas que
 *     nunca habían autorizado esta app.
 *  2. Si el sistema no tiene credenciales que ofrecer, se reintenta con
 *     [GetGoogleIdOption] (`filterByAuthorizedAccounts = false`) para cubrir
 *     dispositivos donde la hoja del botón no está disponible.
 *
 * El flujo anterior empezaba con `filterByAuthorizedAccounts = true` (One Tap).
 * En cuentas nuevas eso lanza GetCredentialException (a menudo error 16 /
 * "Cannot find a matching credential") en vez de NoCredentialException, y el
 * fallback a la hoja completa NUNCA corría: exactamente el "error de Google
 * sign-in" en cuentas nuevas.
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
            ?: return GoogleSignInOutcome.Failure(GoogleSignInMessages.NO_ACTIVITY)

        val serverClientId = webClientIdOrEmpty(context)
        if (serverClientId.isBlank()) {
            return GoogleSignInOutcome.Failure(GoogleSignInMessages.MISSING_WEB_CLIENT)
        }

        val credentialManager = CredentialManager.create(activity)

        // 1) Botón "Continuar con Google": hoja completa de cuentas.
        val signInButton = GetSignInWithGoogleOption.Builder(serverClientId).build()
        requestCredential(credentialManager, activity, signInButton)
            ?.let { return it }

        // 2) Fallback: bottom sheet con TODAS las cuentas del dispositivo
        //    (no solo las ya autorizadas: las cuentas nuevas tienen que verse).
        val allAccounts = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        return requestCredential(credentialManager, activity, allAccounts)
            ?: GoogleSignInOutcome.Failure(GoogleSignInMessages.NO_ACCOUNTS)
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
     * Web client ID de Google.
     *
     * 1. [com.vivid.app.BuildConfig.GOOGLE_WEB_CLIENT_ID]: lo copia Gradle
     *    desde google-services.json (oauth_client client_type 3) al compilar.
     *    Sobrevive a R8 / shrinkResources. Es la fuente principal.
     * 2. Recurso `default_web_client_id` (plugin google-services), leído por
     *    nombre porque puede no existir en un JSON placeholder. El release
     *    lo conserva con `res/values/keep.xml`.
     *
     * Devuelve "" si no hay ninguno: la UI muestra un aviso, no crashea.
     */
    fun webClientIdOrEmpty(context: Context): String {
        com.vivid.app.BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        val resources = context.resources
        val resId = resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId != 0) {
            val fromRes = resources.getString(resId).trim()
            if (fromRes.isNotEmpty()) return fromRes
        }
        Log.e(TAG, "Web client ID ausente: BuildConfig vacío y default_web_client_id no está en resources")
        return ""
    }

    // ── Internals ──────────────────────────────────────────────────────────

    /**
     * Lanza una petición concreta.
     *
     * Devuelve `null` (y solo en ese caso) cuando el sistema no tiene ninguna
     * credencial que ofrecer, para que el llamante pueda reintentar con la otra
     * hoja de cuentas. Cancelar o un error real NO devuelven null.
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            Log.i(TAG, "Sin credenciales para ${option.javaClass.simpleName}, se reintenta")
            null
        } catch (e: GetCredentialException) {
            if (GoogleSignInMessages.isNoCredentialFailure(e.type, e.message)) {
                Log.i(TAG, "Sin credenciales (${e.type}) para ${option.javaClass.simpleName}, se reintenta")
                null
            } else {
                Log.e(TAG, "GetCredential falló type=${e.type} option=${option.javaClass.simpleName}", e)
                GoogleSignInOutcome.Failure(
                    GoogleSignInMessages.fromCredentialManager(e.type, e.message)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "GetCredential inesperado option=${option.javaClass.simpleName}", e)
            GoogleSignInOutcome.Failure(
                GoogleSignInMessages.fromCredentialManager(e.javaClass.name, e.message)
            )
        }
    }

    private fun Credential.toOutcome(): GoogleSignInOutcome {
        if (this is CustomCredential &&
            type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return try {
                val googleCredential = GoogleIdTokenCredential.createFrom(data)
                val idToken = googleCredential.idToken
                if (idToken.isBlank()) {
                    GoogleSignInOutcome.Failure(GoogleSignInMessages.EMPTY_TOKEN)
                } else {
                    GoogleSignInOutcome.Success(idToken)
                }
            } catch (e: GoogleIdTokenParsingException) {
                Log.e(TAG, "No se pudo parsear el ID token de Google", e)
                GoogleSignInOutcome.Failure(GoogleSignInMessages.UNREADABLE_TOKEN)
            }
        }
        return GoogleSignInOutcome.Failure(GoogleSignInMessages.UNEXPECTED_CREDENTIAL)
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
