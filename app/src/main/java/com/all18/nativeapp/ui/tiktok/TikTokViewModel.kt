package com.all18.nativeapp.ui.tiktok

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.all18.nativeapp.core.extractor.LiveCamExtractor
import com.all18.nativeapp.core.model.TikTokItem
import com.all18.nativeapp.core.model.TikTokLiveItem
import com.all18.nativeapp.core.repository.TikTokRepository
import com.all18.nativeapp.core.util.FeedFreshnessManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class TikTokUiState {
    object Loading : TikTokUiState()
    data class Success(val items: List<TikTokItem>, val page: Int) : TikTokUiState()
    data class Error(val message: String) : TikTokUiState()
}

class TikTokViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<TikTokUiState>(TikTokUiState.Loading)
    val uiState: StateFlow<TikTokUiState> = _uiState

    private val _selectedTab = MutableStateFlow("Para ti")
    val selectedTab: StateFlow<String> = _selectedTab

    // LIVE Mode state
    private val _isLiveMode = MutableStateFlow(false)
    val isLiveMode: StateFlow<Boolean> = _isLiveMode

    private val _liveRooms = MutableStateFlow<List<TikTokLiveItem>>(emptyList())
    val liveRooms: StateFlow<List<TikTokLiveItem>> = _liveRooms

    private val _isLiveLoading = MutableStateFlow(false)
    val isLiveLoading: StateFlow<Boolean> = _isLiveLoading

    var currentLiveIndex: Int = 0
        private set

    val liveStreamUrls = mutableStateMapOf<String, String>()

    fun selectTab(tab: String) {
        val sameTab = _selectedTab.value == tab
        _selectedTab.value = tab
        FeedFreshnessManager.nextReloadCycle()
        loadFeed(page = 1)
    }

    fun refreshFeed() {
        FeedFreshnessManager.nextReloadCycle()
        loadFeed(page = 1)
    }

    var currentVideoIndex: Int = 0
        private set

    private val currentItems = mutableListOf<TikTokItem>()
    private var currentPage = 1
    private var isLoadingMore = false

    // Cache of extracted playable stream URLs (id -> streamUrl) with reactive recomposition
    val streamUrls = mutableStateMapOf<String, String>()

    init {
        loadFeed(page = 1)
    }

    fun loadFeed(page: Int) {
        viewModelScope.launch {
            if (page == 1) {
                _uiState.value = TikTokUiState.Loading
                currentItems.clear()
                currentVideoIndex = 0
            }
            try {
                val items = TikTokRepository.getFeed(page, _selectedTab.value)
                if (items.isNotEmpty()) {
                    currentItems.addAll(items)
                    currentPage = page
                    _uiState.value = TikTokUiState.Success(currentItems.toList(), currentPage)
                    // Pre-resolve stream URL for the first item
                    items.firstOrNull()?.let {
                        TikTokRepository.markAsSeen(it.id)
                        resolveStream(it)
                    }
                } else if (currentItems.isEmpty()) {
                    _uiState.value = TikTokUiState.Error("No se pudieron cargar los videos de TikTok.")
                }
            } catch (e: Exception) {
                if (currentItems.isEmpty()) {
                    _uiState.value = TikTokUiState.Error("Error: ${e.localizedMessage}")
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
                val items = TikTokRepository.getFeed(nextPage, _selectedTab.value)
                if (items.isNotEmpty()) {
                    currentItems.addAll(items)
                    currentPage = nextPage
                    _uiState.value = TikTokUiState.Success(currentItems.toList(), currentPage)
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun onPageSelected(index: Int) {
        currentVideoIndex = index
        val current = currentItems.getOrNull(index) ?: return
        TikTokRepository.markAsSeen(current.id)
        resolveStream(current)
        // Pre-resolve next items for instant playback on swipe
        currentItems.getOrNull(index + 1)?.let { resolveStream(it) }
        currentItems.getOrNull(index + 2)?.let { resolveStream(it) }
    }

    fun resolveStream(item: TikTokItem) {
        if (streamUrls.containsKey(item.id) && !streamUrls[item.id].isNullOrEmpty()) return
        if (item.videoUrl.isNotEmpty()) {
            streamUrls[item.id] = item.videoUrl
            return
        }
        viewModelScope.launch {
            try {
                val stream = TikTokRepository.resolveStreamUrl(item)
                if (stream.isNotEmpty()) {
                    streamUrls[item.id] = stream
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun retryStream(item: TikTokItem) {
        viewModelScope.launch {
            try {
                val stream = TikTokRepository.resolveStreamUrl(item)
                if (stream.isNotEmpty()) {
                    streamUrls[item.id] = stream
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleLike(itemId: String) {
        val index = currentItems.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val old = currentItems[index]
            val newIsLiked = !old.isLiked
            val newCount = if (newIsLiked) old.likesCount + 1 else old.likesCount - 1
            val updated = old.copy(isLiked = newIsLiked, likesCount = newCount)
            currentItems[index] = updated
            _uiState.value = TikTokUiState.Success(currentItems.toList(), currentPage)
            com.all18.nativeapp.core.repository.UserLibraryRepository.registerTikTokLike(updated)
        }
    }

    fun toggleFollow(itemId: String) {
        val index = currentItems.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val old = currentItems[index]
            currentItems[index] = old.copy(isFollowing = !old.isFollowing)
            _uiState.value = TikTokUiState.Success(currentItems.toList(), currentPage)
        }
    }

    fun toggleBookmark(itemId: String) {
        val index = currentItems.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val old = currentItems[index]
            val newIsBookmarked = !old.isBookmarked
            val newCount = if (newIsBookmarked) old.bookmarksCount + 1 else (old.bookmarksCount - 1).coerceAtLeast(0)
            val updated = old.copy(isBookmarked = newIsBookmarked, bookmarksCount = newCount)
            currentItems[index] = updated
            _uiState.value = TikTokUiState.Success(currentItems.toList(), currentPage)
            com.all18.nativeapp.core.repository.UserLibraryRepository.registerTikTokSave(updated)
        }
    }

    fun incrementShare(itemId: String) {
        val index = currentItems.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val old = currentItems[index]
            val updated = old.copy(sharesCount = old.sharesCount + 1)
            currentItems[index] = updated
            _uiState.value = TikTokUiState.Success(currentItems.toList(), currentPage)
        }
    }

    // ==========================================
    // MODO LIVE TIKTOK (Webcams & Transmisiones)
    // ==========================================

    fun toggleLiveMode(enabled: Boolean) {
        _isLiveMode.value = enabled
        if (enabled && _liveRooms.value.isEmpty()) {
            loadLiveRooms()
        }
    }

    fun onLivePageSelected(index: Int) {
        currentLiveIndex = index
        val rooms = _liveRooms.value
        if (index in rooms.indices) {
            resolveLiveStream(rooms[index])
            if (index + 1 in rooms.indices) {
                resolveLiveStream(rooms[index + 1])
            }
        }
    }

    fun loadLiveRooms() {
        viewModelScope.launch {
            _isLiveLoading.value = true
            try {
                val rooms = LiveCamExtractor.getLiveRooms(35)
                _liveRooms.value = rooms
                currentLiveIndex = 0
                rooms.firstOrNull()?.let { resolveLiveStream(it) }
            } catch (e: Exception) {
                Log.e("TikTokViewModel", "Error loading live rooms: ${e.message}")
            } finally {
                _isLiveLoading.value = false
            }
        }
    }

    fun resolveLiveStream(room: TikTokLiveItem) {
        if (liveStreamUrls.containsKey(room.id)) return
        viewModelScope.launch {
            val url = LiveCamExtractor.extractLiveStreamUrl(room.username)
            if (!url.isNullOrBlank()) {
                liveStreamUrls[room.id] = url
            }
        }
    }

    fun toggleFollowLive(username: String) {
        val list = _liveRooms.value.toMutableList()
        val idx = list.indexOfFirst { it.username == username }
        if (idx != -1) {
            val current = list[idx]
            list[idx] = current.copy(isFollowing = !current.isFollowing)
            _liveRooms.value = list
        }
    }

    fun sendLiveGift(roomId: String, giftName: String) {
        val list = _liveRooms.value.toMutableList()
        val idx = list.indexOfFirst { it.id == roomId }
        if (idx != -1) {
            val current = list[idx]
            list[idx] = current.copy(likesCount = current.likesCount + 100)
            _liveRooms.value = list
        }
    }

    fun addLiveHeart(roomId: String) {
        val list = _liveRooms.value.toMutableList()
        val idx = list.indexOfFirst { it.id == roomId }
        if (idx != -1) {
            val current = list[idx]
            list[idx] = current.copy(likesCount = current.likesCount + 1)
            _liveRooms.value = list
        }
    }
}
