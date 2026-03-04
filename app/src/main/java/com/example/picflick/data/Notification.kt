package com.example.picflick.data

import com.google.firebase.Timestamp

/**
 * Data class representing a notification for a user
 */
data class Notification(
    val id: String = "",
    val userId: String = "",           // User who receives the notification
    val senderId: String = "",         // User who triggered the notification
    val senderName: String = "",       // Display name of sender
    val senderPhotoUrl: String = "",   // Photo URL of sender
    val type: NotificationType = NotificationType.LIKE,
    val title: String = "",
    val message: String = "",
    val flickId: String? = null,       // Related photo (if any)
    val flickImageUrl: String? = null,   // Thumbnail of related photo
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class NotificationType {
    LIKE,           // Someone liked your photo
    REACTION,       // Someone reacted to your photo (emoji reactions)
    COMMENT,        // Someone commented on your photo
    FOLLOW,         // Someone followed you
    FRIEND_REQUEST, // Someone sent friend request
    MESSAGE,        // Someone sent you a message
    PHOTO_ADDED,    // Someone you follow added a photo
    MENTION,        // Someone mentioned you in comment
    STREAK_REMINDER,// Reminder to maintain streak
    ACHIEVEMENT,    // Achievement unlocked
    SYSTEM          // System announcements
}
