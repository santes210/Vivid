package com.vivid.app.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.entity.toCachedEntity
import com.vivid.app.data.paging.ExplorePaging
import com.vivid.app.data.paging.ExplorePostsPagingSource
import com.vivid.app.domain.repository.HashtagRepository
import com.vivid.app.presentation.feed.PostData
import com.vivid.app.util.Hashtags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Chip de Explorar: un hashtag real con cuántos posts recientes lo usan. */
@androidx.compose.runtime.Immutable
data class ExploreTag(
    val tag: String,
    val count: Int
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val hashtagRepository: HashtagRepository,
    private val postDao: PostDao,
    private val exploreSession: ExploreSession
) : ViewModel() {

    private val _selectedTag = MutableStateFlow(ExplorePaging.TAGS.first())
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()

    private val blockedUserIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Chips de Explorar con hashtags REALES.
     *
     * Fuente: el catálogo Room que llena [HashtagRepository.refresh]
     * (descubierto desde posts públicos recientes). Los tags por defecto
     * solo rellenan huecos, así un #tag nuevo publicado por cualquiera
     * aparece aquí por sí solo — ordenado por uso, cacheado y offline.
     */
    val tags: StateFlow<List<ExploreTag>> = hashtagRepository.observeCached()
        .map { cached ->
            val real = cached
                .sortedWith(compareByDescending<com.vivid.app.data.local.entity.HashtagEntity> { it.count }.thenBy { it.tag })
                .map { ExploreTag(it.tag, it.count) }
            val seen = real.mapTo(mutableSetOf()) { it.tag }
            val defaults = ExplorePaging.TAGS.filter { it !in seen }.map { ExploreTag(it, 0) }
            real + defaults
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Descubrir hashtags en background. Sin red (o con error) el cache
        // anterior sigue sirviendo los chips: no es un estado de error.
        viewModelScope.launch {
            runCatching { hashtagRepository.refresh() }
        }
        // Un `#tag` tocado en el feed llega aquí aunque Explorar aún no
        // se haya abierto: el StateFlow replayea el valor pendiente.
        // Complementa `search?tag=` (restoreState a veces ignora el arg nuevo).
        exploreSession.pendingTag
            .filterNotNull()
            .onEach { tag ->
                selectTag(tag)
                exploreSession.consume()
            }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val posts: Flow<PagingData<PostData>> = combine(_selectedTag, blockedUserIds) { tag, blocked ->
        tag to blocked
    }.flatMapLatest { (tag, blocked) ->
        Pager(
            config = PagingConfig(
                pageSize = ExplorePaging.POST_PAGE_SIZE,
                prefetchDistance = ExplorePaging.PREFETCH_DISTANCE,
                enablePlaceholders = false,
                initialLoadSize = ExplorePaging.POST_PAGE_SIZE
            ),
            pagingSourceFactory = {
                ExplorePostsPagingSource(
                    firestore = firestore,
                    tag = tag,
                    blockedUserIds = blocked,
                    onPostsLoaded = { page ->
                        runCatching {
                            postDao.insertPosts(page.map { it.toCachedEntity() })
                        }
                    }
                )
            }
        ).flow
    }.cachedIn(viewModelScope)

    /** Grid offline del tag activo (posts cacheados en Room). */
    private val _cachedPosts = MutableStateFlow<List<PostData>>(emptyList())
    val cachedPosts: StateFlow<List<PostData>> = _cachedPosts.asStateFlow()

    fun selectTag(tag: String) {
        val normalized = Hashtags.normalize(tag)
        if (normalized.isEmpty() || normalized == _selectedTag.value) return
        _selectedTag.value = normalized
    }

    fun setBlockedUserIds(ids: Set<String>) {
        if (ids == blockedUserIds.value) return
        blockedUserIds.value = ids
    }

    /** Carga del cache Room el grid del tag (usado cuando Firestore falla). */
    fun loadCachedPosts(tag: String) {
        viewModelScope.launch {
            _cachedPosts.value = runCatching {
                hashtagRepository.cachedPostsForTag(tag)
            }.getOrDefault(emptyList())
        }
    }
}
