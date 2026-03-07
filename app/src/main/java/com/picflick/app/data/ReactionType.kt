package com.example.picflick.data

/**
 * Enum representing different reaction types for photos
 */
enum class ReactionType {
    LIKE,   // Default heart
    LOVE,   // Red heart
    LAUGH,  // Laughing face
    WOW,    // Surprised face
    FIRE    // Fire
}

/**
 * Extension function to get emoji for reaction type
 */
fun ReactionType.toEmoji(): String = when (this) {
    ReactionType.LIKE -> "❤️"
    ReactionType.LOVE -> "😍"
    ReactionType.LAUGH -> "😂"
    ReactionType.WOW -> "😮"
    ReactionType.FIRE -> "🔥"
}

/**
 * Extension function to get display name for reaction type
 */
fun ReactionType.toDisplayName(): String = when (this) {
    ReactionType.LIKE -> "Like"
    ReactionType.LOVE -> "Love"
    ReactionType.LAUGH -> "Haha"
    ReactionType.WOW -> "Wow"
    ReactionType.FIRE -> "Fire"
}

/**
 * Extension function to get color for reaction type
 */
fun ReactionType.toColor(): Long = when (this) {
    ReactionType.LIKE -> 0xFFE91E63 // Pink
    ReactionType.LOVE -> 0xFFFF1744 // Red
    ReactionType.LAUGH -> 0xFFFFD700 // Yellow
    ReactionType.WOW -> 0xFFFF9800 // Orange
    ReactionType.FIRE -> 0xFFFF5722 // Deep Orange
}
