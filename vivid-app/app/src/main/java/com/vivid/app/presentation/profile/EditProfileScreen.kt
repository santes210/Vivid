package com.vivid.app.presentation.profile

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

@Composable
fun EditProfileScreen(
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser
    var displayName by remember { mutableStateOf(user?.displayName ?: user?.email?.substringBefore("@") ?: "") }
    var bio by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(user?.email?.substringBefore("@") ?: "") }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            if (profileImageUri != null || user?.photoUrl != null) {
                AsyncImage(
                    model = profileImageUri ?: user?.photoUrl?.toString(),
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
            FloatingActionButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
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
                    saveProfile(
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

private fun saveProfile(
    displayName: String,
    bio: String,
    username: String,
    imageUri: Uri?,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser ?: return onError("No hay sesión iniciada")
    val storage = FirebaseStorage.getInstance()

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

    user.updateProfile(
        UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
    )

    if (imageUri != null) {
        val ref = storage.reference.child("profile_pictures/${user.uid}.jpg")
        ref.putFile(imageUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    baseData["avatarUrl"] = url.toString()
                    updateFirestore(user.uid, baseData, onSuccess, onError)
                }.addOnFailureListener { onError(it.message ?: "No se pudo obtener la imagen") }
            }
            .addOnFailureListener { onError(it.message ?: "No se pudo subir la imagen") }
    } else {
        updateFirestore(user.uid, baseData, onSuccess, onError)
    }
}

private fun updateFirestore(
    uid: String,
    data: Map<String, Any>,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(uid)
        .set(data, com.google.firebase.firestore.SetOptions.merge())
        .addOnSuccessListener { onSuccess() }
        .addOnFailureListener { onError(it.message ?: "No se pudo guardar el perfil") }
}
