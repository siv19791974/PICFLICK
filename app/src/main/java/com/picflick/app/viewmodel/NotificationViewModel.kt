package com.picflick.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picflick.app.data.Notification
import com.picflick.app.data.NotificationType
import com.picflick.app.data.Result
import com.picflick.app.repository.FlickRepository
import com.picflick.app.repository.NotificationRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for managing user notifications
 *
 * Handles:
 * - Real-time notification listening via Firestore
 * - Marking notifications as read
 * - Accepting/declining friend requests and photo tags
 * - Deleting notifications
 * - Tracking unread count
 */
class NotificationViewModel : ViewModel() {
    
    private val flickRepository = FlickRepository.getInstance()
    private val notificationRepository = NotificationRepository.getInstance()
    
    /** List of all notifications for current user */
    var notifications = mutableStateListOf<Notification>()
        private set
    
    /** Number of unread notifications */
    var unreadCount by mutableStateOf(0)
        private set
    
    /** Loading state for operations */
    var isLoading by mutableStateOf(false)
        private set
    
    /** Error message for failed operations */
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    /** Firestore listener for real-time updates */
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    
    /** Currently logged in user ID */
    private var currentUserId: String? = null
    
    /**
     * Accept a tag (user accepts being tagged in a photo)
     * Adds user to the flick's tagged friends list
     * 
     * @param flickId The photo being tagged in
     * @param notificationId The notification to mark as handled
     */
    fun acceptTag(flickId: String, notificationId: String) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            
            when (val result = flickRepository.acceptTag(flickId, userId)) {
                is Result.Success -> {
                    markAsRead(notificationId)
                    removeNotificationFromList(notificationId)
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                else -> {}
            }
        }
    }

    /**
     * Decline a tag (user removes themselves from being tagged in a photo)
     * 
     * @param flickId The photo they were tagged in
     * @param notificationId The notification to remove
     */
    fun declineTag(flickId: String, notificationId: String) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            
            when (val result = flickRepository.declineTag(flickId, userId)) {
                is Result.Success -> {
                    deleteNotification(notificationId)
                    removeNotificationFromList(notificationId)
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                else -> {}
            }
        }
    }

    private fun removeNotificationFromList(notificationId: String) {
        val index = notifications.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            notifications.removeAt(index)
        }
    }

    /**
     * Load notifications for a user with real-time updates
     * Also cleans up old read notifications automatically
     */
    fun loadNotifications(userId: String) {
        if (userId.isEmpty()) {
            android.util.Log.e("NotificationViewModel", "Cannot load notifications - userId is empty")
            return
        }
        
        currentUserId = userId // Store for later use
        android.util.Log.d("NotificationViewModel", "Loading notifications for user: $userId")
        isLoading = true
        errorMessage = null
        
        viewModelScope.launch {
            try {
                // Clean up old read notifications (auto-delete after 7 days)
                cleanupOldNotifications(userId)
                
                // Set up real-time listener
                listenerRegistration = flickRepository.listenToNotifications(
                    userId = userId,
                    onUpdate = { newNotifications ->
                        android.util.Log.d("NotificationViewModel", "Received ${newNotifications.size} notifications")
                        newNotifications.forEachIndexed { index, notification ->
                            android.util.Log.d("NotificationViewModel", "Notification $index: type=${notification.type}, title=${notification.title}, userId=${notification.userId}")
                        }
                        val normalizedNotifications = newNotifications
                            .sortedByDescending { it.timestamp }
                            .dedupeLatestMessagePerConversation()

                        notifications.clear()
                        notifications.addAll(normalizedNotifications)
                        unreadCount = normalizedNotifications.count { !it.isRead }
                        isLoading = false
                        android.util.Log.d("NotificationViewModel", "Notifications updated. Unread: $unreadCount")
                    },
                    onError = { error ->
                        android.util.Log.e("NotificationViewModel", "Error loading notifications: $error")
                        errorMessage = error
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("NotificationViewModel", "Exception loading notifications: ${e.message}", e)
                errorMessage = e.message
                isLoading = false
            }
        }
    }
    
    /**
     * Mark a notification as read
     */
    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            when (val result = flickRepository.markNotificationAsRead(notificationId)) {
                is Result.Success -> {
                    // Update local state
                    val index = notifications.indexOfFirst { it.id == notificationId }
                    if (index != -1) {
                        notifications[index] = notifications[index].copy(isRead = true)
                        unreadCount = notifications.count { !it.isRead }
                    }
                }
                is Result.Error -> {
                    errorMessage = result.exception.message
                }
                else -> {}
            }
        }
    }
    
    /**
     * Mark all notifications as read
     */
    fun markAllAsRead(userId: String) {
        viewModelScope.launch {
            when (val result = flickRepository.markAllNotificationsAsRead(userId)) {
                is Result.Success -> {
                    notifications.replaceAll { it.copy(isRead = true) }
                    unreadCount = 0
                }
                is Result.Error -> {
                    errorMessage = result.exception.message
                }
                else -> {}
            }
        }
    }
    
    /**
     * Delete all notifications for the current user.
     */
    fun deleteAllNotifications(userId: String) {
        viewModelScope.launch {
            when (val result = flickRepository.deleteAllNotifications(userId)) {
                is Result.Success -> {
                    notifications.clear()
                    unreadCount = 0
                }
                is Result.Error -> {
                    errorMessage = result.exception.message
                }
                else -> {}
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        val notification = notifications.firstOrNull { it.id == notificationId }
        if (notification != null) {
            deleteNotification(notification)
            return
        }

        viewModelScope.launch {
            when (val result = flickRepository.deleteNotification(notificationId)) {
                is Result.Success -> {
                    notifications.removeAll { it.id == notificationId }
                    unreadCount = notifications.count { !it.isRead }
                }
                is Result.Error -> {
                    errorMessage = result.exception.message
                }
                else -> {}
            }
        }
    }

    /**
     * Delete a notification.
     * For MESSAGE notifications, delete all notifications from the same conversation.
     */
    fun deleteNotification(notification: Notification) {
        viewModelScope.launch {
            if (notification.type == NotificationType.MESSAGE) {
                val userId = currentUserId ?: return@launch
                when (val result = flickRepository.deleteMessageNotificationsForConversation(
                    userId = userId,
                    chatId = notification.chatId,
                    senderId = notification.senderId
                )) {
                    is Result.Success -> {
                        val hasChatId = !notification.chatId.isNullOrBlank()
                        notifications.removeAll { existing ->
                            existing.type == NotificationType.MESSAGE && if (hasChatId) {
                                existing.chatId == notification.chatId
                            } else {
                                existing.senderId == notification.senderId
                            }
                        }
                        unreadCount = notifications.count { !it.isRead }
                    }
                    is Result.Error -> {
                        errorMessage = result.exception.message
                    }
                    else -> {}
                }
                return@launch
            }

            when (val result = flickRepository.deleteNotification(notification.id)) {
                is Result.Success -> {
                    notifications.removeAll { it.id == notification.id }
                    unreadCount = notifications.count { !it.isRead }
                }
                is Result.Error -> {
                    errorMessage = result.exception.message
                }
                else -> {}
            }
        }
    }

    fun deleteNotifications(notificationIds: Set<String>) {
        if (notificationIds.isEmpty()) return

        val selectedNotifications = notifications.filter { it.id in notificationIds }
        selectedNotifications.forEach { notification ->
            deleteNotification(notification)
        }
    }

    /**
     * Accept a friend request and delete the notification
     */
    fun acceptFollowRequest(currentUserId: String, requesterId: String, notificationId: String) {
        viewModelScope.launch {
            android.util.Log.d("NotificationViewModel", "Accepting friend request: $currentUserId <- $requesterId, deleting notif: $notificationId")
            
            // Immediately remove from local list for instant UI update
            val index = notifications.indexOfFirst { it.id == notificationId }
            if (index != -1) {
                notifications.removeAt(index)
                unreadCount = notifications.count { !it.isRead }
                android.util.Log.d("NotificationViewModel", "Removed notification $notificationId from local list")
            }
            
            // Delete the notification from Firestore
            flickRepository.deleteNotification(notificationId)
            android.util.Log.d("NotificationViewModel", "Deleted notification $notificationId from Firestore")
            
            // Accept the follow request
            flickRepository.getUserProfile(currentUserId) { result ->
                val currentUserName = if (result is Result.Success) {
                    result.data.displayName.ifBlank { "Someone" }
                } else {
                    "Someone"
                }

                viewModelScope.launch {
                    val acceptResult = flickRepository.acceptFollowRequest(
                        currentUserId = currentUserId,
                        currentUserName = currentUserName,
                        requesterId = requesterId
                    )
                    if (acceptResult is Result.Success) {
                        android.util.Log.d("NotificationViewModel", "Successfully accepted friend request")
                    } else {
                        android.util.Log.e("NotificationViewModel", "Failed to accept friend request")
                    }
                }
            }
        }
    }
    
    /**
     * Decline a friend request and delete the notification
     */
    fun declineFollowRequest(currentUserId: String, requesterId: String, notificationId: String) {
        viewModelScope.launch {
            android.util.Log.d("NotificationViewModel", "Declining friend request: $currentUserId <- $requesterId, deleting notif: $notificationId")
            
            // Immediately remove from local list for instant UI update
            val index = notifications.indexOfFirst { it.id == notificationId }
            if (index != -1) {
                notifications.removeAt(index)
                unreadCount = notifications.count { !it.isRead }
                android.util.Log.d("NotificationViewModel", "Removed notification $notificationId from local list")
            }
            
            // Delete the notification from Firestore
            flickRepository.deleteNotification(notificationId)
            android.util.Log.d("NotificationViewModel", "Deleted notification $notificationId from Firestore")
            
            // Decline the follow request
            val declineResult = flickRepository.declineFollowRequest(
                currentUserId = currentUserId,
                requesterId = requesterId
            )
            if (declineResult is Result.Success) {
                android.util.Log.d("NotificationViewModel", "Successfully declined friend request")
            } else {
                android.util.Log.e("NotificationViewModel", "Failed to decline friend request")
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        errorMessage = null
    }

    /**
     * Keep only the latest MESSAGE notification per conversation.
     * Conversation key priority: chatId, then senderId fallback.
     */
    private fun List<Notification>.dedupeLatestMessagePerConversation(): List<Notification> {
        val latestByConversation = linkedMapOf<String, Notification>()
        val result = mutableListOf<Notification>()

        for (notification in this) {
            if (notification.type != NotificationType.MESSAGE) {
                result.add(notification)
                continue
            }

            val conversationKey = notification.chatId
                ?.takeIf { it.isNotBlank() }
                ?: notification.senderId

            if (!latestByConversation.containsKey(conversationKey)) {
                latestByConversation[conversationKey] = notification
                result.add(notification)
            }
        }

        return result
    }
    
    /**
     * Clean up old read notifications (auto-delete after 7 days)
     * Runs silently in background - doesn't affect user experience
     */
    private suspend fun cleanupOldNotifications(userId: String) {
        try {
            notificationRepository.cleanupOldReadNotifications(userId) { deletedCount ->
                if (deletedCount > 0) {
                    android.util.Log.d("NotificationViewModel", "Auto-deleted $deletedCount old read notifications")
                }
            }
        } catch (e: Exception) {
            // Silently fail - cleanup is not critical
            android.util.Log.e("NotificationViewModel", "Cleanup failed: ${e.message}")
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
