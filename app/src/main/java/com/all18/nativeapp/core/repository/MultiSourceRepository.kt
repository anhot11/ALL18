package com.all18.nativeapp.core.repository

import android.util.Log
import com.all18.nativeapp.core.extractor.EpornerExtractor
import com.all18.nativeapp.core.extractor.JustPornExtractor
import com.all18.nativeapp.core.extractor.Porn300Extractor
import com.all18.nativeapp.core.extractor.PornComExtractor
import com.all18.nativeapp.core.extractor.PornTrexExtractor
import com.all18.nativeapp.core.extractor.PornhubExtractor
import com.all18.nativeapp.core.extractor.RedtubeExtractor
import com.all18.nativeapp.core.extractor.XHamsterExtractor
import com.all18.nativeapp.core.extractor.XNXXExtractor
import com.all18.nativeapp.core.extractor.XVideosExtractor
import com.all18.nativeapp.core.extractor.YouPornExtractor
import com.all18.nativeapp.core.model.StreamQuality
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.util.FeedFreshnessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections

object MultiSourceRepository {
    private const val TAG = "MultiSourceRepository"

    val availableSources = listOf(
        "Todas",
        "Pornhub",
        "xHamster",
        "XVideos",
        "XNXX",
        "YouPorn",
        "EPorner",
        "RedTube",
        "Porn300",
        "PornTrex",
        "JustPorn",
        "Porn.com"
    )

    fun triggerReload(): Int = FeedFreshnessManager.nextReloadCycle()

    suspend fun getFeed(source: String, page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val nicheCategories = setOf("hentai", "cosplay", "asiático", "asiatico", "bdsm", "lésbico", "lesbico", "cómics", "comics", "maduras", "pov", "negras", "indio", "latinas")
        if (category.lowercase() in nicheCategories) {
            return@withContext search(source, category, page)
        }

        when (source) {
            "Pornhub" -> PornhubExtractor.getFeed(FeedFreshnessManager.getEffectivePage("Pornhub", page), category)
            "xHamster", "xhamster" -> XHamsterExtractor.getFeed(FeedFreshnessManager.getEffectivePage("xHamster", page), category)
            "XVideos" -> XVideosExtractor.getFeed(FeedFreshnessManager.getEffectivePage("XVideos", page), category)
            "XNXX" -> XNXXExtractor.getFeed(FeedFreshnessManager.getEffectivePage("XNXX", page), category)
            "YouPorn", "Youporn" -> YouPornExtractor.getFeed(FeedFreshnessManager.getEffectivePage("YouPorn", page), category)
            "EPorner", "Eporner" -> EpornerExtractor.getFeed(FeedFreshnessManager.getEffectivePage("EPorner", page), category)
            "RedTube", "Redtube" -> RedtubeExtractor.getFeed(FeedFreshnessManager.getEffectivePage("RedTube", page), category)
            "Porn300", "porn300" -> Porn300Extractor.getFeed(FeedFreshnessManager.getEffectivePage("Porn300", page), category)
            "PornTrex", "porntrex" -> PornTrexExtractor.getFeed(FeedFreshnessManager.getEffectivePage("PornTrex", page), category)
            "JustPorn", "Justporn" -> JustPornExtractor.getFeed(FeedFreshnessManager.getEffectivePage("JustPorn", page), category)
            "Porn.com", "Porncom" -> PornComExtractor.getFeed(FeedFreshnessManager.getEffectivePage("Porn.com", page), category)
            else -> {
                fetchMultiSourceFeed(page, category)
            }
        }
    }

