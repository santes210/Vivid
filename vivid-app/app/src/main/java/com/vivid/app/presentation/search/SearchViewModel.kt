package com.vivid.app.presentation.search

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.app.domain.repository.SearchRepository
import com.vivid.app.presentation.feed.PostData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado del buscador inteligente: las tres secciones ya unificadas,
 * ordenadas por relevancia y acotadas (ver [SmartSearch]).
 */
@Immutable
data class SmartSearchUiState(
    val query: String = "",
    val tab: SearchTab = SearchTab.ALL,
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val users: List<SearchUser> = emptyList(),
    val tags: List<SearchTagResult> = emptyList(),
    val posts: List<PostData> = emptyList(),
    val error: Boolean = false
)

/**
 * ViewModel del buscador inteligente.
 *
 * El texto se debouncea, se lanza una búsqueda combinada (personas + tags +
 * posts en paralelo) y el resultado se expone por secciones. El historial
 * sigue viviendo en SharedPreferences vía [SearchHistory] (puro, testeado).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _history = MutableStateFlow(SearchHistory.decode(prefs.getString(KEY_HISTORY, null)))
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _tab = MutableStateFlow(SearchTab.ALL)
    val tab: StateFlow<SearchTab> = _tab.asStateFlow()

    private val blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    private val retryTick = MutableStateFlow(0)

    init {
        // Catálogo de hashtags fresco para la sección "Temas". Sin red el
        // cache anterior sigue sirviendo: no es un estado de error.
        viewModelScope.launch { searchRepository.refreshTagCatalog() }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val results: Flow<SmartSearchUiState> = combine(
        _query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
        blockedUserIds,
        retryTick
    ) { query, blocked, retry -> Triple(query, blocked, retry) }
        .flatMapLatest { (raw, blocked, _) ->
            if (!SmartSearch.isSearchable(raw)) {
                flowOf(SmartSearchUiState(query = raw))
            } else {
                flow {
                    emit(SmartSearchUiState(query = raw, isSearching = true, isLoading = true))
                    // Las tres secciones se cargan en paralelo y una fuente
                    // caída no tira las demás.
                    val (usersResult, tagsResult, postsResult) = coroutineScope {
                        val users = async { runCatching { searchRepository.searchUsers(raw, blocked) } }
                        val tags = async { runCatching { searchRepository.searchTags(raw) } }
                        val posts = async { runCatching { searchRepository.searchPosts(raw, blocked) } }
                        Triple(users.await(), tags.await(), posts.await())
                    }
                    emit(
                        SmartSearchUiState(
                            query = raw,
                            isSearching = true,
                            users = usersResult.getOrDefault(emptyList()),
                            tags = tagsResult.getOrDefault(emptyList()),
                            posts = postsResult.getOrDefault(emptyList()),
                            error = usersResult.isFailure &&
                                tagsResult.isFailure &&
                                postsResult.isFailure
                        )
                    )
                }
            }
        }

    val uiState: StateFlow<SmartSearchUiState> = combine(results, _tab) { state, tab ->
        state.copy(tab = tab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartSearchUiState())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setTab(tab: SearchTab) {
        _tab.value = tab
    }

    fun setBlockedUserIds(ids: Set<String>) {
        if (ids == blockedUserIds.value) return
        blockedUserIds.value = ids
    }

    /** Reintenta la última búsqueda (tras un error de red). */
    fun retry() {
        retryTick.value = retryTick.value + 1
    }

    fun recordSearch(raw: String) {
        persist(SearchHistory.record(_history.value, raw))
    }

    fun removeHistory(raw: String) {
        persist(SearchHistory.remove(_history.value, raw))
    }

    fun clearHistory() {
        persist(emptyList())
    }

    private fun persist(next: List<String>) {
        if (next == _history.value) return
        _history.value = next
        prefs.edit().putString(KEY_HISTORY, SearchHistory.encode(next)).apply()
    }

    private companion object {
        const val PREFS_NAME = "vivid_search"
        const val KEY_HISTORY = "history"
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
