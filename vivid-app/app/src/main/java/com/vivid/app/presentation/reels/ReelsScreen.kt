package com.vivid.app.presentation.reels

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.domain.repository.FollowActionResult
import com.vivid.app.domain.repository.FollowRelationshipState
import com.vivid.app.domain.repository.FollowRepository
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.util.SettingsManager
import com.vivid.app.util.PushSender
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    onCreateReel: () -> Unit = {},
    initialReelId: String? = null,
    viewModel: ReelsViewModel = hiltViewModel()
) {
    val reels by viewModel.reels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    // Estado del pager: el count se actualiza automáticamente con reels.size
    val pagerState = rememberPagerState(pageCount = { reels.size })

    // Deep link: saltar al reel específico si viene desde notificación
    LaunchedEffect(initialReelId, reels) {
        val reelId = initialReelId ?: return@LaunchedEffect
        if (reels.isEmpty()) return@LaunchedEffect
        val index = reels.indexOfFirst { it.id == reelId }
        if (index >= 0 && index != pagerState.currentPage) {
            pagerState.scrollToPage(index)
        }
    }

    // Scroll infinito TikTok: cuando llegas a los últimos 3, carga más
    LaunchedEffect(pagerState.currentPage, reels.size, hasMore) {
        if (reels.isNotEmpty() && hasMore && pagerState.currentPage >= reels.size - 3) {
            viewModel.loadMore()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

            reels.isEmpty() -> EmptyReelsState(onCreateReel = onCreateReel)

            else -> VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { index -> if (index < reels.size) reels[index].id else index }
            ) { page ->
                if (page in reels.indices) {
                    val reel = reels[page]
                    ReelPage(
                        reel = reel,
                        isPlaying = page == pagerState.currentPage
                    )
                }
            }
        }

        // Header "Reels" flotante — píldora compacta con contenedor translúcido consistente
        Surface(
            color = Color.Black.copy(alpha = 0.35f),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reels", color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
        }

        // Indicador de progreso vertical estilo TikTok (derecha)
        if (reels.size > 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp, top = 80.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                reels.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .width(3.dp)
                            .height(if (index == pagerState.currentPage) 24.dp else 12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }

        // Loading more indicator
        if (isLoadingMore) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        // FAB Crear Reel
        ExtendedFloatingActionButton(
            onClick = onCreateReel,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Crear Reel", modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Crear", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

