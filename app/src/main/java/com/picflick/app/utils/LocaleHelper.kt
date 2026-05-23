package com.picflick.app.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "PicFlickLanguagePrefs"
    private const val KEY_LANGUAGE = "selected_language"
    
    // Default is English/device default. Manual language switching is temporarily disabled
    // while the app still has many hardcoded English screens.
    const val DEFAULT_LANGUAGE = ""
    const val LANGUAGE_SWITCHING_ENABLED = false
    
    fun setLocale(context: Context, languageCode: String): Context {
        val locale = if (languageCode.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageCode)
        }
        
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
    
    fun saveLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_LANGUAGE, languageCode) }
    }
    
    fun getSavedLanguage(context: Context): String {
        if (!LANGUAGE_SWITCHING_ENABLED) return DEFAULT_LANGUAGE
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }
    
    fun applySavedLocale(context: Context): Context {
        val savedLanguage = getSavedLanguage(context)
        return setLocale(context, savedLanguage)
    }
    
    fun restartActivity(activity: Activity) {
        activity.recreate()
    }
}
