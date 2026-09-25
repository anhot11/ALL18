package com.all18.nativeapp.core.extractor

import android.util.Log
import com.all18.nativeapp.core.model.TikTokItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RedGifsExtractor {
    private const val TAG = "RedGifsExtractor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var authToken: String? = null
    private var tokenTimestamp: Long = 0

    private suspend fun getAuthToken(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!authToken.isNullOrEmpty() && (now - tokenTimestamp) < 3000_000) {
            return@withContext authToken
        }

        try {
            val req = Request.Builder()
                .url("https://api.redgifs.com/v2/auth/temporary")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val token = json.optString("token", "")
            if (token.isNotEmpty()) {
                authToken = token
                tokenTimestamp = now
                Log.d(TAG, "RedGifs token acquired")
                return@withContext token
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring RedGifs token: ${e.message}", e)
        }
        return@withContext null
    }

    suspend fun getVerticalShorts(query: String = "trending", page: Int = 1): List<TikTokItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TikTokItem>()
        try {
            val token = getAuthToken()
            val url = "https://api.redgifs.com/v2/gifs/search?search_text=${query.replace(" ", "+")}&count=15&page=$page"
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")

            if (!token.isNullOrEmpty()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            val resp = client.newCall(reqBuilder.build()).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val gifs = json.optJSONArray("gifs") ?: return@withContext emptyList()

            for (i in 0 until gifs.length()) {
                val g = gifs.getJSONObject(i)
                val id = g.optString("id", "")
                if (id.isEmpty()) continue

                val userName = g.optString("userName", "RedGifs Creator").ifEmpty { "Creador VIP" }
                val urls = g.optJSONObject("urls")
                val hdVideo = urls?.optString("hd", "") ?: ""
                val sdVideo = urls?.optString("sd", "") ?: ""
                val poster = urls?.optString("poster", "") ?: ""
                val streamUrl = hdVideo.ifEmpty { sdVideo }

                val tagsArr = g.optJSONArray("tags")
                val tagStr = buildString {
                    if (tagsArr != null) {
                        for (t in 0 until kotlin.math.min(tagsArr.length(), 4)) {
                            append("#${tagsArr.getString(t).replace(" ", "")} ")
                        }
                    }
                    if (isEmpty()) append("#shorts #viral #parati")
                }

                val likes = g.optInt("likes", 1200) + 1500
                val views = g.optInt("views", 8500)

                val width = g.optInt("width", 0)
                val height = g.optInt("height", 0)

                val gifType = g.optInt("type", 1)
                val isImage = (gifType == 2) || streamUrl.endsWith(".jpg", ignoreCase = true) || streamUrl.endsWith(".jpeg", ignoreCase = true) || streamUrl.endsWith(".png", ignoreCase = true)

                list.add(
                    TikTokItem(
                        id = "rg_$id",
                        title = tagStr.trim(),
                        description = tagStr.trim(),
                        authorName = userName,
                        authorHandle = "@${userName.lowercase().replace(" ", "_")}",
                        authorAvatarUrl = poster.ifEmpty { streamUrl },
                        musicTitle = "Sonido original - $userName",
                        thumbUrl = poster.ifEmpty { streamUrl },
                        videoUrl = if (isImage) "" else streamUrl,
                        pageUrl = "https://www.redgifs.com/watch/$id",
                        isPhotoPost = isImage,
                        photoUrls = if (isImage) listOf(streamUrl) else emptyList(),
                        width = width,
                        height = height,
                        likesCount = likes,
                        commentsCount = likes / 12,
                        sharesCount = com.all18.nativeapp.core.model.generateSharesCount("rg_$id"),
                        bookmarksCount = com.all18.nativeapp.core.model.generateBookmarksCount("rg_$id"),
                        isLiked = false
                    )
                )
            }
            Log.d(TAG, "Fetched ${list.size} vertical clips from RedGifs")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching RedGifs shorts: ${e.message}", e)
        }
        return@withContext list
    }
}
