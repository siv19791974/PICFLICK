package com.picflick.app.utils

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Firebase Analytics helper for tracking user engagement
 * Centralized location for all analytics events
 */
object Analytics {

    private val firebaseAnalytics: FirebaseAnalytics by lazy {
        Firebase.analytics
    }

    // ==================== EVENT NAMES ====================
    object Events {
        const val PHOTO_UPLOADED = "photo_uploaded"
        const val PHOTO_VIEWED = "photo_viewed"
        const val REACTION_SENT = "reaction_sent"
        const val FOLLOW = "follow"
        const val UNFOLLOW = "unfollow"
        const val FRIEND_REQUEST_SENT = "friend_request_sent"
        const val FRIEND_REQUEST_ACCEPTED = "friend_request_accepted"
        const val MESSAGE_SENT = "message_sent"
        const val PHOTO_SHARED = "photo_shared"
        const val SCREEN_VIEW = "screen_view"
        const val LOGIN = "login"
        const val SIGN_UP = "sign_up"
        const val SEARCH = "search"
        const val SETTINGS_CHANGED = "settings_changed"
        const val ERROR_OCCURRED = "error_occurred"
    }

    // ==================== PARAMETERS ====================
    object Params {
        const val SOURCE = "source"
        const val REACTION_TYPE = "reaction_type"
        const val SCREEN_NAME = "screen_name"
        const val SEARCH_QUERY = "search_query"
        const val ERROR_MESSAGE = "error_message"
        const val PHOTO_PRIVACY = "photo_privacy"
        const val MESSAGE_TYPE = "message_type"
        const val SUBSCRIPTION_TIER = "subscription_tier"
    }

    // ==================== TRACKING METHODS ====================

    /**
     * Track photo upload
     * @param source Where the upload was triggered from (camera, gallery)
     * @param privacy Photo privacy setting (public, friends)
     */
    fun trackPhotoUploaded(source: String, privacy: String) {
        logEvent(Events.PHOTO_UPLOADED) {
            putString(Params.SOURCE, source)
            putString(Params.PHOTO_PRIVACY, privacy)
        }
    }

    /**
     * Track photo view
     * @param source Which screen the photo was viewed from
     */
    fun trackPhotoViewed(source: String) {
        logEvent(Events.PHOTO_VIEWED) {
            putString(Params.SOURCE, source)
        }
    }

    /**
     * Track reaction sent
     * @param reactionType Type of reaction (LIKE, LOVE, FIRE, etc.)
     */
    fun trackReactionSent(reactionType: String) {
        logEvent(Events.REACTION_SENT) {
            putString(Params.REACTION_TYPE, reactionType)
        }
    }

    /**
     * Track follow action
     */
    fun trackFollow() {
        logEvent(Events.FOLLOW)
    }

    /**
     * Track unfollow action
     */
    fun trackUnfollow() {
        logEvent(Events.UNFOLLOW)
    }

    /**
     * Track friend request sent
     */
    fun trackFriendRequestSent() {
        logEvent(Events.FRIEND_REQUEST_SENT)
    }

    /**
     * Track friend request accepted
     */
    fun trackFriendRequestAccepted() {
        logEvent(Events.FRIEND_REQUEST_ACCEPTED)
    }

    /**
     * Track message sent
     * @param messageType Type of message (text, photo)
     */
    fun trackMessageSent(messageType: String) {
        logEvent(Events.MESSAGE_SENT) {
            putString(Params.MESSAGE_TYPE, messageType)
        }
    }

    /**
     * Track photo shared
     */
    fun trackPhotoShared() {
        logEvent(Events.PHOTO_SHARED)
    }

    /**
     * Track screen view
     * @param screenName Name of the screen viewed
     */
    fun trackScreenView(screenName: String) {
        logEvent(Events.SCREEN_VIEW) {
            putString(Params.SCREEN_NAME, screenName)
        }
    }

    /**
     * Track login
     */
    fun trackLogin() {
        logEvent(Events.LOGIN)
    }

    /**
     * Track sign up
     */
    fun trackSignUp() {
        logEvent(Events.SIGN_UP)
    }

    /**
     * Track search
     * @param query Search query (can be empty for empty searches)
     */
    fun trackSearch(query: String) {
        logEvent(Events.SEARCH) {
            putString(Params.SEARCH_QUERY, query.take(100)) // Limit length
        }
    }

    /**
     * Track settings change
     */
    fun trackSettingsChanged() {
        logEvent(Events.SETTINGS_CHANGED)
    }

    /**
     * Track error occurrence
     * @param errorMessage Error message or type
     */
    fun trackError(errorMessage: String) {
        logEvent(Events.ERROR_OCCURRED) {
            putString(Params.ERROR_MESSAGE, errorMessage.take(100)) // Limit length
        }
    }

    /**
     * Set user properties for segmentation
     * @param subscriptionTier User's subscription tier
     */
    fun setUserProperties(subscriptionTier: String) {
        firebaseAnalytics.setUserProperty(Params.SUBSCRIPTION_TIER, subscriptionTier)
    }

    // ==================== HELPER METHODS ====================

    private inline fun logEvent(eventName: String, block: Bundle.() -> Unit = {}) {
        val params = Bundle().apply(block)
        firebaseAnalytics.logEvent(eventName, params)
    }

    private fun logEvent(eventName: String) {
        firebaseAnalytics.logEvent(eventName, null)
    }
}
