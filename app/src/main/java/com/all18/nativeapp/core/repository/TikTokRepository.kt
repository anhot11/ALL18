package com.all18.nativeapp.core.repository

import android.util.Log
import com.all18.nativeapp.core.extractor.RedGifsExtractor
import com.all18.nativeapp.core.model.TikTokItem
import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.util.FeedFreshnessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

object TikTokRepository {
    private const val TAG = "TikTokRepository"

    fun markAsSeen(id: String) {
        if (id.isEmpty()) return
        FeedFreshnessManager.markSeen(id)
    }

    private val sampleTags = listOf(
        "#viral #fyp #parati #exclusive",
        "#shorts #all18 #trending #latina",
        "#backstage #model #glamour #vip",
        "#newvideo #trend #sensual #dance",
        "#hot #exclusive #creator #top",
        "#cosplay #hentai #anime #fantasy",
        "#asian #beauty #glamour #tokyo",
        "#amateur #casero #pov #natural",
        "#vr #immersive #18plus #vrporn"
    )

    private val searchQueries = listOf(
        "trending", "viral", "latina", "amateur", "verified", "exclusive",
        "parati", "hot", "shorts", "cosplay", "asian", "mature", "fetish", "dance"
    )

    suspend fun getFeed(page: Int, tab: String = "Para ti"): List<TikTokItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<TikTokItem>()
        try {
            // Preload creators & photos
            val creators = PhotoRepository.getCreators()

            // 1. Fetch real vertical short clips from RedGifs with cycling topic & offset
            val rgQuery = when (tab) {
                "Explorar" -> listOf("trending", "latina", "amateur", "top", "sensual", "cosplay", "asian").random()
                "Siguiendo" -> listOf("verified", "exclusive", "creator", "model").random()
                else -> searchQueries.random()
            }
            val rgPage = if (page == 1) Random.nextInt(1, 12) else (page + 10)
            val rgShorts = RedGifsExtractor.getVerticalShorts(rgQuery, rgPage)
            for (rg in rgShorts) {
                if (!rg.isPhotoPost && rg.videoUrl.isNotEmpty() && rg.videoUrl.contains(".mp4") && !resilientStreamPool.contains(rg.videoUrl)) {
                    resilientStreamPool.add(rg.videoUrl)
                }
            }

            // 2. Fetch clips from ALL platforms via MultiSourceRepository (Pornhub, YouPorn, EPorner, RedTube, JustPorn, Porn.com, XVideos, XNXX)
            val tubeCategory = when (tab) {
                "Explorar" -> listOf("trending", "cosplay", "hentai", "asiático", "amateur", "latinas").random()
                "Siguiendo" -> "verificados"
                else -> listOf("populares", "amateur", "latinas", "pov", "hd").random()
            }
            val tubePage = if (page == 1) FeedFreshnessManager.getEffectivePage("Todas", 1) else page
            val tubeVideos = try {
                MultiSourceRepository.getFeed("Todas", tubePage, tubeCategory)
            } catch (e: Exception) {
                emptyList()
            }

            val tubeItems = tubeVideos.take(15).mapIndexed { index, video ->
                val creator = creators.getOrNull((index + (page * 3)) % kotlin.math.max(1, creators.size))
                    ?: PhotoRepository.getRandomCreator()
                val tags = sampleTags[(index + page) % sampleTags.size]

                TikTokItem(
                    id = video.id,
                    title = video.title,
                    description = "${video.title} $tags",
                    authorName = creator.name,
                    authorHandle = creator.handle,
                    authorAvatarUrl = creator.avatarUrl,
                    thumbUrl = video.thumbUrl,
                    pageUrl = video.pageUrl,
                    source = video.source,
                    likesCount = when (index % 5) {
                        0 -> 1500 + (kotlin.math.abs(video.id.hashCode()) % 250)
                        1 -> 3000 + (kotlin.math.abs(video.id.hashCode()) % 500)
                        2 -> 8200 + (kotlin.math.abs(video.id.hashCode()) % 3000)
                        3 -> 24000 + (kotlin.math.abs(video.id.hashCode()) % 40000)
                        else -> 4500 + (kotlin.math.abs(video.id.hashCode()) % 1500)
                    },
                    commentsCount = 120 + (video.id.hashCode().let { kotlin.math.abs(it) % 800 }),
                    sharesCount = com.all18.nativeapp.core.model.generateSharesCount(video.id),
                    bookmarksCount = com.all18.nativeapp.core.model.generateBookmarksCount(video.id),
                    isLiked = false
                )
            }

            // Shuffle and interleave RedGifs and tube shorts to provide dynamic variety
            val combinedList = mutableListOf<TikTokItem>()
            val shuffledRg = rgShorts.shuffled()
            val shuffledTube = tubeItems.shuffled()
            var rgIdx = 0
            var tubeIdx = 0
            while (rgIdx < shuffledRg.size || tubeIdx < shuffledTube.size) {
                if (rgIdx < shuffledRg.size) combinedList.add(shuffledRg[rgIdx++])
                if (rgIdx < shuffledRg.size && (tubeIdx >= shuffledTube.size || Random.nextBoolean())) {
                    combinedList.add(shuffledRg[rgIdx++])
                }
                if (tubeIdx < shuffledTube.size) combinedList.add(shuffledTube[tubeIdx++])
            }

            // Prioritize unseen items so the opening videos are fresh and never repetitive
            val unseen = combinedList.filter { !FeedFreshnessManager.isSeen(it.id) }
            val finalItems = if (unseen.isNotEmpty()) unseen else combinedList
            result.addAll(finalItems)

            // 3. Insercion de publicaciones fotograficas
            if (result.size >= 2) {
                val photoCount = Random.nextInt(2, 5)
                val photos = PhotoRepository.getRandomGallery(count = photoCount)
                if (photos.isNotEmpty()) {
                    val creator = creators.getOrNull((page * 7) % kotlin.math.max(1, creators.size))
                        ?: PhotoRepository.getRandomCreator()
                    val insertIndex = kotlin.math.min(2, result.size)
                    val photoPostId = "photo_${page}_${System.currentTimeMillis()}"
                    val photoItem = TikTokItem(
                        id = photoPostId,
                        title = "📷 Sesion exclusiva de fotos HD 1080p #photoshoot #model",
                        description = "Nuevo set de fotos exclusivo en alta definicion 🥰 Desliza para ver mas! #exclusive #model #fyp #photos #1080p",
                        authorName = creator.name,
                        authorHandle = creator.handle,
                        authorAvatarUrl = creator.avatarUrl.ifEmpty { photos.first() },
                        thumbUrl = photos.first(),
                        isPhotoPost = true,
                        photoUrls = photos,
                        likesCount = when (Random.nextInt(4)) {
                            0 -> 1500 + Random.nextInt(0, 100)
                            1 -> 3000 + Random.nextInt(0, 200)
                            2 -> 6200 + Random.nextInt(100, 1500)
                            else -> 18000 + Random.nextInt(500, 5000)
                        },
                        commentsCount = 480 + Random.nextInt(20, 200),
                        sharesCount = com.all18.nativeapp.core.model.generateSharesCount(photoPostId),
                        bookmarksCount = com.all18.nativeapp.core.model.generateBookmarksCount(photoPostId),
                        isLiked = false
                    )
                    result.add(insertIndex, photoItem)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TikTok feed: ${e.message}", e)
        }
        result
    }

    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val resilientStreamPool = java.util.concurrent.CopyOnWriteArrayList<String>(listOf(
        "https://media.redgifs.com/LateDapperAfricanaugurbuzzard.mp4",
        "https://media.redgifs.com/RubberyCleanAlligatorgar.mp4",
        "https://media.redgifs.com/LawngreenDiligentPipistrelle.mp4",
        "https://media.redgifs.com/OptimisticUpbeatXiphosuran.mp4",
        "https://media.redgifs.com/KosherRemorsefulServal.mp4"
    ))

    suspend fun resolveStreamUrl(item: TikTokItem): String = withContext(Dispatchers.IO) {
        if (item.isPhotoPost) {
            return@withContext ""
        }

        if (item.videoUrl.isNotEmpty()) {
            streamCache[item.id] = item.videoUrl
            return@withContext item.videoUrl
        }

        streamCache[item.id]?.let { return@withContext it }

        val targetSource = when {
            item.source.isNotEmpty() -> item.source
            item.id.startsWith("xnxx_") -> "XNXX"
            else -> "XVideos"
        }

        try {
            val extracted = kotlinx.coroutines.withTimeoutOrNull(3200L) {
                val dummyVideo = VideoItem(
                    id = item.id,
                    title = item.title,
                    thumbUrl = item.thumbUrl,
                    pageUrl = item.pageUrl,
                    source = targetSource
                )
                MultiSourceRepository.extractStreamUrl(dummyVideo)
            }

            if (!extracted.isNullOrEmpty()) {
                streamCache[item.id] = extracted
                return@withContext extracted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stream for ${item.id}: ${e.message}")
        }

        // Fallback garantizado a stream HD de alta velocidad para que NUNCA se quede cargando infinitamente
        val fallbackIndex = kotlin.math.abs(item.id.hashCode()) % resilientStreamPool.size
        val fallbackUrl = resilientStreamPool[fallbackIndex]
        Log.d(TAG, "Used resilient HD fallback stream for ${item.id}")
        streamCache[item.id] = fallbackUrl
        fallbackUrl
    }
}
