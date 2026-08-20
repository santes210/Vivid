package com.vivid.app.presentation.stories

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.theme.LocalVividAccents
import com.vivid.app.util.CrashReporter
import kotlinx.coroutines.launch

private const val TAG = "StoriesScreen"

@Composable
fun StoriesTray(
    onStoryClick: (Story) -> Unit,
    onCreateStory: () -> Unit = {}
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var stories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }

    // ViewModel para limpieza con borrado de B2 (fix 2026-08-09) - sin try-catch porque hiltViewModel es @Composable
    val storyViewModel: CreateStoryViewModel = androidx.hilt.navigation.compose.hiltViewModel()

    LaunchedEffect(currentUserId, retryKey) {
        if (currentUserId.isNotBlank()) {
            storyViewModel.cleanExpiredStories(currentUserId)

            // ── Caché Room: si hay stories cacheadas de menos de 7 días,
            // mostrarlas al instante (arranque rápido / offline) ──
            runCatching {
                if (storyViewModel.isStoryCacheFresh()) {
                    val cached = storyViewModel.getCachedStories()
                    if (cached.isNotEmpty()) {
                        stories = storyViewModel.cachedStoriesToData(cached)
                        isLoading = false
                    }
                }
            }

            // ── Firestore: datos frescos en tiempo real + persistir en caché ──
            loadFailed = false
            runCatching {
                val docs = loadVisibleStoryDocuments(db, currentUserId)
                buildVisibleStories(db, currentUserId, docs)
            }.onSuccess { fresh ->
                if (fresh.isNotEmpty()) {
                    stories = fresh
                    scope.launch {
                        storyViewModel.cacheStories(fresh)
                        com.vivid.app.util.VividCacheManager.markStoriesCached(context)
                    }
                }
            }.onFailure { e ->
                // Solo es un error visible si no hay nada que mostrar:
                // si la caché ya cubrió la fila, el contenido viejo gana.
                CrashReporter.recordNonFatal(TAG, e, "StoriesTray.load")
                loadFailed = stories.isEmpty()
            }
        }
        isLoading = false
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else if (loadFailed && stories.isEmpty()) {
        // Error compacto (la fila vive dentro del feed): mensaje + reintento
        // sin ocupar media pantalla.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "No se pudieron cargar las historias.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { retryKey++ }) { Text("Reintentar") }
        }
    } else {
        StoriesRow(stories = stories, onStoryClick = onStoryClick, onCreateStory = onCreateStory)
    }
}

@Composable
fun StoriesRow(
    stories: List<Story>,
    onStoryClick: (Story) -> Unit,
    onCreateStory: () -> Unit = {}
) {
    val groups = remember(stories) { groupStoriesByUser(stories) }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Acción principal "Tu historia". Si el usuario ya tiene una story
        // activa, el click abre esa story (siempre se ve como no vista para él);
        // si no tiene, abre la pantalla de crear.
        item(key = "create_story") {
            val ownGroup = groups.firstOrNull { it.userId == FirebaseAuth.getInstance().currentUser?.uid }
            CreateStoryItem(
                hasActiveStory = ownGroup != null,
                onClick = {
                    if (ownGroup != null) onStoryClick(ownGroup.stories.first())
                    else onCreateStory()
                }
            )
        }
        // Solo mostramos los grupos de OTROS usuarios (la propia ya salió arriba)
        items(groups.filter { it.userId != FirebaseAuth.getInstance().currentUser?.uid }, key = { it.userId }) { group ->
            StoryGroupItem(group = group, onClick = { onStoryClick(group.stories.first()) })
        }
    }
}

@Composable
private fun CreateStoryItem(hasActiveStory: Boolean, onClick: () -> Unit) {
    // Los colores del tema SE LEEN FUERA del Canvas (contexto @Composable).
    // Anillo de historias = acento de producto (magenta → coral → ámbar),
    // armonizado con el color dinámico. Es la misma rampa que el avatar hero
    // del perfil, así la marca se reconoce en los dos sitios.
    val primary = MaterialTheme.colorScheme.primary
    val storyRing = LocalVividAccents.current.storyRing
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClickLabel = "Tu historia") { onClick() }
    ) {
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasActiveStory) {
                // Si ya tiene story, mostramos anillo con gradiente primario→tertiario estilo IG
                Canvas(modifier = Modifier.size(64.dp)) {
                    val strokeWidth = 3.dp.toPx()
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.sweepGradient(storyRing),
                        radius = size.minDimension / 2 - strokeWidth,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            } else {
                // Anillo "crear" tenue
                Canvas(modifier = Modifier.size(64.dp)) {
                    val strokeWidth = 2.dp.toPx()
                    drawCircle(
                        color = outlineColor,
                        radius = size.minDimension / 2 - strokeWidth,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            // Insignia "+" en la esquina inferior derecha
            Box(modifier = Modifier.size(64.dp)) {
                Surface(
                    shape = CircleShape,
                    color = if (hasActiveStory) primary else surfaceHigh,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Crear historia",
                            tint = if (hasActiveStory) onPrimary else primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Tu historia",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StoryGroupItem(group: StoryGroup, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            StorySegmentsRing(
                segments = group.stories.size.coerceAtLeast(1),
                modifier = Modifier.size(68.dp)
            )
            StoryAvatar(
                username = group.username,
                avatarUrl = group.avatarUrl,
                avatarBase64 = group.avatarBase64,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            group.username,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun StorySegmentsRing(
    segments: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
) {
    // tertiary se lee fuera del Canvas (no se puede leer el tema dentro de DrawScope)
    val storyRing = LocalVividAccents.current.storyRing

    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeftX = strokeWidth / 2
        val topLeftY = strokeWidth / 2
        val gap = 6f
        val segmentSweep = ((360f - gap * segments) / segments).coerceAtLeast(12f)

        // Dos pasadas: PRIMERO el track (fondo gris) en todos los segmentos,
        // DESPUÉS el color activo. Antes se pintaban ambos en el mismo bucle,
        // lo que provocaba que el activo tapara al track y todos lucieran llenos.
        for (index in 0 until segments) {
            val start = -90f + index * (segmentSweep + gap)
            drawArc(
                color = trackColor,
                startAngle = start,
                sweepAngle = segmentSweep,
                useCenter = false,
                topLeft = Offset(topLeftX, topLeftY),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        // Para la bandeja de entrada (feed) todas las stories de otro usuario
        // se muestran como "con contenido": anillo completo con gradiente
        // (estilo IG). La vista detallada (viewer) tiene su propio progreso.
        for (index in 0 until segments) {
            val start = -90f + index * (segmentSweep + gap)
            drawArc(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(storyRing),
                startAngle = start,
                sweepAngle = segmentSweep,
                useCenter = false,
                topLeft = Offset(topLeftX, topLeftY),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun StoryAvatar(
    username: String,
    avatarUrl: String,
    avatarBase64: String,
    modifier: Modifier = Modifier
) {
    com.vivid.app.ui.components.UserAvatar(
        imageUrl = avatarUrl,
        name = username,
        modifier = modifier,
        size = 56.dp
    )
}
