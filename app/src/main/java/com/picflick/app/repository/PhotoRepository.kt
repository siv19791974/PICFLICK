package com.picflick.app.repository

import com.picflick.app.data.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Repository for photo/flick operations
 * Handles photo uploads, retrieval, likes, and reactions
 */
class PhotoRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val flickRepository = FlickRepository.getInstance()

    companion object {
        @Volatile
        private var instance: PhotoRepository? = null

        fun getInstance(): PhotoRepository {
            return instance ?: synchronized(this) {
                instance ?: PhotoRepository().also { instance = it }
            }
        }
    }

    /**
     * Upload flick image to Firebase Storage
     * @param userId The user uploading the photo
     * @param imageBytes The image data
     * @return Result with download URL or error
     */
    suspend fun uploadFlickImage(userId: String, imageBytes: ByteArray): Result<String> {
        return try {
            val filename = "photos/${userId}/${UUID.randomUUID()}.jpg"
            val storageRef = storage.reference.child(filename)

            storageRef.putBytes(imageBytes).await()
            val downloadUrl = storageRef.downloadUrl.await()

            Result.Success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.Error(e, "Failed to upload image")
        }
    }

    /**
     * Create a new flick and notify friends
     * @param flick The flick data to create
     * @param userPhotoUrl The uploader's profile photo URL
     * @return Result success or error
     */
    suspend fun createFlick(flick: Flick, userPhotoUrl: String): Result<Unit> {
        return try {
            // Add to Firestore
            val docRef = db.collection("flicks").add(flick).await()
            val flickId = docRef.id

            // Update with ID
            docRef.update("id", flickId).await()

            // Create notifications for friends via FlickRepository
            val updatedFlick = flick.copy(id = flickId)
            flickRepository.createPhotoNotifications(updatedFlick, userPhotoUrl)

            // Create notifications for tagged friends
            if (flick.taggedFriends.isNotEmpty()) {
                flick.taggedFriends.forEach { taggedUserId ->
                    flickRepository.createTagNotification(
                        flickId = flickId,
                        photoOwnerId = flick.userId,
                        photoOwnerName = flick.userName,
                        photoOwnerPhotoUrl = userPhotoUrl,
                        taggedUserId = taggedUserId
                    )
                }
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create flick")
        }
    }

    /**
     * Delete a flick
     * @param flickId The flick to delete
     * @param onResult Callback with result
     */
    fun deleteFlick(flickId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("flicks").document(flickId)
            .delete()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to delete flick")) }
    }

    /**
     * Toggle like on a flick
     * @param flickId The flick to like/unlike
     * @param userId The user liking the flick
     * @param onResult Callback with result
     */
    fun toggleLike(flickId: String, userId: String, onResult: (Result<Unit>) -> Unit) {
        val flickRef = db.collection("flicks").document(flickId)

        flickRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val likes = document.get("likes") as? List<String> ?: emptyList()
                    val likedByMe = likes.contains(userId)

                    val updateMap = if (likedByMe) {
                        mapOf(
                            "likes" to FieldValue.arrayRemove(userId),
                            "likesCount" to FieldValue.increment(-1)
                        )
                    } else {
                        mapOf(
                            "likes" to FieldValue.arrayUnion(userId),
                            "likesCount" to FieldValue.increment(1)
                        )
                    }

                    flickRef.update(updateMap)
                        .addOnSuccessListener { onResult(Result.Success(Unit)) }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to toggle like")) }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch flick")) }
    }

    /**
     * Toggle reaction on a flick
     * @param flickId The flick to react to
     * @param userId The user reacting
     * @param reactionType The reaction type (or null to remove)
     * @param onResult Callback with result
     */
    fun toggleReaction(
        flickId: String,
        userId: String,
        reactionType: ReactionType?,
        onResult: (Result<Unit>) -> Unit
    ) {
        val flickRef = db.collection("flicks").document(flickId)

        flickRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val reactions = document.get("reactions") as? Map<String, String> ?: emptyMap()
                    val existingReaction = reactions[userId]

                    val updateMap = when {
                        // Adding new reaction
                        existingReaction == null && reactionType != null -> {
                            mapOf(
                                "reactions.${userId}" to reactionType.name,
                                "reactionsCount" to FieldValue.increment(1)
                            )
                        }
                        // Changing reaction
                        existingReaction != null && reactionType != null && existingReaction != reactionType.name -> {
                            mapOf("reactions.${userId}" to reactionType.name)
                        }
                        // Removing reaction
                        existingReaction != null && reactionType == null -> {
                            mapOf(
                                "reactions.${userId}" to FieldValue.delete(),
                                "reactionsCount" to FieldValue.increment(-1)
                            )
                        }
                        else -> emptyMap()
                    }

                    if (updateMap.isNotEmpty()) {
                        flickRef.update(updateMap)
                            .addOnSuccessListener {
                                // Create notification for new reactions (not for removing)
                                if (reactionType != null && existingReaction == null) {
                                    flickRepository.createReactionNotification(
                                        flickId = flickId,
                                        reactorId = userId,
                                        reactionType = reactionType
                                    )
                                }
                                onResult(Result.Success(Unit))
                            }
                            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to toggle reaction")) }
                    } else {
                        onResult(Result.Success(Unit))
                    }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch flick")) }
    }

    /**
     * Get flicks for a specific user
     * @param userId The user whose flicks to get
     * @param onResult Callback with list of flicks
     */
    fun getUserFlicks(userId: String, onResult: (Result<List<Flick>>) -> Unit) {
        db.collection("flicks")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val flicks = snapshot.documents.mapNotNull { it.toObject(Flick::class.java) }
                onResult(Result.Success(flicks))
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch user flicks")) }
    }

    /**
     * Get explore flicks (public photos from all users)
     * @param onResult Callback with list of flicks
     */
    fun getExploreFlicks(onResult: (Result<List<Flick>>) -> Unit) {
        db.collection("flicks")
            .whereEqualTo("privacy", "public")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val flicks = snapshot.documents.mapNotNull { it.toObject(Flick::class.java) }
                onResult(Result.Success(flicks))
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch explore flicks")) }
    }

    /**
     * Get all flicks from friends + user's own photos with pagination
     * @param userId The current user ID
     * @param following List of users the current user follows
     * @param lastTimestamp Timestamp for pagination (null for first load)
     * @param pageSize Number of photos per page
     * @param onResult Callback with flicks and last timestamp
     */
    fun getFlicksForUserPaginated(
        userId: String,
        following: List<String>,
        lastTimestamp: Long?,
        pageSize: Int,
        onResult: (Result<Pair<List<Flick>, Long?>>) -> Unit
    ) {
        // Build list of users to include (following + self)
        val usersToInclude = following.toMutableList()
        if (!usersToInclude.contains(userId)) {
            usersToInclude.add(userId)
        }

        var query = db.collection("flicks")
            .whereIn("userId", usersToInclude.take(10)) // Firestore limit
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        // Apply pagination cursor if provided
        if (lastTimestamp != null) {
            query = query.whereLessThan("timestamp", lastTimestamp)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                val flicks = snapshot.documents.mapNotNull { it.toObject(Flick::class.java) }

                // Get timestamp of last document for next pagination
                val lastDocTimestamp = if (flicks.isNotEmpty()) {
                    flicks.last().timestamp
                } else null

                onResult(Result.Success(Pair(flicks, lastDocTimestamp)))
            }
            .addOnFailureListener { e ->
                onResult(Result.Error(e, "Failed to fetch paginated flicks"))
            }
    }

    /**
     * Get flicks for home feed
     * @param userId The current user ID
     * @param following List of users the current user follows
     * @param onResult Callback with list of flicks
     */
    fun getFlicks(userId: String, following: List<String>, onResult: (Result<List<Flick>>) -> Unit) {
        val usersToInclude = following.toMutableList()
        if (!usersToInclude.contains(userId)) {
            usersToInclude.add(userId)
        }

        db.collection("flicks")
            .whereIn("userId", usersToInclude.take(10))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val flicks = snapshot.documents.mapNotNull { it.toObject(Flick::class.java) }
                onResult(Result.Success(flicks))
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch flicks")) }
    }

    /**
     * Get flicks for a specific user (legacy method)
     * @param userId The user whose flicks to get
     * @param onResult Callback with list of flicks
     */
    fun getFlicksForUser(userId: String, onResult: (Result<List<Flick>>) -> Unit) {
        getUserFlicks(userId, onResult)
    }

    /**
     * Update privacy setting for all user's flicks
     * @param userId The user ID
     * @param privacy The new privacy setting
     * @param onResult Callback with result
     */
    fun updateDefaultPrivacy(userId: String, privacy: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("flicks")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()

                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "privacy", privacy)
                }

                batch.commit()
                    .addOnSuccessListener { onResult(Result.Success(Unit)) }
                    .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to update privacy")) }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch flicks")) }
    }
}
