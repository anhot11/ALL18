package com.all18.nativeapp.core.extractor

import android.util.Log
import com.all18.nativeapp.core.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object PornhubExtractor : VideoExtractor {
    override val sourceName: String = "Pornhub"
    private const val TAG = "PornhubExtractor"

    private val client get() = com.all18.nativeapp.core.network.AppNetworkClient.client

    private const val BASE_URL = "https://www.pornhub.com"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    private const val COOKIE = "accessAgeDisclaimerPH=1; platform=mobile; hasVisited=1; hasAcceptedCookies=1"

    private val streamCache = ConcurrentHashMap<String, String>()

    override suspend fun getFeed(page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val path = when (category.lowercase()) {
            "verificados", "verified" -> "/video?o=m&page=$page"
            "populares", "popular", "best" -> "/video?o=mv&page=$page"
            "amateur" -> "/video?c=3&page=$page"
            "hd" -> "/video?hd=1&page=$page"
            "trending" -> "/video?o=tr&page=$page"
            "latinas" -> "/video/search?search=latinas&page=$page"
            else -> "/video?page=$page"
        }
        val url = "$BASE_URL$path"
        Log.d(TAG, "Fetching feed from: $url (page $page, category $category)")
        fetchAndParse(url)
    }

    override suspend fun search(query: String, page: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val encoded = try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (_: Exception) {
            query.trim().replace(" ", "+")
        }
        val url = "$BASE_URL/video/search?search=$encoded&page=$page"
        Log.d(TAG, "Searching: $url")
        fetchAndParse(url)
    }

    private fun fetchAndParse(targetUrl: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", UA)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Cookie", COOKIE)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return emptyList()
            val doc = Jsoup.parse(html, BASE_URL)

            val cards = doc.select("li[data-video-vkey]")
            for (card in cards) {
                val vkey = card.attr("data-video-vkey").ifEmpty {
                    card.attr("data-video-id")
                }
                if (vkey.isEmpty()) continue

                // Titulo
                val titleElem = card.selectFirst(".title a")
                var title = titleElem?.text()?.trim() ?: ""
                if (title.isEmpty()) {
                    title = card.selectFirst("img")?.attr("alt")?.trim() ?: "Video Pornhub"
                }

                // URL de la pagina
                val relHref = titleElem?.attr("href") ?: "/view_video.php?viewkey=$vkey"
                val pageUrl = if (relHref.startsWith("http")) relHref else "$BASE_URL$relHref"

                // Miniatura robusta
                var thumb = ""
                val img = card.selectFirst("img")
                val candidates = listOfNotNull(
                    card.selectFirst("[data-poster]")?.attr("data-poster"),
                    img?.attr("data-path"),
                    img?.attr("data-src"),
                    img?.attr("src"),
                    img?.attr("data-thumb_url"),
                    card.selectFirst("a.imageLink")?.attr("data-poster")
                )
                for (c in candidates) {
                    val trimmed = c.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("data:image") &&
                        (trimmed.contains("phncdn.com") || trimmed.startsWith("http") || trimmed.startsWith("//"))
                    ) {
                        thumb = trimmed
                        break
                    }
                }
                if (thumb.isEmpty()) {
                    val moreAction = card.selectFirst("[data-more-action]")?.attr("data-more-action") ?: ""
                    if (moreAction.isNotEmpty()) {
                        val match = Regex("thumbnailUrl\\\\s*:\\\\s*['\"]([^'\"&]+)").find(moreAction)
                        if (match != null) {
                            thumb = match.groupValues[1].replace("\\/", "/")
                        }
                    }
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                // Duracion
                val duration = card.selectFirst(".time")?.text()?.trim()
                    ?: card.selectFirst(".duration")?.text()?.trim() ?: "12:00"

                // Autor / Creador
                val author = card.selectFirst(".uploaderLink")?.text()?.trim()
                    ?: card.selectFirst(".videoUploaderBlock a")?.text()?.trim() ?: "All18"

                // Vistas
                val viewsRaw = card.selectFirst(".views")?.text()?.trim() ?: ""
                val views = viewsRaw.replace("vistas", "", ignoreCase = true)
                    .replace("views", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "120K" }

                // Calidad HD
                val hasHd = card.selectFirst(".hd-thumbnail, .hd, [class*='hd']") != null
                        || title.contains("HD", ignoreCase = true)
                val quality = if (hasHd) "HD" else ""

                // Segundos de duracion
                val durationSeconds = parseDurationSeconds(duration)

                list.add(
                    VideoItem(
                        id = vkey,
                        title = title,
                        thumbUrl = thumb,
                        pageUrl = pageUrl,
                        durationText = duration,
                        durationSeconds = durationSeconds,
                        author = author,
                        source = "Pornhub",
                        quality = quality,
                        views = views
                    )
                )
            }
            Log.d(TAG, "Parsed ${list.size} videos from $targetUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Pornhub feed: ${e.message}", e)
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
                .header("Cookie", COOKIE)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext ""

            // 1. Extraer mediaDefinitions desde flashvars
            val mdPattern = Pattern.compile("\"mediaDefinitions\"\\s*:\\s*(\\[\\{.*?\\}\\])")
            val mdMatcher = mdPattern.matcher(html)
            if (mdMatcher.find()) {
                val jsonRaw = mdMatcher.group(1)?.replace("\\/", "/") ?: ""
                try {
                    val array = JSONArray(jsonRaw)
                    var bestHlsUrl = ""
                    var fallbackHlsUrl = ""
                    var fallbackMp4Url = ""

                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val format = item.optString("format", "")
                        val videoUrl = item.optString("videoUrl", "")
                        val quality = item.optString("quality", "")

                        if (videoUrl.isNotEmpty()) {
                            if (format.equals("hls", ignoreCase = true)) {
                                if (quality == "720" || quality == "1080") {
                                    bestHlsUrl = videoUrl
                                } else if (fallbackHlsUrl.isEmpty()) {
                                    fallbackHlsUrl = videoUrl
                                }
                            } else if (format.equals("mp4", ignoreCase = true) && fallbackMp4Url.isEmpty()) {
                                fallbackMp4Url = videoUrl
                            }
                        }
                    }

                    val chosenUrl = bestHlsUrl.ifEmpty { fallbackHlsUrl.ifEmpty { fallbackMp4Url } }
                    if (chosenUrl.isNotEmpty()) {
                        streamCache[video.id] = chosenUrl
                        return@withContext chosenUrl
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing mediaDefinitions JSON: ${e.message}")
                }
            }

            // 2. Fallback regex directo para master.m3u8 o videoUrl HLS
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
}
