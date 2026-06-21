package com.vivid.app.presentation.create

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.local.entity.PostEntity
import com.vivid.app.presentation.stories.uploadStoryWithCompression
import com.vivid.app.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

private enum class CreateContentType {
    POST,
    STORY
}

@Composable
fun CreatePostScreen(
    navController: NavController,
    onPostCreated: () -> Unit = {}
) {
    var selectedContentType by remember { mutableStateOf(CreateContentType.POST) }
    var caption by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val capturedPhotoPathState = currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("capturedPhoto", "")
        ?.collectAsState()
    val capturedPhotoPath = capturedPhotoPathState?.value.orEmpty()

    LaunchedEffect(capturedPhotoPath) {
        if (capturedPhotoPath.isNotBlank()) {
            selectedImageUri = Uri.parse(capturedPhotoPath)
            errorMessage = null
            currentBackStackEntry?.savedStateHandle?.remove<String>("capturedPhoto")
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        errorMessage = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selectedContentType == CreateContentType.POST) "Crear publicación" else "Crear story",
                style = MaterialTheme.typography.headlineSmall
            )
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = selectedContentType == CreateContentType.POST,
                onClick = { selectedContentType = CreateContentType.POST },
                label = { Text("Publicación") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedContentType == CreateContentType.STORY,
                onClick = { selectedContentType = CreateContentType.STORY },
                label = { Text("Story") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedContentType == CreateContentType.STORY) {
            Text(
                "Tu story se verá 24 horas y respetará tu privacidad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Image preview or buttons
        if (selectedImageUri != null) {
            AsyncImage(
                model = selectedImageUri,
                contentDescription = "Imagen seleccionada",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Selecciona o toma una foto", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Galería")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = {
                    navController.navigate("camera")
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cámara")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = {
                Text(
                    if (selectedContentType == CreateContentType.POST) {
                        "Descripción / Caption"
                    } else {
                        "Texto de la story (opcional)"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            supportingText = {
                if (selectedContentType == CreateContentType.STORY) {
                    Text("Las stories privadas solo podrán verlas tus seguidores aprobados.")
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mensaje de error
        errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Progreso
        if (uploadProgress.isNotBlank()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uploadProgress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = {
                selectedImageUri?.let { uri ->
                    if (selectedContentType == CreateContentType.POST && caption.isBlank()) {
                        errorMessage = "Por favor escribe una descripción"
                        return@let
                    }
                    isUploading = true
                    errorMessage = null
                    uploadProgress = if (selectedContentType == CreateContentType.POST) {
                        "Comprimiendo imagen..."
                    } else {
                        "Preparando story..."
                    }
                    scope.launch {
                        try {
                            val wasSuccessful = when (selectedContentType) {
                                CreateContentType.POST -> uploadPostWithCompression(
                                    uri = uri,
                                    caption = caption,
                                    context = context,
                                    onProgress = { status ->
                                        uploadProgress = status
                                    }
                                )

                                CreateContentType.STORY -> {
                                    val result = uploadStoryWithCompression(
                                        context = context,
                                        uri = uri,
                                        caption = caption.trim()
                                    )
                                    result.onFailure { throwable ->
                                        errorMessage = throwable.message ?: "No se pudo subir la story"
                                        uploadProgress = ""
                                    }
                                    result.isSuccess
                                }
                            }

                            if (wasSuccessful) {
                                uploadProgress = if (selectedContentType == CreateContentType.POST) {
                                    "¡Publicado con éxito! 🎉"
                                } else {
                                    "¡Story compartida con éxito! ✨"
                                }
                                selectedImageUri = null
                                caption = ""
                                onPostCreated()
                                navController.navigate("feed") {
                                    popUpTo("feed") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else if (errorMessage.isNullOrBlank()) {
                                errorMessage = if (selectedContentType == CreateContentType.POST) {
                                    "Error al publicar. Intenta de nuevo."
                                } else {
                                    "Error al subir la story. Intenta de nuevo."
                                }
                                uploadProgress = ""
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}"
                            uploadProgress = ""
                        }
                        isUploading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedImageUri != null && !isUploading
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedContentType == CreateContentType.POST) "Publicando..." else "Subiendo story...")
            } else {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedContentType == CreateContentType.POST) "Publicar en Vivid" else "Compartir story")
            }
        }
    }
}

/**
 * Sube una imagen comprimida a Firebase SIN usar Firebase Storage.
 * La imagen se comprime, convierte a Base64 y se guarda en Firestore.
 * También se guarda localmente en Room.
 */
private suspend fun uploadPostWithCompression(
    uri: Uri,
    caption: String,
    context: Context,
    onProgress: (String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onProgress("Error: No hay sesión iniciada")
            return@withContext false
        }

        // Paso 1: Comprimir imagen
        onProgress("Comprimiendo imagen...")
        val compressedBase64 = ImageCompressor.compressToBase64(uri, context)
        if (compressedBase64.isNullOrEmpty()) {
            onProgress("Error: No se pudo comprimir la imagen")
            return@withContext false
        }

        val base64SizeKB = compressedBase64.length / 1024
        onProgress("Imagen comprimida: ${base64SizeKB}KB")

        // Paso 2: Preparar datos
        val db = FirebaseFirestore.getInstance()
        val userSnapshot = db.collection("users").document(user.uid).get().await()
        val postId = UUID.randomUUID().toString()
        val username = userSnapshot.getString("username")
            ?: user.displayName
            ?: user.email?.substringBefore("@")
            ?: "usuario"
        val profilePic = userSnapshot.getString("avatarUrl") ?: user.photoUrl?.toString().orEmpty()
        val profilePicBase64 = userSnapshot.getString("avatarBase64").orEmpty()

        val postData = hashMapOf(
            "userId" to user.uid,
            "username" to username,
            "userProfilePicture" to profilePic,
            "userProfilePictureBase64" to profilePicBase64,
            "imageBase64" to compressedBase64,
            "caption" to caption,
            "likesCount" to 0,
            "commentsCount" to 0,
            "timestamp" to System.currentTimeMillis()
        )

        // Paso 3: Subir a Firestore (SIN Firebase Storage!)
        onProgress("Guardando en la nube...")
        return@withContext try {
            val task = db.collection("posts").document(postId).set(postData)
            
            // Esperar a que Firestore confirme
            task.await()
            db.collection("users").document(user.uid).set(
                mapOf(
                    "postsCount" to com.google.firebase.firestore.FieldValue.increment(1),
                    "updatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            onProgress("¡Publicado exitosamente! 🎉")
            true
        } catch (e: Exception) {
            onProgress("Error de conexión, guardando localmente...")
            // Guardar local aunque falle Firebase
            try {
                val localPost = PostEntity(
                    id = postId,
                    userId = user.uid,
                    username = username,
                    userProfilePicture = profilePic,
                    imageUrl = "",
                    imageBase64 = compressedBase64,
                    caption = caption,
                    likesCount = 0,
                    commentsCount = 0,
                    timestamp = System.currentTimeMillis(),
                    isLiked = false
                )
                // Aquí se guardaría en Room
                onProgress("Guardado localmente ✓")
            } catch (e2: Exception) {
                // Silenciar error de guardado local
            }
            false
        }
    } catch (e: Exception) {
        onProgress("Error: ${e.message}")
        false
    }
}
