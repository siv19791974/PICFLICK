package com.example.picflick.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.picflick.data.Flick
import com.example.picflick.data.Result
import com.example.picflick.repository.FlickRepository

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

    var photoCount by mutableStateOf(0)
        private set

    /**
     * Load photos for a specific user
     */
    fun loadUserPhotos(userId: String) {
        isLoading = true
        errorMessage = null
        
        repository.getUserFlicks(userId) { result ->
            when (result) {
                is Result.Success -> {
                    photos.clear()
                    photos.addAll(result.data)
                    photoCount = result.data.size
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
     * Clear error message
     */
    fun clearError() {
        errorMessage = null
    }
}
