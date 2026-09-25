package com.all18.nativeapp.core.extractor

import android.util.Log
import com.all18.nativeapp.core.model.StreamQuality
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.network.AppNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object JustPornExtractor : VideoExtractor {
    override val sourceName: String = "JustPorn"
    private const val TAG = "JustPornExtractor"
    private const val BASE_URL = "https://www.justporn.com"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val client get() = AppNetworkClient.client
    private val streamCache = ConcurrentHashMap<String, String>()
    private val qualitiesCache = ConcurrentHashMap<String, List<StreamQuality>>()

    override suspend fun getFeed(page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val url = when (category.lowercase()) {
            "verificados", "verified" -> if (page <= 1) "$BASE_URL/top-rated/" else "$BASE_URL/top-rated/$page/"
            "populares", "popular", "best" -> if (page <= 1) "$BASE_URL/most-popular/" else "$BASE_URL/most-popular/$page/"
            "amateur" -> if (page <= 1) "$BASE_URL/search/amateur/" else "$BASE_URL/search/amateur/$page/"
            "hd" -> if (page <= 1) "$BASE_URL/hd-videos/" else "$BASE_URL/hd-videos/$page/"
            "latinas" -> if (page <= 1) "$BASE_URL/search/latinas/" else "$BASE_URL/search/latinas/$page/"
            "trending" -> if (page <= 1) "$BASE_URL/" else "$BASE_URL/latest-updates/$page/"
            else -> if (page <= 1) "$BASE_URL/latest-updates/" else "$BASE_URL/latest-updates/$page/"
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
        val url = if (page <= 1) "$BASE_URL/search/$encoded/" else "$BASE_URL/search/$encoded/$page/"
        Log.d(TAG, "Searching JustPorn: $url")
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
                .header("Referer", "$BASE_URL/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return emptyList()

            val doc = Jsoup.parse(html, BASE_URL)
            val itemElements = doc.select("div.item, div.thumb_rel")
            val seenIds = mutableSetOf<String>()

            for (el in itemElements) {
                val linkEl = el.selectFirst("a[href*=/video/]") ?: continue
                val href = linkEl.attr("abs:href").ifEmpty { linkEl.attr("href") }
                val pageUrl = if (href.startsWith("http")) href else "$BASE_URL$href"

                val idMatch = Pattern.compile("/video/(\\d+)").matcher(pageUrl)
                val rawId = if (idMatch.find()) idMatch.group(1) else ""
                if (rawId.isEmpty() || !seenIds.add(rawId)) continue

                var title = linkEl.attr("title").trim()
                if (title.isEmpty()) {
                    val titleEl = el.selectFirst(".title, strong.title, a.title")
                    title = titleEl?.text()?.trim() ?: "Video JustPorn"
                }

                val imgEl = el.selectFirst("img")
                var thumbUrl = imgEl?.attr("data-original") ?: ""
                if (thumbUrl.isEmpty()) thumbUrl = imgEl?.attr("data-src") ?: ""
                if (thumbUrl.isEmpty()) thumbUrl = imgEl?.attr("src") ?: ""
                if (thumbUrl.startsWith("//")) thumbUrl = "https:$thumbUrl"

                val durEl = el.selectFirst(".duration, .badge-duration, span.dur")
                val durationText = durEl?.text()?.trim() ?: "15:00"
                val durationSec = parseDuration(durationText)

                val viewsEl = el.selectFirst(".views, .item-views")
                val views = viewsEl?.text()?.trim() ?: "120K"

                val isHd = el.selectFirst(".hd, .is-hd, .badge-hd") != null || title.contains("HD", ignoreCase = true)
                val quality = if (isHd) "HD" else ""

                list.add(
                    VideoItem(
                        id = "justporn_$rawId",
                        title = title,
                        thumbUrl = thumbUrl,
                        pageUrl = pageUrl,
                        durationText = durationText,
                        durationSeconds = durationSec,
                        views = views,
                        author = "JustPorn",
                        quality = quality,
                        source = sourceName
                    )
                )
            }

            Log.d(TAG, "Parsed ${list.size} videos from JustPorn")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching JustPorn: ${e.message}", e)
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
                .header("Referer", "$BASE_URL/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext ""

            val qList = mutableListOf<StreamQuality>()

            // 1. Signed MP4 in flashvars (1080p, 720p, default)
            val p1080 = Pattern.compile("video_alt_url2\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(html)
            if (p1080.find()) {
                val url = p1080.group(1).trim()
                if (url.isNotEmpty()) qList.add(StreamQuality("1080p", url))
            }

            val p720 = Pattern.compile("video_alt_url\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(html)
            if (p720.find()) {
                val url = p720.group(1).trim()
                if (url.isNotEmpty()) qList.add(StreamQuality("720p", url))
            }

            val pDef = Pattern.compile("video_url\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(html)
            if (pDef.find()) {
                val url = pDef.group(1).trim()
                if (url.isNotEmpty()) qList.add(StreamQuality("480p", url))
            }

            if (qList.isNotEmpty()) {
                qualitiesCache[video.id] = qList
                val best = qList.first().url
                streamCache[video.id] = best
                return@withContext best
            }

            // 2. Generic signed get_file regex
            val pGetFile = Pattern.compile("(https?://[^\\s\"'<>]+/get_file/[^\\s\"'<>]+(\\.[a-zA-Z0-9]+)?[^\\s\"'<>]*)").matcher(html)
            if (pGetFile.find()) {
                val url = pGetFile.group(1).trim()
                streamCache[video.id] = url
                return@withContext url
            }

            // 3. Fallback HLS or standard mp4
            val pHls = Pattern.compile("(https?://[^\\s\"'<>]+?\\.m3u8[^\\s\"'<>]*)").matcher(html)
            if (pHls.find()) {
                val url = pHls.group(1).trim()
                streamCache[video.id] = url
                return@withContext url
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting stream for ${video.id}: ${e.message}", e)
        }

        ""
    }

    private fun parseDuration(text: String): Int {
        val parts = text.split(":").mapNotNull { it.trim().toIntOrNull() }
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
