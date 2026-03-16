package com.picflick.app.data

/**
 * User notification preferences - simplified unified settings
 * One toggle controls both push and in-app notifications
 */
data class NotificationPreferences(
    // In-app master switch
    val notificationsEnabled: Boolean = true,

    // Push master switch
    val pushNotificationsEnabled: Boolean = true,

    // Unified in-app notification toggles
    val likes: Boolean = true,
    val reactions: Boolean = true,
    val comments: Boolean = true,
    val follows: Boolean = true,
    val messages: Boolean = true,
    val newPhotos: Boolean = true,
    val mentions: Boolean = true,
    val streakReminders: Boolean = true,
    val achievements: Boolean = true,
    val systemAnnouncements: Boolean = true,

    // Push notification type toggles
    val pushLikes: Boolean = true,
    val pushReactions: Boolean = true,
    val pushComments: Boolean = true,
    val pushFollows: Boolean = true,
    val pushMessages: Boolean = true,
    val pushNewPhotos: Boolean = true,
    val pushMentions: Boolean = true,
    val pushStreakReminders: Boolean = true,
    val pushAchievements: Boolean = true,
    val pushSystemAnnouncements: Boolean = true,

    // Quiet Hours / Do Not Disturb (only affects push)
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 22, // 10 PM
    val quietHoursEnd: Int = 8,    // 8 AM

    // Display Settings
    val showNotificationPreviews: Boolean = true,
    val notificationSoundEnabled: Boolean = true,
    val notificationVibrationEnabled: Boolean = true,

    // Email Notifications
    val emailNotificationsEnabled: Boolean = false,
    val emailForSecurityAlerts: Boolean = true,
    val emailForAccountChanges: Boolean = true,
    val emailWeeklyDigest: Boolean = false
) {
    /**
     * Check if push notification should be sent
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
     * Check if in-app notification should be shown
     */
    fun shouldShowInApp(type: NotificationType): Boolean {
        if (!notificationsEnabled) return false

        return when (type) {
            NotificationType.LIKE -> likes
            NotificationType.REACTION -> reactions
            NotificationType.COMMENT -> comments
            NotificationType.FOLLOW -> follows
            NotificationType.FRIEND_REQUEST -> follows
            NotificationType.MESSAGE -> messages
            NotificationType.PHOTO_ADDED -> newPhotos
            NotificationType.MENTION -> mentions
            NotificationType.STREAK_REMINDER -> streakReminders
            NotificationType.ACHIEVEMENT -> achievements
            NotificationType.SYSTEM -> systemAnnouncements
        }
    }

    /**
     * Check if in quiet hours
     */
    fun isInQuietHours(): Boolean {
        if (!quietHoursEnabled) return false
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (quietHoursStart > quietHoursEnd) {
            currentHour >= quietHoursStart || currentHour < quietHoursEnd
        } else {
            currentHour >= quietHoursStart && currentHour < quietHoursEnd
        }
    }
}

