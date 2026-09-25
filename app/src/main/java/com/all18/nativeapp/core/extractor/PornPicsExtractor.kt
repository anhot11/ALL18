package com.all18.nativeapp.core.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object PornPicsExtractor {
    private const val TAG = "PornPicsExtractor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val photoCache = CopyOnWriteArrayList<String>()

    private val fallbackPhotos = listOf(
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1469334031218-e382a71b716b?w=1920&q=90&auto=format&fit=crop"
    )

    suspend fun getPhotos(category: String = ""): List<String> = withContext(Dispatchers.IO) {
        if (photoCache.isNotEmpty()) {
            return@withContext photoCache.toList()
        }

        val urls = listOf(
            "https://www.pornpics.com/",
            "https://www.pornpics.com/latina/",
            "https://www.pornpics.com/cosplay/",
            "https://www.pornpics.com/amateur/",
            "https://www.pornpics.com/bikini/",
            "https://www.pornpics.com/asian/",
            "https://www.pornpics.com/lingerie/"
        )

        for (targetUrl in urls) {
            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val resp = client.newCall(req).execute()
                val html = resp.body?.string() ?: continue
                val matcher = Pattern.compile("data-src=\"(https://cdni\\.pornpics\\.com/[^\"]+\\.jpg)\"").matcher(html)
                var count = 0
                while (matcher.find()) {
                    val rawUrl = matcher.group(1) ?: continue
                    // Elevar resolucion de miniatura 460px a 1280px / 1080p nativa de alta definicion
                    val hdUrl = rawUrl.replace("cdni.pornpics.com/460/", "cdni.pornpics.com/1280/")
                    if (!photoCache.contains(hdUrl)) {
                        photoCache.add(hdUrl)
                        count++
                    }
                }
                Log.d(TAG, "Scraped $count photos from $targetUrl (Total in cache: ${photoCache.size})")
            } catch (e: Exception) {
                Log.e(TAG, "Error scraping photos from $targetUrl: ${e.message}", e)
            }
        }

        if (photoCache.isEmpty()) {
            photoCache.addAll(fallbackPhotos)
        }
        photoCache.toList()
    }

    suspend fun getRandomGallery(count: Int = 1): List<String> = withContext(Dispatchers.IO) {
        val pool = getPhotos()
        if (pool.isEmpty()) return@withContext fallbackPhotos.take(count)
        pool.shuffled().take(count)
    }
}
