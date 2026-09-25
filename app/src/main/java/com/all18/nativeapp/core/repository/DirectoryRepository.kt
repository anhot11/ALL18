package com.all18.nativeapp.core.repository

import android.content.Context
import android.util.Log
import com.all18.nativeapp.core.model.AdultCategory
import com.all18.nativeapp.core.model.AdultSite
import com.all18.nativeapp.core.model.DirectoryGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Repositorio centralizado para el directorio masivo de 98 categorías y 2,284 sitios.
 * Carga de forma asíncrona y almacena en memoria para consultas instantáneas en toda la app.
 */
object DirectoryRepository {
    private const val TAG = "DirectoryRepository"

    @Volatile
    private var isInitialized = false

    private val categoriesList = mutableListOf<AdultCategory>()
    private val allSitesList = mutableListOf<AdultSite>()

    suspend fun ensureInitialized(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        synchronized(this) {
            if (isInitialized) return@withContext
            try {
                val assetManager = context.applicationContext.assets
                val inputStream = assetManager.open("directory.json")
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val jsonString = reader.use { it.readText() }

                val root = JSONObject(jsonString)
                val categoriesArray = root.optJSONArray("categories") ?: return@withContext

                val parsedCats = mutableListOf<AdultCategory>()
                val parsedSites = mutableListOf<AdultSite>()

                for (i in 0 until categoriesArray.length()) {
                    val catObj = categoriesArray.getJSONObject(i)
                    val catId = catObj.optString("id", "")
                    val catTitle = catObj.optString("title", "")
                    val catSlug = catObj.optString("slug", "")
                    val catType = catObj.optString("type", "")
                    val catDesc = catObj.optString("description", "")
                    val catCount = catObj.optInt("site_count", 0)

                    val sitesArray = catObj.optJSONArray("sites")
                    val catSites = mutableListOf<AdultSite>()

                    if (sitesArray != null) {
                        for (j in 0 until sitesArray.length()) {
                            val siteObj = sitesArray.getJSONObject(j)
                            val site = AdultSite(
                                siteId = siteObj.optString("site_id", ""),
                                name = siteObj.optString("name", ""),
                                url = siteObj.optString("url", ""),
                                reviewUrl = siteObj.optString("review_url", ""),
                                description = siteObj.optString("description", ""),
                                categoryId = catId,
                                categoryTitle = catTitle,
                                type = catType
                            )
                            catSites.add(site)
                            parsedSites.add(site)
                        }
                    }

                    parsedCats.add(
                        AdultCategory(
                            id = catId,
                            title = catTitle,
                            slug = catSlug,
                            type = catType,
                            description = catDesc,
                            siteCount = catCount,
                            sites = catSites
                        )
                    )
                }

                categoriesList.clear()
                categoriesList.addAll(parsedCats)

                allSitesList.clear()
                allSitesList.addAll(parsedSites)

                isInitialized = true
                Log.d(TAG, "Directory initialized: ${categoriesList.size} categories, ${allSitesList.size} sites loaded.")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing DirectoryRepository: ${e.message}", e)
            }
        }
    }

    fun getAllCategories(): List<AdultCategory> = categoriesList.toList()

    fun getCategoriesByGroup(group: DirectoryGroup): List<AdultCategory> {
        if (group == DirectoryGroup.ALL) return categoriesList.toList()
        return categoriesList.filter { it.type.equals(group.key, ignoreCase = true) }
    }

    fun getCategoryById(id: String): AdultCategory? {
        return categoriesList.find { it.id == id }
    }

    fun search(query: String, group: DirectoryGroup = DirectoryGroup.ALL): List<AdultSite> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val baseSites = if (group == DirectoryGroup.ALL) {
            allSitesList
        } else {
            allSitesList.filter { it.type.equals(group.key, ignoreCase = true) }
        }

        return baseSites.filter { site ->
            site.name.lowercase().contains(q) ||
            site.url.lowercase().contains(q) ||
            site.description.lowercase().contains(q) ||
            site.categoryTitle.lowercase().contains(q)
        }
    }

    fun getFeaturedCategories(): List<AdultCategory> {
        // Seleccionar categorías estrella de varios tipos
        val featuredSlugs = listOf(
            "top-porn-tube-sites",
            "top-sex-cam-sites",
            "hentai-streaming-sites",
            "best-porn-games",
            "ai-porn-generator-sites-reviews",
            "best-vr-porn-sites",
            "free-onlyfans-accounts",
            "best-nsfw-reddit-sites",
            "top-asian-porn-tube-sites",
            "top-amateur-porn-sites"
        )
        val matches = categoriesList.filter { it.slug in featuredSlugs }
        return if (matches.isNotEmpty()) matches else categoriesList.take(8)
    }

    fun getRandomSites(count: Int = 10, group: DirectoryGroup? = null): List<AdultSite> {
        val pool = if (group == null || group == DirectoryGroup.ALL) {
            allSitesList
        } else {
            allSitesList.filter { it.type.equals(group.key, ignoreCase = true) }
        }
        return pool.shuffled().take(count)
    }

    fun getCounts(): Pair<Int, Int> = Pair(categoriesList.size, allSitesList.size)
}
