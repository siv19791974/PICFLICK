package com.example.picflick.data

/**
 * Data class representing a photo flick/post in the PicFlick app
 */
data class Flick(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val timestamp: Long = 0,
    val reactions: Map<String, String> = emptyMap(), // userId -> reactionType
    val commentCount: Int = 0,
    val privacy: String = "friends" // "friends" = only friends can see, "public" = everyone
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
