package com.all18.nativeapp.core.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ModelPhoto(
    val modelName: String,
    val modelHandle: String,
    val photoUrl: String,
    val gallery: List<String> = emptyList()
)

object XNXXPhotosExtractor {
    private const val TAG = "XNXXPhotosExtractor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cachedModels = CopyOnWriteArrayList<ModelPhoto>()

    suspend fun getModelPhotos(): List<ModelPhoto> = withContext(Dispatchers.IO) {
        if (cachedModels.isNotEmpty()) {
            return@withContext cachedModels.toList()
        }

        try {
            val req = Request.Builder()
                .url("https://www.xnxx.com/pornstars")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext emptyList()

            val blocks = html.split("class=\"thumb-block")
            val slugPattern = Pattern.compile("href=\"/pornstar/([^\"]+)\"")
            val imgPattern = Pattern.compile("src=\\\\?\"(https://[^\"\\\\]+\\.jpg)")
            val dvPattern = Pattern.compile("data-videos=\\\\*'(\\[.*?\\])\\\\*'")

            for (block in blocks.drop(1)) {
                val slugMatcher = slugPattern.matcher(block)
                if (!slugMatcher.find()) continue
                val slug = slugMatcher.group(1) ?: continue

                val cleanName = slug.replace('-', ' ').replace('_', ' ')
                    .split(" ")
                    .filter { it.isNotEmpty() }
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    .ifEmpty { "Creadora VIP" }

                val imgMatcher = imgPattern.matcher(block)
                val thumb = if (imgMatcher.find()) imgMatcher.group(1) ?: "" else ""

                val gallery = mutableListOf<String>()
                if (thumb.isNotEmpty()) gallery.add(thumb)

                val dvMatcher = dvPattern.matcher(block)
                if (dvMatcher.find()) {
                    val rawJson = dvMatcher.group(1)?.replace("\\/", "/") ?: ""
                    try {
                        val arr = JSONArray(rawJson)
                        for (i in 0 until arr.length()) {
                            val vObj = arr.getJSONObject(i)
                            val gImg = vObj.optString("img", "")
                            if (gImg.isNotEmpty() && !gallery.contains(gImg)) {
                                gallery.add(gImg)
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (thumb.isNotEmpty() || gallery.isNotEmpty()) {
                    val handle = "@${slug.replace('-', '_')}"
                    cachedModels.add(
                        ModelPhoto(
                            modelName = cleanName,
                            modelHandle = handle,
                            photoUrl = thumb.ifEmpty { gallery.first() },
                            gallery = gallery
                        )
                    )
                }
            }
            Log.d(TAG, "Parsed ${cachedModels.size} models and photo galleries from XNXX")
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping XNXX photos: ${e.message}", e)
        }
        cachedModels.toList()
    }
}
