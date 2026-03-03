package com.example.picflick.data

/**
 * Data class representing a chat session between users
 */
data class ChatSession(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTimestamp: Long = 0
)
