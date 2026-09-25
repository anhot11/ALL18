package com.all18.nativeapp.ui.xfeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.all18.nativeapp.core.model.XPostItem
import com.all18.nativeapp.core.repository.XFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class XFeedUiState {
    object Loading : XFeedUiState()
    data class Success(val posts: List<XPostItem>, val page: Int) : XFeedUiState()
    data class Error(val message: String) : XFeedUiState()
}

class XFeedViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<XFeedUiState>(XFeedUiState.Loading)
    val uiState: StateFlow<XFeedUiState> = _uiState

    private val _selectedTab = MutableStateFlow("Para ti")
    val selectedTab: StateFlow<String> = _selectedTab

    private val currentPosts = mutableListOf<XPostItem>()
    private var currentPage = 1
    private var isLoadingMore = false

    init {
        loadFeed(page = 1)
    }

    fun selectTab(tab: String) {
        val sameTab = _selectedTab.value == tab
        _selectedTab.value = tab
        if (sameTab) {
            com.all18.nativeapp.core.util.FeedFreshnessManager.nextReloadCycle()
        }
        loadFeed(page = 1)
    }

    fun refreshFeed() {
        com.all18.nativeapp.core.util.FeedFreshnessManager.nextReloadCycle()
        loadFeed(page = 1)
    }

    fun loadFeed(page: Int) {
        viewModelScope.launch {
            if (page == 1) {
                _uiState.value = XFeedUiState.Loading
                currentPosts.clear()
            }
            try {
                val posts = XFeedRepository.getFeed(page)
                if (posts.isNotEmpty()) {
                    currentPosts.addAll(posts)
                    currentPage = page
                    _uiState.value = XFeedUiState.Success(currentPosts.toList(), currentPage)
                } else if (currentPosts.isEmpty()) {
                    _uiState.value = XFeedUiState.Error("No se pudieron cargar las publicaciones.")
                }
            } catch (e: Exception) {
                if (currentPosts.isEmpty()) {
                    _uiState.value = XFeedUiState.Error("Error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun loadNextPage() {
        if (isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val nextPage = currentPage + 1
                val posts = XFeedRepository.getFeed(nextPage)
                if (posts.isNotEmpty()) {
                    currentPosts.addAll(posts)
                    currentPage = nextPage
                    _uiState.value = XFeedUiState.Success(currentPosts.toList(), currentPage)
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun toggleLike(postId: String) {
        val index = currentPosts.indexOfFirst { it.id == postId }
        if (index != -1) {
            val old = currentPosts[index]
            val newIsLiked = !old.isLiked
            val newCount = if (newIsLiked) old.likesCount + 1 else (old.likesCount - 1).coerceAtLeast(0)
            val updated = old.copy(isLiked = newIsLiked, likesCount = newCount)
            currentPosts[index] = updated
            _uiState.value = XFeedUiState.Success(currentPosts.toList(), currentPage)
            com.all18.nativeapp.core.repository.UserLibraryRepository.registerPostLike(updated)
        }
    }

    fun toggleRepost(postId: String) {
        val index = currentPosts.indexOfFirst { it.id == postId }
        if (index != -1) {
            val old = currentPosts[index]
            val newIsReposted = !old.isReposted
            val newCount = if (newIsReposted) old.repostsCount + 1 else old.repostsCount - 1
            currentPosts[index] = old.copy(isReposted = newIsReposted, repostsCount = newCount)
            _uiState.value = XFeedUiState.Success(currentPosts.toList(), currentPage)
        }
    }

    fun toggleBookmark(postId: String) {
        val index = currentPosts.indexOfFirst { it.id == postId }
        if (index != -1) {
            val old = currentPosts[index]
            val newIsBookmarked = !old.isBookmarked
            val updated = old.copy(isBookmarked = newIsBookmarked)
            currentPosts[index] = updated
            _uiState.value = XFeedUiState.Success(currentPosts.toList(), currentPage)
            com.all18.nativeapp.core.repository.UserLibraryRepository.registerPostSave(updated)
        }
    }
}
