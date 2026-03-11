package com.picflick.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase

/**
 * PicFlick Application class for global app initialization
 * 
 * Initializes Firebase services:
 * - Firebase Analytics (user engagement tracking)
 * - Firebase Crashlytics (crash reporting)
 */
class PicFlickApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Initialize Analytics
        Firebase.analytics
        
        // Initialize Crashlytics
        FirebaseCrashlytics.getInstance().apply {
            // Enable Crashlytics in production builds
            // In debug builds, data is not sent unless explicitly enabled
            setCrashlyticsCollectionEnabled(true)
            
            // Set custom keys for better crash debugging
            setCustomKey("app_version", BuildConfig.VERSION_NAME)
            setCustomKey("app_code", BuildConfig.VERSION_CODE)
        }
        
        // Log app start for analytics
        Firebase.analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
    }
}
