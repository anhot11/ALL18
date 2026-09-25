package com.all18.nativeapp.core.repository

import com.all18.nativeapp.core.model.TikTokItem
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.model.XPostItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio centralizado para el perfil del usuario: Likes, Guardados e Historial.
 */
object UserLibraryRepository {
    private val _likedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val likedVideos: StateFlow<List<VideoItem>> = _likedVideos.asStateFlow()

    private val _savedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val savedVideos: StateFlow<List<VideoItem>> = _savedVideos.asStateFlow()

    private val _historyVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val historyVideos: StateFlow<List<VideoItem>> = _historyVideos.asStateFlow()

    fun setVideoLiked(video: VideoItem, isLiked: Boolean) {
        val current = _likedVideos.value.toMutableList()
        val index = current.indexOfFirst { it.id == video.id }
        if (isLiked) {
            if (index == -1) {
                current.add(0, video.copy(isFavorite = true))
            }
        } else {
            if (index != -1) {
                current.removeAt(index)
            }
        }
        _likedVideos.value = current
    }

    fun setVideoSaved(video: VideoItem, isSaved: Boolean) {
        val current = _savedVideos.value.toMutableList()
        val index = current.indexOfFirst { it.id == video.id }
        if (isSaved) {
            if (index == -1) {
                current.add(0, video)
            }
        } else {
            if (index != -1) {
                current.removeAt(index)
            }
        }
        _savedVideos.value = current
    }

    fun toggleLikeVideo(video: VideoItem) {
        setVideoLiked(video, !isVideoLiked(video.id))
    }

    fun toggleSaveVideo(video: VideoItem) {
        setVideoSaved(video, !isVideoSaved(video.id))
    }

    fun addToHistory(video: VideoItem) {
        val current = _historyVideos.value.toMutableList()
        current.removeAll { it.id == video.id }
        current.add(0, video)
        if (current.size > 80) {
            _historyVideos.value = current.take(80)
        } else {
            _historyVideos.value = current
        }
    }

    fun removeHistoryVideo(video: VideoItem) {
        val current = _historyVideos.value.toMutableList()
        current.removeAll { it.id == video.id }
        _historyVideos.value = current
    }

    fun clearHistory() {
        _historyVideos.value = emptyList()
    }

    fun clearSaved() {
        _savedVideos.value = emptyList()
    }

    fun clearLiked() {
        _likedVideos.value = emptyList()
    }

    fun isVideoLiked(id: String): Boolean = _likedVideos.value.any { it.id == id }
    fun isVideoSaved(id: String): Boolean = _savedVideos.value.any { it.id == id }

    fun registerTikTokLike(item: TikTokItem) {
        val video = VideoItem(
            id = item.id,
            title = if (item.description.isNotEmpty()) item.description else item.title,
            thumbUrl = item.thumbUrl,
            pageUrl = item.pageUrl,
            durationText = "Short",
            author = item.authorName,
            source = "TikTok",
            isFavorite = true
        )
        toggleLikeVideo(video)
    }

    fun registerTikTokSave(item: TikTokItem) {
        val video = VideoItem(
            id = item.id,
            title = if (item.description.isNotEmpty()) item.description else item.title,
            thumbUrl = item.thumbUrl,
            pageUrl = item.pageUrl,
            durationText = "Short",
            author = item.authorName,
            source = "TikTok"
        )
        toggleSaveVideo(video)
    }

    fun registerPostLike(post: XPostItem) {
        val photoThumb = post.images.firstOrNull() ?: post.authorAvatarUrl
        val postUrl = post.videoItem?.pageUrl?.takeIf { it.isNotBlank() }
            ?: "https://x.com/${post.authorHandle.removePrefix("@")}/status/${post.id}"
        val video = post.videoItem ?: VideoItem(
            id = post.id,
            title = post.content.take(80).ifBlank { "Foto de ${post.authorName}" },
            thumbUrl = photoThumb,
            pageUrl = postUrl,
            durationText = if (post.images.isNotEmpty()) "𝕏 Foto" else "𝕏 Post",
            author = post.authorName,
            source = "𝕏",
            isFavorite = post.isLiked
        )
        setVideoLiked(video, post.isLiked)
    }

    fun registerPostSave(post: XPostItem) {
        val photoThumb = post.images.firstOrNull() ?: post.authorAvatarUrl
        val postUrl = post.videoItem?.pageUrl?.takeIf { it.isNotBlank() }
            ?: "https://x.com/${post.authorHandle.removePrefix("@")}/status/${post.id}"
        val video = post.videoItem ?: VideoItem(
            id = post.id,
            title = post.content.take(80).ifBlank { "Foto de ${post.authorName}" },
            thumbUrl = photoThumb,
            pageUrl = postUrl,
            durationText = if (post.images.isNotEmpty()) "𝕏 Foto" else "𝕏 Post",
            author = post.authorName,
            source = "𝕏"
        )
        setVideoSaved(video, post.isBookmarked)
    }
}
