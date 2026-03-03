package com.example.picflick.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picflick.data.Result
import com.example.picflick.data.UserProfile
import com.example.picflick.repository.FlickRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for finding and managing friends
 */
class FriendsViewModel : ViewModel() {

    private val repository = FlickRepository.getInstance()

    var searchResults = mutableStateListOf<UserProfile>()
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var searchQuery by mutableStateOf("")
        private set

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
     * Follow a user
     */
    fun followUser(currentUserId: String, targetUser: UserProfile, currentUser: UserProfile) {
        viewModelScope.launch {
            val result = repository.followUser(currentUserId, targetUser.uid)
            when (result) {
                is Result.Success -> {
                    // Update local state to reflect the follow
                    val index = searchResults.indexOfFirst { it.uid == targetUser.uid }
                    if (index != -1) {
                        // Refresh search results to show updated state
                        searchUsers(searchQuery, currentUserId)
                    }
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { /* Do nothing */ }
            }
        }
    }

    /**
     * Unfollow a user
     */
    fun unfollowUser(currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            val result = repository.unfollowUser(currentUserId, targetUserId)
            when (result) {
                is Result.Success -> {
                    // Refresh search results
                    searchUsers(searchQuery, currentUserId)
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { /* Do nothing */ }
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
