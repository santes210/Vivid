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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.domain.repository.FollowActionResult
import com.vivid.app.domain.repository.FollowRelationshipState
import com.vivid.app.domain.repository.FollowRepository
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.SoraFamily
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.pressMorphShape
import com.vivid.app.ui.components.VividOfflineBannerHost
import com.vivid.app.util.SettingsManager
import com.vivid.app.util.PushSender
import com.vivid.app.util.rememberPlaybackPolicy
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
    val errorMessage by viewModel.errorMessage.collectAsState()
    val policy = rememberPlaybackPolicy()

    LaunchedEffect(policy.pageSize, policy.constrained) {
        viewModel.applyPlaybackPolicy(policy.pageSize)
    }

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
                LoadingIndicator(color = Color.White, polygons = VividMaterialShapes.LoadingSequence)
            }

            reels.isEmpty() && errorMessage != null -> Surface(Modifier.fillMaxSize()) {
                VividErrorState(
                    message = errorMessage.orEmpty(),
                    onRetry = { viewModel.refresh() }
                )
            }

            reels.isEmpty() -> EmptyReelsState(onCreateReel = onCreateReel)

            else -> VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = policy.beyondViewportPageCount,
                key = { index -> if (index < reels.size) reels[index].id else index }
            ) { page ->
                if (page in reels.indices) {
                    val reel = reels[page]
                    ReelPage(
                        reel = reel,
                        isPlaying = page == pagerState.currentPage && policy.autoplayAllowed
                    )
                }
            }
        }

        // Banner de sin conexión, debajo de la píldora "Reels".
        // safeDrawing (y no statusBars): en pantallas inmersivas el Scaffold
        // no aplica insets, así que cada overlay consume el suyo; con
        // safeDrawing el notch/cutout queda cubierto también en modos
        // borderless, donde statusBars solo daría el alto de la barra.
        VividOfflineBannerHost(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 66.dp)
        )

        // Header "Reels" flotante — píldora compacta con contenedor translúcido consistente
        Surface(
            color = Color.Black.copy(alpha = 0.35f),
            shape = VividExpressiveShapes.SmallCard,
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
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
                LoadingIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    polygons = VividMaterialShapes.LoadingSequence
                )
            }
        }

        // FAB Crear Reel — misma gramática que el botón "Crear" del bottom nav:
        // al pulsarlo la silueta se transforma (círculo → galleta de 9 puntas)
        // en vez de limitarse a un ripple. Consistencia con pressMorphShape.
        val createFabInteractions = remember { MutableInteractionSource() }
        ExtendedFloatingActionButton(
            onClick = onCreateReel,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // safeDrawing: en nav de 3 botones el FAB ya no se pinta
                // detrás de la barra; en gestual el inset es mínimo.
                .safeDrawingPadding()
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            interactionSource = createFabInteractions,
            shape = pressMorphShape(
                interactionSource = createFabInteractions,
                resting = VividMaterialShapes.FabResting,
                pressed = VividMaterialShapes.FabPressed
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(com.vivid.app.R.string.cd_create_reel), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(VividSpace.xs))
            Text(
                stringResource(com.vivid.app.R.string.nav_create),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

