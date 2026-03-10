package com.picflick.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picflick.app.data.Notification
import com.picflick.app.data.Result
import com.picflick.app.repository.FlickRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for managing notifications
 */
class NotificationViewModel : ViewModel() {
    
    private val repository = FlickRepository.getInstance()
    
    var notifications = mutableStateListOf<Notification>()
        private set
    
    var unreadCount by mutableStateOf(0)
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    
    /**
     * Load notifications for a user with real-time updates
     */
    fun loadNotifications(userId: String) {
        if (userId.isEmpty()) {
            android.util.Log.e("NotificationViewModel", "Cannot load notifications - userId is empty")
            return
        }
        
        android.util.Log.d("NotificationViewModel", "Loading notifications for user: $userId")
        isLoading = true
        errorMessage = null
        
        viewModelScope.launch {
            try {
                // Set up real-time listener
                listenerRegistration = repository.listenToNotifications(
                    userId = userId,
                    onUpdate = { newNotifications ->
                        android.util.Log.d("NotificationViewModel", "Received ${newNotifications.size} notifications")
                        newNotifications.forEachIndexed { index, notification ->
                            android.util.Log.d("NotificationViewModel", "Notification $index: type=${notification.type}, title=${notification.title}, userId=${notification.userId}")
                        }
                        notifications.clear()
                        notifications.addAll(newNotifications.sortedByDescending { it.timestamp })
                        unreadCount = newNotifications.count { !it.isRead }
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
            when (val result = repository.markNotificationAsRead(notificationId)) {
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
            when (val result = repository.markAllNotificationsAsRead(userId)) {
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
     * Delete a notification
     */
    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteNotification(notificationId)) {
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
            repository.deleteNotification(notificationId)
            android.util.Log.d("NotificationViewModel", "Deleted notification $notificationId from Firestore")
            
            // Accept the follow request
            repository.getUserProfile(requesterId) { result ->
                if (result is Result.Success) {
                    val requester = result.data
                    viewModelScope.launch {
                        val acceptResult = repository.acceptFollowRequest(
                            currentUserId = currentUserId,
                            requesterId = requesterId,
                            requesterName = requester.displayName
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
            repository.deleteNotification(notificationId)
            android.util.Log.d("NotificationViewModel", "Deleted notification $notificationId from Firestore")
            
            // Decline the follow request
            val declineResult = repository.declineFollowRequest(
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
    
    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
