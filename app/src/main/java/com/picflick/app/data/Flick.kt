package com.picflick.app.data

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Result of an image upload, including thumbnail URLs.
 */
data class ImageUploadResult(
    val imageUrl: String,
    val thumbnailUrl256: String,
    val thumbnailUrl512: String
)

/**
 * Data class representing a photo flick/post in the PicFlick app
 */
@IgnoreExtraProperties
data class Flick(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "", // User's profile picture URL
    val imageUrl: String = "",
    val thumbnailUrl256: String = "", // 256px thumbnail for grid
    val thumbnailUrl512: String = "",   // 512px thumbnail for fullscreen
    val description: String = "",
    val timestamp: Long = 0,
    val reactions: Map<String, String> = emptyMap(), // userId -> reactionType
    val commentCount: Int = 0,
    val privacy: String = "friends", // "friends" = only friends can see, "public" = everyone
    val sharedGroupId: String = "", // Optional: when set, this post is targeted to one shared album
    val taggedFriends: List<String> = emptyList(), // List of tagged friend userIds
    val reportCount: Int = 0, // Number of reports for moderation
    val imageSizeBytes: Long = 0, // Exact uploaded image size in bytes (for storage accounting)
    val clientUploadId: String = "", // Client-side id used to reconcile optimistic -> real upload
    val reactionsCount: Int = 0, // Cached count for fast reads (may differ from reactions.size when migrated)
    val reactionsMigrated: Boolean = false // True when reactions moved to subcollection
) {
    /**
     * Get total reaction count
     */
    fun getTotalReactions(): Int = reactions.size
    
    /**
     * Get count for specific reaction type
     */
    fun getReactionCount(type: ReactionType): Int = 
        reactions.count { it.value == type.name }
    
    /**
     * Get user's reaction type (null if no reaction)
     */
    fun getUserReaction(userId: String): ReactionType? = 
        reactions[userId]?.let { ReactionType.valueOf(it) }
    
    /**
     * Check if user has reacted
     */
    fun hasUserReacted(userId: String): Boolean = 
        reactions.containsKey(userId)
    
    /**
     * Get all reaction types with counts (sorted by count desc)
     */
    fun getReactionCounts(): Map<ReactionType, Int> = 
        reactions.values
            .groupBy { ReactionType.valueOf(it) }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .toMap()
    
    /**
     * Backward compatibility - get likes (users with LIKE reaction)
     */
    @Deprecated("Use reactions instead")
    val likes: List<String> 
        get() = reactions.filter { it.value == ReactionType.LIKE.name }.keys.toList()
}

