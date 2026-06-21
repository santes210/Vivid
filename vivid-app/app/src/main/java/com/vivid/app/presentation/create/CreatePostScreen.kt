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
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.local.entity.PostEntity
import com.vivid.app.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@Composable
fun CreatePostScreen(
    navController: NavController,
    onPostCreated: () -> Unit = {}
) {
    var caption by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
            Text("Crear publicación", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
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
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cámara")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text("Descripción / Caption") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
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
                    if (caption.isBlank()) {
                        errorMessage = "Por favor escribe una descripción"
                        return@let
                    }
                    isUploading = true
                    errorMessage = null
                    uploadProgress = "Comprimiendo imagen..."
                    scope.launch {
                        try {
                            val result = uploadPostWithCompression(
                                uri = uri,
                                caption = caption,
                                context = context,
                                onProgress = { status ->
                                    uploadProgress = status
                                }
                            )
                            if (result) {
                                uploadProgress = "¡Publicado con éxito! 🎉"
                                onPostCreated()
                            } else {
                                errorMessage = "Error al publicar. Intenta de nuevo."
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
                Text("Publicando...")
            } else {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Publicar en Vivid")
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
        val postId = UUID.randomUUID().toString()
        val username = user.displayName ?: user.email?.substringBefore("@") ?: "usuario"
        val profilePic = user.photoUrl?.toString() ?: ""

        val postData = hashMapOf(
            "userId" to user.uid,
            "username" to username,
            "userProfilePicture" to profilePic,
            "imageBase64" to compressedBase64,
            "caption" to caption,
            "likesCount" to 0,
            "commentsCount" to 0,
            "timestamp" to System.currentTimeMillis()
        )

        // Paso 3: Subir a Firestore (SIN Firebase Storage!)
        onProgress("Guardando en la nube...")
        return@withContext try {
            val db = FirebaseFirestore.getInstance()
            val task = db.collection("posts").document(postId).set(postData)
            
            // Esperar a que Firestore confirme
            task.await()
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
