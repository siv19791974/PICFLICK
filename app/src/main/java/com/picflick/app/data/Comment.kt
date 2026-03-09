package com.picflick.app.data

/**
 * Data class representing a comment on a flick
 * Supports nested replies and likes
 */
data class Comment(
    val id: String = "",
    val flickId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val parentCommentId: String? = null, // For replies - null if top-level comment
    val likes: List<String> = emptyList(), // List of user IDs who liked this comment
    val replyCount: Int = 0 // Number of replies to this comment
) {
    /**
     * Check if a user has liked this comment
     */
    fun hasUserLiked(userId: String): Boolean = likes.contains(userId)
    
    /**
     * Get like count
     */
    fun getLikeCount(): Int = likes.size
    
    /**
     * Check if this is a reply to another comment
     */
    fun isReply(): Boolean = parentCommentId != null
}

