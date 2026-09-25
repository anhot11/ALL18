package com.all18.nativeapp.core.extractor

import android.util.Log
import com.all18.nativeapp.core.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object XNXXExtractor : VideoExtractor {
    override val sourceName: String = "XNXX"
    private const val TAG = "XNXXExtractor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val BASE_URL = "https://www.xnxx.com"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    private val streamCache = ConcurrentHashMap<String, String>()

    override suspend fun getFeed(page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val path = when (category.lowercase()) {
            "verificados", "verified" -> "/?k=verified&p=$page"
            "populares", "best" -> if (page <= 1) "/best" else "/best/$page"
            "amateur" -> "/?k=amateur&p=$page"
            else -> if (page <= 1) "/best" else "/best/$page"
        }
        val url = "$BASE_URL$path"
        Log.d(TAG, "Fetching XNXX feed: $url")
        fetchAndParse(url)
    }

    override suspend fun search(query: String, page: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val encoded = query.trim().replace(" ", "+")
        val url = "$BASE_URL/?k=$encoded&p=$page"
        Log.d(TAG, "Searching XNXX: $url")
        fetchAndParse(url)
    }

    private fun fetchAndParse(targetUrl: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", UA)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return emptyList()
            val doc = Jsoup.parse(html, BASE_URL)

            val cards = doc.select("div.thumb-block")
            for (card in cards) {
                var eid = ""
                var title = ""
                var thumb = ""
                var relHref = ""

                // 1. Try parsing JSON from data-video attribute
                val dataVideo = card.attr("data-video")
                if (dataVideo.isNotEmpty()) {
                    try {
                        val json = JSONObject(dataVideo)
                        eid = json.optString("encodedId", json.optString("id", ""))
                        thumb = json.optString("sfwThumbUrl", "")
                    } catch (_: Exception) {}
                }

                // 2. Element parsing
                val titleElem = card.selectFirst(".title a") ?: card.selectFirst("a.thumb-link")
                if (title.isEmpty()) {
                    title = titleElem?.attr("title")?.ifEmpty { titleElem.text() } ?: ""
                }
                if (relHref.isEmpty()) {
                    relHref = titleElem?.attr("href") ?: ""
                }

                if (thumb.isEmpty()) {
                    val img = card.selectFirst("img")
                    thumb = img?.attr("data-src")?.ifEmpty { img.attr("src") } ?: ""
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                if (relHref.isEmpty()) continue
                val pageUrl = if (relHref.startsWith("http")) relHref else "$BASE_URL$relHref"

                if (eid.isEmpty()) {
                    val m = Pattern.compile("video-([a-zA-Z0-9]+)").matcher(relHref)
                    if (m.find()) eid = m.group(1) ?: ""
                }
                if (eid.isEmpty()) eid = relHref.hashCode().toString()

                if (title.isEmpty()) {
                    title = relHref.substringAfterLast("/").replace("_", " ").replace("-", " ")
                }

                val duration = card.selectFirst(".duration")?.text() ?: ""
                val author = card.selectFirst(".name")?.text() ?: "XNXX"
                val quality = if (card.selectFirst(".video-hd-mark") != null) "HD" else ""
                val views = card.selectFirst(".spr-views")?.parent()?.text() ?: ""

                var durationSeconds = 0
                val durMatch = Pattern.compile("(\\d+)\\s*min").matcher(duration)
                if (durMatch.find()) {
                    val min = durMatch.group(1)?.toIntOrNull() ?: 0
                    durationSeconds = min * 60
                }

                list.add(
                    VideoItem(
                        id = "xnxx_$eid",
                        title = title.trim(),
                        thumbUrl = thumb,
                        pageUrl = pageUrl,
                        durationText = duration.trim(),
                        durationSeconds = durationSeconds,
                        author = author.trim(),
                        source = "XNXX",
                        quality = quality.trim(),
                        views = views.trim()
                    )
                )
            }
            Log.d(TAG, "Parsed ${list.size} videos from XNXX ($targetUrl)")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching XNXX: ${e.message}", e)
        }
        return list
    }

    override suspend fun extractStreamUrl(video: VideoItem): String = withContext(Dispatchers.IO) {
        streamCache[video.id]?.let { return@withContext it }

        try {
            val req = Request.Builder()
                .url(video.pageUrl)
                .header("User-Agent", UA)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext ""

            // 1. HLS m3u8
            val hlsP = Pattern.compile("html5player\\.setVideoHLS\\('([^']+)'\\)").matcher(html)
            if (hlsP.find()) {
                val hlsUrl = hlsP.group(1)
                if (!hlsUrl.isNullOrEmpty()) {
                    streamCache[video.id] = hlsUrl
                    return@withContext hlsUrl
                }
            }

            // 2. High MP4
            val highP = Pattern.compile("html5player\\.setVideoUrlHigh\\('([^']+)'\\)").matcher(html)
            if (highP.find()) {
                val highUrl = highP.group(1)
                if (!highUrl.isNullOrEmpty()) {
                    streamCache[video.id] = highUrl
                    return@withContext highUrl
                }
            }

            // 3. Low MP4
            val lowP = Pattern.compile("html5player\\.setVideoUrlLow\\('([^']+)'\\)").matcher(html)
            if (lowP.find()) {
                val lowUrl = lowP.group(1)
                if (!lowUrl.isNullOrEmpty()) {
                    streamCache[video.id] = lowUrl
                    return@withContext lowUrl
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting stream for ${video.id}: ${e.message}", e)
        }
        return@withContext ""
    }
}
