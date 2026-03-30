package com.picflick.app.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * ThemeManager - Manages app theme state (Light/Dark/System mode)
 * Persists user preferences to SharedPreferences
 */
object ThemeManager {
    private const val PREFS_NAME = "picflick_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    enum class ThemeMode { LIGHT, DARK, SYSTEM }

    // Observable state for Compose recomposition
    var themeMode = mutableStateOf(ThemeMode.SYSTEM)
        private set

    // Resolved theme (used by existing screens)
    var isDarkMode = mutableStateOf(false)
        private set

    /**
     * Initialize theme manager - call in MainActivity.onCreate
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val mode = stored?.let {
            try {
                ThemeMode.valueOf(it)
            } catch (_: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }
        } ?: ThemeMode.SYSTEM

        themeMode.value = mode
        isDarkMode.value = resolveIsDark(mode, isSystemDarkMode(context))
    }

    /**
     * Set theme mode and persist
     */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        themeMode.value = mode
        isDarkMode.value = resolveIsDark(mode, isSystemDarkMode(context))
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_THEME_MODE, mode.name)
        }
    }

    /**
     * Keep resolved dark/light in sync with system when mode is SYSTEM
     */
    fun syncWithSystem(systemIsDark: Boolean) {
        if (themeMode.value == ThemeMode.SYSTEM) {
            isDarkMode.value = systemIsDark
        }
    }

    /**
     * Legacy API kept for compatibility with existing code paths
     */
    fun setDarkMode(context: Context, enabled: Boolean) {
        setThemeMode(context, if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    /**
     * Toggle between explicit light and dark (exits SYSTEM mode)
     */
    fun toggle(context: Context): Boolean {
        val newValue = !isDarkMode.value
        setDarkMode(context, newValue)
        return newValue
    }

    private fun resolveIsDark(mode: ThemeMode, systemIsDark: Boolean): Boolean {
        return when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemIsDark
        }
    }

    private fun isSystemDarkMode(context: Context): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }
}
