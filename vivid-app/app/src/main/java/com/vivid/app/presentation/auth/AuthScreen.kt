package com.vivid.app.presentation.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.R
import com.vivid.app.theme.LocalVividAnimationsEnabled
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)
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
                runCatching {
                    result.user?.sendEmailVerification()?.await()
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSuccess = true,
                    info = "Te enviamos un correo de verificación a $cleanEmail."
                )
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)
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

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    error = "Ingresa tu correo electrónico para restablecer tu contraseña.",
                    info = null
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)
            try {
                auth.sendPasswordResetEmail(cleanEmail).await()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    info = "Te enviamos un enlace de recuperación a $cleanEmail. Revisa tu bandeja de entrada o spam."
                )
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

    /** El usuario abrió la hoja de Google: bloquea la UI mientras decide. */
    fun startExternalSignIn() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)
    }

    /** Cerró la hoja de Google sin elegir cuenta: no es un error. */
    fun cancelExternalSignIn() {
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    fun reportExternalError(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearInfo() {
        _uiState.value = _uiState.value.copy(info = null)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, info = null)
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

private fun Exception.toReadableAuthMessage(): String {
    return when (this) {
        is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "No existe ninguna cuenta registrada con este correo."
        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
        is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "Este correo ya está registrado en otra cuenta."
        is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "La contraseña es muy débil (debe tener al menos 6 caracteres)."
        is FirebaseNetworkException -> "No se pudo conectar con Firebase. Revisa tu internet o intenta de nuevo en unos minutos."
        else -> message ?: "Ocurrió un error de autenticación."
    }
}

/**
 * Pantalla de autenticación — Material You 3 de verdad.
 *
 * Diseño pensado para ser ligero en teléfonos antiguos:
 *   - Sin blur, sin sombras pesadas, sin animaciones infinitas ni gradientes costosos.
 *   - Superficies tonales del color scheme (dynamic color en Android 12+,
 *     paleta Vivid en <12) en lugar de imágenes de fondo.
 *   - verticalScroll + imePadding: el teclado nunca tapa los campos.
 *   - Transición login/registro con fade + slide corto (barata de componer).
 *   - Estados de carga en el propio botón (sin overlays ni dialogs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val animationsEnabled = LocalVividAnimationsEnabled.current
    var isLoginMode by remember { mutableStateOf(true) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Credential Manager sustituye al viejo startActivityForResult de
    // GoogleSignIn: la hoja de cuentas la dibuja el sistema y el resultado
    // llega como valor de retorno de una suspend fun, sin ActivityResult.
    val startGoogleSignIn: () -> Unit = {
        viewModel.startExternalSignIn()
        scope.launch {
            when (val outcome = GoogleCredentialSignIn.requestIdToken(context)) {
                is GoogleSignInOutcome.Success -> viewModel.loginWithGoogle(outcome.idToken)
                is GoogleSignInOutcome.Cancelled -> viewModel.cancelExternalSignIn()
                is GoogleSignInOutcome.Failure -> viewModel.reportExternalError(outcome.message)
            }
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

    val submit: () -> Unit = {
        keyboardController?.hide()
        if (isLoginMode) {
            viewModel.login(email, password)
        } else {
            viewModel.register(email, password, username)
        }
    }

    val brandPrimary = MaterialTheme.colorScheme.primary
    val brandTertiary = MaterialTheme.colorScheme.tertiary
    val brandBrush = remember(brandPrimary, brandTertiary) {
        Brush.linearGradient(
            colors = listOf(
                brandPrimary,
                brandTertiary
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Marca (hero) ────────────────────────────────────────────────
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Vivid",
                style = MaterialTheme.typography.displaySmall.copy(brush = brandBrush),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Crea, comparte y conéctate",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Formulario (login / registro) ──────────────────────────────
            AnimatedContent(
                targetState = isLoginMode,
                transitionSpec = {
                    if (animationsEnabled) {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 10 }) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 10 })
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "authMode"
            ) { loginMode ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (loginMode) "Inicia sesión" else "Crea tu cuenta",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (loginMode) "Bienvenido de nuevo a Vivid" else "Únete a la comunidad",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (!loginMode) {
                        TextField(
                            value = username,
                            onValueChange = {
                                username = it
                                viewModel.clearError()
                            },
                            label = { Text("Nombre de usuario") },
                            leadingIcon = {
                                Icon(Icons.Filled.Person, contentDescription = null)
                            },
                            singleLine = true,
                            enabled = !uiState.isLoading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    TextField(
                        value = email,
                        onValueChange = {
                            email = it
                            viewModel.clearError()
                        },
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(Icons.Filled.Email, contentDescription = null)
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(emailFocus)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = password,
                        onValueChange = {
                            password = it
                            viewModel.clearError()
                        },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible)
                                        stringResource(R.string.cd_hide_password)
                                    else stringResource(R.string.cd_show_password)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (loginMode) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            TextButton(
                                onClick = {
                                    showForgotPasswordDialog = true
                                    viewModel.clearMessages()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "¿Olvidaste tu contraseña?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Info (correo de verificación o recuperación) ────────────────
            AnimatedVisibility(
                visible = uiState.info != null,
                enter = if (animationsEnabled) fadeIn(tween(180)) else EnterTransition.None,
                exit = if (animationsEnabled) fadeOut(tween(120)) else ExitTransition.None
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.info.orEmpty(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearInfo() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            if (uiState.info != null) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Error ──────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = if (animationsEnabled) fadeIn(tween(180)) else EnterTransition.None,
                exit = if (animationsEnabled) fadeOut(tween(120)) else ExitTransition.None
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.error.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Botón principal ────────────────────────────────────────────
            Button(
                onClick = { submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = if (isLoginMode) "Iniciar sesión" else "Registrarse",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Separador ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = "o",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Google (Credential Manager) ────────────────────────────────
            OutlinedButton(
                onClick = startGoogleSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                enabled = !uiState.isLoading
            ) {
                GoogleLogo()
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Continuar con Google",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { isLoginMode = !isLoginMode }) {
                Text(
                    text = if (isLoginMode) "¿No tienes cuenta? Regístrate"
                    else "¿Ya tienes cuenta? Inicia sesión"
                )
            }
        }
    }

    if (showForgotPasswordDialog) {
        var resetEmail by remember(email) { mutableStateOf(email) }
        var resetError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Text(
                    text = "Restablecer contraseña",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        text = "Ingresa tu correo electrónico. Te enviaremos un enlace para crear una nueva contraseña.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = resetEmail,
                        onValueChange = {
                            resetEmail = it
                            resetError = null
                        },
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(Icons.Filled.Email, contentDescription = null)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val cleanEmail = resetEmail.trim()
                                if (cleanEmail.isBlank()) {
                                    resetError = "Por favor ingresa un correo válido."
                                } else {
                                    showForgotPasswordDialog = false
                                    viewModel.sendPasswordReset(cleanEmail)
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (resetError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = resetError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanEmail = resetEmail.trim()
                        if (cleanEmail.isBlank()) {
                            resetError = "Por favor ingresa un correo válido."
                            return@TextButton
                        }
                        showForgotPasswordDialog = false
                        viewModel.sendPasswordReset(cleanEmail)
                    }
                ) {
                    Text("Enviar enlace")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/** Logo oficial multicolor de Google. Es decorativo: el botón ya anuncia su acción completa. */
@Composable
private fun GoogleLogo(modifier: Modifier = Modifier.size(20.dp)) {
    Image(
        painter = painterResource(R.drawable.ic_google_g),
        contentDescription = null,
        modifier = modifier
    )
}
