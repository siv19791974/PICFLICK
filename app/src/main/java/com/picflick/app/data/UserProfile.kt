package com.picflick.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.picflick.app.Constants
import com.picflick.app.util.CostControlManager

/**
 * Data class representing a user profile in the PicFlick app
 */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val displayNameLower: String = "",
    val photoUrl: String = "",
    val phoneNumber: String = "", // Auto-detected from device SIM
    val bio: String = "",
    val followers: List<String> = emptyList(),
    val following: List<String> = emptyList(),
    val pendingFollowRequests: List<String> = emptyList(), // Users who want to follow this user (pending approval)
    val sentFollowRequests: List<String> = emptyList(), // Users this user has requested to follow (waiting for approval)
    val blockedUsers: List<String> = emptyList(), // Users this person has blocked
    val mutedUsers: Map<String, Long> = emptyMap(), // userId -> mute expiry epoch millis (Long.MAX_VALUE = forever)
    val fcmToken: String = "", // Firebase Cloud Messaging token for push notifications
    val notificationPreferences: NotificationPreferences = NotificationPreferences(), // Notification settings
    val totalLikes: Int = 0,
    val totalViews: Int = 0,
    // Subscription and Storage Management Fields
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
    val subscriptionActive: Boolean = false,
    val autoRenewing: Boolean = false,
    @get:PropertyName("tier")
    @field:PropertyName("tier")
    val legacyTier: SubscriptionTier? = null, // Legacy/mirrored field kept for backward compatibility
    val subscriptionExpiryDate: Any? = 0L, // Supports Firestore Timestamp or legacy epoch millis
    val storageUsedBytes: Long = 0, // Current storage used in bytes
    val totalPhotos: Int = 0, // Total photos uploaded
    val dailyUploadsToday: Int = 0, // Uploads used today
    val lastUploadResetDate: String = "", // Date string for tracking daily resets
    val isFounder: Boolean = false, // Early tester status
    val defaultPrivacy: String = "friends", // Default privacy for new posts: "friends" or "public"
    val joinedAt: Long = System.currentTimeMillis(),
    // Mythic Monthly Draw fields
    val mythicDrawHistory: List<Map<String, Any>> = emptyList(), // List of {monthKey, place, prizeLabel, streak, wonAt}
    val mythicCrown: String = "", // "gold", "silver", "bronze", or ""
    val mythicCrownExpiry: Any? = 0L, // Epoch millis when crown expires
    val mythicWinnerBanner: String = "", // e.g. "Mythic Draw Winner — 2026-05"
    val mythicWinnerBannerExpiry: Any? = 0L, // Epoch millis when banner expires
    val mythicContenderCount: Int = 0, // How many draws entered
    val mythicLastWonMonthKey: String = "", // Last month they won (for repeat protection display)
    // Mythic tier badge fields
    val mythicConsecutiveMonths: Int = 0, // Consecutive months entered into draw
    val mythicTier: String = "", // "bronze", "silver", "gold", "diamond", or ""
    val mythicTierUpdatedAt: Any? = 0L, // When tier was last updated
    // Mythic upload boost (replaces storage bonus)
    val mythicUploadBoostAmount: Int = 0, // Extra daily uploads granted
    val mythicUploadBoostExpiry: Any? = 0L, // Epoch millis when boost expires
    // Mythic Champion — permanent badge for ULTRA winners
    val mythicChampion: Boolean = false,
    val mythicChampionMonth: String = "",
    // Streak Recovery (free, 1 per month)
    val streakRecoveryAvailable: Boolean = false, // Set by Cloud Function when streak breaks
    val streakRecoveryValue: Int = 0, // Streak count at time of break
    val streakRecoveryUsedMonth: String = "", // "2026-05" — tracks monthly limit
    // Timezone and country for localized push + leaderboard flags
    val timezoneOffset: Int = 0, // UTC offset in hours (e.g., 0, +1, -5, +8)
    val countryCode: String = "", // ISO country code (e.g., "GB", "US", "FR") for flag emoji
) {
    /**
     * Check if user has a pending follow request from another user
     */
    fun hasPendingFollowRequest(fromUserId: String): Boolean = pendingFollowRequests.contains(fromUserId)
    
    /**
     * Check if user has sent a follow request to another user
     */
    fun hasSentFollowRequest(toUserId: String): Boolean = sentFollowRequests.contains(toUserId)
    
    /**
     * Get mutual followers count with another user
     */
    fun getMutualFollowers(otherUser: UserProfile): Int {
        return followers.intersect(otherUser.followers.toSet()).size
    }
    
    /**
     * Check if this user is following another user
     */
    fun isFollowing(userId: String): Boolean = following.contains(userId)
    
    /**
     * Check if this user is followed by another user
     */
    fun isFollowedBy(userId: String): Boolean = followers.contains(userId)
    
    /**
     * Check if users are mutual friends (follow each other)
     */
    fun isMutualFriend(userId: String): Boolean = following.contains(userId) && followers.contains(userId)
    /**
     * Get the current subscription tier (with legacy fallback).
     * If the freeTierBypass kill-switch is active, always returns PRO for UI testing.
     */
    fun getEffectiveTier(): SubscriptionTier {
        if (CostControlManager.isEnabled(Constants.FeatureFlags.FREE_TIER_BYPASS)) {
            return SubscriptionTier.PRO
        }
        if (!subscriptionActive) return SubscriptionTier.FREE

        return when {
            subscriptionTier != SubscriptionTier.FREE -> subscriptionTier
            legacyTier != null -> legacyTier
            else -> SubscriptionTier.FREE
        }
    }
    
    /**
     * Get effective daily upload limit including Mythic boost
     */
    fun getEffectiveDailyUploadLimit(): Int {
        val base = getEffectiveTier().getDailyUploadLimit()
        if (base == Int.MAX_VALUE) return Int.MAX_VALUE // ULTRA

        val boost = mythicUploadBoostAmount
        if (boost <= 0) return base

        val expiryMillis = when (val expiry = mythicUploadBoostExpiry) {
            null -> 0L
            is Long -> expiry
            is Int -> expiry.toLong()
            is Double -> expiry.toLong()
            is Timestamp -> expiry.toDate().time
            is String -> expiry.toLongOrNull() ?: 0L
            else -> 0L
        }

        return if (expiryMillis > System.currentTimeMillis()) base + boost else base
    }

    /**
     * Check if user has active Mythic upload boost
     */
    fun hasActiveUploadBoost(): Boolean {
        val boost = mythicUploadBoostAmount
        if (boost <= 0) return false
        val expiryMillis = when (val expiry = mythicUploadBoostExpiry) {
            null -> 0L
            is Long -> expiry
            is Int -> expiry.toLong()
            is Double -> expiry.toLong()
            is Timestamp -> expiry.toDate().time
            is String -> expiry.toLongOrNull() ?: 0L
            else -> 0L
        }
        return expiryMillis > System.currentTimeMillis()
    }

    /**
     * Calculate storage used in GB
     */
    fun calculateStorageUsedGB(): Float = (storageUsedBytes / (1024f * 1024f * 1024f))
    
    /**
     * Check if user has active subscription (not expired)
     */
    fun hasActiveSubscription(): Boolean {
        if (isFounder) return true // Founders always have access
        if (getEffectiveTier() == SubscriptionTier.FREE) return false

        val expiryMillis = when (val expiry = subscriptionExpiryDate) {
            null -> 0L
            is Long -> expiry
            is Int -> expiry.toLong()
            is Double -> expiry.toLong()
            is Timestamp -> expiry.toDate().time
            is String -> expiry.toLongOrNull() ?: 0L
            else -> 0L
        }

        if (expiryMillis == 0L) return true // Lifetime/unspecified
        return System.currentTimeMillis() < expiryMillis
    }
}

