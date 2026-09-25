package com.all18.nativeapp.core.extractor

import com.all18.nativeapp.core.model.StreamQuality
import com.all18.nativeapp.core.model.VideoItem

interface VideoExtractor {
    val sourceName: String
    suspend fun getFeed(page: Int, category: String = ""): List<VideoItem>
    suspend fun search(query: String, page: Int): List<VideoItem>
    suspend fun extractStreamUrl(video: VideoItem): String
    suspend fun extractStreamQualities(video: VideoItem): List<StreamQuality> = emptyList()
}
