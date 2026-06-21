package com.vivid.app.presentation.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.util.ImageCompressor
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = FirebaseAuth.getInstance().currentUser

    var displayName by remember { mutableStateOf(user?.displayName ?: user?.email?.substringBefore("@") ?: "") }
    var bio by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(user?.email?.substringBefore("@") ?: "") }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentAvatarUrl by remember { mutableStateOf(user?.photoUrl?.toString().orEmpty()) }
    var currentAvatarBase64 by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load existing bio if present
    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    bio = doc.getString("bio") ?: ""
                    val existingUsername = doc.getString("username")
                    if (!existingUsername.isNullOrBlank()) username = existingUsername
                    val existingName = doc.getString("displayName")
                    if (!existingName.isNullOrBlank()) displayName = existingName
                    currentAvatarUrl = doc.getString("avatarUrl").orEmpty()
                    currentAvatarBase64 = doc.getString("avatarBase64").orEmpty()
                }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        profileImageUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Editar perfil", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(24.dp))

        Box(contentAlignment = Alignment.BottomEnd) {
            if (profileImageUri != null) {
                AsyncImage(
                    model = profileImageUri,
                    contentDescription = "Foto de perfil",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Show current from Firestore or fallback
                ProfileAvatarPreview(
                    displayName = displayName,
                    avatarUrl = currentAvatarUrl,
                    avatarBase64 = currentAvatarBase64
                )
            }
            FloatingActionButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.size(36.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Nombre de usuario") },
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("@") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Biografía") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = {
                    isSaving = true
                    errorMessage = null
                    scope.launch {
                        saveProfile(
                            context = context,
                            displayName = displayName.trim(),
                            bio = bio.trim(),
                            username = username.trim(),
                            imageUri = profileImageUri,
                            onSuccess = onSave,
                            onError = { message ->
                                errorMessage = message
                                isSaving = false
                            }
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isSaving && displayName.isNotBlank() && username.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar")
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatarPreview(displayName: String, avatarUrl: String, avatarBase64: String) {
    if (avatarBase64.isNotBlank()) {
        var bitmap by remember(avatarBase64) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(avatarBase64) {
            bitmap = try {
                val bytes = android.util.Base64.decode(avatarBase64, android.util.Base64.NO_WRAP)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            return
        }
    }
    if (avatarUrl.isNotBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Foto de perfil",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "V",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private suspend fun saveProfile(
    context: Context,
    displayName: String,
    bio: String,
    username: String,
    imageUri: Uri?,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser ?: return onError("No hay sesión iniciada")
    val db = FirebaseFirestore.getInstance()

    val baseData = mutableMapOf<String, Any>(
        "uid" to user.uid,
        "displayName" to displayName,
        "displayNameLower" to displayName.lowercase(),
        "bio" to bio,
        "username" to username,
        "usernameLower" to username.lowercase(),
        "email" to (user.email ?: ""),
        "updatedAt" to System.currentTimeMillis()
    )

    try {
        user.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
        ).await()

        if (imageUri != null) {
            // COMPRESS LIKE POSTS
            val compressedBase64 = ImageCompressor.compressToBase64(imageUri, context)
            if (compressedBase64.isNullOrEmpty()) {
                onError("No se pudo comprimir la foto de perfil")
                return
            }

            // Store as base64 (same as posts)
            baseData["avatarBase64"] = compressedBase64
            // Also keep a placeholder for old compatibility
            baseData["avatarUrl"] = ""

            db.collection("users").document(user.uid)
                .set(baseData, com.google.firebase.firestore.SetOptions.merge())
                .await()
            onSuccess()
        } else {
            db.collection("users").document(user.uid)
                .set(baseData, com.google.firebase.firestore.SetOptions.merge())
                .await()
            onSuccess()
        }
    } catch (e: Exception) {
        onError(e.message ?: "Error al guardar")
    }
}
