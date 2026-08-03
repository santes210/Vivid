package com.vivid.app.presentation.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.di.BuildConfigSecrets
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, notice = null)
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, notice = null)
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, notice = null)
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

    fun resetPassword(email: String) {
        viewModelScope.launch {
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                reportExternalError("Escribe tu correo arriba para enviarte el enlace de restablecimiento.")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, notice = null)
            try {
                auth.sendPasswordResetEmail(cleanEmail).await()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    notice = "Te enviamos un enlace a $cleanEmail para restablecer tu contraseña."
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

    fun reportExternalError(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = message)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, notice = null)
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

private fun Exception.toReadableAuthMessage(): String {
    return when (this) {
        is FirebaseNetworkException ->
            "No se pudo conectar con Firebase. Revisa tu internet o intenta de nuevo en unos minutos."
        is FirebaseAuthInvalidUserException ->
            "No encontramos una cuenta con ese correo. Crea una cuenta nueva abajo."
        is FirebaseAuthInvalidCredentialsException ->
            "Correo o contraseña incorrectos. Verifica tus datos."
        is FirebaseAuthWeakPasswordException ->
            "La contraseña es muy débil: usa al menos 6 caracteres con mayúsculas y números."
        is FirebaseAuthUserCollisionException ->
            "Ese correo ya tiene una cuenta. Inicia sesión en su lugar."
        else -> message ?: "Ocurrió un error de autenticación."
    }
}

// ======================================================================
//  LOGIN / REGISTRO — Material You 3
// ======================================================================

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scheme = MaterialTheme.colorScheme

    var isLoginMode by rememberSaveable { mutableStateOf(true) }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

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
            val apiError = (it as? ApiException)?.statusCode
            // 12501 = sign-in cancelado por el usuario: no mostrar como error.
            if (apiError != 12501) {
                val message = when (apiError) {
                    // DEVELOPER_ERROR: app o web client no registrados en Firebase Console.
                    10 -> "Google rechazó el inicio (DEVELOPER_ERROR). Falta registrar el SHA-1/SHA-256 en Firebase Console o renovar google-services.json."
                    else -> it.message ?: "No se pudo iniciar sesión con Google."
                }
                viewModel.reportExternalError(message)
            }
        }
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) onLoginSuccess()
    }
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onLoginSuccess()
    }

    val canSubmit = !uiState.isLoading &&
            email.contains("@") &&
            password.length >= 6

    fun submit() {
        keyboardController?.hide()
        if (isLoginMode) viewModel.login(email, password)
        else viewModel.register(email, password, username)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // ── Auras decorativas (tonos del tema dinámico, sin cambiar colores) ──
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopEnd)
                .offset(x = 110.dp, y = (-80).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(scheme.tertiary.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(420.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-140).dp, y = 110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(scheme.primary.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // ── Marca ──
            Text(
                text = "Vivid",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp,
                    brush = Brush.linearGradient(
                        listOf(scheme.primary, scheme.tertiary)
                    )
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isLoginMode) "Bienvenido de vuelta a tu mundo en colores"
                else "Crea tu cuenta y empieza a compartir",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // ── Tarjeta principal ──
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Selector Entrar / Crear cuenta
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = isLoginMode,
                            onClick = { isLoginMode = true; viewModel.clearMessages() },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Entrar") }
                        SegmentedButton(
                            selected = !isLoginMode,
                            onClick = { isLoginMode = false; viewModel.clearMessages() },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Crear cuenta") }
                    }

                    // Campo username (solo registro)
                    AnimatedVisibility(
                        visible = !isLoginMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.trimStart() },
                            label = { Text("Nombre de usuario") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("Correo electrónico") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Contraseña") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Filled.VisibilityOff
                                        else Icons.Filled.Visibility,
                                        contentDescription = if (passwordVisible) "Ocultar contraseña"
                                        else "Mostrar contraseña"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { if (canSubmit) submit() }
                            ),
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Medidor de fortaleza (solo en registro)
                        AnimatedVisibility(visible = !isLoginMode && password.isNotEmpty()) {
                            PasswordStrengthHint(password = password)
                        }
                    }

                    // Mensajes de error / aviso
                    uiState.error?.let { error ->
                        MessageCard(
                            message = error,
                            isError = true,
                            onDismiss = viewModel::clearMessages
                        )
                    }
                    uiState.notice?.let { notice ->
                        MessageCard(
                            message = notice,
                            isError = false,
                            onDismiss = viewModel::clearMessages
                        )
                    }

                    // Botón principal
                    Button(
                        onClick = { submit() },
                        enabled = canSubmit,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = scheme.onPrimary
                            )
                        } else {
                            Text(
                                if (isLoginMode) "Iniciar sesión" else "Crear cuenta",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // ¿Olvidaste tu contraseña? (solo en login)
                    if (isLoginMode) {
                        TextButton(
                            onClick = { viewModel.resetPassword(email) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("¿Olvidaste tu contraseña?")
                        }
                    }

                    // Separador
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text("o", style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant)
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    // Google
                    FilledTonalButton(
                        onClick = {
                            // El ID vive en BuildConfigSecrets: google-services.json trae
                            // oauth_client vacío y R.string.default_web_client_id no se genera.
                            val webClientId = BuildConfigSecrets.GOOGLE_WEB_CLIENT_ID
                            if (webClientId.isBlank()) {
                                viewModel.reportExternalError(
                                    "Inicio con Google no configurado: falta el Web Client ID. " +
                                        "Pégalo en BuildConfigSecrets.GOOGLE_WEB_CLIENT_ID " +
                                        "(Firebase Console > Authentication > Google > ID de cliente web)."
                                )
                            } else {
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestIdToken(webClientId)
                                    .requestEmail()
                                    .build()
                                val client = GoogleSignIn.getClient(context, gso)
                                client.signOut().addOnCompleteListener {
                                    googleLauncher.launch(client.signInIntent)
                                }
                            }
                        },
                        enabled = !uiState.isLoading,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = scheme.surfaceVariant.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            "G",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = scheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Continuar con Google",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Al continuar aceptas los Términos y la Política de privacidad de Vivid.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

/** Tarjeta inline para errores (rojo) y avisos positivos (tonal). */
@Composable
private fun MessageCard(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = content,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    }
}

/** Medidor simple de fortaleza de contraseña para el registro. */
@Composable
private fun PasswordStrengthHint(password: String) {
    val scheme = MaterialTheme.colorScheme
    var score = 0
    if (password.length >= 6) score++
    if (password.length >= 10) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++

    val (label, color, fraction) = when {
        score <= 1 -> Triple("Débil", scheme.error, 0.25f)
        score <= 3 -> Triple("Media", scheme.tertiary, 0.6f)
        else -> Triple("Fuerte", scheme.primary, 1f)
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MaterialTheme.shapes.extraSmall),
            color = color,
            trackColor = scheme.surfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Seguridad de la contraseña: $label",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
    }
}
