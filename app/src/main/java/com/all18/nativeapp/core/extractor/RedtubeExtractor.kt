package com.all18.nativeapp.core.extractor

import android.util.Log
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.network.AppNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object RedtubeExtractor : VideoExtractor {
    override val sourceName: String = "RedTube"
    private const val TAG = "RedtubeExtractor"
    private const val BASE_URL = "https://www.redtube.com"
    private const val API_BASE = "https://api.redtube.com/?data=redtube.Videos.searchVideos&output=json"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    private const val COOKIE = "accessAgeDisclaimerPH=1; platform=mobile; hasVisited=1; hasAcceptedCookies=1"

    private val client get() = AppNetworkClient.client
    private val streamCache = ConcurrentHashMap<String, String>()

    override suspend fun getFeed(page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val searchTerm = when (category.lowercase()) {
            "verificados", "verified" -> "verified"
            "populares", "popular", "best" -> "popular"
            "amateur" -> "amateur"
            "hd" -> "hd"
            "latinas" -> "latinas"
            "trending" -> "trending"
            else -> "all"
        }

        val url = "$API_BASE&search=$searchTerm&page=$page&thumbsize=big"
        Log.d(TAG, "Fetching feed from: $url (page $page, category $category)")
        fetchApi(url)
    }

    override suspend fun search(query: String, page: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val encoded = try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (_: Exception) {
            query.trim().replace(" ", "+")
        }
        val url = "$API_BASE&search=$encoded&page=$page&thumbsize=big"
        Log.d(TAG, "Searching RedTube: $url")
        fetchApi(url)
    }

    private fun fetchApi(apiUrl: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", UA)
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()

            val json = JSONObject(body)
            val videos = json.optJSONArray("videos") ?: return emptyList()

            for (i in 0 until videos.length()) {
                val item = videos.getJSONObject(i)
                val v = item.optJSONObject("video") ?: continue
                val vid = v.optString("video_id", "")
                if (vid.isEmpty()) continue

                val title = v.optString("title", "Video RedTube")
                val pageUrl = v.optString("url", "").ifEmpty { "$BASE_URL/$vid" }
                val thumbUrl = v.optString("default_thumb", "")
                val durationText = v.optString("duration", "12:00")
                val durationSeconds = parseDurationSeconds(durationText)

                val viewsCount = v.optLong("views", 0L)
                val views = formatViews(viewsCount)

                val tags = v.optJSONArray("tags")
                val author = if (tags != null && tags.length() > 0) {
                    tags.optString(0).replaceFirstChar { it.uppercase() }
                } else {
                    "RedTube"
                }

                val isHd = title.contains("HD", ignoreCase = true)
                val quality = if (isHd) "HD" else ""

                list.add(
                    VideoItem(
                        id = "redtube_$vid",
                        title = title,
                        thumbUrl = thumbUrl,
                        pageUrl = pageUrl,
                        durationText = durationText,
                        durationSeconds = durationSeconds,
                        author = author,
                        source = "RedTube",
                        quality = quality,
                        views = views
                    )
                )
            }
            Log.d(TAG, "Parsed ${list.size} videos from RedTube API")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching RedTube API: ${e.message}", e)
        }
        return list
    }

    override suspend fun extractStreamUrl(video: VideoItem): String = withContext(Dispatchers.IO) {
        streamCache[video.id]?.let { return@withContext it }

        val cleanId = video.id.removePrefix("redtube_")
        val pageUrl = if (video.pageUrl.startsWith("http")) video.pageUrl else "$BASE_URL/$cleanId"

        try {
            val req = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", UA)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Cookie", COOKIE)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext ""

            // 1. Extraer mediaDefinitions JSON
            val mdPattern = Pattern.compile("\"mediaDefinitions\"\\s*:\\s*(\\[\\{.*?\\}\\])")
            val mdMatcher = mdPattern.matcher(html)
            if (mdMatcher.find()) {
                val jsonRaw = mdMatcher.group(1)?.replace("\\/", "/") ?: ""
                var hlsEndpoint = ""
                var mp4Endpoint = ""
                try {
                    val array = JSONArray(jsonRaw)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val fmt = item.optString("format", "")
                        val videoUrl = item.optString("videoUrl", "")
                        if (fmt.equals("hls", ignoreCase = true) && hlsEndpoint.isEmpty()) {
                            hlsEndpoint = videoUrl
                        } else if (fmt.equals("mp4", ignoreCase = true) && mp4Endpoint.isEmpty()) {
                            mp4Endpoint = videoUrl
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing mediaDefinitions JSON: ${e.message}")
                }

                val chosenEndpoint = hlsEndpoint.ifEmpty { mp4Endpoint }
                if (chosenEndpoint.isNotEmpty()) {
                    val fullMediaUrl = if (chosenEndpoint.startsWith("http")) chosenEndpoint else "$BASE_URL$chosenEndpoint"
                    val mediaReq = Request.Builder()
                        .url(fullMediaUrl)
                        .header("User-Agent", UA)
                        .header("Referer", pageUrl)
                        .header("Cookie", COOKIE)
                        .build()

                    val mediaResp = client.newCall(mediaReq).execute()
                    val mediaBody = mediaResp.body?.string() ?: ""

                    var bestStreamUrl = ""
                    var fallbackStreamUrl = ""
                    try {
                        val mediaArray = JSONArray(mediaBody)
                        for (i in 0 until mediaArray.length()) {
                            val m = mediaArray.getJSONObject(i)
                            val q = m.optString("quality", "")
                            val vu = m.optString("videoUrl", "")
                            if (vu.isNotEmpty()) {
                                if (q == "720" || q == "1080") {
                                    bestStreamUrl = vu
                                } else if (fallbackStreamUrl.isEmpty()) {
                                    fallbackStreamUrl = vu
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing media array: ${e.message}")
                    }

                    val finalUrl = bestStreamUrl.ifEmpty { fallbackStreamUrl }
                    if (finalUrl.isNotEmpty()) {
                        Log.d(TAG, "Extracted direct stream for ${video.id}: $finalUrl")
                        streamCache[video.id] = finalUrl
                        return@withContext finalUrl
                    }
                }
            }

            // 2. Fallback regex directo para master.m3u8 o mp4
            val hlsRegex = Pattern.compile("(https?:\\/\\/[^\\s\"'<>]+master\\.m3u8[^\\s\"'<>]*)")
            val hlsMatcher = hlsRegex.matcher(html)
            if (hlsMatcher.find()) {
                val foundHls = hlsMatcher.group(1)?.replace("\\/", "/") ?: ""
                if (foundHls.isNotEmpty()) {
                    streamCache[video.id] = foundHls
                    return@withContext foundHls
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting stream for ${video.id}: ${e.message}", e)
        }

        return@withContext ""
    }

    private fun parseDurationSeconds(dur: String): Int {
        val parts = dur.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private fun formatViews(views: Long): String {
        return when {
            views >= 1_000_000 -> String.format("%.1fM", views / 1_000_000.0)
            views >= 1_000 -> String.format("%.1fK", views / 1_000.0)
            views > 0 -> "$views"
            else -> "80K"
        }
    }
}
