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
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import com.picflick.app.data.getDailyUploadLimit
import com.picflick.app.data.getStorageLimitBytes

/**
 * ViewModel for handling photo upload with filters and daily limits
 */
class UploadViewModel : ViewModel() {

    companion object {
        private const val STORAGE_HARD_STOP_THRESHOLD = 1.10
        private const val MAX_UPLOAD_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 500L
    }

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
                
            } catch (_: Exception) {
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
     * Recalculate precise storage usage from Firebase Storage and persist to Firestore.
     */
    private suspend fun recalculateStorageUsedBytes(userId: String) {
        try {
            val root = storage.reference.child("photos").child(userId)
            val totalBytes = calculateFolderBytesRecursive(root)
            firestore.collection("users").document(userId)
                .update("storageUsedBytes", totalBytes)
                .await()
        } catch (e: Exception) {
            android.util.Log.w("UploadViewModel", "Failed to recalculate storageUsedBytes", e)
        }
    }

    private suspend fun calculateFolderBytesRecursive(folderRef: StorageReference): Long {
        val listResult = folderRef.listAll().await()
        var total = 0L

        listResult.items.forEach { itemRef ->
            total += itemRef.metadata.await().sizeBytes
        }

        listResult.prefixes.forEach { childFolder ->
            total += calculateFolderBytesRecursive(childFolder)
        }

        return total
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
        @Suppress("UNUSED_PARAMETER") filter: PhotoFilter,
        taggedFriends: List<String> = emptyList(),
        description: String = "",
        onOptimisticAdd: ((Flick) -> Unit)? = null,
        onOptimisticRemove: ((String, Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            var optimisticFlickId: String? = null
            try {
                // Check daily limit based on subscription tier
                val dailyLimit = userProfile.getEffectiveTier().getDailyUploadLimit()
                if (dailyUploadCount >= dailyLimit) {
                    uploadError = "Daily upload limit reached (${dailyUploadCount}/${dailyLimit}). Try again tomorrow!"
                    return@launch
                }

                // Storage policy: warn from 90%, allow uploads up to 110%, hard-stop beyond 110%.
                val tierStorageLimit = userProfile.getEffectiveTier().getStorageLimitBytes()
                val hardStorageLimit = (tierStorageLimit * STORAGE_HARD_STOP_THRESHOLD).toLong()
                val estimatedUploadSize = estimatePhotoSizeBytes(context, photoUri)
                val projectedStorageUsed = userProfile.storageUsedBytes + estimatedUploadSize
                if (projectedStorageUsed > hardStorageLimit) {
                    val hardLimitGb = hardStorageLimit / (1024.0 * 1024.0 * 1024.0)
                    uploadError = "Storage limit reached (${String.format(Locale.getDefault(), "%.1f", hardLimitGb)} GB max). Delete photos or upgrade your plan to keep uploading."
                    return@launch
                }

                isUploading = true
                uploadError = null
                uploadSuccess = false

                val clientUploadId = UUID.randomUUID().toString()
                val optimisticFlick = Flick(
                    id = "optimistic_$clientUploadId",
                    userId = userProfile.uid,
                    userName = userProfile.displayName,
                    userPhotoUrl = userProfile.photoUrl,
                    imageUrl = photoUri.toString(),
                    description = description,
                    timestamp = System.currentTimeMillis(),
                    reactions = emptyMap(),
                    commentCount = 0,
                    privacy = "friends",
                    taggedFriends = taggedFriends,
                    clientUploadId = clientUploadId
                )
                optimisticFlickId = optimisticFlick.id
                onOptimisticAdd?.invoke(optimisticFlick)

                // 1. Upload image to Firebase Storage
                val (imageUrl, uploadedBytes) = uploadImageToStorage(context, photoUri, userProfile.uid)

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
                    taggedFriends = taggedFriends,
                    imageSizeBytes = uploadedBytes,
                    clientUploadId = clientUploadId
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
                recalculateStorageUsedBytes(userProfile.uid)

                val optimisticId = optimisticFlickId
                if (optimisticId != null && onOptimisticRemove != null) {
                    onOptimisticRemove(optimisticId, true)
                }
                uploadSuccess = true

            } catch (e: Exception) {
                optimisticFlickId?.let { onOptimisticRemove?.invoke(it, false) }
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

    private fun estimatePhotoSizeBytes(context: Context, photoUri: Uri): Long {
        return try {
            val sizeFromCursor = context.contentResolver.query(photoUri, null, null, null, null)?.use { cursor ->
                val sizeColumn = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeColumn >= 0 && cursor.moveToFirst()) cursor.getLong(sizeColumn) else -1L
            } ?: -1L

            if (sizeFromCursor > 0L) return sizeFromCursor

            context.contentResolver.openAssetFileDescriptor(photoUri, "r")?.use { afd ->
                val len = afd.length
                if (len > 0L) len else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun uploadPhotosBatch(
        context: Context,
        photoUris: List<Uri>,
        userProfile: UserProfile,
        onOptimisticAdd: ((Flick) -> Unit)? = null,
        onOptimisticRemove: ((String, Boolean) -> Unit)? = null,
        onBatchSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            if (photoUris.isEmpty()) return@launch

            val tier = userProfile.getEffectiveTier()
            val dailyLimit = tier.getDailyUploadLimit()
            val remainingDaily = if (dailyLimit == Int.MAX_VALUE) Int.MAX_VALUE else (dailyLimit - dailyUploadCount).coerceAtLeast(0)
            val perBatchCap = if (tier == com.picflick.app.data.SubscriptionTier.ULTRA) 100 else remainingDaily
            val allowedCount = photoUris.size.coerceAtMost(perBatchCap)

            if (allowedCount <= 0) {
                uploadError = "Daily upload limit reached (${dailyUploadCount}/${dailyLimit}). Try again tomorrow!"
                return@launch
            }

            isUploading = true
            uploadError = null
            uploadSuccess = false

            val tierStorageLimit = tier.getStorageLimitBytes()
            val hardStorageLimit = (tierStorageLimit * STORAGE_HARD_STOP_THRESHOLD).toLong()
            var rollingStorageUsed = userProfile.storageUsedBytes

            var successCount = 0
            var failCount = 0

            photoUris.take(allowedCount).forEach { photoUri ->
                var optimisticFlickId: String? = null
                try {
                    val estimatedUploadSize = estimatePhotoSizeBytes(context, photoUri)
                    val projectedStorageUsed = rollingStorageUsed + estimatedUploadSize
                    if (projectedStorageUsed > hardStorageLimit) {
                        failCount++
                        return@forEach
                    }

                    val clientUploadId = UUID.randomUUID().toString()
                    val optimisticFlick = Flick(
                        id = "optimistic_$clientUploadId",
                        userId = userProfile.uid,
                        userName = userProfile.displayName,
                        userPhotoUrl = userProfile.photoUrl,
                        imageUrl = photoUri.toString(),
                        description = "",
                        timestamp = System.currentTimeMillis(),
                        reactions = emptyMap(),
                        commentCount = 0,
                        privacy = "friends",
                        taggedFriends = emptyList(),
                        clientUploadId = clientUploadId
                    )
                    optimisticFlickId = optimisticFlick.id
                    onOptimisticAdd?.invoke(optimisticFlick)

                    val (imageUrl, uploadedBytes) = uploadImageToStorage(context, photoUri, userProfile.uid)
                    val flick = Flick(
                        id = "",
                        userId = userProfile.uid,
                        userName = userProfile.displayName,
                        userPhotoUrl = userProfile.photoUrl,
                        imageUrl = imageUrl,
                        description = "",
                        timestamp = System.currentTimeMillis(),
                        reactions = emptyMap(),
                        commentCount = 0,
                        privacy = "friends",
                        taggedFriends = emptyList(),
                        imageSizeBytes = uploadedBytes,
                        clientUploadId = clientUploadId
                    )

                    val result = flickRepository.createFlick(flick, userProfile.photoUrl)
                    if (result is com.picflick.app.data.Result.Error) {
                        throw Exception(result.message)
                    }

                    incrementDailyUploadCount(userProfile.uid)
                    dailyUploadCount++
                    incrementTotalPhotos(userProfile.uid)
                    rollingStorageUsed += uploadedBytes

                    val optimisticId = optimisticFlickId
                    if (optimisticId != null && onOptimisticRemove != null) {
                        onOptimisticRemove(optimisticId, true)
                    }
                    successCount++
                } catch (e: Exception) {
                    optimisticFlickId?.let { onOptimisticRemove?.invoke(it, false) }
                    failCount++
                    android.util.Log.w("UploadViewModel", "Batch upload item failed", e)
                }
            }

            recalculateStorageUsedBytes(userProfile.uid)

            if (successCount > 0) {
                uploadSuccess = true
                onBatchSuccess?.invoke()
            }
            if (failCount > 0) {
                uploadError = if (successCount > 0) {
                    "$successCount uploaded, $failCount failed"
                } else {
                    "Batch upload failed"
                }
            }

            isUploading = false
        }
    }

    /**
     * Upload image file to Firebase Storage
     */
    private suspend fun uploadImageToStorage(
        context: Context,
        photoUri: Uri,
        userId: String
    ): Pair<String, Long> {
        val storageRef = storage.reference
        val imageName = "photos/${userId}/${UUID.randomUUID()}.jpg"
        val imageRef = storageRef.child(imageName)

        var lastError: Exception? = null

        repeat(MAX_UPLOAD_RETRIES) { attempt ->
            try {
                // Re-open stream on every retry attempt (streams are one-shot).
                context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
                    imageRef.putStream(inputStream).await()
                } ?: throw IllegalStateException("Cannot open photo stream")

                // Get exact uploaded size + download URL
                val uploadedBytes = imageRef.metadata.await().sizeBytes
                val downloadUrl = imageRef.downloadUrl.await().toString()
                return downloadUrl to uploadedBytes
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_UPLOAD_RETRIES - 1) {
                    val delayMs = RETRY_BASE_DELAY_MS * (attempt + 1)
                    delay(delayMs)
                }
            }
        }

        throw lastError ?: IllegalStateException("Upload failed")
    }
}
