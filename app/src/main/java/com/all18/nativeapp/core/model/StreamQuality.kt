package com.all18.nativeapp.core.model

data class StreamQuality(
    val label: String, // e.g. "1080p", "720p", "480p", "360p", "240p", "Auto"
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val isHlsTrack: Boolean = false
)
