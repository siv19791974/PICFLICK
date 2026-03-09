package com.picflick.app.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * ThemeManager - Manages app theme state (Light/Dark mode)
 * Persists user preferences to SharedPreferences
 */
object ThemeManager {
    private const val PREFS_NAME = "picflick_theme_prefs"
    private const val KEY_DARK_MODE = "is_dark_mode"
    
    // Observable state for Compose recomposition
    var isDarkMode = mutableStateOf(false)
        private set
    
    /**
     * Initialize theme manager - call in MainActivity.onCreate
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkMode.value = prefs.getBoolean(KEY_DARK_MODE, false)
    }
    
    /**
     * Check if dark mode is enabled
     */
    fun isDarkModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }
    
    /**
     * Set dark mode and persist
     */
    fun setDarkMode(context: Context, enabled: Boolean) {
        isDarkMode.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_DARK_MODE, enabled)
        }
    }
    
    /**
     * Toggle between light and dark
     */
    fun toggle(context: Context): Boolean {
        val newValue = !isDarkMode.value
        setDarkMode(context, newValue)
        return newValue
    }
}
