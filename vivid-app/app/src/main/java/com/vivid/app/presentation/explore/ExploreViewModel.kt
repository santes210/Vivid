package com.vivid.app.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.paging.ExplorePaging
import com.vivid.app.data.paging.ExplorePostsPagingSource
import com.vivid.app.presentation.feed.PostData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _selectedTag = MutableStateFlow(ExplorePaging.TAGS.first())
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()

    private val blockedUserIds = MutableStateFlow<Set<String>>(emptySet())

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
                    blockedUserIds = blocked
                )
            }
        ).flow
    }.cachedIn(viewModelScope)

    fun selectTag(tag: String) {
        if (tag.isBlank() || tag == _selectedTag.value) return
        _selectedTag.value = tag
    }

    fun setBlockedUserIds(ids: Set<String>) {
        if (ids == blockedUserIds.value) return
        blockedUserIds.value = ids
    }
}
