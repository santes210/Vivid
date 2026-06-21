package com.vivid.app.presentation.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(user?.displayName ?: "") }
    var bio by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(user?.email?.substringBefore("@") ?: "") }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }

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

        // Profile picture
        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = profileImageUri ?: user?.photoUrl?.toString() ?: "https://picsum.photos/id/1011/120/120",
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
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
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Nombre de usuario") },
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("@") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Biografía") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

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
                    scope.launch {
                        saveProfile(
                            displayName = displayName,
                            bio = bio,
                            username = username,
                            imageUri = profileImageUri,
                            onSuccess = onSave
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isSaving
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
    onSuccess: () -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()

    val userData = mutableMapOf<String, Any>(
        "displayName" to displayName,
        "bio" to bio,
        "username" to username
    )

    if (imageUri != null) {
        val ref = storage.reference.child("profile_pictures/${user.uid}.jpg")
        ref.putFile(imageUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    userData["profilePictureUrl"] = url.toString()
                    updateFirestore(user.uid, userData, onSuccess)
                }
            }
    } else {
        updateFirestore(user.uid, userData, onSuccess)
    }
}

private fun updateFirestore(
    uid: String,
    data: Map<String, Any>,
    onSuccess: () -> Unit
) {
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(uid)
        .set(data, com.google.firebase.firestore.SetOptions.merge())
        .addOnSuccessListener { onSuccess() }
}