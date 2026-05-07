package com.picflick.app.util

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Minimal helper for Google Play In-App Review API.
 * Only supports manual review requests from Settings.
 */
object AppReviewHelper {

    fun requestReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                manager.launchReviewFlow(activity, reviewInfo)
                    .addOnCompleteListener {
                        Log.d("AppReviewHelper", "Review flow completed")
                    }
            } else {
                Log.w("AppReviewHelper", "Review flow request failed", task.exception)
            }
        }
    }
}
