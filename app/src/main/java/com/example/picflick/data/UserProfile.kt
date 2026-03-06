package com.example.picflick.data

/**
 * Data class representing a user profile in the PicFlick app
 */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val followers: List<String> = emptyList(),
    val following: List<String> = emptyList(),
    val blockedUsers: List<String> = emptyList(), // Users this person has blocked
    val fcmToken: String = "", // Firebase Cloud Messaging token for push notifications
    val notificationPreferences: NotificationPreferences = NotificationPreferences(), // Notification settings
    val totalLikes: Int = 0,
    val totalViews: Int = 0
)
