package com.all18.nativeapp.ui.profile

import androidx.lifecycle.ViewModel
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.repository.UserLibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ProfileTab(val title: String) {
    Liked("Me gusta"),
    Saved("Guardados"),
    History("Historial")
}

class ProfileViewModel : ViewModel() {
    val likedVideos: StateFlow<List<VideoItem>> = UserLibraryRepository.likedVideos
    val savedVideos: StateFlow<List<VideoItem>> = UserLibraryRepository.savedVideos
    val historyVideos: StateFlow<List<VideoItem>> = UserLibraryRepository.historyVideos

    private val _selectedTab = MutableStateFlow(ProfileTab.Liked)
    val selectedTab: StateFlow<ProfileTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: ProfileTab) {
        _selectedTab.value = tab
    }

    fun removeLiked(video: VideoItem) {
        UserLibraryRepository.toggleLikeVideo(video)
    }

    fun removeSaved(video: VideoItem) {
        UserLibraryRepository.toggleSaveVideo(video)
    }

    fun removeHistory(video: VideoItem) {
        UserLibraryRepository.removeHistoryVideo(video)
    }

    fun clearHistory() {
        UserLibraryRepository.clearHistory()
    }

    fun clearSaved() {
        UserLibraryRepository.clearSaved()
    }

    fun clearLiked() {
        UserLibraryRepository.clearLiked()
    }
}
