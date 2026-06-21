package com.vivid.app.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
                ensureUserProfile()
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toReadableAuthMessage()
                )
            }
        }
    }

    fun register(email: String, password: String, username: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val cleanEmail = email.trim()
                val cleanUsername = username.trim().ifBlank { cleanEmail.substringBefore("@") }
                val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                result.user?.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanUsername)
                        .build()
                )?.await()
                ensureUserProfile(cleanUsername)
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toReadableAuthMessage()
                )
            }
        }
    }

    private suspend fun ensureUserProfile(usernameOverride: String? = null) {
        val user = auth.currentUser ?: return
        val username = usernameOverride
            ?: user.displayName
            ?: user.email?.substringBefore("@")
            ?: "usuario"

        val userRef = firestore.collection("users").document(user.uid)
        val snapshot = userRef.get().await()
        val existing = snapshot.data.orEmpty()

        userRef.set(
            mapOf(
                "uid" to user.uid,
                "username" to (existing["username"] as? String ?: username),
                "usernameLower" to (existing["usernameLower"] as? String ?: username.lowercase()),
                "displayName" to (existing["displayName"] as? String ?: username),
                "displayNameLower" to (existing["displayNameLower"] as? String ?: username.lowercase()),
                "email" to (user.email ?: ""),
                "avatarUrl" to (existing["avatarUrl"] as? String ?: user.photoUrl?.toString().orEmpty()),
                "bio" to (existing["bio"] as? String ?: ""),
                "followersCount" to (existing["followersCount"] ?: 0),
                "followingCount" to (existing["followingCount"] ?: 0),
                "postsCount" to (existing["postsCount"] ?: 0),
                "createdAt" to (existing["createdAt"] ?: System.currentTimeMillis()),
                "updatedAt" to System.currentTimeMillis()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

private fun Exception.toReadableAuthMessage(): String {
    return when (this) {
        is FirebaseNetworkException -> "No se pudo conectar con Firebase. Revisa tu internet o intenta de nuevo en unos minutos."
        else -> message ?: "Ocurrió un error de autenticación."
    }
}

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Vivid",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isLoginMode) "Inicia sesión" else "Crea tu cuenta",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isLoginMode) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nombre de usuario") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isLoginMode) {
                    viewModel.login(email, password)
                } else {
                    viewModel.register(email, password, username)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (isLoginMode) "Iniciar sesión" else "Registrarse")
            }
        }

        TextButton(onClick = { isLoginMode = !isLoginMode }) {
            Text(
                if (isLoginMode) "¿No tienes cuenta? Regístrate"
                else "¿Ya tienes cuenta? Inicia sesión"
            )
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = { viewModel.clearError() }) {
                Text("Reintentar")
            }
        }
    }
}
