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
import com.google.firebase.firestore.FieldValue
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
import com.picflick.app.utils.Analytics
import com.picflick.app.util.ImageResizer

/**
 * ViewModel for handling photo upload with filters and daily limits
 */
class UploadViewModel : ViewModel() {

    companion object {
        private const val STORAGE_HARD_STOP_THRESHOLD = 1.10
        private const val MAX_UPLOAD_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 500L
        private val STORAGE_WARNING_MILESTONES = listOf(5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
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
                    "dailyUploadsToday" to FieldValue.increment(1),
                    "lastUploadResetDate" to todayDate
                ))
                .await()
        } catch (e: Exception) {
            // Silently fail - the upload succeeded, just the counter update failed
            android.util.Log.w("UploadViewModel", "Failed to update daily upload count", e)
        }
    }

    fun consumeDailyUploadSlot(userId: String) {
        viewModelScope.launch {
            incrementDailyUploadCount(userId)
            dailyUploadCount++
        }
    }

    /**
     * Increment the total photos count in Firestore
     * This tracks lifetime photos uploaded (never decrements on delete)
     */
    private suspend fun incrementTotalPhotos(userId: String) {
        try {
            firestore.collection("users").document(userId)
                .update("totalPhotos", FieldValue.increment(1))
                .await()
        } catch (e: Exception) {
            // Silently fail - the upload succeeded, just the counter update failed
            android.util.Log.w("UploadViewModel", "Failed to update total photos count", e)
        }
    }

    // Storage is now tracked incrementally via flickRepository.incrementStorageUsedBytes()
    // (original + thumbnail sizes summed at upload time). No recursive Storage scans needed.

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
        sharedGroupId: String = "",
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
                    sharedGroupId = sharedGroupId,
                    taggedFriends = taggedFriends,
                    clientUploadId = clientUploadId
                )
                optimisticFlickId = optimisticFlick.id
                onOptimisticAdd?.invoke(optimisticFlick)

                // 1. Upload image to Firebase Storage (with thumbnails)
                val uploadResult = uploadImageToStorage(context, photoUri, userProfile.uid)

                // 2. Create Flick using repository (sends notifications to followers & tagged friends)
                val flick = Flick(
                    id = "", // Will be set by Firestore
                    userId = userProfile.uid,
                    userName = userProfile.displayName,
                    userPhotoUrl = userProfile.photoUrl,
                    imageUrl = uploadResult.imageUrl,
                    thumbnailUrl512 = uploadResult.thumbnailUrl512,
                    thumbnailUrl1080 = uploadResult.thumbnailUrl1080,
                    description = description,
                    timestamp = System.currentTimeMillis(),
                    reactions = emptyMap(),
                    commentCount = 0,
                    privacy = "friends",
                    sharedGroupId = sharedGroupId,
                    taggedFriends = taggedFriends,
                    imageSizeBytes = uploadResult.uploadedBytes,
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

                // Increment total photos count and storage
                incrementTotalPhotos(userProfile.uid)
                flickRepository.incrementStorageUsedBytes(userProfile.uid, uploadResult.totalBytes)

                onOptimisticRemove?.invoke(optimisticFlickId, true)
                uploadSuccess = true
                Analytics.trackPhotoUploaded(source = "single", privacy = if (sharedGroupId.isBlank()) "friends" else "album")

            } catch (e: Exception) {
                optimisticFlickId?.let { onOptimisticRemove?.invoke(it, false) }
                uploadError = e.message ?: "Upload failed"
                Analytics.trackError("upload_single_failed")
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
        sharedGroupId: String = "",
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
                        sharedGroupId = sharedGroupId,
                        taggedFriends = emptyList(),
                        clientUploadId = clientUploadId
                    )
                    optimisticFlickId = optimisticFlick.id
                    onOptimisticAdd?.invoke(optimisticFlick)

                    val uploadResult = uploadImageToStorage(context, photoUri, userProfile.uid)
                    val flick = Flick(
                        id = "",
                        userId = userProfile.uid,
                        userName = userProfile.displayName,
                        userPhotoUrl = userProfile.photoUrl,
                        imageUrl = uploadResult.imageUrl,
                        thumbnailUrl512 = uploadResult.thumbnailUrl512,
                        thumbnailUrl1080 = uploadResult.thumbnailUrl1080,
                        description = "",
                        timestamp = System.currentTimeMillis(),
                        reactions = emptyMap(),
                        commentCount = 0,
                        privacy = "friends",
                        sharedGroupId = sharedGroupId,
                        taggedFriends = emptyList(),
                        imageSizeBytes = uploadResult.uploadedBytes,
                        clientUploadId = clientUploadId
                    )

                    val result = flickRepository.createFlick(flick, userProfile.photoUrl)
                    if (result is com.picflick.app.data.Result.Error) {
                        throw Exception(result.message)
                    }

                    incrementDailyUploadCount(userProfile.uid)
                    dailyUploadCount++
                    incrementTotalPhotos(userProfile.uid)
                    rollingStorageUsed += uploadResult.totalBytes

                    onOptimisticRemove?.invoke(optimisticFlickId, true)
                    successCount++
                } catch (e: Exception) {
                    optimisticFlickId?.let { onOptimisticRemove?.invoke(it, false) }
                    failCount++
                    android.util.Log.w("UploadViewModel", "Batch upload item failed", e)
                }
            }

            // Apply incremental storage update for the entire batch (one Firestore write)
            val totalBytesAdded = rollingStorageUsed - userProfile.storageUsedBytes
            if (totalBytesAdded > 0) {
                flickRepository.incrementStorageUsedBytes(userProfile.uid, totalBytesAdded)
            }

            if (successCount > 0) {
                uploadSuccess = true
                Analytics.trackPhotoUploaded(source = "batch", privacy = if (sharedGroupId.isBlank()) "friends" else "album")
                onBatchSuccess?.invoke()
            }
            if (failCount > 0) {
                uploadError = if (successCount > 0) {
                    "$successCount uploaded, $failCount failed"
                } else {
                    "Batch upload failed"
                }
                Analytics.trackError("upload_batch_failed")
            }

            isUploading = false
        }
    }

    data class UploadResult(
        val imageUrl: String,
        val thumbnailUrl512: String,
        val thumbnailUrl1080: String,
        val uploadedBytes: Long,
        val totalBytes: Long = uploadedBytes // original + all thumbnails
    )

    /**
     * Upload image file to Firebase Storage along with 256px and 512px thumbnails.
     */
    private suspend fun uploadImageToStorage(
        context: Context,
        photoUri: Uri,
        userId: String
    ): UploadResult {
        val storageRef = storage.reference
        val baseName = UUID.randomUUID().toString()
        val imageName = "photos/${userId}/${baseName}.jpg"
        val imageRef = storageRef.child(imageName)

        var lastError: Exception? = null

        repeat(MAX_UPLOAD_RETRIES) { attempt ->
            try {
                // Upload original image
                context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
                    imageRef.putStream(inputStream).await()
                } ?: throw IllegalStateException("Cannot open photo stream")

                val originalBytes = imageRef.metadata.await().sizeBytes
                val downloadUrl = imageRef.downloadUrl.await().toString()

                // Generate and upload 512px (grid) and 1080px (fullscreen) thumbnails
                val thumbnail512Url = uploadThumbnail(
                    context, photoUri, userId, baseName,
                    ImageResizer.THUMBNAIL_SIZE_512, "thumbnails_512"
                )
                val thumbnail1080Url = uploadThumbnail(
                    context, photoUri, userId, baseName,
                    ImageResizer.THUMBNAIL_SIZE_1080, "thumbnails_1080"
                )

                // Read thumbnail metadata sizes for accurate incremental storage tracking
                val thumb512Ref = storage.reference.child("photos/${userId}/thumbnails_512/${baseName}.jpg")
                val thumb1080Ref = storage.reference.child("photos/${userId}/thumbnails_1080/${baseName}.jpg")
                val thumb512Bytes = runCatching { thumb512Ref.metadata.await().sizeBytes }.getOrDefault(0L)
                val thumb1080Bytes = runCatching { thumb1080Ref.metadata.await().sizeBytes }.getOrDefault(0L)
                val totalBytes = originalBytes + thumb512Bytes + thumb1080Bytes

                return UploadResult(
                    imageUrl = downloadUrl,
                    thumbnailUrl512 = thumbnail512Url ?: downloadUrl,
                    thumbnailUrl1080 = thumbnail1080Url ?: downloadUrl,
                    uploadedBytes = originalBytes,
                    totalBytes = totalBytes
                )
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

    private suspend fun uploadThumbnail(
        context: Context,
        photoUri: Uri,
        userId: String,
        baseName: String,
        maxDimension: Int,
        folderName: String
    ): String? {
        return try {
            val thumbBytes = ImageResizer.resizeToThumbnail(context, photoUri, maxDimension)
                ?: return null
            val thumbRef = storage.reference.child("photos/${userId}/${folderName}/${baseName}.jpg")
            thumbRef.putBytes(thumbBytes).await()
            thumbRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            android.util.Log.w("UploadViewModel", "Thumbnail upload failed for $maxDimension", e)
            null
        }
    }
}
