package com.all18.nativeapp.core.model

/**
 * Genera una cantidad realista y variada de guardados/favoritos basada en el ID
 */
fun generateBookmarksCount(id: String): Int {
    val h = kotlin.math.abs(id.hashCode())
    return when (h % 7) {
        0 -> 320 + (h % 480)                   // 320 - 799 (ej. 540)
        1 -> 1200 + (h % 800)                  // 1.2k - 1.9k (ej. 1.5k)
        2 -> 2500 + (h % 1500)                 // 2.5k - 3.9k (ej. 3.4k)
        3 -> 4800 + (h % 3200)                 // 4.8k - 7.9k (ej. 6.2k)
        4 -> 12000 + (h % 15000)               // 12k - 26k (ej. 18.5k)
        5 -> 850 + (h % 900)                   // 850 - 1749 (ej. 1.1k)
        else -> 3100 + (h % 2000)              // 3.1k - 5k (ej. 4.3k)
    }
}

/**
 * Genera una cantidad realista y variada de compartidos basada en el ID
 */
fun generateSharesCount(id: String): Int {
    val h = kotlin.math.abs(id.hashCode() / 7)
    return when (h % 7) {
        0 -> 45 + (h % 180)                    // 45 - 224 (ej. 132)
        1 -> 350 + (h % 450)                   // 350 - 799 (ej. 620)
        2 -> 1100 + (h % 900)                  // 1.1k - 1.9k (ej. 1.4k)
        3 -> 2400 + (h % 1800)                 // 2.4k - 4.1k (ej. 3.2k)
        4 -> 5600 + (h % 4500)                 // 5.6k - 10k (ej. 8.1k)
        5 -> 780 + (h % 600)                   // 780 - 1379 (ej. 1.2k)
        else -> 1800 + (h % 1200)              // 1.8k - 2.9k (ej. 2.1k)
    }
}

/**
 * Representa una publicación vertical para la pestaña TikTok (Video o Foto)
 */
data class TikTokItem(
    val id: String,
    val title: String,
    val description: String,
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String,
    val musicTitle: String = "Sonido original - All18",
    val thumbUrl: String,
    val videoUrl: String = "",
    val pageUrl: String = "",
    val isPhotoPost: Boolean = false,
    val photoUrls: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val likesCount: Int = 12400,
    val commentsCount: Int = 348,
    val sharesCount: Int = generateSharesCount(id),
    val bookmarksCount: Int = generateBookmarksCount(id),
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val isFollowing: Boolean = false,
    val source: String = ""
)

/**
 * Representa una publicación social estilo Twitter/𝕏
 */
data class XPostItem(
    val id: String,
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String,
    val isVerified: Boolean = true,
    val timeAgo: String = "2h",
    val content: String,
    val images: List<String> = emptyList(),
    val videoItem: VideoItem? = null,
    val likesCount: Int = 5420,
    val repostsCount: Int = 820,
    val commentsCount: Int = 142,
    val viewsCount: String = "45.2K",
    val isLiked: Boolean = false,
    val isReposted: Boolean = false,
    val isBookmarked: Boolean = false
)

/**
 * Representa el perfil verificado de una creadora/modelo con su avatar y galería
 */
data class CreatorProfile(
    val name: String,
    val handle: String,
    val avatarUrl: String,
    val source: String = "XNXX",
    val profileUrl: String = "",
    val bio: String = "",
    val gallery: List<String> = emptyList()
)

/**
 * Representa una sala de transmisión en vivo (Modo LIVE TikTok)
 */
data class TikTokLiveItem(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val snapshotUrl: String,
    val streamUrl: String = "",
    val viewersCount: Int = 1200,
    val likesCount: Int = (1200 * 3.2).toInt(),
    val roomSubject: String = "",
    val categoryBadge: String = "🔥 Clasificación diaria",
    val isFollowing: Boolean = false,
    val tags: List<String> = emptyList(),
    val source: String = "Chaturbate"
)


