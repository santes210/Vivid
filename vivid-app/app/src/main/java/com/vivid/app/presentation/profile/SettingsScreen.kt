package com.vivid.app.presentation.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current
    
    var isPrivateAccount by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            firestore.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                isPrivateAccount = snapshot.getBoolean("isPrivate") ?: false
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        "Ajustes",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SettingsSectionHeader("Tu Cuenta") }
            item {
                SettingsListItem(
                    title = "Privacidad de la cuenta",
                    subtitle = if (isPrivateAccount) "Cuenta Privada" else "Cuenta Pública",
                    icon = if (isPrivateAccount) Icons.Default.Lock else Icons.Default.Public,
                    trailingContent = {
                        Switch(
                            checked = isPrivateAccount,
                            onCheckedChange = { checked ->
                                isPrivateAccount = checked
                                user?.uid?.let { uid ->
                                    firestore.collection("users").document(uid).update("isPrivate", checked)
                                }
                            }
                        )
                    },
                    onClick = { /* Toggle happens via switch */ }
                )
            }
            item {
                SettingsListItem(
                    title = "Centro de Cuentas",
                    subtitle = "Contraseña, seguridad, detalles personales",
                    icon = Icons.Outlined.Person,
                    onClick = { }
                )
            }

            item { SettingsSectionHeader("Cómo usas Vivid") }
            item {
                SettingsListItem(
                    title = "Notificaciones",
                    icon = Icons.Outlined.Notifications,
                    onClick = { }
                )
            }
            item {
                SettingsListItem(
                    title = "Tiempo en la app",
                    icon = Icons.Outlined.Timer,
                    onClick = { }
                )
            }

            item { SettingsSectionHeader("Quién puede ver tu contenido") }
            item {
                SettingsListItem(
                    title = "Mejores amigos",
                    icon = Icons.Outlined.StarOutline,
                    onClick = { }
                )
            }
            item {
                SettingsListItem(
                    title = "Bloqueados",
                    icon = Icons.Outlined.Block,
                    onClick = { }
                )
            }

            item { SettingsSectionHeader("Más información y ayuda") }
            item {
                SettingsListItem(
                    title = "Centro de Ayuda",
                    subtitle = "Reportar un problema o contactar soporte",
                    icon = Icons.Outlined.HelpOutline,
                    onClick = { showHelpDialog = true }
                )
            }
            item {
                SettingsListItem(
                    title = "Estado de la cuenta",
                    icon = Icons.Outlined.Info,
                    onClick = { }
                )
            }
            item {
                SettingsListItem(
                    title = "Sobre Vivid",
                    subtitle = "Versión 1.0.0 (Build 2026.06.21)",
                    icon = Icons.Outlined.VerifiedUser,
                    onClick = { }
                )
            }

            item {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                
                ListItem(
                    headlineContent = { 
                        Text(
                            "Cerrar sesión", 
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        ) 
                    },
                    leadingContent = { 
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) 
                    },
                    modifier = Modifier.clickable { showSignOutDialog = true }
                )
            }
        }
    }

    // Diálogo de Centro de Ayuda
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Centro de Ayuda") },
            text = { 
                Column {
                    Text("¿Tienes algún problema con Vivid? Estamos aquí para ayudarte.")
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Puedes contactar directamente al desarrollador enviando un correo a:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "poncho2010santes@gmail.com",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:poncho2010santes@gmail.com")
                        putExtra(Intent.EXTRA_SUBJECT, "Soporte Vivid App")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Handle case where no email app is installed
                    }
                    showHelpDialog = false
                }) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enviar Correo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Tendrás que volver a ingresar tus credenciales para entrar a Vivid.") },
            confirmButton = {
                TextButton(onClick = { 
                    auth.signOut()
                    showSignOutDialog = false
                    onBack()
                }) {
                    Text("Cerrar Sesión", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
}

@Composable
fun SettingsListItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    trailingContent: @Composable (() -> Unit)? = { 
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline) 
    },
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = { 
            Icon(
                icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            ) 
        },
        trailingContent = trailingContent,
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    )
}
