package com.example.picflick.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picflick.data.Flick
import com.example.picflick.data.Result
import com.example.picflick.repository.FlickRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the user profile and my photos screen
 */
class ProfileViewModel : ViewModel() {

    private val repository = FlickRepository.getInstance()

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
     * Load photos for a specific user and calculate total reactions and streak
     */
    fun loadUserPhotos(userId: String) {
        isLoading = true
        errorMessage = null
        
        // Load photos
        repository.getUserFlicks(userId) { result ->
            when (result) {
                is Result.Success -> {
                    photos.clear()
                    photos.addAll(result.data)
                    photoCount = result.data.size
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
        
        // Load streak separately
        loadUserStreak(userId)
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
     * Clear error message
     */
    fun clearError() {
        errorMessage = null
    }
}
