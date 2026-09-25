package com.all18.nativeapp.core.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object PicHunterExtractor {
    private const val TAG = "PicHunterExtractor"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val photoCache = CopyOnWriteArrayList<String>()

    suspend fun getPhotos(): List<String> = withContext(Dispatchers.IO) {
        if (photoCache.isNotEmpty()) {
            return@withContext photoCache.toList()
        }

        val targetUrls = listOf(
            "https://www.pichunter.com/",
            "https://www.pichunter.com/tags/latina",
            "https://www.pichunter.com/tags/amateur",
            "https://www.pichunter.com/tags/cosplay"
        )

        val seen = mutableSetOf<String>()
        val imgPattern = Pattern.compile("(https?://cdn\\d*\\.pichunter\\.com/media/posts/[^\"']+\\.(?:jpg|jpeg|webp))")

        for (url in targetUrls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Referer", "https://www.pichunter.com/")
                    .build()

                val resp = client.newCall(req).execute()
                val html = resp.body?.string() ?: continue

                val matcher = imgPattern.matcher(html)
                while (matcher.find()) {
                    val imgUrl = matcher.group(1) ?: continue
                    if (seen.add(imgUrl)) {
                        photoCache.add(imgUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching photos from $url: ${e.message}", e)
            }
        }

        Log.d(TAG, "Fetched ${photoCache.size} unique HD photos from PicHunter")
        photoCache.toList()
    }
}
