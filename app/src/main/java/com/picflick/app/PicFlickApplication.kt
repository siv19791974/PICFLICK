package com.picflick.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import okio.Path.Companion.toOkioPath

/**
 * PicFlick Application class for global app initialization
 * 
 * Initializes Firebase services:
 * - Firebase Analytics (user engagement tracking)
 * - Firebase Crashlytics (crash reporting)
 */
class PicFlickApplication : Application(), SingletonImageLoader.Factory {

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
        }

        // Log app start for analytics
        Firebase.analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("picflick_image_cache").toOkioPath())
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB
                    .build()
            }
            .build()
    }
}