    private suspend fun fetchMultiSourceFeed(page: Int, category: String): List<VideoItem> = coroutineScope {
        val sourceExtractors = listOf(
            "Pornhub" to suspend { PornhubExtractor.getFeed(FeedFreshnessManager.getEffectivePage("Pornhub", page), category) },
            "xHamster" to suspend { XHamsterExtractor.getFeed(FeedFreshnessManager.getEffectivePage("xHamster", page), category) },
            "XVideos" to suspend { XVideosExtractor.getFeed(FeedFreshnessManager.getEffectivePage("XVideos", page), category) },
            "XNXX" to suspend { XNXXExtractor.getFeed(FeedFreshnessManager.getEffectivePage("XNXX", page), category) },
            "YouPorn" to suspend { YouPornExtractor.getFeed(FeedFreshnessManager.getEffectivePage("YouPorn", page), category) },
            "EPorner" to suspend { EpornerExtractor.getFeed(FeedFreshnessManager.getEffectivePage("EPorner", page), category) },
            "RedTube" to suspend { RedtubeExtractor.getFeed(FeedFreshnessManager.getEffectivePage("RedTube", page), category) },
            "Porn300" to suspend { Porn300Extractor.getFeed(FeedFreshnessManager.getEffectivePage("Porn300", page), category) },
            "PornTrex" to suspend { PornTrexExtractor.getFeed(FeedFreshnessManager.getEffectivePage("PornTrex", page), category) },
            "JustPorn" to suspend { JustPornExtractor.getFeed(FeedFreshnessManager.getEffectivePage("JustPorn", page), category) },
            "Porn.com" to suspend { PornComExtractor.getFeed(FeedFreshnessManager.getEffectivePage("Porn.com", page), category) }
        )

        val results = Collections.synchronizedList(mutableListOf<List<VideoItem>>())

        val jobs = sourceExtractors.map { (name, fetcher) ->
            launch {
                try {
                    val list = fetcher()
                    if (list.isNotEmpty()) {
                        results.add(list)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Extractor failed for $name: ${e.message}")
                }
            }
        }

        // Fast quorum: wait up to 2600ms or until all finish
        withTimeoutOrNull(2600L) {
            jobs.joinAll()
        }

        FeedFreshnessManager.interweaveDiverse(results.toList())
    }

    suspend fun search(source: String, query: String, page: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        when (source) {
            "Pornhub" -> PornhubExtractor.search(query, page)
            "xHamster", "xhamster" -> XHamsterExtractor.search(query, page)
            "XVideos" -> XVideosExtractor.search(query, page)
            "XNXX" -> XNXXExtractor.search(query, page)
            "YouPorn", "Youporn" -> YouPornExtractor.search(query, page)
            "EPorner", "Eporner" -> EpornerExtractor.search(query, page)
            "RedTube", "Redtube" -> RedtubeExtractor.search(query, page)
            "Porn300", "porn300" -> Porn300Extractor.search(query, page)
            "PornTrex", "porntrex" -> PornTrexExtractor.search(query, page)
            "JustPorn", "Justporn" -> JustPornExtractor.search(query, page)
            "Porn.com", "Porncom" -> PornComExtractor.search(query, page)
            else -> {
                fetchMultiSourceSearch(query, page)
            }
        }
    }

    private suspend fun fetchMultiSourceSearch(query: String, page: Int): List<VideoItem> = coroutineScope {
        val searchExtractors = listOf(
            "Pornhub" to suspend { PornhubExtractor.search(query, page) },
            "xHamster" to suspend { XHamsterExtractor.search(query, page) },
            "XVideos" to suspend { XVideosExtractor.search(query, page) },
            "XNXX" to suspend { XNXXExtractor.search(query, page) },
            "YouPorn" to suspend { YouPornExtractor.search(query, page) },
            "EPorner" to suspend { EpornerExtractor.search(query, page) },
            "RedTube" to suspend { RedtubeExtractor.search(query, page) },
            "Porn300" to suspend { Porn300Extractor.search(query, page) },
            "PornTrex" to suspend { PornTrexExtractor.search(query, page) },
            "JustPorn" to suspend { JustPornExtractor.search(query, page) },
            "Porn.com" to suspend { PornComExtractor.search(query, page) }
        )

        val results = Collections.synchronizedList(mutableListOf<List<VideoItem>>())

        val jobs = searchExtractors.map { (name, fetcher) ->
            launch {
                try {
                    val list = fetcher()
                    if (list.isNotEmpty()) {
                        results.add(list)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Search extractor failed for $name: ${e.message}")
                }
            }
        }

        withTimeoutOrNull(2600L) {
            jobs.joinAll()
        }

        FeedFreshnessManager.interweaveDiverse(results.toList())
    }

    suspend fun extractStreamUrl(video: VideoItem): String = withContext(Dispatchers.IO) {
        when {
            video.source.equals("Pornhub", ignoreCase = true) -> PornhubExtractor.extractStreamUrl(video)
            video.source.equals("xHamster", ignoreCase = true) -> XHamsterExtractor.extractStreamUrl(video)
            video.source.equals("YouPorn", ignoreCase = true) || video.source.equals("Youporn", ignoreCase = true) -> YouPornExtractor.extractStreamUrl(video)
            video.source.equals("EPorner", ignoreCase = true) || video.source.equals("Eporner", ignoreCase = true) -> EpornerExtractor.extractStreamUrl(video)
            video.source.equals("RedTube", ignoreCase = true) || video.source.equals("Redtube", ignoreCase = true) -> RedtubeExtractor.extractStreamUrl(video)
            video.source.equals("Porn300", ignoreCase = true) -> Porn300Extractor.extractStreamUrl(video)
            video.source.equals("PornTrex", ignoreCase = true) -> PornTrexExtractor.extractStreamUrl(video)
            video.source.equals("JustPorn", ignoreCase = true) || video.source.equals("Justporn", ignoreCase = true) -> JustPornExtractor.extractStreamUrl(video)
            video.source.equals("Porn.com", ignoreCase = true) || video.source.equals("Porncom", ignoreCase = true) -> PornComExtractor.extractStreamUrl(video)
            video.source.equals("XNXX", ignoreCase = true) -> XNXXExtractor.extractStreamUrl(video)
            else -> XVideosExtractor.extractStreamUrl(video)
        }
    }

    suspend fun extractStreamQualities(video: VideoItem): List<StreamQuality> = withContext(Dispatchers.IO) {
        when {
            video.source.equals("xHamster", ignoreCase = true) -> XHamsterExtractor.extractStreamQualities(video)
            video.source.equals("Porn300", ignoreCase = true) -> Porn300Extractor.extractStreamQualities(video)
            video.source.equals("PornTrex", ignoreCase = true) -> PornTrexExtractor.extractStreamQualities(video)
            video.source.equals("YouPorn", ignoreCase = true) || video.source.equals("Youporn", ignoreCase = true) -> YouPornExtractor.extractStreamQualities(video)
            video.source.equals("JustPorn", ignoreCase = true) || video.source.equals("Justporn", ignoreCase = true) -> JustPornExtractor.extractStreamQualities(video)
            video.source.equals("EPorner", ignoreCase = true) || video.source.equals("Eporner", ignoreCase = true) -> EpornerExtractor.extractStreamQualities(video)
            video.source.equals("Pornhub", ignoreCase = true) -> PornhubExtractor.extractStreamQualities(video)
            video.source.equals("RedTube", ignoreCase = true) || video.source.equals("Redtube", ignoreCase = true) -> RedtubeExtractor.extractStreamQualities(video)
            video.source.equals("Porn.com", ignoreCase = true) || video.source.equals("Porncom", ignoreCase = true) -> PornComExtractor.extractStreamQualities(video)
            video.source.equals("XNXX", ignoreCase = true) -> XNXXExtractor.extractStreamQualities(video)
            else -> XVideosExtractor.extractStreamQualities(video)
        }
    }
}
