package com.all18.nativeapp.core.model

/**
 * Representa un sitio web individual del directorio curado de ThePornDude.
 */
data class AdultSite(
    val siteId: String,
    val name: String,
    val url: String,
    val reviewUrl: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryTitle: String = "",
    val type: String = ""
)

/**
 * Representa una categoría curada con su lista de sitios web.
 */
data class AdultCategory(
    val id: String,
    val title: String,
    val slug: String,
    val type: String,
    val description: String,
    val siteCount: Int,
    val sites: List<AdultSite> = emptyList()
)

/**
 * Grupos funcionales para clasificar y filtrar las 98 categorías y 2,284 sitios.
 */
enum class DirectoryGroup(
    val key: String,
    val displayName: String,
    val subtitle: String
) {
    ALL("all", "Todos", "Catálogo completo (+2,280 sitios)"),
    VIDEO_TUBES("video_tubes", "Tubes de Vídeo", "PornHub, XVideos, xHamster, Eporner"),
    LIVE_CAMS("live_cams", "Cámaras en Vivo", "Webcams gratis, shows en directo"),
    HENTAI_COMICS("hentai_comics_anime", "Hentai & Anime", "Manga, cómic, anime y streaming"),
    AI_TOOLS("ai_tools_generators", "Inteligencia Artificial", "Generadores, chatbots y herramientas"),
    SOCIAL_CREATORS("social_creators", "Redes & Creadores", "OnlyFans gratis, TikTok y Twitter"),
    VR_PORN("vr_porn", "Realidad Virtual", "Vídeos 180° y 360° gratuitos"),
    ADULT_GAMES("adult_games", "Juegos Adultos", "Juegos interactivos y novelas visuales"),
    PHOTOS_GALLERIES("photos_and_galleries", "Fotos & Galerías", "Fotos HD, foros NSFW y GIFs"),
    DATING_CHAT("dating_and_chat", "Citas & Chats", "Chats eróticos y encuentros"),
    PORTALS_DIRECTORIES("portals_and_directories", "Directorios & Stars", "Buscadores, actrices porno y listas");

    companion object {
        fun fromKey(key: String): DirectoryGroup {
            return entries.find { it.key.equals(key, ignoreCase = true) } ?: ALL
        }
    }
}
