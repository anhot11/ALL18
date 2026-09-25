package com.all18.nativeapp.ui.directory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.all18.nativeapp.core.model.AdultCategory
import com.all18.nativeapp.core.model.AdultSite
import com.all18.nativeapp.core.model.DirectoryGroup
import com.all18.nativeapp.core.repository.DirectoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class DirectoryUiState {
    object Loading : DirectoryUiState()
    data class Success(
        val categories: List<AdultCategory>,
        val totalCategories: Int,
        val totalSites: Int,
        val searchResults: List<AdultSite> = emptyList(),
        val selectedCategory: AdultCategory? = null
    ) : DirectoryUiState()
}

class DirectoryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DirectoryUiState>(DirectoryUiState.Loading)
    val uiState: StateFlow<DirectoryUiState> = _uiState

    private val _selectedGroup = MutableStateFlow(DirectoryGroup.ALL)
    val selectedGroup: StateFlow<DirectoryGroup> = _selectedGroup

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _activeCategory = MutableStateFlow<AdultCategory?>(null)
    val activeCategory: StateFlow<AdultCategory?> = _activeCategory

    fun init(context: Context) {
        viewModelScope.launch {
            _uiState.value = DirectoryUiState.Loading
            DirectoryRepository.ensureInitialized(context)
            refreshState()
        }
    }

    fun selectGroup(group: DirectoryGroup) {
        _selectedGroup.value = group
        _activeCategory.value = null
        refreshState()
    }

    fun selectCategory(category: AdultCategory?) {
        _activeCategory.value = category
        refreshState()
    }

    fun search(query: String) {
        _searchQuery.value = query
        refreshState()
    }

    private fun refreshState() {
        val q = _searchQuery.value.trim()
        val group = _selectedGroup.value
        val counts = DirectoryRepository.getCounts()

        val searchResults = if (q.isNotEmpty()) {
            DirectoryRepository.search(q, DirectoryGroup.ALL)
        } else {
            emptyList()
        }

        val categories = DirectoryRepository.getCategoriesByGroup(group)

        _uiState.value = DirectoryUiState.Success(
            categories = categories,
            totalCategories = counts.first,
            totalSites = counts.second,
            searchResults = searchResults,
            selectedCategory = _activeCategory.value
        )
    }
}
