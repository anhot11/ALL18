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

object PornTrexExtractor : VideoExtractor {
    override val sourceName: String = "PornTrex"
    private const val TAG = "PornTrexExtractor"
    private const val BASE_URL = "https://www.porntrex.com"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val client get() = AppNetworkClient.client
    private val streamCache = ConcurrentHashMap<String, String>()
    private val qualitiesCache = ConcurrentHashMap<String, List<StreamQuality>>()

    override suspend fun getFeed(page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val url = when (category.lowercase()) {
            "populares", "popular", "best" -> if (page <= 1) "$BASE_URL/most-popular/" else "$BASE_URL/most-popular/?page=$page"
            "amateur" -> if (page <= 1) "$BASE_URL/tags/amateur/" else "$BASE_URL/tags/amateur/?page=$page"
            "latinas" -> if (page <= 1) "$BASE_URL/tags/latina/" else "$BASE_URL/tags/latina/?page=$page"
            "hentai" -> if (page <= 1) "$BASE_URL/tags/hentai/" else "$BASE_URL/tags/hentai/?page=$page"
            "cosplay" -> if (page <= 1) "$BASE_URL/tags/cosplay/" else "$BASE_URL/tags/cosplay/?page=$page"
            "asiático", "asiatico" -> if (page <= 1) "$BASE_URL/tags/asian/" else "$BASE_URL/tags/asian/?page=$page"
            "maduras" -> if (page <= 1) "$BASE_URL/tags/mature/" else "$BASE_URL/tags/mature/?page=$page"
            "pov" -> if (page <= 1) "$BASE_URL/tags/pov/" else "$BASE_URL/tags/pov/?page=$page"
            else -> if (page <= 1) "$BASE_URL/latest-updates/" else "$BASE_URL/latest-updates/?page=$page"
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
        val url = if (page <= 1) "$BASE_URL/search/$encoded/" else "$BASE_URL/search/$encoded/?page=$page"
        Log.d(TAG, "Searching PornTrex: $url")
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
            val boxes = doc.select(".porntrex-box, .video-preview-screen, .item")
            val seenIds = mutableSetOf<String>()

            for (box in boxes) {
                val linkEl = box.selectFirst("a[href*=/video/]") ?: continue
                val href = linkEl.attr("abs:href").ifEmpty { linkEl.attr("href") }
                val pageUrl = if (href.startsWith("http")) href else "$BASE_URL$href"

                val idMatcher = Pattern.compile("/video/(\\d+)").matcher(pageUrl)
                val rawId = if (idMatcher.find()) idMatcher.group(1) ?: "" else ""
                if (rawId.isEmpty() || !seenIds.add(rawId)) continue

                val imgEl = box.selectFirst("img")
                var title = imgEl?.attr("alt")?.trim() ?: linkEl.attr("title").trim()
                if (title.isEmpty()) {
                    title = box.selectFirst(".title, h3")?.text()?.trim() ?: "Video PornTrex"
                }

                var thumbUrl = imgEl?.attr("data-src") ?: ""
                if (thumbUrl.isEmpty()) thumbUrl = imgEl?.attr("src") ?: ""
                if (thumbUrl.startsWith("//")) thumbUrl = "https:$thumbUrl"

                val durEl = box.selectFirst(".duration")
                val durationText = durEl?.text()?.trim() ?: "12:00"

                list.add(
                    VideoItem(
                        id = "ptx_$rawId",
                        title = title,
                        thumbUrl = thumbUrl,
                        pageUrl = pageUrl,
                        durationText = durationText,
                        author = "PornTrex",
                        quality = "1080p",
                        source = "PornTrex"
                    )
                )
            }
            Log.d(TAG, "Parsed ${list.size} videos from PornTrex ($url)")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching PornTrex: ${e.message}", e)
        }
        return list
    }

    override suspend fun extractStreamUrl(video: VideoItem): String = withContext(Dispatchers.IO) {
        streamCache[video.id]?.let { return@withContext it }

        try {
            val req = Request.Builder()
                .url(video.pageUrl)
                .header("User-Agent", UA)
                .header("Referer", "$BASE_URL/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext ""

            // 1. Check for 1080p / 720p mp4 stream
            val mp4Matcher = Pattern.compile("(https?://www\\.porntrex\\.com/get_file/[^\"]+\\.mp4/?)").matcher(html)
            var chosenStream = ""
            while (mp4Matcher.find()) {
                val candidate = mp4Matcher.group(1) ?: continue
                if (candidate.contains("1080p") || candidate.contains("720p")) {
                    chosenStream = candidate
                    break
                }
                if (chosenStream.isEmpty()) {
                    chosenStream = candidate
                }
            }

            if (chosenStream.isNotEmpty()) {
                streamCache[video.id] = chosenStream
                return@withContext chosenStream
            }

            // 2. Generic video_url pattern
            val vurlMatcher = Pattern.compile("video_url\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(html)
            if (vurlMatcher.find()) {
                val streamUrl = vurlMatcher.group(1) ?: ""
                if (streamUrl.isNotEmpty()) {
                    streamCache[video.id] = streamUrl
                    return@withContext streamUrl
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting stream for ${video.pageUrl}: ${e.message}", e)
        }
        return@withContext ""
    }

    override suspend fun extractStreamQualities(video: VideoItem): List<StreamQuality> = withContext(Dispatchers.IO) {
        qualitiesCache[video.id]?.let { return@withContext it }

        try {
            val req = Request.Builder()
                .url(video.pageUrl)
                .header("User-Agent", UA)
                .header("Referer", "$BASE_URL/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext emptyList()

            val list = mutableListOf<StreamQuality>()
            val mp4Matcher = Pattern.compile("(https?://www\\.porntrex\\.com/get_file/[^\"]+\\.mp4/?)").matcher(html)
            while (mp4Matcher.find()) {
                val url = mp4Matcher.group(1) ?: continue
                val label = when {
                    url.contains("1080p") -> "1080p FHD"
                    url.contains("720p") -> "720p HD"
                    url.contains("480p") -> "480p"
                    else -> "Auto"
                }
                if (list.none { it.url == url }) {
                    list.add(StreamQuality(label = label, url = url))
                }
            }

            if (list.isNotEmpty()) {
                qualitiesCache[video.id] = list
                return@withContext list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting stream qualities: ${e.message}", e)
        }

        val fallback = extractStreamUrl(video)
        if (fallback.isNotEmpty()) {
            return@withContext listOf(StreamQuality(label = "720p HD", url = fallback))
        }
        emptyList()
    }
}
