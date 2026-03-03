package com.example.picflick.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picflick.data.Flick
import com.example.picflick.data.Result
import com.example.picflick.repository.FlickRepository
import kotlinx.coroutines.launch
import java.util.UUID

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
     * Search flicks by user name or description
     */
    fun searchFlicks(query: String) {
        if (query.isBlank()) {
            loadFlicks()
            return
        }
        
        isLoading = true
        val searchQuery = query.lowercase()
        
        // Filter local flicks
        val filtered = flicks.filter { flick ->
            flick.userName.lowercase().contains(searchQuery) ||
            flick.description.lowercase().contains(searchQuery)
        }
        
        flicks.clear()
        flicks.addAll(filtered)
        isLoading = false
    }

    /**
     * Clear search and reload all flicks
     */
    fun clearSearch() {
        loadFlicks()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        errorMessage = null
    }

    /**
     * Upload a new flick
     */
    fun uploadFlick(
        userId: String,
        userDisplayName: String,
        userPhotoUrl: String,
        imageUri: android.net.Uri,
        context: android.content.Context,
        caption: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                // Read image bytes
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val imageBytes = inputStream?.readBytes()
                inputStream?.close()
                
                if (imageBytes == null) {
                    errorMessage = "Failed to read image"
                    isLoading = false
                    onComplete(false)
                    return@launch
                }
                
                // Upload image to Firebase Storage
                val uploadResult = repository.uploadFlickImage(userId, imageBytes)
                
                when (uploadResult) {
                    is Result.Success -> {
                        val imageUrl = uploadResult.data
                        
                        // Create flick document
                        val flick = Flick(
                            id = "",
                            userId = userId,
                            userName = userDisplayName,
                            imageUrl = imageUrl,
                            description = caption,
                            timestamp = System.currentTimeMillis(),
                            likes = emptyList()
                        )
                        
                        // Save to Firestore
                        val createResult = repository.createFlick(flick)
                        
                        when (createResult) {
                            is Result.Success -> {
                                todayUploadCount++
                                loadFlicks() // Refresh feed
                                isLoading = false
                                onComplete(true)
                            }
                            is Result.Error -> {
                                errorMessage = createResult.message
                                isLoading = false
                                onComplete(false)
                            }
                            is Result.Loading -> { }
                        }
                    }
                    is Result.Error -> {
                        errorMessage = uploadResult.message
                        isLoading = false
                        onComplete(false)
                    }
                    is Result.Loading -> { }
                }
            } catch (e: Exception) {
                errorMessage = "Upload failed: ${e.message}"
                isLoading = false
                onComplete(false)
            }
        }
    }
}
