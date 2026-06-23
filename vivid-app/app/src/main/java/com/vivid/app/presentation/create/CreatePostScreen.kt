package com.vivid.app.presentation.create

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
    STORY,
    REEL
}

/**
 * CreatePostScreen v2 — punto de entrada único para crear contenido.
 *
 * Opciones:
 *   - POST: foto con caption
 *   - STORY: foto o video, expira a 24h
 *   - REEL: video para el feed, con trim/watermark opcional
 */
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
                when (selectedContentType) {
                    CreateContentType.POST -> "Crear publicación"
                    CreateContentType.STORY -> "Crear story"
                    CreateContentType.REEL -> "Crear Reel"
                },
                style = MaterialTheme.typography.headlineSmall
            )
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        // Selector de tipo (3 chips)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedContentType == CreateContentType.POST,
                onClick = { selectedContentType = CreateContentType.POST },
                label = { Text("Publicación") },
                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedContentType == CreateContentType.STORY,
                onClick = { selectedContentType = CreateContentType.STORY },
                label = { Text("Story") },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedContentType == CreateContentType.REEL,
                onClick = { selectedContentType = CreateContentType.REEL },
                label = { Text("Reel") },
                leadingIcon = { Icon(Icons.Default.MovieCreation, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Si eligió Reel, redirigir a CreateReelScreen
        if (selectedContentType == CreateContentType.REEL) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.MovieCreation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Para crear un Reel con video:",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Elige o graba → ajusta el trim → agrega watermark → publica",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { navController.navigate("create_reel") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ir a crear Reel")
                }
            }
            return@Column
        }

        if (selectedContentType == CreateContentType.STORY) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { navController.navigate("create_story") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Foto / Video")
                }
            }
            Text(
                "Las stories duran 24 horas y respetan tu privacidad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Image preview o botones (solo para POST)
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
                Spacer(Modifier.height(16.dp))
                Text(
                    "Selecciona o toma una foto",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                Spacer(Modifier.width(8.dp))
                Text("Galería")
            }

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = {
                    navController.navigate("camera")
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cámara")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text("Caption") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (selectedContentType == CreateContentType.POST && selectedImageUri != null) {
                    isUploading = true
                    errorMessage = null
                    uploadProgress = "Comprimiendo..."
                    scope.launch {
                        try {
                            val auth = FirebaseAuth.getInstance()
                            val user = auth.currentUser
                                ?: throw IllegalStateException("No autenticado")
                            val db = FirebaseFirestore.getInstance()
                            val userSnapshot = db.collection("users").document(user.uid).get().await()
                            val username = userSnapshot.getString("username")
                                ?: user.displayName
                                ?: user.email?.substringBefore('@')
                                ?: "usuario"
                            val compressed = ImageCompressor.compressToBase64(
                                selectedImageUri!!, context
                            ) ?: throw IllegalStateException("No se pudo comprimir")

                            uploadProgress = "Guardando..."
                            val postId = "post_${user.uid}_${System.currentTimeMillis()}"
                            val postData = hashMapOf(
                                "userId" to user.uid,
                                "username" to username,
                                "imageBase64" to compressed,
                                "caption" to caption,
                                "timestamp" to System.currentTimeMillis()
                            )
                            db.collection("posts").document(postId).set(postData).await()
                            uploadProgress = "¡Publicado!"
                            isUploading = false
                            onPostCreated()
                            navController.popBackStack()
                        } catch (e: Exception) {
                            uploadProgress = "Error: ${e.message}"
                            isUploading = false
                            errorMessage = e.message
                        }
                    }
                }
            },
            enabled = !isUploading && selectedImageUri != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(uploadProgress)
            } else {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Publicar")
            }
        }

        errorMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
    }
}
