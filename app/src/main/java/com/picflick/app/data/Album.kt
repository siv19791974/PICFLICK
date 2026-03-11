package com.picflick.app.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data class representing a photo album/collection
 */
data class Album(
    val id: String = "",
    val userId: String = "",           // Album owner
    val name: String = "",              // Album name
    val description: String = "",       // Optional description
    val coverPhotoUrl: String = "",     // Thumbnail/cover image
    val photoIds: List<String> = emptyList(), // IDs of photos in album
    val photoCount: Int = 0,            // Cached count
    val privacy: String = "friends",    // "public", "friends", "private"
    val isDefault: Boolean = false,     // System albums (e.g., "All Photos")
    val sortOrder: Int = 0,             // For custom ordering
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)

/**
 * Predefined album types for system albums
 */
object AlbumTypes {
    const val ALL_PHOTOS = "all_photos"
    const val FAVORITES = "favorites"
    const val CAMERA_ROLL = "camera_roll"
}
