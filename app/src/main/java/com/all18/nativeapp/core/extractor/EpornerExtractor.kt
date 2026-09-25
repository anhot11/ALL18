package com.all18.nativeapp.core.extractor

import android.util.Log
import com.all18.nativeapp.core.model.StreamQuality
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.network.AppNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object EpornerExtractor : VideoExtractor {
    override val sourceName: String = "EPorner"
    private const val TAG = "EpornerExtractor"
    private const val BASE_URL = "https://www.eporner.com"

    private val client get() = AppNetworkClient.client
    private val streamCache = ConcurrentHashMap<String, String>()
    private val qualitiesCache = ConcurrentHashMap<String, List<StreamQuality>>()

    override suspend fun getFeed(page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val (query, order) = when (category.lowercase()) {
            "verificados", "verified" -> "verified" to "top-weekly"
            "populares", "popular", "best" -> "all" to "top-weekly"
            "amateur" -> "amateur" to "top-weekly"
            "hd" -> "hd" to "top-weekly"
            "latinas" -> "latinas" to "top-weekly"
            "trending" -> "trending" to "top-weekly"
            else -> "all" to "latest"
        }

        val url = "$BASE_URL/api/v2/video/search/?query=$query&per_page=30&page=$page&thumbsize=big&order=$order&format=json"
        Log.d(TAG, "Fetching feed from: $url (page $page, category $category)")
        fetchApi(url)
    }

    override suspend fun search(query: String, page: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val encoded = try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (_: Exception) {
            query.trim().replace(" ", "+")
        }
        val url = "$BASE_URL/api/v2/video/search/?query=$encoded&per_page=30&page=$page&thumbsize=big&order=top-weekly&format=json"
        Log.d(TAG, "Searching Eporner: $url")
        fetchApi(url)
    }

    private fun fetchApi(apiUrl: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()

            val jsonStart = body.indexOf('{')
            val jsonEnd = body.lastIndexOf('}') + 1
            if (jsonStart < 0 || jsonEnd <= jsonStart) return emptyList()

            val cleanJson = body.substring(jsonStart, jsonEnd)
            val json = JSONObject(cleanJson)
            val videos = json.optJSONArray("videos") ?: return emptyList()

            for (i in 0 until videos.length()) {
                val v = videos.getJSONObject(i)
                val id = v.optString("id", "")
                if (id.isEmpty()) continue

                val title = v.optString("title", "Video Eporner")
                val pageUrl = v.optString("url", "").ifEmpty { "$BASE_URL/video-$id/" }
                val defaultThumb = v.optJSONObject("default_thumb")
                val thumbUrl = defaultThumb?.optString("src", "") ?: ""

                val durationText = v.optString("length_min", "10:00")
                val durationSeconds = v.optInt("length_sec", 600)
                val viewsCount = v.optLong("views", 0L)
                val views = formatViews(viewsCount)

                val keywords = v.optString("keywords", "")
                val author = keywords.split(",")
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && it.length in 3..25 }
                    ?.replaceFirstChar { it.uppercase() } ?: "EPorner"

                val isHd = title.contains("HD", ignoreCase = true) || keywords.contains("HD", ignoreCase = true)
                val quality = if (isHd) "HD" else ""

                list.add(
                    VideoItem(
                        id = "eporner_$id",
                        title = title,
                        thumbUrl = thumbUrl,
                        pageUrl = pageUrl,
                        durationText = durationText,
                        durationSeconds = durationSeconds,
                        author = author,
                        source = "EPorner",
                        quality = quality,
                        views = views
                    )
                )
            }
            Log.d(TAG, "Parsed ${list.size} videos from Eporner API")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Eporner API: ${e.message}", e)
        }
        return list
    }

    override suspend fun extractStreamUrl(video: VideoItem): String = withContext(Dispatchers.IO) {
        streamCache[video.id]?.let { return@withContext it }

        val cleanId = video.id.removePrefix("eporner_")
        val targetPage = if (video.pageUrl.startsWith("http")) video.pageUrl else "$BASE_URL/video-$cleanId/"

        try {
            val req = Request.Builder()
                .url(targetPage)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext ""

            // Parse all /dload/ links
            val dloadPattern = Pattern.compile("href=\"(/dload/[^\"]+)\"")
            val matcher = dloadPattern.matcher(html)
            val dloadLinks = mutableListOf<String>()
            while (matcher.find()) {
                matcher.group(1)?.let { dloadLinks.add(it) }
            }

            // Order candidates by quality: 720p > 1080p > 480p > 360p > 240p
            val preferredOrder = listOf("720p.mp4", "1080p.mp4", "480p.mp4", "360p.mp4", "240p.mp4", "720", "1080", "480", "360")
            val candidateLinks = mutableListOf<String>()
            for (tag in preferredOrder) {
                for (link in dloadLinks) {
                    if (link.contains(tag) && !candidateLinks.contains(link)) {
                        candidateLinks.add(link)
                    }
                }
            }
            for (link in dloadLinks) {
                if (!candidateLinks.contains(link)) candidateLinks.add(link)
            }

            // Resolve direct CDN location header via HEAD request (without following redirect)
            val noRedirectClient = client.newBuilder().followRedirects(false).build()
            val qList = mutableListOf<StreamQuality>()
            for (candidate in candidateLinks) {
                val candidateUrl = if (candidate.startsWith("http")) candidate else "$BASE_URL$candidate"
                try {
                    val headReq = Request.Builder()
                        .url(candidateUrl)
                        .head()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile)")
                        .build()

                    val headResp = noRedirectClient.newCall(headReq).execute()
                    val location = headResp.header("Location") ?: ""
                    if (location.isNotEmpty() && !location.startsWith("/login") && location.contains(".mp4")) {
                        Log.d(TAG, "Resolved direct CDN stream for ${video.id}: $location")
                        val label = when {
                            candidate.contains("1080") -> "1080p"
                            candidate.contains("720") -> "720p"
                            candidate.contains("480") -> "480p"
                            candidate.contains("360") -> "360p"
                            candidate.contains("240") -> "240p"
                            else -> "HD"
                        }
                        if (qList.none { it.label == label }) {
                            qList.add(StreamQuality(label = label, url = location))
                        }
                    }
                } catch (_: Exception) {}
            }

            if (qList.isNotEmpty()) {
                qualitiesCache[video.id] = qList
                val best = qList.first().url
                streamCache[video.id] = best
                return@withContext best
            }

            // Fallback: check embed page for mp4 or gvideo
            val embedUrl = "$BASE_URL/embed/$cleanId/"
            val embedReq = Request.Builder().url(embedUrl).build()
            val embedResp = client.newCall(embedReq).execute()
            val embedHtml = embedResp.body?.string() ?: ""

            val mp4Pattern = Pattern.compile("(https?://[^\\s\"'<>]+gvideo[^\\s\"'<>]*\\.mp4)")
            val mp4Matcher = mp4Pattern.matcher(embedHtml)
            if (mp4Matcher.find()) {
                val gvideoUrl = mp4Matcher.group(1) ?: ""
                if (gvideoUrl.isNotEmpty()) {
                    streamCache[video.id] = gvideoUrl
                    return@withContext gvideoUrl
                }
            }

            // Fallback: first dload link
            val firstDload = candidateLinks.firstOrNull()
            if (!firstDload.isNullOrEmpty()) {
                val fullUrl = if (firstDload.startsWith("http")) firstDload else "$BASE_URL$firstDload"
                streamCache[video.id] = fullUrl
                return@withContext fullUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting stream for ${video.id}: ${e.message}", e)
        }

        return@withContext ""
    }

    private fun formatViews(views: Long): String {
        return when {
            views >= 1_000_000 -> String.format("%.1fM", views / 1_000_000.0)
            views >= 1_000 -> String.format("%.1fK", views / 1_000.0)
            views > 0 -> "$views"
            else -> "95K"
        }
    }

    override suspend fun extractStreamQualities(video: VideoItem): List<StreamQuality> = withContext(Dispatchers.IO) {
        qualitiesCache[video.id]?.let { return@withContext it }
        extractStreamUrl(video)
        qualitiesCache[video.id] ?: emptyList()
    }
}
