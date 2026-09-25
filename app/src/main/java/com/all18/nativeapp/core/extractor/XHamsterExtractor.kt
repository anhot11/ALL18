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

object XHamsterExtractor : VideoExtractor {
    override val sourceName: String = "xHamster"
    private const val TAG = "XHamsterExtractor"
    private const val BASE_URL = "https://xhamster.com"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val client get() = AppNetworkClient.client
    private val streamCache = ConcurrentHashMap<String, String>()
    private val qualitiesCache = ConcurrentHashMap<String, List<StreamQuality>>()

    override suspend fun getFeed(page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val url = when (category.lowercase()) {
            "populares", "popular", "best" -> if (page <= 1) "$BASE_URL/best/weekly" else "$BASE_URL/best/weekly/$page"
            "amateur" -> if (page <= 1) "$BASE_URL/categories/amateur" else "$BASE_URL/categories/amateur/$page"
            "hd" -> if (page <= 1) "$BASE_URL/categories/hd" else "$BASE_URL/categories/hd/$page"
            "latinas" -> if (page <= 1) "$BASE_URL/categories/latina" else "$BASE_URL/categories/latina/$page"
            "hentai" -> if (page <= 1) "$BASE_URL/categories/hentai" else "$BASE_URL/categories/hentai/$page"
            "cosplay" -> if (page <= 1) "$BASE_URL/search/cosplay?page=$page" else "$BASE_URL/search/cosplay?page=$page"
            "asiático", "asiatico" -> if (page <= 1) "$BASE_URL/categories/asian" else "$BASE_URL/categories/asian/$page"
            "maduras" -> if (page <= 1) "$BASE_URL/categories/mature" else "$BASE_URL/categories/mature/$page"
            "pov" -> if (page <= 1) "$BASE_URL/categories/pov" else "$BASE_URL/categories/pov/$page"
            else -> if (page <= 1) "$BASE_URL/new" else "$BASE_URL/new/$page"
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
        val url = if (page <= 1) "$BASE_URL/search/$encoded" else "$BASE_URL/search/$encoded?page=$page"
        Log.d(TAG, "Searching xHamster: $url")
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
            val thumbLinks = doc.select("a[data-role=thumb-link], a[href*=/videos/]")
            val seenIds = mutableSetOf<String>()

            for (linkEl in thumbLinks) {
                val href = linkEl.attr("abs:href").ifEmpty { linkEl.attr("href") }
                if (!href.contains("/videos/")) continue
                val pageUrl = if (href.startsWith("http")) href else "$BASE_URL$href"

                val idMatcher = Pattern.compile("/videos/[^/]+-([a-zA-Z0-9]+)$").matcher(pageUrl)
                var rawId = if (idMatcher.find()) idMatcher.group(1) ?: "" else ""
                if (rawId.isEmpty()) {
                    val altMatcher = Pattern.compile("/videos/([^/?#]+)").matcher(pageUrl)
                    rawId = if (altMatcher.find()) altMatcher.group(1) ?: "" else ""
                }
                if (rawId.isEmpty() || !seenIds.add(rawId)) continue

                var title = linkEl.attr("aria-label").trim()
                if (title.isEmpty()) title = linkEl.attr("title").trim()
                if (title.isEmpty()) {
                    val imgEl = linkEl.selectFirst("img")
                    title = imgEl?.attr("alt")?.trim() ?: "Video xHamster"
                }

                val imgEl = linkEl.selectFirst("img")
                var thumbUrl = imgEl?.attr("src") ?: ""
                if (thumbUrl.isEmpty()) {
                    val srcset = imgEl?.attr("srcset") ?: ""
                    if (srcset.isNotEmpty()) {
                        thumbUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull() ?: ""
                    }
                }
                if (thumbUrl.startsWith("//")) thumbUrl = "https:$thumbUrl"

                val durEl = linkEl.selectFirst("div[data-role=video-duration], .thumb-image-container__duration, span.duration")
                val durationText = durEl?.text()?.trim() ?: "10:00"

                list.add(
                    VideoItem(
                        id = "xh_$rawId",
                        title = title,
                        thumbUrl = thumbUrl,
                        pageUrl = pageUrl,
                        durationText = durationText,
                        author = "xHamster",
                        quality = "HD",
                        source = "xHamster"
                    )
                )
            }
            Log.d(TAG, "Parsed ${list.size} videos from xHamster ($url)")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching xHamster: ${e.message}", e)
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

            // 1. Check for HLS .m3u8 stream
            val hlsMatcher = Pattern.compile("[\"'](https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*)[\"']").matcher(html)
            if (hlsMatcher.find()) {
                val streamUrl = hlsMatcher.group(1)?.replace("\\/", "/") ?: ""
                if (streamUrl.isNotEmpty()) {
                    streamCache[video.id] = streamUrl
                    return@withContext streamUrl
                }
            }

            // 2. Check for MP4 stream
            val mp4Matcher = Pattern.compile("[\"'](https?://[^\"'\\s]+\\.mp4[^\"'\\s]*)[\"']").matcher(html)
            while (mp4Matcher.find()) {
                val streamUrl = mp4Matcher.group(1)?.replace("\\/", "/") ?: ""
                if (streamUrl.isNotEmpty() && !streamUrl.contains(".t.mp4") && !streamUrl.contains("preview")) {
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
        val stream = extractStreamUrl(video)
        if (stream.isNotEmpty()) {
            val isHls = stream.contains(".m3u8")
            val q = listOf(
                StreamQuality(label = if (isHls) "Auto (HLS)" else "720p HD", url = stream, isHlsTrack = isHls),
                StreamQuality(label = "Direct", url = stream)
            )
            qualitiesCache[video.id] = q
            return@withContext q
        }
        emptyList()
    }
}
