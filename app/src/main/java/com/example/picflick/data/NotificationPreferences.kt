package com.example.picflick.data

/**
 * User notification preferences for push and in-app notifications
 */
data class NotificationPreferences(
    // Push Notifications Master Switch
    val pushNotificationsEnabled: Boolean = true,
    val pushNewPhotos: Boolean = true,
    val pushLikes: Boolean = true,
    val pushReactions: Boolean = true,
    val pushComments: Boolean = true,
    val pushFollows: Boolean = true,
    val pushMessages: Boolean = true,
    val pushMentions: Boolean = true,
    val pushStreakReminders: Boolean = true,
    val pushAchievements: Boolean = true,
    val pushSystemAnnouncements: Boolean = true,

    // In-App Notifications Master Switch
    val inAppNotificationsEnabled: Boolean = true,
    val inAppNewPhotos: Boolean = true,
    val inAppLikes: Boolean = true,
    val inAppReactions: Boolean = true,
    val inAppComments: Boolean = true,
    val inAppFollows: Boolean = true,
    val inAppMessages: Boolean = true,
    val inAppMentions: Boolean = true,
    val inAppStreakReminders: Boolean = true,
    val inAppAchievements: Boolean = true,
    val inAppSystemAnnouncements: Boolean = true,

    // Quiet Hours / Do Not Disturb
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 22, // 10 PM
    val quietHoursEnd: Int = 8,    // 8 AM

    // Notification Display Settings
    val showNotificationPreviews: Boolean = true,  // Show content in notification previews
    val notificationSoundEnabled: Boolean = true,
    val notificationVibrationEnabled: Boolean = true,
    val groupSimilarNotifications: Boolean = true,   // Group multiple likes into one notification

    // Email Notifications (for important stuff)
    val emailNotificationsEnabled: Boolean = false,
    val emailForSecurityAlerts: Boolean = true,    // Always email for security
    val emailForAccountChanges: Boolean = true,
    val emailWeeklyDigest: Boolean = false
) {
    /**
     * Check if push notification should be sent for a specific type
     */
    fun shouldSendPush(type: NotificationType): Boolean {
        if (!pushNotificationsEnabled) return false
        if (isInQuietHours()) return false

        return when (type) {
            NotificationType.LIKE -> pushLikes
            NotificationType.REACTION -> pushReactions
            NotificationType.COMMENT -> pushComments
            NotificationType.FOLLOW -> pushFollows
            NotificationType.FRIEND_REQUEST -> pushFollows
            NotificationType.MESSAGE -> pushMessages
            NotificationType.PHOTO_ADDED -> pushNewPhotos
            NotificationType.MENTION -> pushMentions
            NotificationType.STREAK_REMINDER -> pushStreakReminders
            NotificationType.ACHIEVEMENT -> pushAchievements
            NotificationType.SYSTEM -> pushSystemAnnouncements
        }
    }

    /**
     * Check if in quiet hours (Do Not Disturb)
     */
    fun isInQuietHours(): Boolean {
        if (!quietHoursEnabled) return false

        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        return if (quietHoursStart > quietHoursEnd) {
            // Crosses midnight (e.g., 22:00 to 08:00)
            currentHour >= quietHoursStart || currentHour < quietHoursEnd
        } else {
            // Same day (e.g., 01:00 to 06:00)
            currentHour >= quietHoursStart && currentHour < quietHoursEnd
        }
    }

    /**
     * Check if in-app notification should be shown for a specific type
     */
    fun shouldShowInApp(type: NotificationType): Boolean {
        if (!inAppNotificationsEnabled) return false

        return when (type) {
            NotificationType.LIKE -> inAppLikes
            NotificationType.REACTION -> inAppReactions
            NotificationType.COMMENT -> inAppComments
            NotificationType.FOLLOW -> inAppFollows
            NotificationType.FRIEND_REQUEST -> inAppFollows
            NotificationType.MESSAGE -> inAppMessages
            NotificationType.PHOTO_ADDED -> inAppNewPhotos
            NotificationType.MENTION -> inAppMentions
            NotificationType.STREAK_REMINDER -> inAppStreakReminders
            NotificationType.ACHIEVEMENT -> inAppAchievements
            NotificationType.SYSTEM -> inAppSystemAnnouncements
        }
    }
}
