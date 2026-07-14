package com.vivid.app.presentation.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.R
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

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
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
                "activityStatusEnabled" to (existing["activityStatusEnabled"] ?: true),
                "isOnline" to (existing["isOnline"] ?: false),
                "lastActiveAt" to (existing["lastActiveAt"] ?: System.currentTimeMillis()),
                "createdAt" to (existing["createdAt"] ?: System.currentTimeMillis()),
                "updatedAt" to System.currentTimeMillis()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    fun reportExternalError(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = message)
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
    val currentUser = FirebaseAuth.getInstance().currentUser
    val context = LocalContext.current
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsState()
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching {
            task.getResult(ApiException::class.java)
        }.onSuccess { account ->
            val idToken = account.idToken
            if (!idToken.isNullOrBlank()) {
                viewModel.loginWithGoogle(idToken)
            } else {
                viewModel.reportExternalError("Google no devolvió un token válido.")
            }
        }.onFailure {
            viewModel.reportExternalError(it.message ?: "No se pudo iniciar sesión con Google.")
        }
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            onLoginSuccess()
        }
    }

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

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(context.getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build()
                val client = GoogleSignIn.getClient(context, gso)
                client.signOut().addOnCompleteListener {
                    googleLauncher.launch(client.signInIntent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Continuar con Google")
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
