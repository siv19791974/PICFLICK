package com.picflick.app.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "PicFlickLanguagePrefs"
    private const val KEY_LANGUAGE = "selected_language"
    
    // Default is English (device language or "en")
    const val DEFAULT_LANGUAGE = ""
    
    // Supported languages
    val SUPPORTED_LANGUAGES = listOf(
        "" to "English (Device Default)",
        "ar" to "العربية",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "zh" to "中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "pt" to "Português",
        "sq" to "Shqip",
        "hi" to "हिन्दी"
    )
    
    fun setLocale(context: Context, languageCode: String): Context {
        val locale = if (languageCode.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(languageCode)
        }
        
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
    
    fun saveLanguage(context: Context, languageCode: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }
    
    fun getSavedLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }
    
    fun applySavedLocale(context: Context): Context {
        val savedLanguage = getSavedLanguage(context)
        return setLocale(context, savedLanguage)
    }
    
    fun restartActivity(activity: Activity) {
        val intent = activity.intent
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.finish()
        activity.startActivity(intent)
    }
}
