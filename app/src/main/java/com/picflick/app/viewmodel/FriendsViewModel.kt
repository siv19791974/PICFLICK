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
import com.picflick.app.repository.SocialRepository
import com.picflick.app.utils.Analytics
import kotlinx.coroutines.launch

/**
 * ViewModel for finding and managing friends
 */
class FriendsViewModel : ViewModel() {

    private val flickRepository = FlickRepository.getInstance()
    private val socialRepository = SocialRepository.getInstance()

    var searchResults = mutableStateListOf<UserProfile>()
        private set

    var followingUsers = mutableStateListOf<UserProfile>()
        private set

    var suggestedUsers = mutableStateListOf<UserProfile>()
        private set

    var isLoading by mutableStateOf(false)
        private set

    /** For infinite scroll - loading more users */
    var isLoadingMore by mutableStateOf(false)
        private set

    /** For infinite scroll - can we load more */
    var canLoadMore by mutableStateOf(true)
        private set

    /** Last user ID for pagination */
    private var lastUserId: String? = null

    /** Token to ignore stale async callbacks when loadFollowingUsers is called repeatedly */
    private var followingLoadToken: Int = 0

    var searchQuery by mutableStateOf("")
        private set

    var contactUsers = mutableStateListOf<UserProfile>()
        private set

    /** Error message for failed operations */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Alias for contactUsers - contacts found on PicFlick
    val contactsOnApp: List<UserProfile> get() = contactUsers

    // Track which users are currently being processed (follow/unfollow)
    var processingUserIds = mutableStateListOf<String>()
        private set

    /**
     * Clear any error message
     */
    fun clearError() {
        errorMessage = null
    }

    init {
        // Load suggested users on init
        loadSuggestedUsers()
    }

    /**
     * Load users that the current user is following
     */
    fun loadFollowingUsers(followingIds: List<String>) {
        val loadToken = ++followingLoadToken

        if (followingIds.isEmpty()) {
            followingUsers.clear()
            return
        }

        isLoading = true
        followingUsers.clear()

        // Avoid duplicate requests (and duplicate keys) when source list contains repeated ids
        val uniqueFollowingIds = followingIds.distinct()

        // Load each user's profile
        uniqueFollowingIds.forEach { userId ->
            flickRepository.getUserProfile(userId) { result ->
                // Ignore stale callbacks from previous loads
                if (loadToken != followingLoadToken) return@getUserProfile

                when (result) {
                    is Result.Success -> {
                        val profile = result.data
                        // Upsert by uid to guarantee uniqueness for LazyColumn keys
                        val existingIndex = followingUsers.indexOfFirst { it.uid == profile.uid }
                        if (existingIndex >= 0) {
                            followingUsers[existingIndex] = profile
                        } else {
                            followingUsers.add(profile)
                        }
                    }
                    is Result.Error -> {
                        errorMessage = result.message
                    }
                    is Result.Loading -> { }
                }
            }
        }

        isLoading = false
    }

