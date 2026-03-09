package com.picflick.app.viewmodel

import android.content.Context
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picflick.app.data.Result
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.FlickRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for finding and managing friends
 */
class FriendsViewModel : ViewModel() {

    private val repository = FlickRepository.getInstance()

    var searchResults = mutableStateListOf<UserProfile>()
        private set

    var followingUsers = mutableStateListOf<UserProfile>()
        private set

    var suggestedUsers = mutableStateListOf<UserProfile>()
        private set

    var contactsOnApp = mutableStateListOf<UserProfile>()
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var searchQuery by mutableStateOf("")
        private set
    
    // Track which users are being followed/unfollowed (for button loading state)
    var processingUserIds = mutableStateListOf<String>()
        private set

    init {
        // Load suggested users on init
        loadSuggestedUsers()
    }

    /**
     * Load users that the current user is following
     */
    fun loadFollowingUsers(followingIds: List<String>) {
        if (followingIds.isEmpty()) {
            followingUsers.clear()
            return
        }

        isLoading = true
        followingUsers.clear()

        // Load each user's profile
        followingIds.forEach { userId ->
            repository.getUserProfile(userId) { result ->
                when (result) {
                    is Result.Success -> {
                        followingUsers.add(result.data)
                    }
                    is Result.Error -> {
                        // Silently skip users that can't be loaded
                    }
                    is Result.Loading -> { }
                }
            }
        }

        isLoading = false
    }

    /**
     * Search for users by display name
     */
    fun searchUsers(query: String, currentUserId: String) {
        searchQuery = query

        if (query.isBlank()) {
            searchResults.clear()
            return
        }

        isLoading = true
        errorMessage = null

        repository.searchUsers(query, currentUserId) { result ->
            when (result) {
                is Result.Success -> {
                    searchResults.clear()
                    searchResults.addAll(result.data)
                    isLoading = false
                }
                is Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                }
                is Result.Loading -> { /* Do nothing */ }
            }
        }
    }

    /**
     * Load suggested users (users with mutual followers)
     */
    fun loadSuggestedUsers(currentUserId: String? = null) {
        viewModelScope.launch {
            isLoading = true

            // Get all users and filter by mutual connections
            repository.getAllUsers { result ->
                when (result) {
                    is Result.Success -> {
                        val allUsers = result.data

                        // Filter out current user and users already followed
                        val potentialSuggestions = if (currentUserId != null) {
                            allUsers.filter { user ->
                                user.uid != currentUserId &&
                                !user.followers.contains(currentUserId)
                            }
                        } else {
                            allUsers
                        }

                        // Sort by follower count (popular users first)
                        suggestedUsers.clear()
                        suggestedUsers.addAll(
                            potentialSuggestions
                                .sortedByDescending { it.followers.size }
                                .take(20) // Limit to 20 suggestions
                        )

                        isLoading = false
                    }
                    is Result.Error -> {
                        isLoading = false
                    }
                    is Result.Loading -> { }
                }
            }
        }
    }

    /**
     * Sync phone contacts with app users
     */
    fun syncContacts(context: Context) {
        viewModelScope.launch {
            isLoading = true
            contactsOnApp.clear()

            try {
                // Get phone contacts
                val phoneNumbers = getPhoneContacts(context)

                // Query Firebase for users with matching phone numbers
                repository.findUsersByPhoneNumbers(phoneNumbers) { result ->
                    when (result) {
                        is Result.Success -> {
                            contactsOnApp.addAll(result.data)
                            isLoading = false
                        }
                        is Result.Error -> {
                            errorMessage = result.message
                            isLoading = false
                        }
                        is Result.Loading -> { }
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Could not access contacts"
                isLoading = false
            }
        }
    }

    /**
     * Get phone contacts (simplified - just returns dummy data for now)
     * In production, this would read actual phone contacts
     */
    private fun getPhoneContacts(context: Context): List<String> {
        val contacts = mutableListOf<String>()

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null
            )

            cursor?.use {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val number = it.getString(numberIndex)?.replace("[^0-9]".toRegex(), "")
                    if (number != null && number.length >= 10) {
                        contacts.add(number)
                    }
                }
            }
        } catch (e: Exception) {
            // Permission denied or error
        }

        return contacts
    }

    /**
     * Send a follow request to a user
     */
    fun sendFollowRequest(currentUserId: String, targetUser: UserProfile, currentUser: UserProfile) {
        viewModelScope.launch {
            processingUserIds.add(targetUser.uid)
            
            val result = repository.sendFollowRequest(
                currentUserId, 
                targetUser.uid, 
                currentUser.displayName,
                currentUser.photoUrl
            )
            when (result) {
                is Result.Success -> {
                    // Update suggested users - remove sent request from suggestions
                    val suggestedIndex = suggestedUsers.indexOfFirst { it.uid == targetUser.uid }
                    if (suggestedIndex != -1) {
                        suggestedUsers.removeAt(suggestedIndex)
                    }
                    errorMessage = null
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
            
            processingUserIds.remove(targetUser.uid)
        }
    }

    /**
     * Accept a follow request
     */
    fun acceptFollowRequest(currentUserId: String, requester: UserProfile) {
        viewModelScope.launch {
            processingUserIds.add(requester.uid)
            
            val result = repository.acceptFollowRequest(currentUserId, requester.uid, requester.displayName)
            when (result) {
                is Result.Success -> {
                    // Refresh pending requests
                    loadPendingRequests(currentUserId)
                    errorMessage = null
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
            
            processingUserIds.remove(requester.uid)
        }
    }

    /**
     * Reject a follow request
     */
    fun rejectFollowRequest(currentUserId: String, requesterId: String) {
        viewModelScope.launch {
            processingUserIds.add(requesterId)
            
            val result = repository.rejectFollowRequest(currentUserId, requesterId)
            when (result) {
                is Result.Success -> {
                    // Refresh pending requests
                    loadPendingRequests(currentUserId)
                    errorMessage = null
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
            
            processingUserIds.remove(requesterId)
        }
    }

    /**
     * Cancel a sent follow request
     */
    fun cancelFollowRequest(currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            processingUserIds.add(targetUserId)
            
            val result = repository.cancelFollowRequest(currentUserId, targetUserId)
            when (result) {
                is Result.Success -> {
                    // Refresh suggested users
                    loadSuggestedUsers(currentUserId)
                    errorMessage = null
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
            
            processingUserIds.remove(targetUserId)
        }
    }

    /**
     * Unfollow a user
     */
    fun unfollowUser(currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            processingUserIds.add(targetUserId)
            
            val result = repository.unfollowUser(currentUserId, targetUserId)
            when (result) {
                is Result.Success -> {
                    searchUsers(searchQuery, currentUserId)
                    loadSuggestedUsers(currentUserId)
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
            
            processingUserIds.remove(targetUserId)
        }
    }

    /**
     * Load pending follow requests
     */
    fun loadPendingRequests(userId: String) {
        isLoading = true
        repository.getPendingFollowRequests(userId) { result ->
            when (result) {
                is Result.Success -> {
                    // This would update a pendingRequests list in the ViewModel
                    isLoading = false
                }
                is Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                }
                is Result.Loading -> { }
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        errorMessage = null
    }
}
