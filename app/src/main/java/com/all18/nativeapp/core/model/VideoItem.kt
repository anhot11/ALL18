package com.all18.nativeapp.core.model

data class VideoItem(
    val id: String,
    val title: String,
    val thumbUrl: String,
    val pageUrl: String,
    val durationText: String = "",
    val durationSeconds: Int = 0,
    val author: String = "All18",
    val source: String = "XVideos",
    val quality: String = "HD",
    val views: String = "",
    val isFavorite: Boolean = false
)