    /**
     * Search for users by name
     */
    fun searchUsers(query: String, currentUserId: String) {
        searchQuery = query
        if (query.isBlank()) {
            searchResults.clear()
            return
        }

        // Track search analytics
        Analytics.trackSearch(query)

        isLoading = true
        flickRepository.searchUsers(query, currentUserId) { result ->
            when (result) {
                is Result.Success -> {
                    searchResults.clear()
                    // Filter out current user
                    searchResults.addAll(result.data.filter { it.uid != currentUserId })
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
            isLoading = false
        }
    }

    /**
     * Load suggested users to follow
     */
    fun loadSuggestedUsers(currentUserId: String? = null) {
        if (currentUserId == null) {
            // Can't load without a user ID
            return
        }
        isLoading = true
        socialRepository.getSuggestedUsers(currentUserId) { result ->
            when (result) {
                is Result.Success -> {
                    suggestedUsers.clear()
                    suggestedUsers.addAll(result.data)
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
            isLoading = false
        }
    }

    /**
     * Load ALL users on PicFlick (for Discover tab - shows everyone including followed)
     */
    fun loadAllUsers(currentUserId: String) {
        isLoading = true
        canLoadMore = true
        lastUserId = null
        socialRepository.getAllUsers(currentUserId) { result ->
            when (result) {
                is Result.Success -> {
                    suggestedUsers.clear()
                    suggestedUsers.addAll(result.data)
                    // Check if we got a full page (100 users)
                    canLoadMore = result.data.size >= 100
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
            isLoading = false
        }
    }

    /**
     * Load more users for infinite scroll
     */
    fun loadMoreUsers() {
        if (isLoadingMore || !canLoadMore) return
        
        isLoadingMore = true
        // For now, just mark as no more users (until backend supports pagination)
        // In the future, this would call socialRepository.getAllUsersPaginated()
        canLoadMore = false
        isLoadingMore = false
    }

    /**
     * Send a follow request
     */
    fun sendFollowRequest(
        currentUserId: String,
        targetUser: UserProfile,
        currentUserProfile: UserProfile,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        addProcessingUser(targetUser.uid)
        viewModelScope.launch {
            val result = socialRepository.sendFollowRequest(
                currentUserId = currentUserId,
                targetUserId = targetUser.uid,
                currentUserName = currentUserProfile.displayName,
                currentUserPhotoUrl = currentUserProfile.photoUrl
            )
            if (result is com.picflick.app.data.Result.Error) {
                android.util.Log.e("FriendsViewModel", "Failed to send follow request: ${result.exception?.message}")
                errorMessage = result.exception.message ?: result.message
                onComplete?.invoke(false)
            } else if (result is com.picflick.app.data.Result.Success) {
                Analytics.trackFriendRequestSent()
                onComplete?.invoke(true)
            }
            removeProcessingUser(targetUser.uid)
        }
    }

    /**
     * Unfollow a user
     */
    fun unfollowUser(currentUserId: String, targetUserId: String, onComplete: ((Boolean) -> Unit)? = null) {
        addProcessingUser(targetUserId)
        socialRepository.unfollowUser(currentUserId, targetUserId) { result ->
            removeProcessingUser(targetUserId)
            if (result is com.picflick.app.data.Result.Success) {
                // Remove from local list immediately for instant UI update
                followingUsers.removeAll { it.uid == targetUserId }
                android.util.Log.d("FriendsViewModel", "Removed user $targetUserId from following list")
                Analytics.trackUnfollow()
                onComplete?.invoke(true)
            } else {
                android.util.Log.e("FriendsViewModel", "Failed to unfollow user $targetUserId")
                errorMessage = "Failed to delete friend. Please try again."
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Accept a follow request
     */
    fun acceptFollowRequest(currentUserId: String, currentUserName: String, requester: UserProfile) {
        addProcessingUser(requester.uid)
        viewModelScope.launch {
            socialRepository.acceptFollowRequest(
                currentUserId = currentUserId,
                currentUserName = currentUserName.ifBlank { "Someone" },
                requesterId = requester.uid
            )
            removeProcessingUser(requester.uid)
            Analytics.trackFriendRequestAccepted()
        }
    }

    /**
     * Decline/reject an incoming follow request.
     */
    fun declineFollowRequest(currentUserId: String, requesterId: String) {
        addProcessingUser(requesterId)
        viewModelScope.launch {
            val result = socialRepository.declineFollowRequest(currentUserId, requesterId)
            if (result is com.picflick.app.data.Result.Error) {
                android.util.Log.e("FriendsViewModel", "Failed to decline follow request: ${result.exception?.message}")
                errorMessage = result.exception.message ?: result.message
            }
            removeProcessingUser(requesterId)
        }
    }

    /**
     * Cancel a follow request (withdraw request sent to another user)
     */
    fun cancelFollowRequest(currentUserId: String, targetUserId: String) {
        android.util.Log.d("FriendsViewModel", "cancelFollowRequest called: $currentUserId -> $targetUserId")
        addProcessingUser(targetUserId)
        viewModelScope.launch {
            val result = socialRepository.cancelFollowRequest(currentUserId, targetUserId)
            // Log error if cancel fails
            if (result is com.picflick.app.data.Result.Error) {
                android.util.Log.e("FriendsViewModel", "Failed to cancel follow request: ${result.exception?.message}")
            } else if (result is com.picflick.app.data.Result.Success) {
                android.util.Log.d("FriendsViewModel", "Successfully cancelled follow request")
            }
            removeProcessingUser(targetUserId)
        }
    }

    /**
     * Sync contacts - wrapper function for compatibility
     */
    fun syncContacts(context: Context, currentUserId: String? = null) {
        val userId = currentUserId ?: return
        loadContacts(context, userId)
    }

    /**
     * Add a user to processing list (to show loading state)
     */
    fun addProcessingUser(userId: String) {
        if (!processingUserIds.contains(userId)) {
            processingUserIds.add(userId)
        }
    }

    /**
     * Remove a user from processing list
     */
    fun removeProcessingUser(userId: String) {
        processingUserIds.remove(userId)
    }

    /**
     * Load contacts from phone and find matching PicFlick users
     */
    private fun loadContacts(context: Context, currentUserId: String) {
        isLoading = true
        contactUsers.clear()

        viewModelScope.launch {
            try {
                // Get phone numbers from contacts
                val phoneNumbers = mutableListOf<String>()
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )

                cursor?.use {
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val number = it.getString(numberIndex)
                        // Normalize phone number - remove all non-digits
                        val normalized = number.replace("[^0-9]".toRegex(), "")
                        // Add both full number and last 10 digits for matching
                        if (normalized.length >= 10) {
                            phoneNumbers.add(normalized)
                            // Also add last 10 digits (without country code) for better matching
                            val last10 = normalized.takeLast(10)
                            if (last10.length == 10 && !phoneNumbers.contains(last10)) {
                                phoneNumbers.add(last10)
                            }
                        }
                    }
                }

                // Find matching PicFlick users
                if (phoneNumbers.isNotEmpty()) {
                    flickRepository.findUsersByPhoneNumbers(phoneNumbers.toList()) { result ->
                        when (result) {
                            is Result.Success -> {
                                contactUsers.clear()
                                contactUsers.addAll(result.data)
                            }
                            is Result.Error -> {
                    errorMessage = result.message
                }
                            is Result.Loading -> { }
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle permission denied or other errors
                errorMessage = "Failed to load contacts: ${e.message}"
            }
            isLoading = false
        }
    }
}