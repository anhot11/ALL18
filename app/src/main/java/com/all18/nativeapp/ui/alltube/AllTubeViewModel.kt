package com.all18.nativeapp.ui.alltube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.repository.MultiSourceRepository
import com.all18.nativeapp.core.util.FeedFreshnessManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AllTubeUiState {
    object Loading : AllTubeUiState()
    data class Success(val videos: List<VideoItem>, val page: Int) : AllTubeUiState()
    data class Error(val message: String) : AllTubeUiState()
}

class AllTubeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<AllTubeUiState>(AllTubeUiState.Loading)
    val uiState: StateFlow<AllTubeUiState> = _uiState

    private val _selectedSource = MutableStateFlow("Todas")
    val selectedSource: StateFlow<String> = _selectedSource

    private val _selectedCategory = MutableStateFlow("Todos")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showSourceBadge = MutableStateFlow(true)
    val showSourceBadge: StateFlow<Boolean> = _showSourceBadge

    private val currentVideos = mutableListOf<VideoItem>()
    private var currentPage = 1
    private var isLoadingMore = false

    val sources = MultiSourceRepository.availableSources
    val categories = listOf(
        "Todos", "Populares", "Hentai", "Amateur", "Asiático", "Cosplay",
        "Latinas", "BDSM", "Lésbico", "Cómics", "Maduras", "POV",
        "Negras", "Indio", "Verificados", "HD", "Trending"
    )

    init {
        loadFeed(page = 1)
    }

    fun toggleShowSourceBadge(enabled: Boolean) {
        _showSourceBadge.value = enabled
    }

    fun refreshFeed() {
        MultiSourceRepository.triggerReload()
        loadFeed(page = 1)
    }

    fun selectSource(source: String) {
        val sameSource = _selectedSource.value == source
        _selectedSource.value = source
        if (sameSource) {
            MultiSourceRepository.triggerReload()
        }
        loadFeed(page = 1)
    }

    fun selectCategory(cat: String) {
        val sameCategory = _selectedCategory.value == cat && _searchQuery.value.isEmpty()
        _selectedCategory.value = cat
        _searchQuery.value = ""
        if (sameCategory) {
            MultiSourceRepository.triggerReload()
        }
        loadFeed(page = 1)
    }

    fun search(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            _uiState.value = AllTubeUiState.Loading
            currentVideos.clear()
            currentPage = 1
            try {
                val items = if (query.isBlank()) {
                    MultiSourceRepository.getFeed(_selectedSource.value, 1, _selectedCategory.value)
                } else {
                    MultiSourceRepository.search(_selectedSource.value, query, 1)
                }
                if (items.isNotEmpty()) {
                    currentVideos.addAll(items)
                    _uiState.value = AllTubeUiState.Success(currentVideos.toList(), 1)
                } else {
                    _uiState.value = AllTubeUiState.Error("No se encontraron resultados para '$query'")
                }
            } catch (e: Exception) {
                _uiState.value = AllTubeUiState.Error("Error en la búsqueda: ${e.localizedMessage}")
            }
        }
    }

    fun loadFeed(page: Int) {
        viewModelScope.launch {
            if (page == 1) {
                _uiState.value = AllTubeUiState.Loading
                currentVideos.clear()
            }
            try {
                val items = if (_searchQuery.value.isNotEmpty()) {
                    MultiSourceRepository.search(_selectedSource.value, _searchQuery.value, page)
                } else {
                    MultiSourceRepository.getFeed(_selectedSource.value, page, _selectedCategory.value)
                }

                if (items.isNotEmpty()) {
                    currentVideos.addAll(items)
                    FeedFreshnessManager.markAllSeen(items.map { it.id })
                    currentPage = page
                    _uiState.value = AllTubeUiState.Success(currentVideos.toList(), currentPage)
                } else if (currentVideos.isEmpty()) {
                    _uiState.value = AllTubeUiState.Error("No se pudieron cargar los videos. Verifica la conexión.")
                }
            } catch (e: Exception) {
                if (currentVideos.isEmpty()) {
                    _uiState.value = AllTubeUiState.Error("Error: ${e.localizedMessage ?: "Error de red"}")
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
                val items = if (_searchQuery.value.isNotEmpty()) {
                    MultiSourceRepository.search(_selectedSource.value, _searchQuery.value, nextPage)
                } else {
                    MultiSourceRepository.getFeed(_selectedSource.value, nextPage, _selectedCategory.value)
                }

                if (items.isNotEmpty()) {
                    currentVideos.addAll(items)
                    currentPage = nextPage
                    _uiState.value = AllTubeUiState.Success(currentVideos.toList(), currentPage)
                }
            } finally {
                isLoadingMore = false
            }
        }
    }
}
