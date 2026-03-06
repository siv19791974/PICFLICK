package com.example.picflick.data

/**
 * User notification preferences - simplified unified settings
 * One toggle controls both push and in-app notifications
 */
data class NotificationPreferences(
    // Master Switch
    val notificationsEnabled: Boolean = true,

    // Unified Notification Toggles (controls both push + in-app)
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
        if (!notificationsEnabled) return false
        if (isInQuietHours()) return false

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
