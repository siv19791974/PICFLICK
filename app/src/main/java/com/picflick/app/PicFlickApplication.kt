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

        // Capture uncaught exceptions and save for dev feedback inbox
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashReporter.saveCrashReport(this, throwable)
            FirebaseCrashlytics.getInstance().recordException(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
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

/**
 * Crash reporting helper that persists fatal crashes to SharedPreferences
 * so they can be submitted to the dev feedback inbox on next app launch.
 */
object CrashReporter {
    private const val CRASH_PREFS = "picflick_crash_reports"
    private const val KEY_PENDING_CRASH = "pending_crash"
    private const val KEY_CRASH_TIME = "crash_time"
    private const val KEY_CRASH_MESSAGE = "crash_message"

    fun saveCrashReport(context: android.content.Context, throwable: Throwable) {
        try {
            val prefs = context.getSharedPreferences(CRASH_PREFS, android.content.Context.MODE_PRIVATE)
            val stackTrace = android.util.Log.getStackTraceString(throwable)
            prefs.edit()
                .putString(KEY_PENDING_CRASH, stackTrace)
                .putLong(KEY_CRASH_TIME, System.currentTimeMillis())
                .putString(KEY_CRASH_MESSAGE, throwable.message ?: "Unknown error")
                .apply()
        } catch (_: Exception) {
            // Best-effort: don't let crash reporter itself crash
        }
    }

    fun getPendingCrashReport(context: android.content.Context): Triple<String, String, Long>? {
        val prefs = context.getSharedPreferences(CRASH_PREFS, android.content.Context.MODE_PRIVATE)
        val stackTrace = prefs.getString(KEY_PENDING_CRASH, null) ?: return null
        val message = prefs.getString(KEY_CRASH_MESSAGE, "Unknown error") ?: "Unknown error"
        val time = prefs.getLong(KEY_CRASH_TIME, 0)
        return Triple(message, stackTrace, time)
    }

    fun clearPendingCrashReport(context: android.content.Context) {
        context.getSharedPreferences(CRASH_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
