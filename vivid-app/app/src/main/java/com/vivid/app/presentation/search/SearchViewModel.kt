package com.vivid.app.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.paging.ExplorePaging
import com.vivid.app.data.paging.UserSearchPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val blockedUserIds = MutableStateFlow<Set<String>>(emptySet())

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val users: Flow<PagingData<SearchUser>> = combine(
        _query.debounce(250),
        blockedUserIds
    ) { raw, blocked ->
        ExplorePaging.normalizeQuery(raw) to blocked
    }.flatMapLatest { (normalized, blocked) ->
        if (!ExplorePaging.isValidUserQuery(normalized)) {
            flowOf(PagingData.empty())
        } else {
            Pager(
                config = PagingConfig(
                    pageSize = ExplorePaging.USER_PAGE_SIZE,
                    prefetchDistance = ExplorePaging.PREFETCH_DISTANCE,
                    enablePlaceholders = false,
                    initialLoadSize = ExplorePaging.USER_PAGE_SIZE
                ),
                pagingSourceFactory = {
                    UserSearchPagingSource(
                        firestore = firestore,
                        rawQuery = normalized,
                        currentUserId = auth.currentUser?.uid.orEmpty(),
                        blockedUserIds = blocked
                    )
                }
            ).flow
        }
    }.cachedIn(viewModelScope)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setBlockedUserIds(ids: Set<String>) {
        if (ids == blockedUserIds.value) return
        blockedUserIds.value = ids
    }
}
