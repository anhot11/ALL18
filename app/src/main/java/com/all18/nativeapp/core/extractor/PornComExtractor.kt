package com.all18.nativeapp.core.extractor

import android.util.Base64
import android.util.Log
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.network.AppNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object PornComExtractor : VideoExtractor {
    override val sourceName: String = "Porn.com"
    private const val TAG = "PornComExtractor"
    private const val BASE_URL = "https://es.porn.com"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val client get() = AppNetworkClient.client
    private val streamCache = ConcurrentHashMap<String, String>()

    override suspend fun getFeed(page: Int, category: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val url = when (category.lowercase()) {
            "verificados", "verified" -> "$BASE_URL/search?q=verificado&pg=$page"
            "populares", "popular", "best" -> "$BASE_URL/search?q=popular&pg=$page"
            "amateur" -> if (page <= 1) "$BASE_URL/amateur" else "$BASE_URL/amateur?pg=$page"
            "hd" -> if (page <= 1) "$BASE_URL/4k-porn" else "$BASE_URL/4k-porn?pg=$page"
            "latinas" -> if (page <= 1) "$BASE_URL/latina" else "$BASE_URL/latina?pg=$page"
            "trending" -> "$BASE_URL/search?q=trending&pg=$page"
            else -> "$BASE_URL/search?q=all&pg=$page"
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
        val url = "$BASE_URL/search?q=$encoded&pg=$page"
        Log.d(TAG, "Searching Porn.com: $url")
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
            val items = doc.select("div.list-global__item")
            val seenIds = mutableSetOf<String>()

            for (el in items) {
                val sceneLink = el.selectFirst("a[href*=/scene/]")
                val outLink = el.selectFirst("a[href*=/out/n/]")

                if (sceneLink == null && outLink == null) continue

                val isNativeScene = sceneLink != null
                val pageUrl: String
                val finalId: String
                var author = "Porn.com"

                if (isNativeScene) {
                    val href = sceneLink!!.attr("href").trim()
                    pageUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
                    val m = Pattern.compile("/scene/(\\d+)").matcher(href)
                    val rawId = if (m.find()) m.group(1) else Math.abs(href.hashCode()).toString()
                    finalId = "scene_$rawId"
                } else {
                    val outHref = outLink!!.attr("href").trim()
                    val parts = outHref.trim('/').split("/")
                    var targetUrl = ""
                    if (parts.size >= 4) {
                        val sec = parts[3]
                        try {
                            val unquoted = URLDecoder.decode(sec, "UTF-8")
                            val padLen = (4 - unquoted.length % 4) % 4
                            val padded = unquoted + "=".repeat(padLen)
                            val dec = String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
                            if (dec.contains("^http")) {
                                targetUrl = dec.substringAfter("^")
                            }
                        } catch (_: Exception) {}
                    }
                    if (targetUrl.isEmpty()) {
                        targetUrl = if (outHref.startsWith("http")) outHref else "$BASE_URL$outHref"
                    }
                    pageUrl = targetUrl

                    val reportLink = el.selectFirst("a[href*=/report-link?sid=]")
                    val rawId = if (reportLink != null) {
                        val href = reportLink.attr("href")
                        val m = Pattern.compile("sid=(\\d+)").matcher(href)
                        if (m.find()) m.group(1) else ""
                    } else ""
                    finalId = if (rawId.isNotEmpty()) rawId else Math.abs(targetUrl.hashCode()).toString()

                    val partnerEl = el.selectFirst(".list-global__details a, a.partner-link")
                    val partnerName = partnerEl?.text()?.trim() ?: ""
                    if (partnerName.isNotEmpty()) {
                        author = partnerName
                    }
                }

                if (!seenIds.add(finalId)) continue

                // Title
                val primaryLink = sceneLink ?: outLink
                var title = primaryLink?.attr("title")?.trim() ?: ""
                if (title.isEmpty() || title == "Report this link") {
                    val titleLink = el.selectFirst(".list-global__title a")
                    title = titleLink?.text()?.trim() ?: titleLink?.attr("title")?.trim() ?: ""
                }
                if (title.isEmpty() || title == "Report this link") {
                    val anyA = el.select("a[title]")
                    for (a in anyA) {
                        val t = a.attr("title").trim()
                        if (t.isNotEmpty() && t != "Report this link") {
                            title = t
                            break
                        }
                    }
                }
                if (title.isEmpty()) {
                    val metaEl = el.selectFirst(".list-global__meta")
                    title = metaEl?.ownText()?.trim() ?: "Video Porn.com"
                }

                // Duration
                val durEl = el.selectFirst(".list-global__duration")
                val durationText = durEl?.text()?.trim() ?: "10 min"
                val durationSec = parseDuration(durationText)

                // Views / Rating
                val detailsEl = el.selectFirst(".list-global__details, .list-global__rating")
                val views = detailsEl?.text()?.trim() ?: "180K"

                // Thumbnail
                val imgEl = el.selectFirst("img")
                var thumbUrl = imgEl?.attr("data-src") ?: ""
                if (thumbUrl.isEmpty() || thumbUrl.startsWith("data:image")) {
                    val sourceEl = el.selectFirst("source[data-srcset]")
                    thumbUrl = sourceEl?.attr("data-srcset") ?: ""
                }
                if (thumbUrl.isEmpty() || thumbUrl.startsWith("data:image")) {
                    thumbUrl = imgEl?.attr("src") ?: ""
                }
                if (thumbUrl.startsWith("//")) thumbUrl = "https:$thumbUrl"

                val isHd = title.contains("HD", ignoreCase = true) || url.contains("4k")
                val quality = if (isHd) "HD" else ""

                list.add(
                    VideoItem(
                        id = "porncom_$finalId",
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

            Log.d(TAG, "Parsed ${list.size} videos from Porn.com")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Porn.com: ${e.message}", e)
        }
        return list
    }

    override suspend fun extractStreamUrl(video: VideoItem): String = withContext(Dispatchers.IO) {
        streamCache[video.id]?.let { return@withContext it }

        val target = video.pageUrl

        // 1. Native scene on Porn.com (direct HLS master playlist)
        if (target.contains("/scene/")) {
            try {
                val req = Request.Builder()
                    .url(target)
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Referer", "$BASE_URL/")
                    .build()

                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val m3u8Regex = Pattern.compile("(https?://[^\\s\"'<>]+?\\.m3u8[^\\s\"'<>]*)")
                    val mM3u8 = m3u8Regex.matcher(html)
                    if (mM3u8.find()) {
                        val stream = mM3u8.group(1).replace("&amp;", "&").trim()
                        streamCache[video.id] = stream
                        return@withContext stream
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving native scene stream for ${video.id}: ${e.message}", e)
            }
        }

        // 2. Delegate to XVideosExtractor if target is xvideos.com
        if (target.contains("xvideos.com")) {
            val stream = XVideosExtractor.extractStreamUrl(video.copy(pageUrl = target))
            if (stream.isNotEmpty()) {
                streamCache[video.id] = stream
                return@withContext stream
            }
        }

        // 3. Delegate to XNXXExtractor if target is xnxx.com
        if (target.contains("xnxx.com")) {
            val stream = XNXXExtractor.extractStreamUrl(video.copy(pageUrl = target))
            if (stream.isNotEmpty()) {
                streamCache[video.id] = stream
                return@withContext stream
            }
        }

        // 4. Partner sites (KVS, direct HLS/MP4)
        try {
            val req = Request.Builder()
                .url(target)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "$BASE_URL/")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w(TAG, "Partner site returned HTTP ${resp.code} for $target")
                return@withContext ""
            }

            val html = resp.body?.string() ?: return@withContext ""

            // Look for HLS .m3u8
            val hlsRegex = Pattern.compile("setVideoHLS\\s*\\(\\s*['\"]([^'\"]+)['\"]|(https?://[^\\s\"'<>]+?\\.m3u8[^\\s\"'<>]*)")
            val mHls = hlsRegex.matcher(html)
            if (mHls.find()) {
                val url = (mHls.group(1) ?: mHls.group(2) ?: "").replace("&amp;", "&").trim()
                if (url.isNotEmpty()) {
                    streamCache[video.id] = url
                    return@withContext url
                }
            }

            // Look for KVS/HTML5 full video MP4
            val kvsRegex = Pattern.compile("(?:video_url|setVideoUrl(?:High)?|file)\\s*[:=]\\s*['\"](https?://[^'\"\\s]+)[\"']")
            val mKvs = kvsRegex.matcher(html)
            if (mKvs.find()) {
                val url = mKvs.group(1).replace("&amp;", "&").trim()
                if (!url.contains("preview.mp4") && !url.contains("-preview.")) {
                    streamCache[video.id] = url
                    return@withContext url
                }
            }

            // Direct MP4 (excluding preview clips)
            val mp4Regex = Pattern.compile("(https?://[^\\s\"'<>]+?\\.mp4[^\\s\"'<>]*)")
            val mMp4 = mp4Regex.matcher(html)
            while (mMp4.find()) {
                val url = mMp4.group(1).replace("&amp;", "&").trim()
                if (!url.contains("preview") && !url.contains("thumb")) {
                    streamCache[video.id] = url
                    return@withContext url
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stream for ${video.id}: ${e.message}", e)
        }

        ""
    }

    private fun parseDuration(text: String): Int {
        val clean = text.replace("min", "").replace("m", "").trim()
        val parts = clean.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0] * 60
            else -> 600
        }
    }
}
