package com.picflick.app.data

/**
 * Data class representing a comment on a flick
 */
data class Comment(
    val id: String = "",
    val flickId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "",
    val text: String = "",
    val timestamp: Long = 0
)

