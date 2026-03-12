package com.picflick.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picflick.app.data.ChatMessage
import com.picflick.app.data.ChatSession
import com.picflick.app.data.Result
import com.picflick.app.repository.ChatRepository
import com.picflick.app.utils.Analytics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for chat/messaging functionality
 *
 * Manages:
 * - Chat sessions (conversations list)
 * - Real-time messages within a chat
 * - Sending text and photo messages
 * - Starting new chats with friends
 * - Unread message counts
 */
class ChatViewModel : ViewModel() {
    private val repository = ChatRepository()

    /** List of all chat sessions for current user */
    var chatSessions by mutableStateOf<List<ChatSession>>(emptyList())
        private set

    /** Messages in the currently open chat */
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    /** Loading state for operations */
    var isLoading by mutableStateOf(false)
        private set

    /** Error message for failed operations */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** ID of currently open chat session */
    var currentChatId by mutableStateOf<String?>(null)
        private set
    
    /** Job for message collection - prevents duplicate listeners */
    private var messagesJob: kotlinx.coroutines.Job? = null

    /** Total unread messages across all chats */
    var unreadCount by mutableStateOf(0)
        private set

    /**
     * Load chat sessions for a user
     * Updates chatSessions with real-time data from Firestore
     * 
     * @param userId The user whose sessions to load
     */
    fun loadChatSessions(userId: String) {
        if (userId.isBlank()) {
            errorMessage = "Cannot load conversations: User not logged in"
            isLoading = false
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null // Clear previous error
            try {
                repository.getChatSessions(userId).collectLatest { sessions ->
                    chatSessions = sessions
                    // Calculate total unread
                    unreadCount = sessions.sumOf { it.unreadCount }
                    isLoading = false
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to load conversations", e)
                errorMessage = "Failed to load conversations: ${e.message}"
                isLoading = false
            }
        }
    }

    /**
     * Load messages for a specific chat
     * Sets up real-time listener for new messages
     * FIXED: Cancels previous job to prevent duplicate listeners
     * 
     * @param chatId The chat session ID to load messages for
     */
    fun loadMessages(chatId: String) {
        // Cancel any existing message collection job
        messagesJob?.cancel()
        
        currentChatId = chatId
        messagesJob = viewModelScope.launch {
            errorMessage = null // Clear previous error
            try {
                android.util.Log.d("ChatViewModel", "Starting message collection for chat: $chatId")
                repository.getMessages(chatId).collectLatest { msgs ->
                    android.util.Log.d("ChatViewModel", "Received ${msgs.size} messages from repository")
                    messages = msgs
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Message collection error: ${e.message}")
                errorMessage = "Failed to load messages: ${e.message}"
            }
        }
    }

    /**
     * Send a message (text or photo)
     */
    fun sendMessage(
        chatId: String,
        text: String,
        senderId: String,
        recipientId: String,
        senderName: String,
        senderPhotoUrl: String,
        imageUrl: String = "",
        replyToMessage: ChatMessage? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val message = ChatMessage(
                chatId = chatId,
                senderId = senderId,
                senderName = senderName,
                senderPhotoUrl = senderPhotoUrl,
                text = text,
                imageUrl = imageUrl,
                timestamp = System.currentTimeMillis(),
                read = false,
                delivered = false,
                // Reply functionality
                replyToMessageId = replyToMessage?.id,
                quotedText = replyToMessage?.text?.take(100), // First 100 chars
                quotedSenderName = replyToMessage?.senderName
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
     * Send a photo message
     */
    fun sendPhotoMessage(
        chatId: String,
        imageUri: android.net.Uri,
        senderId: String,
        recipientId: String,
        senderName: String,
        senderPhotoUrl: String,
        context: android.content.Context,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // Convert URI to bytes
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val imageBytes = inputStream?.readBytes()
                inputStream?.close()

                if (imageBytes == null) {
                    errorMessage = "Failed to read image"
                    return@launch
                }

                // Upload to Firebase Storage
                val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance()
                    .reference
                    .child("chat_images")
                    .child(chatId)
                    .child("${System.currentTimeMillis()}.jpg")

                storageRef.putBytes(imageBytes).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // Send message with image URL
                sendMessage(
                    chatId = chatId,
                    text = "",
                    senderId = senderId,
                    recipientId = recipientId,
                    senderName = senderName,
                    senderPhotoUrl = senderPhotoUrl,
                    imageUrl = downloadUrl,
                    onComplete = onComplete
                )
            } catch (e: Exception) {
                errorMessage = "Failed to send photo: ${e.message}"
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
     * Mark messages as read and reload to refresh UI
     */
    fun markAsRead(chatId: String, userId: String) {
        viewModelScope.launch {
            android.util.Log.d("ChatViewModel", "markAsRead starting for chat $chatId")
            when (val result = repository.markMessagesAsRead(chatId, userId)) {
                is Result.Success -> {
                    android.util.Log.d("ChatViewModel", "markAsRead success, reloading messages")
                    // Reload messages to refresh UI with new read status
                    loadMessages(chatId)
                }
                is Result.Error -> {
                    android.util.Log.e("ChatViewModel", "markAsRead failed: ${result.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Mark messages as delivered and reload to refresh UI
     */
    fun markAsDelivered(chatId: String, userId: String) {
        viewModelScope.launch {
            android.util.Log.d("ChatViewModel", "markAsDelivered starting for chat $chatId")
            when (val result = repository.markMessagesAsDelivered(chatId, userId)) {
                is Result.Success -> {
                    android.util.Log.d("ChatViewModel", "markAsDelivered success, reloading messages")
                    // Small delay to let Firestore propagate, then reload
                    kotlinx.coroutines.delay(300)
                    loadMessages(chatId)
                }
                is Result.Error -> {
                    android.util.Log.e("ChatViewModel", "markAsDelivered failed: ${result.message}")
                }
                else -> {}
            }
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
     * Clear current chat and stop listening
     */
    fun clearCurrentChat() {
        messagesJob?.cancel()
        messagesJob = null
        currentChatId = null
        messages = emptyList()
    }
}
