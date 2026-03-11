package com.picflick.app.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data class representing a comment on a photo/flick
 */
data class Comment(
    val id: String = "",
    val flickId: String = "",           // The photo being commented on
    val userId: String = "",            // Comment author ID
    val userName: String = "",          // Comment author name
    val userPhotoUrl: String = "",      // Comment author photo
    val text: String = "",              // Comment text
    val parentCommentId: String? = null,  // For threaded replies (null = top-level)
    val replyCount: Int = 0,            // Number of replies to this comment
    val likeCount: Int = 0,             // Number of likes on this comment
    val likedBy: List<String> = emptyList(), // User IDs who liked this
    @ServerTimestamp
    val timestamp: Date? = null,
    val isEdited: Boolean = false       // Track if comment was edited
)

/**
 * Data class for comment with user info (for UI display)
 */
data class CommentWithUser(
    val comment: Comment,
    val userProfile: UserProfile? = null
)
