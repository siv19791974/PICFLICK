package com.picflick.app.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

/**
 * Opens PicFlick's Play Store listing for reliable manual ratings from Settings.
 */
object AppReviewHelper {

    fun requestReview(activity: Activity) {
        val packageName = activity.packageName
        val playStoreIntent = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$packageName".toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }

        try {
            activity.startActivity(playStoreIntent)
        } catch (e: ActivityNotFoundException) {
            Log.w("AppReviewHelper", "Google Play app unavailable; opening browser", e)
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri()
            )
            activity.startActivity(browserIntent)
        }
    }
}
