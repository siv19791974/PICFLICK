package com.picflick.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.picflick.app.data.Flick
import com.picflick.app.data.ReactionType
import com.picflick.app.data.Result
import com.picflick.app.repository.FlickRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the user profile and my photos screen
 */
class ProfileViewModel : ViewModel() {

    private val repository = FlickRepository.getInstance()

    private var photosListener: ListenerRegistration? = null

    var photos = mutableStateListOf<Flick>()
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var photoCount by mutableIntStateOf(0)
        private set

    var totalReactions by mutableIntStateOf(0)
        private set

    var currentStreak by mutableIntStateOf(0)
        private set

    /**
     * Load visible profile photos, plus the user's total uploaded flick count for the POSTS stat.
     */
    fun loadUserPhotos(userId: String, lifetimeUploadCount: Int = 0) {
        isLoading = true
        errorMessage = null
        photoCount = maxOf(photoCount, lifetimeUploadCount)

        // Remove previous listener to avoid duplicate registrations
        photosListener?.remove()

        // Load photos
        photosListener = repository.getUserFlicks(userId) { result ->
            when (result) {
                is Result.Success -> {
                    photos.clear()
                    photos.addAll(result.data)
                    photoCount = maxOf(photoCount, lifetimeUploadCount, result.data.size)
                    // Calculate ALL reactions (likes, loves, fires, etc.)
                    totalReactions = result.data.sumOf { it.getTotalReactions() }
                    isLoading = false
                }
                is Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                }
                is Result.Loading -> { /* Do nothing */ }
            }
        }

        viewModelScope.launch {
            when (val countResult = repository.getUserFlickUploadCount(userId)) {
                is Result.Success -> {
                    photoCount = maxOf(photoCount, lifetimeUploadCount, countResult.data)
                }
                is Result.Error -> {
                    photoCount = maxOf(photoCount, lifetimeUploadCount, photos.size)
                }
                is Result.Loading -> Unit
            }
        }

        // Load streak separately
        loadUserStreak(userId)
    }

    override fun onCleared() {
        super.onCleared()
        photosListener?.remove()
        photosListener = null
    }
    
    /**
     * Load user's current streak
     */
    private fun loadUserStreak(userId: String) {
        viewModelScope.launch {
            when (val result = repository.getUserStreak(userId)) {
                is Result.Success -> {
                    currentStreak = result.data.first
                }
                else -> {
                    currentStreak = 0
                }
            }
        }
    }
    
    /**
     * Toggle reaction on a photo
     */
    fun toggleReaction(
        flick: Flick,
        userId: String,
        userName: String,
        userPhotoUrl: String,
        reactionType: ReactionType?
    ) {
        repository.toggleReaction(
            flick.id,
            userId,
            userName,
            userPhotoUrl,
            reactionType
        ) { result ->
            when (result) {
                is Result.Success -> {
                    // Update local state
                    val index = photos.indexOfFirst { it.id == flick.id }
                    if (index != -1) {
                        val newReactions = flick.reactions.toMutableMap().apply {
                            if (reactionType == null) {
                                remove(userId)
                            } else {
                                put(userId, reactionType.name)
                            }
                        }
                        photos[index] = flick.copy(reactions = newReactions)
                        // Recalculate total
                        totalReactions = photos.sumOf { it.getTotalReactions() }
                    }
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
        }
    }
    
    /**
     * Delete a photo
     */
    fun deletePhoto(photoId: String, onComplete: (Boolean) -> Unit) {
        repository.deleteFlick(photoId) { result ->
            when (result) {
                is Result.Success -> {
                    // Remove from local list
                    val removed = photos.removeIf { it.id == photoId }
                    if (removed) {
                        photoCount = photos.size
                        totalReactions = photos.sumOf { it.getTotalReactions() }
                    }
                    onComplete(true)
                }
                is Result.Error -> {
                    errorMessage = result.message
                    onComplete(false)
                }
                is Result.Loading -> { }
            }
        }
    }

    fun deletePhotos(photoIds: Set<String>, onComplete: (Boolean) -> Unit) {
        if (photoIds.isEmpty()) {
            onComplete(false)
            return
        }

        var remaining = photoIds.size
        var deletedCount = 0
        var hadError = false

        photoIds.forEach { photoId ->
            repository.deleteFlick(photoId) { result ->
                when (result) {
                    is Result.Success -> {
                        val removed = photos.removeIf { it.id == photoId }
                        if (removed) deletedCount++
                        remaining--
                    }
                    is Result.Error -> {
                        hadError = true
                        errorMessage = result.message
                        remaining--
                    }
                    is Result.Loading -> Unit
                }

                if (remaining <= 0) {
                    photoCount = photos.size
                    totalReactions = photos.sumOf { it.getTotalReactions() }
                    onComplete(deletedCount > 0 && !hadError)
                }
            }
        }
    }
    
    /**
     * Update photo caption/description
     */
    fun updateCaption(photoId: String, newCaption: String) {
        viewModelScope.launch {
            val result = repository.updateFlickDescription(photoId, newCaption)
            when (result) {
                is Result.Success -> {
                    // Update local state
                    val index = photos.indexOfFirst { it.id == photoId }
                    if (index != -1) {
                        photos[index] = photos[index].copy(description = newCaption)
                    }
                }
                is Result.Error -> {
                    errorMessage = result.message
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
