package com.all18.nativeapp.core.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class XVideosModel(
    val name: String,
    val handle: String,
    val profileUrl: String,
    val avatarUrl: String,
    val info: String = ""
)

object XVideosModelsExtractor {
    private const val TAG = "XVideosModelsExtractor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cachedModels = CopyOnWriteArrayList<XVideosModel>()

    suspend fun getModels(): List<XVideosModel> = withContext(Dispatchers.IO) {
        if (cachedModels.isNotEmpty()) {
            return@withContext cachedModels.toList()
        }

        try {
            val req = Request.Builder()
                .url("https://www.xvideos.com/models")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext emptyList()

            val blocks = html.split("class=\"thumb-block")
            val namePattern = Pattern.compile("<p class=\"profile-name\">\\s*<a href=\"([^\"]+)\">([^<]+)</a>")
            val imgPattern = Pattern.compile("src=\"([^\"]+pp_big\\.jpg[^\"]*)\"")
            val infoPattern = Pattern.compile("<p class=\"profile-info\">\\s*([^<]+)\\s*</p>")

            for (block in blocks.drop(1)) {
                val nameMatcher = namePattern.matcher(block)
                if (!nameMatcher.find()) continue
                val profileHref = nameMatcher.group(1) ?: continue
                val rawName = nameMatcher.group(2)?.trim() ?: continue

                val imgMatcher = imgPattern.matcher(block)
                val avatarUrl = if (imgMatcher.find()) imgMatcher.group(1) ?: "" else ""

                val infoMatcher = infoPattern.matcher(block)
                val info = if (infoMatcher.find()) infoMatcher.group(1)?.trim() ?: "" else ""

                val slug = profileHref.substringAfterLast("/")
                val handle = "@${slug.replace('-', '_')}"

                if (avatarUrl.isNotEmpty()) {
                    cachedModels.add(
                        XVideosModel(
                            name = rawName,
                            handle = handle,
                            profileUrl = if (profileHref.startsWith("http")) profileHref else "https://www.xvideos.com$profileHref",
                            avatarUrl = avatarUrl,
                            info = info
                        )
                    )
                }
            }
            Log.d(TAG, "Parsed ${cachedModels.size} verified models from XVideos")
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping XVideos models: ${e.message}", e)
        }
        cachedModels.toList()
    }
}
