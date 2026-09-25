package com.all18.nativeapp.core.repository

import com.all18.nativeapp.core.model.VideoItem
import com.all18.nativeapp.core.model.XPostItem
import com.all18.nativeapp.core.util.XWordDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

object XFeedRepository {
    // Banco extenso de publicaciones cortas, naturales y directas al estilo Twitter / X
    private val shortTexts = listOf(
        "Buenos días a todos ☀️",
        "Nuevo contenido disponible hoy 🔥",
        "¿Qué opinan de este look? 👀✨",
        "Viernes de estrenos 🖤",
        "Feeling cute today 💋",
        "Detrás de cámaras 🎬✨",
        "Directo en mi bio 🔗💕",
        "Buenas noches mis amores 🌙",
        "Spicy mood tonight 😈",
        "Casual Tuesday ☕️",
        "Un adelanto de lo que se viene... 📸",
        "Selfie antes de empezar a grabar 💄",
        "¿1, 2 o 3? ¿Cuál prefieren? 👇",
        "Golden hour magic 🌅✨",
        "Ready for the weekend 🍾",
        "Late night thoughts 💭",
        "Piscina time 🏊‍♀️☀️",
        "Sweet dreams a todos 💖",
        "Simplemente yo ✨",
        "Besos para todos 💋",
        "Outfit de hoy 👗🔥",
        "¿Quién está despierto a esta hora? 👀",
        "Nuevo set de fotos listo 📸",
        "Amando los días de sol ☀️🌴",
        "No olviden dejar su like ❤️",
        "Backstage de hoy 🎬",
        "Recién salida de sesión ✨",
        "Vibra positiva siempre 🌸",
        "Les tengo una sorpresa pronto 🎁",
        "Feliz inicio de semana 🥰"
    )

    // Publicaciones largas reservadas estrictamente para el 1% de los posts que realmente requieren "Mostrar más"
    private val rareLongTexts = listOf(
        "Hoy quiero tomarme un momento para agradecerles a todos por el apoyo incondicional que le dan a mi contenido cada día. Esta última sesión de fotos en exteriores fue un reto enorme por el clima y la iluminación, pero el equipo logró capturar planos cinematográficos únicos que superaron todas mis expectativas. Ya pueden disfrutar de la galería completa en máxima resolución en mi perfil. ¿Cuál fue su toma favorita del set? Los leo a todos en comentarios! 💖🎬✨ #all18 #exclusive #behindthescenes #vip",
        "Terminando una semana intensa de grabaciones y viajes por fin de vuelta en el estudio. Estuvimos produciendo más de diez escenas exclusivas con calidad 4K UHD y lencería diseñada a medida para este lanzamiento especial. No se pierdan las sorpresas que tenemos preparadas para este fin de semana, recuerden activar las notificaciones y guardar este post para no perderse nada del estreno oficial! 🔥💋 #premiere #photoshoot #newdrop #model"
    )

    suspend fun getFeed(page: Int): List<XPostItem> = withContext(Dispatchers.IO) {
        // Fetch real creators and verified models
        val creators = PhotoRepository.getCreators()
        val allPhotos = PhotoRepository.getAllPhotos()

        // Fetch videos from tube platforms
        val videos = try {
            MultiSourceRepository.getFeed("Todas", page, "verificados")
        } catch (e: Exception) {
            emptyList<VideoItem>()
        }

        val items = mutableListOf<XPostItem>()
        for (i in 0 until 20) {
            val globalIndex = page * 20 + i

            val creator = creators.getOrNull((i + page * 5) % kotlin.math.max(1, creators.size))
                ?: PhotoRepository.getRandomCreator()

            // 50% videos, 50% fotos para abundancia de contenido de video
            val isVideoPost = (i % 2 == 0) && videos.isNotEmpty()
            val attachedVideo = if (isVideoPost) videos.getOrNull((i / 2) % videos.size) else null

            // Assign scraped photos
            val photos = if (!isVideoPost) {
                val photoCount = if (i % 2 == 0) 1 else Random.nextInt(2, 5)
                if (creator.gallery.size >= photoCount) {
                    creator.gallery.shuffled().take(photoCount)
                } else {
                    PhotoRepository.getRandomGallery(photoCount)
                }
            } else {
                emptyList()
            }

            val avatarUrl = creator.avatarUrl.ifEmpty {
                if (allPhotos.isNotEmpty()) allPhotos[(i * 3 + page) % allPhotos.size]
                else "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop"
            }

            // 1. REGLA DEL 1%: Solo el 1% de las publicaciones tienen texto largo que realmente necesite 'Mostrar más'
            val isLongPost = (globalIndex % 100 == 7)

            // 2. REGLA DEL 10%: El 10% de las publicaciones usa una palabra de la lista de +1k palabras
            // seleccionada aleatoriamente o dependiendo del carácter/hash de su foto
            val isVocabPost = !isLongPost && (globalIndex % 10 == 3)

            val text = when {
                isLongPost -> {
                    rareLongTexts[(globalIndex / 100) % rareLongTexts.size]
                }
                isVocabPost -> {
                    val referencePhoto = photos.firstOrNull() ?: avatarUrl
                    val dictWord = XWordDictionary.getWordFromPhoto(referencePhoto, salt = globalIndex)
                    when (globalIndex % 6) {
                        0 -> "Vibra de hoy: #$dictWord ✨"
                        1 -> "Totalmente $dictWord 🖤"
                        2 -> "Mood: $dictWord 🔥"
                        3 -> "Hoy me siento muy $dictWord 💋"
                        4 -> "Simplemente #$dictWord ✨"
                        else -> "Definición de esta sesión: $dictWord 📸"
                    }
                }
                else -> {
                    // Publicaciones con texto corto auténtico y variado
                    shortTexts[(globalIndex + i * 3) % shortTexts.size]
                }
            }

            items.add(
                XPostItem(
                    id = "x_post_${page}_$i",
                    authorName = creator.name,
                    authorHandle = creator.handle,
                    authorAvatarUrl = avatarUrl,
                    isVerified = true,
                    timeAgo = "${(i + 1) * 2}h",
                    content = text,
                    images = photos,
                    videoItem = attachedVideo,
                    likesCount = 3400 + i * 850 + Random.nextInt(100, 999),
                    repostsCount = 420 + i * 110 + Random.nextInt(10, 90),
                    commentsCount = 180 + i * 45 + Random.nextInt(5, 50),
                    viewsCount = "${18 + i * 5}.${i + 2}K"
                )
            )
        }
        items
    }
}

