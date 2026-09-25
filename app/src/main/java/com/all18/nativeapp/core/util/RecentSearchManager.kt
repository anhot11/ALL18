package com.all18.nativeapp.core.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Gestor ligero y persistente del historial de búsquedas del usuario.
 * Garantiza acceso instantáneo (O(1)) con StateFlow y almacenamiento en SharedPreferences.
 */
object RecentSearchManager {
    private const val PREFS_NAME = "recent_searches_prefs"
    private const val KEY_SEARCHES = "recent_searches_list"
    private const val MAX_HISTORY = 12

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val prefs = getPrefs(context)
        val raw = prefs.getString(KEY_SEARCHES, null)
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val s = array.optString(i)
                    if (s.isNotBlank() && !list.contains(s)) {
                        list.add(s)
                    }
                }
                _recentSearches.value = list
            } catch (_: Exception) {
                _recentSearches.value = emptyList()
            }
        }
        initialized = true
    }

    fun addSearch(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || trimmed.length < 2) return
        init(context)
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val updated = current.take(MAX_HISTORY)
        _recentSearches.value = updated
        save(context, updated)
    }

    fun removeSearch(context: Context, query: String) {
        init(context)
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        _recentSearches.value = current
        save(context, current)
    }

    fun clearAll(context: Context) {
        _recentSearches.value = emptyList()
        save(context, emptyList())
    }

    private fun save(context: Context, list: List<String>) {
        val prefs = getPrefs(context)
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY_SEARCHES, array.toString()).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
