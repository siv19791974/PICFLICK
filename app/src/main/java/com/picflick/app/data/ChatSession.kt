package com.picflick.app.data

/**
 * Data class representing a chat session between users (WhatsApp-style)
 */
data class ChatSession(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val isGroup: Boolean = false,
    val groupId: String = "",
    val groupName: String = "",
    val groupIcon: String = "👥",
    val lastMessage: String = "",
    val lastTimestamp: Long = 0,
    val lastSenderId: String = "",
    val lastMessageRead: Boolean = false,  // Is last message read by recipient?
    val unreadCount: Int = 0
)

