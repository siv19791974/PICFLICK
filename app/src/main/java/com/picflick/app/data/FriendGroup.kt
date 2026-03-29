package com.picflick.app.data

/**
 * Data class representing a friend group/album
 * Users can organize friends into groups and filter their feed by group
 */
data class FriendGroup(
    val id: String = "",
    // Legacy field kept for backward compatibility (historically owner id)
    val userId: String = "",
    val ownerId: String = "", // True owner/admin of the shared group
    val name: String = "", // Group name like "Family", "Work", "School"
    // Legacy field kept for backward compatibility (historically members excluding owner)
    val friendIds: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(), // All members including owner
    val adminIds: List<String> = emptyList(), // Admin users (owner always implied)
    val icon: String = "👥", // Emoji icon for the group
    val color: String = "#4FC3F7", // Color hex for the group chip
    val eventAt: Long? = null, // Optional event date-time
    val orderIndex: Long = Long.MAX_VALUE, // Manual group ordering in manager sheet
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun effectiveOwnerId(): String = ownerId.ifBlank { userId }

    fun effectiveMemberIds(): List<String> {
        val fallback = (friendIds + effectiveOwnerId()).filter { it.isNotBlank() }
        val source = if (memberIds.isNotEmpty()) memberIds else fallback
        return source.distinct()
    }

    fun effectiveAdminIds(): List<String> {
        val source = if (adminIds.isNotEmpty()) adminIds else listOf(effectiveOwnerId())
        return source.filter { it.isNotBlank() }.distinct()
    }

    fun isOwner(userId: String): Boolean = userId.isNotBlank() && userId == effectiveOwnerId()

    fun isAdmin(userId: String): Boolean = userId.isNotBlank() && (isOwner(userId) || effectiveAdminIds().contains(userId))

    fun isMember(userId: String): Boolean = userId.isNotBlank() && effectiveMemberIds().contains(userId)

    fun membersExcludingOwner(): List<String> = effectiveMemberIds().filter { it != effectiveOwnerId() }

    fun roleOf(userId: String): GroupRole = when {
        isOwner(userId) -> GroupRole.OWNER
        isAdmin(userId) -> GroupRole.ADMIN
        isMember(userId) -> GroupRole.MEMBER
        else -> GroupRole.NONE
    }

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
enum class GroupRole {
    OWNER,
    ADMIN,
    MEMBER,
    NONE
}

enum class GroupInviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELED
}

data class GroupInvite(
    val id: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val inviterId: String = "",
    val inviterName: String = "",
    val inviteeId: String = "",
    val status: GroupInviteStatus = GroupInviteStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

sealed class FeedFilter {
    data object AllFriends : FeedFilter()
    data class ByGroup(val group: FriendGroup) : FeedFilter()
}
