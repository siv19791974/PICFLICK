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
    val likes: List<String> = emptyList(),
    val commentCount: Int = 0
)
