package com.picflick.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picflick.app.data.Flick
import com.picflick.app.data.PhotoFilter
import com.picflick.app.data.UserProfile
import com.picflick.app.repository.FlickRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID
import com.picflick.app.data.getDailyUploadLimit

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

    /**
     * Load today's upload count for a user from their profile
     * This tracks upload ACTIONS, not current photos (persists even if photos are deleted)
     */
    fun loadDailyUploadCount(userProfile: UserProfile) {
        viewModelScope.launch {
            try {
                isLoadingUploadCount = true
                
                // Get today's date string for comparison
                val calendar = Calendar.getInstance()
                val todayDate = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
                
                // Check if we need to reset the counter (new day)
                if (userProfile.lastUploadResetDate != todayDate) {
                    // It's a new day - reset the counter in Firestore
                    firestore.collection("users").document(userProfile.uid)
                        .update(mapOf(
                            "dailyUploadsToday" to 0,
                            "lastUploadResetDate" to todayDate
                        ))
                        .await()
                    dailyUploadCount = 0
                } else {
                    // Same day - use the stored count
                    dailyUploadCount = userProfile.dailyUploadsToday
                }
                
            } catch (e: Exception) {
                // If error, use the profile value or assume 0
                dailyUploadCount = userProfile.dailyUploadsToday
            } finally {
                isLoadingUploadCount = false
            }
        }
    }

    /**
     * Increment the daily upload count in Firestore
     * Call this after successful upload
     */
    private suspend fun incrementDailyUploadCount(userId: String) {
        try {
            val calendar = Calendar.getInstance()
            val todayDate = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
            
            firestore.collection("users").document(userId)
                .update(mapOf(
                    "dailyUploadsToday" to com.google.firebase.firestore.FieldValue.increment(1),
                    "lastUploadResetDate" to todayDate
                ))
                .await()
        } catch (e: Exception) {
            // Silently fail - the upload succeeded, just the counter update failed
            android.util.Log.w("UploadViewModel", "Failed to update daily upload count", e)
        }
    }

    /**
     * Increment the total photos count in Firestore
     * This tracks lifetime photos uploaded (never decrements on delete)
     */
    private suspend fun incrementTotalPhotos(userId: String) {
        try {
            firestore.collection("users").document(userId)
                .update("totalPhotos", com.google.firebase.firestore.FieldValue.increment(1))
                .await()
        } catch (e: Exception) {
            // Silently fail - the upload succeeded, just the counter update failed
            android.util.Log.w("UploadViewModel", "Failed to update total photos count", e)
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
                // Check daily limit based on subscription tier
                val dailyLimit = userProfile.subscriptionTier.getDailyUploadLimit()
                if (dailyUploadCount >= dailyLimit) {
                    uploadError = "Daily upload limit reached (${dailyUploadCount}/${dailyLimit}). Try again tomorrow!"
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
                
                if (result is com.picflick.app.data.Result.Error) {
                    throw Exception(result.message)
                }
                
                // Increment daily count in Firestore (persists even if photo is deleted later)
                incrementDailyUploadCount(userProfile.uid)
                dailyUploadCount++

                // Increment total photos count
                incrementTotalPhotos(userProfile.uid)

                uploadSuccess = true
                
            } catch (e: Exception) {
                uploadError = e.message ?: "Upload failed"
            } finally {
                isUploading = false
            }
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
