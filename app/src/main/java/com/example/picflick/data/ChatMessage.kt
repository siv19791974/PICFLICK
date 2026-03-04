package com.example.picflick.data

/**
 * Data class representing a single chat message (WhatsApp-style)
 */
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val read: Boolean = false,
    val delivered: Boolean = false,
    val senderName: String = "",
    val senderPhotoUrl: String = ""
)
