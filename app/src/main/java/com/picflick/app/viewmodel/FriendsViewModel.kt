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

    var isLoading by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var contactUsers = mutableStateListOf<UserProfile>()
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
     * Search for users by name
     */
    fun searchUsers(query: String, currentUserId: String) {
        searchQuery = query
        if (query.isBlank()) {
            searchResults.clear()
            return
        }

        isLoading = true
        repository.searchUsers(query) { result ->
            when (result) {
                is Result.Success -> {
                    searchResults.clear()
                    // Filter out current user
                    searchResults.addAll(result.data.filter { it.uid != currentUserId })
                }
                is Result.Error -> {
                    // Handle error silently
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
        isLoading = true
        repository.getSuggestedUsers(currentUserId) { result ->
            when (result) {
                is Result.Success -> {
                    suggestedUsers.clear()
                    suggestedUsers.addAll(result.data)
                }
                is Result.Error -> {
                    // Handle error silently
                }
                is Result.Loading -> { }
            }
            isLoading = false
        }
    }

    /**
     * Send a follow request
     */
    fun sendFollowRequest(currentUserId: String, targetUser: UserProfile, currentUserProfile: UserProfile) {
        viewModelScope.launch {
            repository.followUser(currentUserId, targetUser.uid)
        }
    }

    /**
     * Unfollow a user
     */
    fun unfollowUser(currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            repository.unfollowUser(currentUserId, targetUserId)
        }
    }

    /**
     * Accept a follow request
     */
    fun acceptFollowRequest(currentUserId: String, requester: UserProfile) {
        viewModelScope.launch {
            repository.acceptFollowRequest(currentUserId, requester.uid)
        }
    }

    /**
     * Load contacts from phone and find matching PicFlick users
     */
    fun loadContacts(context: Context, currentUserId: String) {
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
                        // Normalize phone number
                        val normalized = number.replace("[^0-9]".toRegex(), "")
                        if (normalized.length >= 10) {
                            phoneNumbers.add(normalized)
                        }
                    }
                }

                // Find matching PicFlick users
                if (phoneNumbers.isNotEmpty()) {
                    repository.findUsersByPhoneNumbers(phoneNumbers.toList(), currentUserId) { result ->
                        when (result) {
                            is Result.Success -> {
                                contactUsers.clear()
                                contactUsers.addAll(result.data)
                            }
                            is Result.Error -> {
                                // Handle error silently
                            }
                            is Result.Loading -> { }
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle permission denied or other errors
            }
            isLoading = false
        }
    }
}
