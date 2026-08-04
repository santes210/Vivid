package com.vivid.app.presentation.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledButton
import androidx.compose.material3.FilledTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

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

    val submit: () -> Unit = {
        keyboardController?.hide()
        if (isLoginMode) {
            viewModel.login(email, password)
        } else {
            viewModel.register(email, password, username)
        }
    }

    val brandBrush = remember(MaterialTheme.colorScheme) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary
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
                brush = brandBrush,
                style = MaterialTheme.typography.displaySmall,
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
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 10 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 10 })
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
                        FilledTextField(
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

                    FilledTextField(
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

                    FilledTextField(
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
                                    contentDescription = if (passwordVisible) "Ocultar contraseña"
                                    else "Mostrar contraseña"
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Error ──────────────────────────────────────────────────────
            AnimatedVisibility(visible = uiState.error != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Botón principal ────────────────────────────────────────────
            FilledButton(
                onClick = { submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
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

            // ── Google (flujo intacto) ─────────────────────────────────────
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
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
}

/**
 * Logo "G" de Google dibujado con Canvas: 4 arcos (azul, rojo, amarillo, verde)
 * + la "G" blanca encima. Cero recursos, cero red, ligero en cualquier teléfono.
 */
@Composable
private fun GoogleLogo(modifier: Modifier = Modifier.size(20.dp)) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.22f
            val radius = size.minDimension / 2f - strokeWidth / 2f
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            val style = Stroke(width = strokeWidth)
            // Orden de cuadrantes (sentido horario desde arriba):
            // azul → rojo → amarillo → verde. Pequeños huecos de 2° en las diagonales.
            drawArc(Color(0xFF4285F4), startAngle = 92f, sweepAngle = 86f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            drawArc(Color(0xFFEA4335), startAngle = 2f, sweepAngle = 86f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            drawArc(Color(0xFFFBBC05), startAngle = 272f, sweepAngle = 86f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            drawArc(Color(0xFF34A853), startAngle = 182f, sweepAngle = 86f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
        }
        Text(
            text = "G",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
    }
}
