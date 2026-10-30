package com.vivid.app.presentation.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.vivid.app.data.local.VividDatabase
import com.vivid.app.domain.repository.AccountRepository
import com.vivid.app.domain.repository.DeleteAccountResult
import com.vivid.app.presentation.auth.GoogleCredentialSignIn
import com.vivid.app.presentation.auth.GoogleSignInOutcome
import com.vivid.app.util.AccountPurge
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Estados del flujo de eliminación de cuenta (Ajustes → Tu Cuenta).
 */
sealed interface DeleteAccountState {
    /** Diálogo de confirmación inicial. */
    data object Idle : DeleteAccountState

    /** Purga en curso: no se puede cerrar el diálogo. */
    data class Working(val stage: AccountPurge.Stage) : DeleteAccountState

    /** Firebase exige reautenticación reciente antes de borrar Auth. */
    data class ReauthNeeded(
        val useGoogle: Boolean,
        val usePassword: Boolean,
        val email: String,
        val error: String? = null
    ) : DeleteAccountState

    /** Cuenta eliminada: limpieza local hecha y sesión cerrada. */
    data object Success : DeleteAccountState

    /** Fallo recuperable (p. ej. sin conexión): se puede reintentar. */
    data class Error(val message: String) : DeleteAccountState
}

/**
 * Orquesta la eliminación completa de la cuenta: purga remota (Firestore +
 * Storage + Auth) con reautenticación si Firebase la exige, y limpieza local
 * (Room + historial de búsqueda) al terminar.
 */
@HiltViewModel
class AccountDeletionViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val database: VividDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val state: StateFlow<DeleteAccountState> = _state.asStateFlow()

    /** Comienza (o reanuda) el borrado completo. La purga es idempotente. */
    fun startDeletion() {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                _state.value = DeleteAccountState.Error("No hay sesión activa")
                return@launch
            }
            _state.value = DeleteAccountState.Working(AccountPurge.Stage.CONTENT)
            when (
                val result = accountRepository.deleteAccount(user.uid) { stage ->
                    _state.value = DeleteAccountState.Working(stage)
                }
            ) {
                is DeleteAccountResult.Success -> {
                    clearLocalData()
                    _state.value = DeleteAccountState.Success
                }
                is DeleteAccountResult.ReauthRequired -> {
                    _state.value = DeleteAccountState.ReauthNeeded(
                        useGoogle = result.useGoogle,
                        usePassword = result.usePassword,
                        email = result.email
                    )
                }
                is DeleteAccountResult.Error -> {
                    _state.value = DeleteAccountState.Error(result.message)
                }
            }
        }
    }

    /** Reautenticación con Google (Credential Manager) y reintento del borrado. */
    fun reauthWithGoogle() {
        viewModelScope.launch {
            _state.value = DeleteAccountState.Working(AccountPurge.Stage.AUTH)
            val outcome = GoogleCredentialSignIn.requestIdToken(context)
            when (outcome) {
                is GoogleSignInOutcome.Success -> {
                    val credential = GoogleAuthProvider.getCredential(outcome.idToken, null)
                    val reauthed = runCatching {
                        FirebaseAuth.getInstance().currentUser
                            ?.reauthenticateWithCredential(credential)?.await()
                    }
                    if (reauthed.isSuccess) {
                        startDeletion()
                    } else {
                        _state.value = reauthNeeded(
                            error = reauthed.exceptionOrNull()?.localizedMessage
                                ?: "No se pudo verificar tu identidad"
                        )
                    }
                }
                is GoogleSignInOutcome.Cancelled -> {
                    _state.value = reauthNeeded(error = null)
                }
                is GoogleSignInOutcome.Failure -> {
                    _state.value = reauthNeeded(error = outcome.message)
                }
            }
        }
    }

    /** Reautenticación con correo y contraseña y reintento del borrado. */
    fun reauthWithPassword(password: String) {
        viewModelScope.launch {
            _state.value = DeleteAccountState.Working(AccountPurge.Stage.AUTH)
            val user = FirebaseAuth.getInstance().currentUser
            val email = user?.email.orEmpty()
            if (user == null || email.isBlank() || password.isBlank()) {
                _state.value = reauthNeeded(error = "Escribe tu contraseña")
                return@launch
            }
            val credential = EmailAuthProvider.getCredential(email, password)
            val reauthed = runCatching { user.reauthenticateWithCredential(credential).await() }
            if (reauthed.isSuccess) {
                startDeletion()
            } else {
                _state.value = reauthNeeded(
                    error = reauthed.exceptionOrNull()?.localizedMessage
                        ?: "Contraseña incorrecta"
                )
            }
        }
    }

    /** Vuelve al diálogo de confirmación (o al error) y descarta el estado. */
    fun reset() {
        _state.value = DeleteAccountState.Idle
    }

    private fun reauthNeeded(error: String?): DeleteAccountState {
        val providers = FirebaseAuth.getInstance().currentUser?.providerData
            .orEmpty()
            .mapNotNull { it?.providerId }
            .filter { it != "firebase" }
        return DeleteAccountState.ReauthNeeded(
            useGoogle = providers.contains("google.com"),
            usePassword = providers.contains("password") || providers.contains("email"),
            email = FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
            error = error
        )
    }

    /**
     * Limpieza local tras borrar la cuenta remota: Room (cachés de posts,
     * chats, mensajes, usuarios, reels, historias y hashtags) e historial
     * de búsqueda. Los ajustes de dispositivo (vivid_settings / vivid_locale)
     * se conservan: no son datos personales.
     */
    private suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        runCatching {
            database.postDao().clearPosts()
            database.chatDao().clearAllChats()
            database.messageDao().clearAllMessages()
            database.userDao().clearAllUsers()
            database.reelDao().clearReels()
            database.storyDao().clearStories()
            database.hashtagDao().clear()
            context.getSharedPreferences("vivid_search", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        runCatching { FirebaseAuth.getInstance().signOut() }
        // Credential Manager cachea la cuenta usada: se limpia como en el logout.
        runCatching { GoogleCredentialSignIn.clearCredentialState(context) }
        Unit
    }
}
