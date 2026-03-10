package com.picflick.app.data

/**
 * Data class representing a friend group/album
 * Users can organize friends into groups and filter their feed by group
 */
data class FriendGroup(
    val id: String = "",
    val userId: String = "", // Owner of the group
    val name: String = "", // Group name like "Family", "Work", "School"
    val friendIds: List<String> = emptyList(), // List of user IDs in this group
    val icon: String = "👥", // Emoji icon for the group
    val color: String = "#4FC3F7", // Color hex for the group chip
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        // Predefined group suggestions
        val DEFAULT_GROUPS = listOf(
            "Family" to "👨‍👩‍👧‍👦",
            "Work" to "💼",
            "School" to "🎓",
            "Close Friends" to "⭐",
            "Travel" to "✈️",
            "Sports" to "⚽",
            "Hobby" to "🎨",
            "Neighbors" to "🏠"
        )
    }
}

/**
 * Sealed class representing the feed filter type
 */
sealed class FeedFilter {
    data object AllFriends : FeedFilter()
    data class ByGroup(val group: FriendGroup) : FeedFilter()
}
