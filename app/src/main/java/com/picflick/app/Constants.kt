package com.picflick.app

/**
 * Application constants for PicFlick
 * Centralized location for all magic numbers and configuration values
 */
object Constants {

    /**
     * Pagination and Query Limits
     */
    object Pagination {
        const val FLICKS_PER_PAGE = 50
        const val EXPLORE_FLICKS_LIMIT = 100
        const val USERS_PER_PAGE = 100
        const val SUGGESTED_USERS_LIMIT = 20
        const val MAX_FRIENDS_BATCH = 10  // Firestore whereIn limit is 10
        const val COMMENTS_PER_PAGE = 50
        const val NOTIFICATIONS_PER_PAGE = 50
    }

    /**
     * Storage and Upload Configuration
     */
    object Storage {
        // These are now defined in SubscriptionTier but kept here for reference
        const val FREE_TIER_GB = 2
        const val STANDARD_TIER_GB = 15
        const val PLUS_TIER_GB = 50
        const val PRO_TIER_GB = 100
        const val ULTRA_TIER_GB = 200
    }

    /**
     * Image Quality Settings (percentage)
     */
    object ImageQuality {
        const val FREE = 80
        const val STANDARD = 85
        const val PLUS = 90
        const val PRO = 95
        const val ULTRA = 100
    }

    /**
     * Time Windows (in milliseconds)
     */
    object TimeWindows {
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
        const val ONE_WEEK_MS = 7 * ONE_DAY_MS
        const val ONE_MONTH_MS = 30 * ONE_DAY_MS
    }

    /**
     * Photo and Filter Configuration
     */
    object Photo {
        const val FILTER_PREVIEW_SIZE = 1024  // Max dimension for filter preview
        const val FILTER_THUMBNAIL_SIZE = 256 // Filter icon thumbnail size
        const val HIGH_CONTRAST_SCALE = 1.5f  // ColorMatrix scale factor
        const val VINTAGE_SEPIIA_RED = 1.2f   // Sepia matrix red multiplier
        const val VINTAGE_SEPIA_GREEN = 1.0f  // Sepia matrix green multiplier  
        const val VINTAGE_SEPIA_BLUE = 0.8f   // Sepia matrix blue multiplier
    }

    /**
     * UI/UX Constants
     */
    object UI {
        const val ANIMATION_DURATION_SHORT = 150
        const val ANIMATION_DURATION_MEDIUM = 300
        const val ANIMATION_DURATION_LONG = 500
        const val SPLASH_SCREEN_DURATION_MS = 2000L
        const val ERROR_MESSAGE_DURATION_MS = 3000L
        const val PHOTO_GRID_COLUMNS = 2
    }

    /**
     * Firebase Collection Names
     */
    object FirebaseCollections {
        const val USERS = "users"
        const val FLICKS = "flicks"
        const val CHAT_SESSIONS = "chatSessions"
        const val NOTIFICATIONS = "notifications"
        const val MESSAGES = "messages"
        const val ACHIEVEMENTS = "achievements"
    }

    /**
     * Privacy Settings
     */
    object Privacy {
        const val PRIVACY_FRIENDS = "friends"
        const val PRIVACY_PUBLIC = "public"
    }

    /**
     * Notification Types (as strings for consistency)
     */
    object NotificationTypes {
        const val LIKE = "LIKE"
        const val REACTION = "REACTION"
        const val COMMENT = "COMMENT"
        const val FOLLOW = "FOLLOW"
        const val FRIEND_REQUEST = "FRIEND_REQUEST"
        const val MESSAGE = "MESSAGE"
        const val PHOTO_ADDED = "PHOTO_ADDED"
        const val MENTION = "MENTION"
        const val STREAK_REMINDER = "STREAK_REMINDER"
        const val ACHIEVEMENT = "ACHIEVEMENT"
        const val SYSTEM = "SYSTEM"
    }

    /**
     * Achievements Milestones
     */
    object Achievements {
        const val FIRST_PHOTO_COUNT = 1
        const val ACTIVE_USER_PHOTO_COUNT = 5
        const val POWER_USER_PHOTO_COUNT = 50
        const val INFLUENCER_FOLLOWER_COUNT = 1000
    }

    /**
     * Developer/Testing
     */
    object Developer {
        const val DUMMY_FRIEND_ID = "firebender_dummy_001"
        const val DUMMY_FRIEND_NAME = "Firebender"
        const val DUMMY_FRIEND_EMAIL = "firebender@picflick.app"
        const val DUMMY_FRIEND_BIO = "AI Assistant helping with PicFlick development! 🤖📸"
        const val DUMMY_FRIEND_PHOTO = "https://ui-avatars.com/api/?name=Fire+Bender&background=1565C0&color=fff&size=256"
        const val PREF_DUMMY_FRIEND_ENABLED = "dummy_friend_enabled"
        
        /**
         * Create a dummy friend UserProfile for testing
         */
        fun createDummyFriendProfile(): com.picflick.app.data.UserProfile {
            return com.picflick.app.data.UserProfile(
                uid = DUMMY_FRIEND_ID,
                email = DUMMY_FRIEND_EMAIL,
                displayName = DUMMY_FRIEND_NAME,
                photoUrl = DUMMY_FRIEND_PHOTO,
                bio = DUMMY_FRIEND_BIO,
                followers = listOf(), // Will be dynamically set
                following = listOf(),
                subscriptionTier = com.picflick.app.data.SubscriptionTier.PLUS,
                totalPhotos = 42,
                joinedAt = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // Joined 30 days ago
            )
        }
    }

    /**
     * Cache and Performance
     */
    object Cache {
        const val MAX_MEMORY_CACHE_SIZE_MB = 50
        const val MAX_DISK_CACHE_SIZE_MB = 200
        const val CACHE_EXPIRY_HOURS = 24
    }
}
