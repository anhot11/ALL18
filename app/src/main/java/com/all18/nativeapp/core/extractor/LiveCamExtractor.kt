package com.all18.nativeapp.core.extractor

import android.util.Log
import com.all18.nativeapp.core.model.TikTokLiveItem
import com.all18.nativeapp.core.network.AppNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Extractor para transmisiones en directo (Modo LIVE TikTok)
 * Consume la API de salas en vivo y extrae streams nativos HLS (.m3u8).
 */
object LiveCamExtractor {
    private const val TAG = "LiveCamExtractor"

    suspend fun getLiveRooms(limit: Int = 30): List<TikTokLiveItem> = withContext(Dispatchers.IO) {
        val roomsList = mutableListOf<TikTokLiveItem>()
        try {
            val url = "https://chaturbate.com/api/ts/roomlist/room-list/?offset=0&limit=$limit"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            val response = AppNetworkClient.client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string().orEmpty()
                val root = JSONObject(jsonStr)
                val rooms = root.optJSONArray("rooms") ?: JSONArray()
                for (i in 0 until rooms.length()) {
                    val r = rooms.getJSONObject(i)
                    val username = r.optString("username").trim()
                    if (username.isEmpty()) continue

                    val viewers = r.optInt("num_users", 1200)
                    val img = r.optString("img")
                    val subject = r.optString("room_subject", r.optString("subject"))
                    val tagsArray = r.optJSONArray("tags")
                    val tags = mutableListOf<String>()
                    if (tagsArray != null) {
                        for (t in 0 until tagsArray.length()) {
                            tags.add(tagsArray.optString(t))
                        }
                    }

                    val displayName = username.replaceFirstChar { it.uppercase() }
                    val likes = (viewers * 3.4).toInt().coerceAtLeast(viewers)

                    val categoryBadge = if (tags.isNotEmpty()) {
                        "🔥 #${tags.first()}"
                    } else {
                        "🔥 Clasificación diaria"
                    }

                    roomsList.add(
                        TikTokLiveItem(
                            id = "live_cb_$username",
                            username = username,
                            displayName = displayName,
                            avatarUrl = img,
                            snapshotUrl = img,
                            streamUrl = "",
                            viewersCount = viewers,
                            likesCount = likes,
                            roomSubject = subject,
                            categoryBadge = categoryBadge,
                            tags = tags,
                            source = "Chaturbate"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching live rooms: ${e.message}", e)
        }
        roomsList
    }

    suspend fun extractLiveStreamUrl(username: String): String? = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("room_slug", username)
                .add("bandwidth", "high")
                .build()

            val request = Request.Builder()
                .url("https://chaturbate.com/get_edge_hls_url_ajax/")
                .post(formBody)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = AppNetworkClient.client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string().orEmpty()
                val json = JSONObject(jsonStr)
                if (json.optBoolean("success", false)) {
                    val streamUrl = json.optString("url")
                    if (streamUrl.isNotEmpty()) {
                        return@withContext streamUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting HLS stream for $username: ${e.message}", e)
        }
        null
    }
}
