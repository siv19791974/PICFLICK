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
        if (userId.isEmpty()) return
        
        isLoading = true
        errorMessage = null
        
        viewModelScope.launch {
            try {
                // Set up real-time listener
                listenerRegistration = repository.listenToNotifications(
                    userId = userId,
                    onUpdate = { newNotifications ->
                        notifications.clear()
                        notifications.addAll(newNotifications.sortedByDescending { it.timestamp })
                        unreadCount = newNotifications.count { !it.isRead }
                        isLoading = false
                    },
                    onError = { error ->
                        errorMessage = error
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
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
     * Accept a friend request
     */
    fun acceptFollowRequest(currentUserId: String, requesterId: String) {
        viewModelScope.launch {
            // Get requester's profile for the notification
            repository.getUserProfile(requesterId) { result ->
                if (result is Result.Success) {
                    val requester = result.data
                    viewModelScope.launch {
                        repository.acceptFollowRequest(
                            currentUserId = currentUserId,
                            requesterId = requesterId,
                            requesterName = requester.displayName
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Decline a friend request
     */
    fun declineFollowRequest(currentUserId: String, requesterId: String) {
        viewModelScope.launch {
            repository.declineFollowRequest(
                currentUserId = currentUserId,
                requesterId = requesterId
            )
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
