package com.all18.nativeapp.core.repository

import android.util.Log
import com.all18.nativeapp.core.extractor.PornPicsExtractor
import com.all18.nativeapp.core.extractor.XHamsterPhotosExtractor
import com.all18.nativeapp.core.extractor.XNXXPhotosExtractor
import com.all18.nativeapp.core.extractor.XVideosModelsExtractor
import com.all18.nativeapp.core.model.CreatorProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

object PhotoRepository {
    private const val TAG = "PhotoRepository"

    private val cachedCreators = CopyOnWriteArrayList<CreatorProfile>()
    private val cachedPhotos = CopyOnWriteArrayList<String>()

    private val fallbackCreators = listOf(
        CreatorProfile(
            name = "Sophia Lux",
            handle = "@sophia_lux",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop",
            source = "VIP"
        ),
        CreatorProfile(
            name = "Luna Martinez",
            handle = "@luna_martinez",
            avatarUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop",
            source = "VIP"
        ),
        CreatorProfile(
            name = "Valeria Rios",
            handle = "@valerios_vip",
            avatarUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop",
            source = "VIP"
        ),
        CreatorProfile(
            name = "Camila Star",
            handle = "@camilastar_xxx",
            avatarUrl = "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?w=400&auto=format&fit=crop",
            source = "VIP"
        ),
        CreatorProfile(
            name = "Elena Vega",
            handle = "@elena_vega",
            avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400&auto=format&fit=crop",
            source = "VIP"
        )
    )

    private val fallbackPhotos = listOf(
        // Verticales (1080p Portrait)
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=1920&q=90&auto=format&fit=crop",
        // Horizontales (1080p Landscape)
        "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1469334031218-e382a71b716b?w=1920&q=90&auto=format&fit=crop",
        "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=1920&q=90&auto=format&fit=crop"
    )

    suspend fun preloadData() = withContext(Dispatchers.IO) {
        if (cachedCreators.isNotEmpty() && cachedPhotos.isNotEmpty()) return@withContext

        val xnxxDeferred = async { try { XNXXPhotosExtractor.getModelPhotos() } catch (_: Exception) { emptyList() } }
        val xvDeferred = async { try { XVideosModelsExtractor.getModels() } catch (_: Exception) { emptyList() } }
        val xhDeferred = async { try { XHamsterPhotosExtractor.getPhotos() } catch (_: Exception) { emptyList() } }
        val ppDeferred = async { try { PornPicsExtractor.getPhotos() } catch (_: Exception) { emptyList() } }
        val phDeferred = async { try { com.all18.nativeapp.core.extractor.PicHunterExtractor.getPhotos() } catch (_: Exception) { emptyList() } }

        val xnxxModels = xnxxDeferred.await()
        val xvModels = xvDeferred.await()
        val xhPhotos = xhDeferred.await()
        val ppPhotos = ppDeferred.await()
        val phPhotos = phDeferred.await()

        val newCreators = mutableListOf<CreatorProfile>()
        val newPhotos = mutableListOf<String>()

        // 1. Add XNXX models (with their 10+ photo galleries)
        for (m in xnxxModels) {
            newCreators.add(
                CreatorProfile(
                    name = m.modelName,
                    handle = m.modelHandle,
                    avatarUrl = m.photoUrl,
                    source = "XNXX",
                    gallery = m.gallery
                )
            )
            for (img in m.gallery) {
                if (!newPhotos.contains(img)) newPhotos.add(img)
            }
        }

        // 2. Add XVideos models
        for (m in xvModels) {
            newCreators.add(
                CreatorProfile(
                    name = m.name,
                    handle = m.handle,
                    avatarUrl = m.avatarUrl,
                    source = "XVideos",
                    profileUrl = m.profileUrl,
                    bio = m.info
                )
            )
            if (!newPhotos.contains(m.avatarUrl)) newPhotos.add(m.avatarUrl)
        }

        // 3. Add XHamster 1000px photos
        for (p in xhPhotos) {
            if (!newPhotos.contains(p.photoUrl)) newPhotos.add(p.photoUrl)
        }

        // 4. Add PornPics photos
        for (p in ppPhotos) {
            if (!newPhotos.contains(p)) newPhotos.add(p)
        }

        // 5. Add PicHunter HD photos
        for (p in phPhotos) {
            if (!newPhotos.contains(p)) newPhotos.add(p)
        }

        if (newCreators.isNotEmpty()) {
            cachedCreators.clear()
            cachedCreators.addAll(newCreators.shuffled())
        }

        if (newPhotos.isNotEmpty()) {
            cachedPhotos.clear()
            cachedPhotos.addAll(newPhotos.shuffled())
        }

        Log.d(TAG, "Preloaded ${cachedCreators.size} creators and ${cachedPhotos.size} photos across XNXX, XVideos, XHamster, PornPics, and PicHunter")
    }

    suspend fun getCreators(): List<CreatorProfile> = withContext(Dispatchers.IO) {
        if (cachedCreators.isEmpty()) {
            preloadData()
        }
        if (cachedCreators.isNotEmpty()) cachedCreators.toList() else fallbackCreators
    }

    suspend fun getAllPhotos(): List<String> = withContext(Dispatchers.IO) {
        if (cachedPhotos.isEmpty()) {
            preloadData()
        }
        if (cachedPhotos.isNotEmpty()) cachedPhotos.toList() else fallbackPhotos
    }

    suspend fun getRandomGallery(count: Int = 1): List<String> = withContext(Dispatchers.IO) {
        val creators = getCreators()
        // Check if any creator has an authentic gallery matching the request
        val creatorsWithGallery = creators.filter { it.gallery.size >= count }
        if (creatorsWithGallery.isNotEmpty() && Random.nextBoolean()) {
            val picked = creatorsWithGallery.random()
            return@withContext picked.gallery.shuffled().take(count)
        }

        val pool = getAllPhotos()
        if (pool.isEmpty()) return@withContext fallbackPhotos.take(count)
        pool.shuffled().take(count)
    }

    suspend fun getRandomCreator(): CreatorProfile = withContext(Dispatchers.IO) {
        val list = getCreators()
        if (list.isNotEmpty()) list.random() else fallbackCreators.random()
    }
}
