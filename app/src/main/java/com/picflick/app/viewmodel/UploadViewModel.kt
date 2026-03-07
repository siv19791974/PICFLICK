package com.picflick.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.picflick.data.Flick
import com.app.picflick.data.PhotoFilter
import com.app.picflick.data.UserProfile
import com.app.picflick.repository.FlickRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

/**
 * ViewModel for handling photo upload with filters and daily limits
 */
class UploadViewModel : ViewModel() {

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val flickRepository = FlickRepository.getInstance()

    var isUploading by mutableStateOf(false)
        private set

    var uploadError by mutableStateOf<String?>(null)
        private set
    var uploadSuccess by mutableStateOf(false)
        private set
    
    // Daily upload tracking
    var dailyUploadCount by mutableIntStateOf(0)
        private set
    var isLoadingUploadCount by mutableStateOf(true)
        private set
    
    companion object {
        const val MAX_DAILY_UPLOADS = 5
    }

    /**
     * Load today's upload count for a user
     */
    fun loadDailyUploadCount(userId: String) {
        viewModelScope.launch {
            try {
                isLoadingUploadCount = true
                
                // Get start of today timestamp
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = calendar.timeInMillis
                
                // Query flicks created today by this user
                val querySnapshot = firestore.collection("flicks")
                    .whereEqualTo("userId", userId)
                    .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                    .get()
                    .await()
                
                dailyUploadCount = querySnapshot.size()
                
            } catch (e: Exception) {
                // If error, assume 0 uploads to be safe
                dailyUploadCount = 0
            } finally {
                isLoadingUploadCount = false
            }
        }
    }

    /**
     * Upload a photo to Firebase Storage and create a Flick
     *
     * @param photoUri URI of the filtered photo
     * @param userProfile Current user's profile
     * @param filter The filter applied to the photo
     * @param taggedFriends List of tagged friend userIds
     * @param description Optional description for the photo
     */
    fun uploadPhoto(
        context: Context,
        photoUri: Uri,
        userProfile: UserProfile,
        filter: PhotoFilter,
        taggedFriends: List<String> = emptyList(),
        description: String = ""
    ) {
        viewModelScope.launch {
            try {
                // Check daily limit
                if (dailyUploadCount >= MAX_DAILY_UPLOADS) {
                    uploadError = "Daily upload limit reached (5/5). Try again tomorrow!"
                    return@launch
                }
                
                isUploading = true
                uploadError = null
                uploadSuccess = false

                // 1. Upload image to Firebase Storage
                val imageUrl = uploadImageToStorage(context, photoUri, userProfile.uid)

                // 2. Create Flick using repository (sends notifications to followers & tagged friends)
                val flick = Flick(
                    id = "", // Will be set by Firestore
                    userId = userProfile.uid,
                    userName = userProfile.displayName,
                    userPhotoUrl = userProfile.photoUrl,
                    imageUrl = imageUrl,
                    description = description,
                    timestamp = System.currentTimeMillis(),
                    reactions = emptyMap(),
                    commentCount = 0,
                    privacy = "friends",
                    taggedFriends = taggedFriends
                )

                // Use repository to create flick - this sends notifications!
                val result = flickRepository.createFlick(flick, userProfile.photoUrl)
                
                if (result is com.app.picflick.data.Result.Error) {
                    throw Exception(result.message)
                }
                
                // Increment daily count
                dailyUploadCount++

                uploadSuccess = true
                
                // Check for photo upload achievements
                checkPhotoAchievements(userProfile)

            } catch (e: Exception) {
                uploadError = e.message ?: "Upload failed"
            } finally {
                isUploading = false
            }
        }
    }
    
    /**
     * Check and trigger photo upload achievements
     */
    private suspend fun checkPhotoAchievements(userProfile: UserProfile) {
        try {
            // Get total photo count for user
            val photoCountSnapshot = firestore.collection("flicks")
                .whereEqualTo("userId", userProfile.uid)
                .get()
                .await()
            
            val totalPhotos = photoCountSnapshot.size()
            
            // First photo achievement
            if (totalPhotos == 1) {
                flickRepository.createFirstPhotoAchievement(
                    userProfile.uid,
                    userProfile.displayName
                )
            }
            
            // Active user achievement (5+ photos)
            if (totalPhotos == 5) {
                flickRepository.createActiveUserAchievement(
                    userProfile.uid,
                    userProfile.displayName,
                    totalPhotos
                )
            }
            
        } catch (e: Exception) {
            // Silently fail - don't block upload success
            e.printStackTrace()
        }
    }

    /**
     * Reset upload states after successful upload
     */
    fun resetUploadState() {
        uploadSuccess = false
        uploadError = null
    }

    /**
     * Upload image file to Firebase Storage
     */
    private suspend fun uploadImageToStorage(
        context: Context,
        photoUri: Uri,
        userId: String
    ): String {
        val storageRef = storage.reference
        val imageName = "photos/${userId}/${UUID.randomUUID()}.jpg"
        val imageRef = storageRef.child(imageName)

        // Upload the file
        context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
            imageRef.putStream(inputStream).await()
        } ?: throw IllegalStateException("Cannot open photo stream")

        // Get download URL
        return imageRef.downloadUrl.await().toString()
    }
}
