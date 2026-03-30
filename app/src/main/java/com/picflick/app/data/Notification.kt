package com.picflick.app.data

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
    val commentId: String? = null,       // Related comment id (if any)
    val commentPreview: String? = null,  // Optional comment text preview
    val reactionEmoji: String? = null,   // Related reaction emoji
    val chatId: String? = null,          // Related chat (for MESSAGE notifications)
    val groupId: String? = null,         // Related group (for GROUP_INVITE)
    val groupName: String? = null,       // Group display name
    val inviteId: String? = null,        // Group invite id (for accept/decline)
    val targetScreen: String? = null,    // Optional destination hint for onboarding/system notifications
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class NotificationType {
    LIKE,           // Someone liked your photo
    REACTION,       // Someone reacted to your photo (emoji reactions)
    COMMENT,        // Someone commented on your photo
    FOLLOW,         // Someone followed you
    FOLLOW_ACCEPTED, // Your follow/friend request was accepted ("You are now connected")
    FRIEND_REQUEST, // Someone sent friend request
    MESSAGE,        // Someone sent you a message
    PHOTO_ADDED,    // Someone you follow added a photo
    PROFILE_PHOTO_UPDATED, // A friend updated their profile photo
    MENTION,        // Someone mentioned you in comment
    STREAK_REMINDER,// Reminder to maintain streak
    ACHIEVEMENT,    // Achievement unlocked
    GROUP_INVITE,   // Invitation to join a shared group
    SYSTEM          // System announcements
}

/**
 * Safely parse notification type from string value
 */
fun parseNotificationType(value: String?): NotificationType {
    val normalized = value?.uppercase()
    return when (normalized) {
        "COMMENT_LIKE" -> NotificationType.REACTION
        else -> try {
            normalized?.let { NotificationType.valueOf(it) } ?: NotificationType.LIKE
        } catch (_: IllegalArgumentException) {
            NotificationType.LIKE
        }
    }
}

