package com.example.picflick.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picflick.data.Flick
import com.example.picflick.data.ReactionType
import com.example.picflick.data.Result
import com.example.picflick.repository.FlickRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the home screen with flicks feed
 */
class HomeViewModel : ViewModel() {

    private val repository = FlickRepository.getInstance()

    var flicks = mutableStateListOf<Flick>()
        private set

    var exploreFlicks = mutableStateListOf<Flick>()
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
     * Load flicks for the feed (only friends' photos for privacy)
     * Uses stored currentUserId
     */
    fun loadFlicks() {
        val userId = currentUserId
        if (userId == null) {
            errorMessage = "User not logged in"
            return
        }
        
        isLoading = true
        errorMessage = null
        
        repository.getFlicksForUser(userId) { result ->
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
     * Load flicks with explicit userId (called when userId might have changed)
     */
    fun loadFlicks(userId: String) {
        currentUserId = userId
        loadFlicks()
    }

    /**
     * Load explore flicks (all public/friends photos for discovery)
     */
    fun loadExploreFlicks() {
        isLoading = true
        errorMessage = null
        
        repository.getExploreFlicks { result ->
            when (result) {
                is Result.Success -> {
                    exploreFlicks.clear()
                    exploreFlicks.addAll(result.data)
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
     * Toggle like on a flick (deprecated - use toggleReaction)
     */
    fun toggleLike(flick: Flick, userId: String, userName: String, userPhotoUrl: String) {
        // Map to LIKE reaction
        val userReaction = flick.getUserReaction(userId)
        val newReaction = if (userReaction == ReactionType.LIKE) null else ReactionType.LIKE
        toggleReaction(flick, userId, userName, userPhotoUrl, newReaction)
    }
    
    /**
     * Toggle or update reaction on a flick
     * @param reactionType null = remove reaction
     */
    fun toggleReaction(
        flick: Flick, 
        userId: String, 
        userName: String, 
        userPhotoUrl: String,
        reactionType: ReactionType?
    ) {
        repository.toggleReaction(flick.id, userId, userName, userPhotoUrl, reactionType) { result ->
            when (result) {
                is Result.Success -> {
                    // Update local state optimistically
                    val index = flicks.indexOfFirst { it.id == flick.id }
                    if (index != -1) {
                        val newReactions = flick.reactions.toMutableMap().apply {
                            if (reactionType == null) {
                                remove(userId)
                            } else {
                                put(userId, reactionType.name)
                            }
                        }
                        flicks[index] = flick.copy(reactions = newReactions.toMap())
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
        privacy: String = "friends", // Default to friends-only privacy
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
                        
                        // Create flick document with privacy setting
                        val flick = Flick(
                            id = "",
                            userId = userId,
                            userName = userDisplayName,
                            userPhotoUrl = userPhotoUrl,
                            imageUrl = imageUrl,
                            description = caption,
                            timestamp = System.currentTimeMillis(),
                            reactions = emptyMap(),
                            privacy = privacy // Respect privacy setting
                        )
                        
                        // Save to Firestore and notify friends
                        val createResult = repository.createFlick(flick, userPhotoUrl)
                        
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
