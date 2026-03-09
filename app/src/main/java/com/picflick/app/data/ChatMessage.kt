package com.picflick.app.data

/**
 * Data class representing a single chat message (WhatsApp-style)
 * Supports reply/quote functionality
 */
data class ChatMessage(
    val id: String = "",
    val chatId: String = "", // Reference to parent chat session
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val read: Boolean = false,
    val delivered: Boolean = false,
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    // Reply/Quote functionality
    val replyToMessageId: String? = null, // ID of message being replied to
    val quotedText: String? = null, // Preview of quoted message text
    val quotedSenderName: String? = null // Name of sender of quoted message
) {
    /**
     * Check if this message is a reply to another message
     */
    fun isReply(): Boolean = replyToMessageId != null
    
    /**
     * Get formatted timestamp for display
     */
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

