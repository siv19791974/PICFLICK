package com.picflick.app.navigation

import com.picflick.app.data.Flick

/**
 * Sealed class for type-safe navigation in PicFlick app.
 * Each screen is represented as a data object or data class (for screens with arguments).
 */
sealed class Screen {
    data object Home : Screen()
    data object Profile : Screen()
    data class UserProfile(val userId: String) : Screen() // View another user's profile
    data object MyPhotos : Screen()
    data object Friends : Screen()
    data object UserFriends : Screen() // View another user's friends list (not main Friends tab)
    data object Chats : Screen()
    data object ChatDetail : Screen()
    data object FindFriends : Screen()
    data object About : Screen()
    data object Contact : Screen()
    data object Notifications : Screen()
    data object Filter : Screen()
    data object Settings : Screen()
    data object Explore : Screen()
    data object Privacy : Screen()
    data object NotificationSettings : Screen()
    data object ManageStorage : Screen()           // Storage management
    data object SubscriptionStatus : Screen()      // Subscription details
    data object PlanOptions : Screen()              // Plan comparison and purchase
    data object StreakAchievements : Screen()       // Streak and achievements screen
    data object PrivacyPolicy : Screen()              // Privacy Policy screen
    data object Philosophy : Screen()               // Our Philosophy screen
    data object Legal : Screen()                    // Legal/Terms screen
    data object Developer : Screen()                // Developer diagnostics and tools
    data object Preview : Screen()                    // Photo preview screen
    data class EditPhoto(val flick: Flick) : Screen()  // Edit photo filters
}
