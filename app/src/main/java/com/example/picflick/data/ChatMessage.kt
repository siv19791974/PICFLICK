package com.example.picflick.data

/**
 * Data class representing a single chat message
 */
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0
)
