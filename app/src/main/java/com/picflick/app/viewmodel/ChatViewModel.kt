package com.example.picflick.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picflick.data.ChatMessage
import com.example.picflick.data.ChatSession
import com.example.picflick.data.Result
import com.example.picflick.repository.ChatRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for chat/messaging functionality
 */
class ChatViewModel : ViewModel() {
    private val repository = ChatRepository()

    // Chat sessions list
    var chatSessions by mutableStateOf<List<ChatSession>>(emptyList())
        private set

    // Current chat messages
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    // Loading state
    var isLoading by mutableStateOf(false)
        private set

    // Error message
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Current chat ID
    var currentChatId by mutableStateOf<String?>(null)
        private set

    // Unread message count
    var unreadCount by mutableStateOf(0)
        private set

    /**
     * Load chat sessions for a user
     */
    fun loadChatSessions(userId: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                repository.getChatSessions(userId).collectLatest { sessions ->
                    chatSessions = sessions
                    isLoading = false
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load conversations: ${e.message}"
                isLoading = false
            }
        }
    }

    /**
     * Load messages for a specific chat
     */
    fun loadMessages(chatId: String) {
        currentChatId = chatId
        viewModelScope.launch {
            try {
                repository.getMessages(chatId).collectLatest { msgs ->
                    messages = msgs
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load messages: ${e.message}"
            }
        }
    }

    /**
     * Send a message
     */
    fun sendMessage(
        chatId: String,
        text: String,
        senderId: String,
        recipientId: String,
        senderName: String,
        senderPhotoUrl: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val message = ChatMessage(
                senderId = senderId,
                senderName = senderName,
                senderPhotoUrl = senderPhotoUrl,
                text = text,
                timestamp = System.currentTimeMillis(),
                read = false,
                delivered = false
            )

            when (val result = repository.sendMessage(chatId, message, recipientId)) {
                is Result.Success -> {
                    onComplete()
                }
                is Result.Error -> {
                    errorMessage = "Failed to send message: ${result.message}"
                }
                is Result.Loading -> {}
            }
        }
    }

    /**
     * Start a new chat or open existing
     */
    fun startChat(
        userId: String,
        otherUserId: String,
        userName: String,
        otherUserName: String,
        onChatReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (val result = repository.getOrCreateChatSession(userId, otherUserId, userName, otherUserName)) {
                is Result.Success -> {
                    onChatReady(result.data)
                }
                is Result.Error -> {
                    errorMessage = "Failed to start chat: ${result.message}"
                }
                is Result.Loading -> {}
            }
        }
    }

    /**
     * Mark messages as read
     */
    fun markAsRead(chatId: String, userId: String) {
        viewModelScope.launch {
            repository.markMessagesAsRead(chatId, userId)
        }
    }

    /**
     * Observe unread message count
     */
    fun observeUnreadCount(userId: String) {
        viewModelScope.launch {
            try {
                repository.getUnreadMessageCount(userId).collectLatest { count ->
                    unreadCount = count
                }
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    /**
     * Clear error
     */
    fun clearError() {
        errorMessage = null
    }

    /**
     * Clear current chat
     */
    fun clearCurrentChat() {
        currentChatId = null
        messages = emptyList()
    }
}
