package com.example.picflick.data

/**
 * Data class representing a user profile in the PicFlick app
 */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val phoneNumber: String = "", // Auto-detected from device SIM
    val bio: String = "",
    val followers: List<String> = emptyList(),
    val following: List<String> = emptyList(),
    val blockedUsers: List<String> = emptyList(), // Users this person has blocked
    val fcmToken: String = "", // Firebase Cloud Messaging token for push notifications
    val notificationPreferences: NotificationPreferences = NotificationPreferences(), // Notification settings
    val totalLikes: Int = 0,
    val totalViews: Int = 0,
    // Subscription and Storage Management Fields
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
    val subscriptionExpiryDate: Long = 0, // Timestamp when subscription expires (0 = never/lifetime)
    val storageUsedBytes: Long = 0, // Current storage used in bytes
    val totalPhotos: Int = 0, // Total photos uploaded
    val dailyUploadsToday: Int = 0, // Uploads used today
    val lastUploadResetDate: String = "", // Date string for tracking daily resets
    val isFounder: Boolean = false, // Early tester status
    val joinedAt: Long = System.currentTimeMillis()
)
