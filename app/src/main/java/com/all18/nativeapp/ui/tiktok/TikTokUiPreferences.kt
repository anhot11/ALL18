package com.all18.nativeapp.ui.tiktok

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Modos de interfaz para el reproductor vertical de TikTok
 */
enum class TikTokUiMode {
    DEFAULT_CLEAN, // "2. UI default de tiktok" (limpia, sin comentarios, sin disco/sonido, descripciones compactas, iconos transparentes)
    ACTUAL         // "1. UI actual de tiktok" (completa clásica con comentarios y disco)
}

/**
 * Singleton reactivo para almacenar y sincronizar las preferencias de interfaz de TikTok
 * entre AllTube (menú de 3 barritas) y la pestaña TikTok.
 */
object TikTokUiPreferences {
    private const val PREFS_NAME = "tiktok_ui_prefs"
    private const val KEY_MODE = "tiktok_ui_mode"

    private val _uiMode = MutableStateFlow(TikTokUiMode.DEFAULT_CLEAN)
    val uiMode: StateFlow<TikTokUiMode> = _uiMode.asStateFlow()

    private var isLoaded = false

    fun load(context: Context): TikTokUiMode {
        if (!isLoaded) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_MODE, TikTokUiMode.DEFAULT_CLEAN.name)
            val mode = try {
                TikTokUiMode.valueOf(saved ?: TikTokUiMode.DEFAULT_CLEAN.name)
            } catch (_: Exception) {
                TikTokUiMode.DEFAULT_CLEAN
            }
            _uiMode.value = mode
            isLoaded = true
        }
        return _uiMode.value
    }

    fun setUiMode(context: Context, mode: TikTokUiMode) {
        _uiMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }
}
