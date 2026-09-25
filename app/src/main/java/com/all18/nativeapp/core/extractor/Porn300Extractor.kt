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

object Porn300Extractor : VideoExtractor {
    override val sourceName: String = "Porn300"
    private const val TAG = "Porn300Extractor"
    private const val BASE_URL = "https://www.porn300.com"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val client get() = AppNetworkClient.client
    private val streamCache = ConcurrentHashMap<String, String>()
    private val qualitiesCache = ConcurrentHashMap<String, List<StreamQuality>>()

    override suspend fun getFeed(page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val url = when (category.lowercase()) {
            "populares", "popular", "best" -> if (page <= 1) "$BASE_URL/es/mejores-videos/" else "$BASE_URL/es/mejores-videos/?page=$page"
            "hd" -> if (page <= 1) "$BASE_URL/es/hd/" else "$BASE_URL/es/hd/?page=$page"
            "amateur" -> if (page <= 1) "$BASE_URL/es/buscar/amateur/" else "$BASE_URL/es/buscar/amateur/?page=$page"
            "latinas" -> if (page <= 1) "$BASE_URL/es/buscar/latinas/" else "$BASE_URL/es/buscar/latinas/?page=$page"
            "hentai" -> if (page <= 1) "$BASE_URL/es/buscar/hentai/" else "$BASE_URL/es/buscar/hentai/?page=$page"
            "asiático", "asiatico" -> if (page <= 1) "$BASE_URL/es/buscar/asiaticas/" else "$BASE_URL/es/buscar/asiaticas/?page=$page"
            "cosplay" -> if (page <= 1) "$BASE_URL/es/buscar/cosplay/" else "$BASE_URL/es/buscar/cosplay/?page=$page"
            "maduras" -> if (page <= 1) "$BASE_URL/es/buscar/maduras/" else "$BASE_URL/es/buscar/maduras/?page=$page"
            "pov" -> if (page <= 1) "$BASE_URL/es/buscar/pov/" else "$BASE_URL/es/buscar/pov/?page=$page"
            else -> if (page <= 1) "$BASE_URL/es/" else "$BASE_URL/es/?page=$page"
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
        val url = if (page <= 1) "$BASE_URL/es/buscar/$encoded/" else "$BASE_URL/es/buscar/$encoded/?page=$page"
        Log.d(TAG, "Searching Porn300: $url")
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
                .header("Referer", "$BASE_URL/es/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return emptyList()

            val doc = Jsoup.parse(html, BASE_URL)
            val items = doc.select("li.grid__item--video-thumb, li.grid__item")
            val seenIds = mutableSetOf<String>()

            for (el in items) {
                val linkEl = el.selectFirst("a[href*=/video/]") ?: continue
                val href = linkEl.attr("abs:href").ifEmpty { linkEl.attr("href") }
                val pageUrl = if (href.startsWith("http")) href else "$BASE_URL$href"

                var rawId = linkEl.attr("data-video-id").trim()
                if (rawId.isEmpty()) {
                    val idMatcher = Pattern.compile("/video/([^/]+)").matcher(pageUrl)
                    rawId = if (idMatcher.find()) idMatcher.group(1) ?: "" else ""
                }
                if (rawId.isEmpty() || !seenIds.add(rawId)) continue

                val titleEl = el.selectFirst("h3.grid__item__title, .grid__item__title")
                var title = titleEl?.text()?.trim() ?: linkEl.attr("title").trim()
                if (title.isEmpty()) {
                    val imgAlt = el.selectFirst("img")?.attr("alt")?.trim() ?: ""
                    title = imgAlt.ifEmpty { "Video Porn300" }
                }

                val imgEl = el.selectFirst("img")
                var thumbUrl = imgEl?.attr("data-src") ?: ""
                if (thumbUrl.isEmpty()) thumbUrl = imgEl?.attr("src") ?: ""
                if (thumbUrl.startsWith("//")) thumbUrl = "https:$thumbUrl"

                val durEl = el.selectFirst(".duration-video, span.duration")
                val durationText = durEl?.text()?.trim() ?: "10:00"

                val viewsEl = el.selectFirst("ul.grid__item__data li")
                val views = viewsEl?.text()?.trim() ?: ""

                list.add(
                    VideoItem(
                        id = "p300_$rawId",
                        title = title,
                        thumbUrl = thumbUrl,
                        pageUrl = pageUrl,
                        durationText = durationText,
                        author = "Porn300",
                        views = views,
                        quality = "HD",
                        source = "Porn300"
                    )
                )
            }
            Log.d(TAG, "Parsed ${list.size} videos from Porn300 ($url)")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Porn300: ${e.message}", e)
        }
        return list
    }

    override suspend fun extractStreamUrl(video: VideoItem): String = withContext(Dispatchers.IO) {
        streamCache[video.id]?.let { return@withContext it }

        try {
            val req = Request.Builder()
                .url(video.pageUrl)
                .header("User-Agent", UA)
                .header("Referer", "$BASE_URL/es/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext ""

            // 1. Direct <source src="...">
            val sourceMatcher = Pattern.compile("<source[^>]+src=[\"']([^\"']+\\.mp4[^\"']*)[\"']").matcher(html)
            if (sourceMatcher.find()) {
                val streamUrl = sourceMatcher.group(1) ?: ""
                if (streamUrl.isNotEmpty()) {
                    streamCache[video.id] = streamUrl
                    return@withContext streamUrl
                }
            }

            // 2. Fallback regex for cdn video url
            val cdnMatcher = Pattern.compile("(https?://[^\"]*cdnst[^\"]*\\.mp4[^\"]*)").matcher(html)
            if (cdnMatcher.find()) {
                val streamUrl = cdnMatcher.group(1) ?: ""
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
        val stream = extractStreamUrl(video)
        if (stream.isNotEmpty()) {
            val q = listOf(
                StreamQuality(label = "720p HD", url = stream, height = 720),
                StreamQuality(label = "Auto", url = stream)
            )
            qualitiesCache[video.id] = q
            return@withContext q
        }
        emptyList()
    }
}
