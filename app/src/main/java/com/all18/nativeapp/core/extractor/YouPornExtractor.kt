package com.all18.nativeapp.core.extractor

import android.util.Log
import com.all18.nativeapp.core.model.StreamQuality
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.network.AppNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object YouPornExtractor : VideoExtractor {
    override val sourceName: String = "YouPorn"
    private const val TAG = "YouPornExtractor"
    private const val BASE_URL = "https://www.youporn.com"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val COOKIE = "age_verified=1; accessAgeDisclaimerPH=1; platform=mobile; hasVisited=1; hasAcceptedCookies=1; yp_cookie_accepted=1"

    private val client get() = AppNetworkClient.client
    private val streamCache = ConcurrentHashMap<String, String>()
    private val qualitiesCache = ConcurrentHashMap<String, List<StreamQuality>>()

    override suspend fun getFeed(page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val url = when (category.lowercase()) {
            "todos", "" -> if (page <= 1) "$BASE_URL/porntags/en-espanol/" else "$BASE_URL/porntags/en-espanol/?page=$page"
            "populares", "popular", "best" -> if (page <= 1) "$BASE_URL/most_viewed/" else "$BASE_URL/most_viewed/?page=$page"
            "verificados", "verified" -> if (page <= 1) "$BASE_URL/top_rated/" else "$BASE_URL/top_rated/?page=$page"
            "amateur" -> if (page <= 1) "$BASE_URL/category/amateur/" else "$BASE_URL/category/amateur/?page=$page"
            "hd" -> if (page <= 1) "$BASE_URL/category/hd/" else "$BASE_URL/category/hd/?page=$page"
            "latinas" -> if (page <= 1) "$BASE_URL/porntags/latina/" else "$BASE_URL/porntags/latina/?page=$page"
            "trending" -> if (page <= 1) "$BASE_URL/porntags/en-espanol/" else "$BASE_URL/porntags/en-espanol/?page=$page"
            else -> "$BASE_URL/search/?query=$category&page=$page"
        }

        Log.d(TAG, "Fetching feed from: $url (page $page, category $category)")
        fetchHtml(url)
    }

    override suspend fun search(query: String, page: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val encoded = try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (_: Exception) {
            query.trim().replace(" ", "+")
        }
        val url = "$BASE_URL/search/?query=$encoded&page=$page"
        Log.d(TAG, "Searching YouPorn: $url")
        fetchHtml(url)
    }

    private fun fetchHtml(url: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Cookie", COOKIE)
                .header("Referer", "$BASE_URL/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return emptyList()

            val doc = Jsoup.parse(html, BASE_URL)
            val articles = doc.select("article.video-box, article.js_video-box")
            val seenIds = mutableSetOf<String>()

            for (el in articles) {
                val rawId = el.attr("data-video-id").trim()
                if (rawId.isEmpty() || !seenIds.add(rawId)) continue

                val linkEl = el.selectFirst("a[href*=/watch/]")
                val href = linkEl?.attr("href") ?: "/watch/$rawId/"
                val pageUrl = if (href.startsWith("http")) href else "$BASE_URL$href"

                var title = el.selectFirst(".video-title-text span")?.text()?.trim() ?: ""
                if (title.isEmpty()) title = el.attr("aria-label").trim()
                if (title.isEmpty()) title = linkEl?.attr("title")?.trim() ?: ""
                if (title.isEmpty()) title = "Video YouPorn"

                val imgEl = el.selectFirst("img")
                var thumbUrl = imgEl?.attr("data-src") ?: ""
                if (thumbUrl.isEmpty() || thumbUrl.startsWith("data:image")) {
                    thumbUrl = imgEl?.attr("data-poster") ?: ""
                }
                if (thumbUrl.isEmpty() || thumbUrl.startsWith("data:image")) {
                    thumbUrl = imgEl?.attr("src") ?: ""
                }
                if (thumbUrl.startsWith("//")) thumbUrl = "https:$thumbUrl"

                val durEl = el.selectFirst(".video-duration span, .tm_video_duration span")
                val durationText = durEl?.text()?.trim() ?: "12:00"
                val durationSec = parseDuration(durationText)

                val viewsEl = el.selectFirst(".info-views, .view-rating-container span")
                var views = viewsEl?.text()?.replace("Views:", "")?.trim() ?: "250K"
                if (views.isEmpty()) views = "250K"

                val uploaderEl = el.selectFirst(".author-title-text, a.channel-performer")
                var author = uploaderEl?.text()?.trim() ?: ""
                if (author.isEmpty()) author = el.attr("data-uploader-name").trim()
                if (author.isEmpty()) author = "YouPorn"

                val isHd = el.selectFirst(".hd, .badge-hd") != null || title.contains("HD", ignoreCase = true)
                val quality = if (isHd) "HD" else ""

                list.add(
                    VideoItem(
                        id = "youporn_$rawId",
                        title = title,
                        thumbUrl = thumbUrl,
                        pageUrl = pageUrl,
                        durationText = durationText,
                        durationSeconds = durationSec,
                        views = views,
                        author = author,
                        quality = quality,
                        source = sourceName
                    )
                )
            }

            Log.d(TAG, "Parsed ${list.size} videos from YouPorn")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching YouPorn: ${e.message}", e)
        }
        return list
    }

    override suspend fun extractStreamUrl(video: VideoItem): String = withContext(Dispatchers.IO) {
        streamCache[video.id]?.let { return@withContext it }

        try {
            val req = Request.Builder()
                .url(video.pageUrl)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Cookie", COOKIE)
                .header("Referer", "$BASE_URL/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext ""

            // Extract mediaDefinitions / mediaDefinition JSON array
            val mDef = Pattern.compile("[\"']?mediaDefinitions?[\"']?\\s*:\\s*(\\[.*?\\])", Pattern.DOTALL).matcher(html)
            val jsonStr = if (mDef.find()) mDef.group(1) else null

            if (!jsonStr.isNullOrEmpty()) {
                val array = JSONArray(jsonStr)
                var mp4Url: String? = null
                var hlsUrl: String? = null

                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val fmt = obj.optString("format", "").lowercase()
                    val vUrl = obj.optString("videoUrl", "")
                    if (fmt == "mp4" && vUrl.isNotEmpty()) mp4Url = vUrl
                    else if (fmt == "hls" && vUrl.isNotEmpty()) hlsUrl = vUrl
                }

                // Prefer MP4 endpoint which reliably delivers 1080p/720p stream definitions
                val targetEndpoint = mp4Url ?: hlsUrl
                if (!targetEndpoint.isNullOrEmpty()) {
                    val epReq = Request.Builder()
                        .url(targetEndpoint)
                        .header("User-Agent", UA)
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Cookie", COOKIE)
                        .header("Referer", video.pageUrl)
                        .build()

                    val epResp = client.newCall(epReq).execute()
                    val epBody = epResp.body?.string()?.trim() ?: ""

                    if (epBody.startsWith("[")) {
                        val qualities = JSONArray(epBody)
                        val qList = mutableListOf<StreamQuality>()

                        for (j in 0 until qualities.length()) {
                            val qObj = qualities.optJSONObject(j) ?: continue
                            val q = qObj.optString("quality", "")
                            val vUrl = qObj.optString("videoUrl", "")
                            if (vUrl.isNotEmpty()) {
                                val label = if (q.endsWith("p", ignoreCase = true)) q else "${q}p"
                                qList.add(StreamQuality(label = label, url = vUrl))
                            }
                        }

                        if (qList.isNotEmpty()) {
                            qList.sortByDescending { it.label.replace("p", "").toIntOrNull() ?: 0 }
                            qualitiesCache[video.id] = qList
                            val bestUrl = qList.first().url
                            streamCache[video.id] = bestUrl
                            return@withContext bestUrl
                        }
                    } else if (epBody.contains(".m3u8")) {
                        val hlsQuality = listOf(StreamQuality(label = "Auto (HLS)", url = targetEndpoint, isHlsTrack = true))
                        qualitiesCache[video.id] = hlsQuality
                        streamCache[video.id] = targetEndpoint
                        return@withContext targetEndpoint
                    }
                }
            }

            // Fallback: Direct regex search for MP4 or HLS
            val pDirectHls = Pattern.compile("(https?://[^\\s\"'<>]+?\\.m3u8[^\\s\"'<>]*)").matcher(html)
            if (pDirectHls.find()) {
                val url = pDirectHls.group(1).replace("&amp;", "&").trim()
                streamCache[video.id] = url
                return@withContext url
            }

            val pDirectMp4 = Pattern.compile("(https?://[^\\s\"'<>]+?\\.mp4[^\\s\"'<>]*)").matcher(html)
            if (pDirectMp4.find()) {
                val url = pDirectMp4.group(1).replace("&amp;", "&").trim()
                streamCache[video.id] = url
                return@withContext url
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting stream for ${video.id}: ${e.message}", e)
        }

        ""
    }

    private fun parseDuration(text: String): Int {
        val clean = text.replace("min", "").replace("m", "").trim()
        val parts = clean.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0] * 60
            else -> 600
        }
    }

    override suspend fun extractStreamQualities(video: VideoItem): List<StreamQuality> = withContext(Dispatchers.IO) {
        qualitiesCache[video.id]?.let { return@withContext it }
        extractStreamUrl(video)
        qualitiesCache[video.id] ?: emptyList()
    }
}
