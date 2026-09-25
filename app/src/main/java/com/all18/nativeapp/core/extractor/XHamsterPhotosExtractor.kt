package com.all18.nativeapp.core.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class XHamsterPhoto(
    val title: String,
    val photoUrl: String,
    val galleryUrl: String
)

object XHamsterPhotosExtractor {
    private const val TAG = "XHamsterPhotosExtractor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cachedPhotos = CopyOnWriteArrayList<XHamsterPhoto>()

    suspend fun getPhotos(): List<XHamsterPhoto> = withContext(Dispatchers.IO) {
        if (cachedPhotos.isNotEmpty()) {
            return@withContext cachedPhotos.toList()
        }

        try {
            val req = Request.Builder()
                .url("https://xhamster.com/photos")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext emptyList()

            val pattern = Pattern.compile("<a[^>]+href=\"(https://xhamster\\.com/photos/gallery/[^\"]+)\"[^>]*>.*?<img[^>]+src=\"([^\"]+1000\\.jpg)\"", Pattern.DOTALL)
            val matcher = pattern.matcher(html)

            val seenUrls = mutableSetOf<String>()
            while (matcher.find()) {
                val galleryUrl = matcher.group(1) ?: continue
                val photoUrl = matcher.group(2) ?: continue

                if (seenUrls.add(photoUrl)) {
                    val slug = galleryUrl.substringAfterLast("/")
                    val title = slug.split("-")
                        .filter { it.isNotEmpty() && !it.all { char -> char.isDigit() } }
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                        .ifEmpty { "Galería HD" }

                    cachedPhotos.add(
                        XHamsterPhoto(
                            title = title,
                            photoUrl = photoUrl,
                            galleryUrl = galleryUrl
                        )
                    )
                }
            }
            Log.d(TAG, "Parsed ${cachedPhotos.size} 1000px high-res photos from XHamster")
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping XHamster photos: ${e.message}", e)
        }
        cachedPhotos.toList()
    }
}
