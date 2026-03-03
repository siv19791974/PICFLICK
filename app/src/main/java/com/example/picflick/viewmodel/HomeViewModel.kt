package com.example.picflick.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.picflick.data.Flick
import com.example.picflick.data.Result
import com.example.picflick.data.UserProfile
import com.example.picflick.repository.FlickRepository

/**
 * ViewModel for the home screen with flicks feed
 */
class HomeViewModel : ViewModel() {

    private val repository = FlickRepository.getInstance()

    var flicks = mutableStateListOf<Flick>()
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var todayUploadCount by mutableIntStateOf(0)
        private set

    var currentUserId by mutableStateOf<String?>(null)
        private set

    /**
     * Load all flicks for the feed
     */
    fun loadFlicks() {
        isLoading = true
        errorMessage = null
        
        repository.getFlicks { result ->
            when (result) {
                is Result.Success -> {
                    flicks.clear()
                    flicks.addAll(result.data)
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
     * Check daily upload count for a user
     */
    fun checkDailyUploads(userId: String) {
        currentUserId = userId
        repository.getDailyUploadCount(userId) { result ->
            when (result) {
                is Result.Success -> {
                    todayUploadCount = result.data
                }
                is Result.Error -> {
                    // Silently fail - not critical
                }
                is Result.Loading -> { /* Do nothing */ }
            }
        }
    }

    /**
     * Toggle like on a flick
     */
    fun toggleLike(flick: Flick, userId: String) {
        val isLiked = flick.likes.contains(userId)
        
        repository.toggleLike(flick.id, userId, isLiked) { result ->
            when (result) {
                is Result.Success -> {
                    // Update local state optimistically
                    val index = flicks.indexOfFirst { it.id == flick.id }
                    if (index != -1) {
                        val updatedLikes = if (isLiked) {
                            flick.likes - userId
                        } else {
                            flick.likes + userId
                        }
                        flicks[index] = flick.copy(likes = updatedLikes)
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
     * Clear error message
     */
    fun clearError() {
        errorMessage = null
    }
}
