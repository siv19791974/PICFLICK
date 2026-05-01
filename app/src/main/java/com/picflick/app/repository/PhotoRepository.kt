package com.picflick.app.repository

import com.picflick.app.data.*
import com.picflick.app.util.CostControlManager
import com.picflick.app.Constants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Repository for photo/flick operations
 * Handles photo uploads, retrieval, likes, and reactions
 */
class PhotoRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val notificationRepository = NotificationRepository.getInstance()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
     * Create a new flick
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

            val updatedFlick = flick.copy(id = flickId)

            // Create notifications for tagged friends
            if (flick.taggedFriends.isNotEmpty()) {
                flick.taggedFriends.forEach { taggedUserId ->
                    notificationRepository.createTagNotification(
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
        repositoryScope.launch {
            try {
                val flickRef = db.collection("flicks").document(flickId)
                val snapshot = flickRef.get().await()
                val flick = snapshot.toObject(Flick::class.java)
                val ownerId = flick?.userId.orEmpty()
                val imageUrl = flick?.imageUrl.orEmpty()

                if (imageUrl.isNotBlank()) {
                    runCatching {
                        storage.getReferenceFromUrl(imageUrl).delete().await()
                    }
                }

                // Delete thumbnails if they exist and are different from original
                val thumb512 = flick?.thumbnailUrl512
                if (!thumb512.isNullOrBlank() && thumb512 != imageUrl) {
                    runCatching { storage.getReferenceFromUrl(thumb512).delete().await() }
                }
                val thumb1080 = flick?.thumbnailUrl1080
                if (!thumb1080.isNullOrBlank() && thumb1080 != imageUrl) {
                    runCatching { storage.getReferenceFromUrl(thumb1080).delete().await() }
                }

                flickRef.delete().await()

                if (ownerId.isNotBlank()) {
                    decrementStorageUsedBytes(ownerId, flick?.imageSizeBytes ?: 0L)
                }

                onResult(Result.Success(Unit))
            } catch (e: Exception) {
                onResult(Result.Error(e, "Failed to delete flick"))
            }
        }
    }

    /**
     * Decrement user's storageUsedBytes by the given amount on photo delete.
     * Uses Firestore atomic increment — no recursive Storage scan needed.
     */
    private suspend fun decrementStorageUsedBytes(userId: String, bytes: Long) {
        if (bytes <= 0 || userId.isBlank()) return
        try {
            db.collection("users").document(userId)
                .update("storageUsedBytes", FieldValue.increment(-bytes))
                .await()
        } catch (e: Exception) {
            android.util.Log.w("PhotoRepository", "Failed to decrement storageUsedBytes for user=$userId by $bytes", e)
        }
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
     * Toggle reaction on a flick.
     * Handles both inline reactions (legacy, < 500) and subcollection reactions (>= 500 migrated).
     * @param flickId The flick to react to
     * @param userId The user reacting
     * @param userName The name of the reacting user (for notification)
     * @param reactionType The reaction type (or null to remove)
     * @param ownerId The photo owner ID (for notification)
     * @param onResult Callback with result
     */
    fun toggleReaction(
        flickId: String,
        userId: String,
        userName: String,
        reactionType: ReactionType?,
        ownerId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val flickRef = db.collection("flicks").document(flickId)

        flickRef.get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    onResult(Result.Error(Exception("Flick not found"), "Flick not found"))
                    return@addOnSuccessListener
                }

                val isMigrated = document.getBoolean("reactionsMigrated") == true

                if (isMigrated) {
                    toggleReactionSubcollection(
                        flickId, userId, userName, reactionType, ownerId, onResult
                    )
                } else {
                    toggleReactionInline(
                        flickRef, document, userId, userName, reactionType, ownerId, onResult
                    )
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch flick")) }
    }

    private fun toggleReactionInline(
        flickRef: com.google.firebase.firestore.DocumentReference,
        document: com.google.firebase.firestore.DocumentSnapshot,
        userId: String,
        userName: String,
        reactionType: ReactionType?,
        ownerId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val reactions = document.get("reactions") as? Map<String, String> ?: emptyMap()
        val existingReaction = reactions[userId]

        val updateMap = when {
            existingReaction == null && reactionType != null -> {
                mapOf(
                    "reactions.${userId}" to reactionType.name,
                    "reactionsCount" to FieldValue.increment(1)
                )
            }
            existingReaction != null && reactionType != null && existingReaction != reactionType.name -> {
                mapOf("reactions.${userId}" to reactionType.name)
            }
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
                    if (reactionType != null && existingReaction == null) {
                        notificationRepository.createReactionNotification(
                            flickId = flickRef.id,
                            reactorId = userId,
                            reactorName = userName,
                            reactionType = reactionType,
                            ownerId = ownerId
                        )
                    }
                    onResult(Result.Success(Unit))
                }
                .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to toggle reaction")) }
        } else {
            onResult(Result.Success(Unit))
        }
    }

    private fun toggleReactionSubcollection(
        flickId: String,
        userId: String,
        userName: String,
        reactionType: ReactionType?,
        ownerId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val reactionRef = db.collection("flicks").document(flickId)
            .collection("reactions").document(userId)
        val flickRef = db.collection("flicks").document(flickId)

        reactionRef.get()
            .addOnSuccessListener { doc ->
                val existing = if (doc.exists()) doc.getString("reactionType") else null

                when {
                    // Adding new reaction
                    existing == null && reactionType != null -> {
                        val batch = db.batch()
                        batch.set(reactionRef, mapOf(
                            "userId" to userId,
                            "reactionType" to reactionType.name,
                            "createdAt" to FieldValue.serverTimestamp()
                        ))
                        batch.update(flickRef, mapOf("reactionsCount" to FieldValue.increment(1)))
                        batch.commit()
                            .addOnSuccessListener {
                                notificationRepository.createReactionNotification(
                                    flickId = flickId,
                                    reactorId = userId,
                                    reactorName = userName,
                                    reactionType = reactionType,
                                    ownerId = ownerId
                                )
                                onResult(Result.Success(Unit))
                            }
                            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to add reaction")) }
                    }
                    // Changing reaction
                    existing != null && reactionType != null && existing != reactionType.name -> {
                        reactionRef.update("reactionType", reactionType.name)
                            .addOnSuccessListener { onResult(Result.Success(Unit)) }
                            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to change reaction")) }
                    }
                    // Removing reaction
                    existing != null && reactionType == null -> {
                        val batch = db.batch()
                        batch.delete(reactionRef)
                        batch.update(flickRef, mapOf("reactionsCount" to FieldValue.increment(-1)))
                        batch.commit()
                            .addOnSuccessListener { onResult(Result.Success(Unit)) }
                            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to remove reaction")) }
                    }
                    else -> onResult(Result.Success(Unit))
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to check existing reaction")) }
    }

    /**
     * Get a user's reaction on a flick.
     * Works for both inline reactions (unmigrated) and subcollection reactions (migrated).
     * @param flickId The flick ID
     * @param userId The user ID
     * @param onResult Callback with ReactionType or null
     */
    fun getUserReactionForFlick(
        flickId: String,
        userId: String,
        onResult: (Result<ReactionType?>) -> Unit
    ) {
        val flickRef = db.collection("flicks").document(flickId)
        flickRef.get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult(Result.Success(null))
                    return@addOnSuccessListener
                }

                val isMigrated = doc.getBoolean("reactionsMigrated") == true
                if (!isMigrated) {
                    @Suppress("UNCHECKED_CAST")
                    val reactions = doc.get("reactions") as? Map<String, String> ?: emptyMap()
                    val type = reactions[userId]?.let { ReactionType.valueOf(it) }
                    onResult(Result.Success(type))
                    return@addOnSuccessListener
                }

                // Migrated: read from subcollection
                flickRef.collection("reactions").document(userId).get()
                    .addOnSuccessListener { rDoc ->
                        val type = if (rDoc.exists()) {
                            rDoc.getString("reactionType")?.let { ReactionType.valueOf(it) }
                        } else null
                        onResult(Result.Success(type))
                    }
                    .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch subcollection reaction")) }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch flick for reaction")) }
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
            .limit(CostControlManager.getEffectivePageSize(Constants.Pagination.EXPLORE_FLICKS_LIMIT).toLong())
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

        val effectivePageSize = CostControlManager.getEffectivePageSize(pageSize)
        var query = db.collection("flicks")
            .whereIn("userId", usersToInclude.take(10)) // Firestore limit
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(effectivePageSize.toLong())

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
            .limit(CostControlManager.getEffectivePageSize(100).toLong())
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

    // ==================== COMMENTS ====================

    /**
     * Add a comment to a flick
     * @param flickId The photo being commented on
     * @param userId Comment author ID
     * @param userName Comment author name
     * @param userPhotoUrl Comment author photo
     * @param text Comment text
     * @param parentCommentId For replies (null for top-level)
     * @param onResult Callback with result
     */
    fun addComment(
        flickId: String,
        userId: String,
        userName: String,
        userPhotoUrl: String,
        text: String,
        parentCommentId: String? = null,
        onResult: (Result<Comment>) -> Unit
    ) {
        val commentId = UUID.randomUUID().toString()
        val comment = Comment(
            id = commentId,
            flickId = flickId,
            userId = userId,
            userName = userName,
            userPhotoUrl = userPhotoUrl,
            text = text,
            parentCommentId = parentCommentId
        )

        db.collection("comments").document(commentId).set(comment)
            .addOnSuccessListener {
                // Update flick comment count
                db.collection("flicks").document(flickId)
                    .update("commentCount", FieldValue.increment(1))
                    .addOnSuccessListener { onResult(Result.Success(comment)) }
                    .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to update comment count")) }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to add comment")) }
    }

    /**
     * Get comments for a flick
     * @param flickId The photo to get comments for
     * @param onResult Callback with list of comments
     */
    fun getComments(flickId: String, onResult: (Result<List<Comment>>) -> Unit) {
        db.collection("comments")
            .whereEqualTo("flickId", flickId)
            .whereEqualTo("parentCommentId", null) // Only top-level comments
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val comments = snapshot.documents.mapNotNull { it.toObject(Comment::class.java) }
                onResult(Result.Success(comments))
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch comments")) }
    }

    /**
     * Get replies to a specific comment
     * @param parentCommentId The comment to get replies for
     * @param onResult Callback with list of reply comments
     */
    fun getCommentReplies(parentCommentId: String, onResult: (Result<List<Comment>>) -> Unit) {
        db.collection("comments")
            .whereEqualTo("parentCommentId", parentCommentId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val replies = snapshot.documents.mapNotNull { it.toObject(Comment::class.java) }
                onResult(Result.Success(replies))
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch replies")) }
    }

    /**
     * Delete a comment
     * @param commentId The comment to delete
     * @param flickId The flick the comment belongs to
     * @param onResult Callback with result
     */
    fun deleteComment(commentId: String, flickId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("comments").document(commentId).delete()
            .addOnSuccessListener {
                // Decrement flick comment count
                db.collection("flicks").document(flickId)
                    .update("commentCount", FieldValue.increment(-1))
                    .addOnSuccessListener { onResult(Result.Success(Unit)) }
                    .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to update comment count")) }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to delete comment")) }
    }

    /**
     * Like/unlike a comment
     * @param commentId The comment to like
     * @param userId The user liking the comment
     * @param onResult Callback with result
     */
    fun toggleCommentLike(commentId: String, userId: String, onResult: (Result<Boolean>) -> Unit) {
        val commentRef = db.collection("comments").document(commentId)

        commentRef.get()
            .addOnSuccessListener { doc ->
                val comment = doc.toObject(Comment::class.java)
                if (comment == null) {
                    onResult(Result.Error(Exception("Comment not found"), "Comment not found"))
                    return@addOnSuccessListener
                }

                val isLiked = comment.likedBy.contains(userId)
                val newLikedBy = if (isLiked) {
                    comment.likedBy - userId
                } else {
                    comment.likedBy + userId
                }

                commentRef.update(
                    "likedBy", newLikedBy,
                    "likeCount", newLikedBy.size
                )
                    .addOnSuccessListener { onResult(Result.Success(!isLiked)) }
                    .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to toggle like")) }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch comment")) }
    }

    // ==================== ALBUMS ====================

    /**
     * Create a new album
     * @param userId Album owner
     * @param name Album name
     * @param description Optional description
     * @param privacy Privacy setting
     * @param onResult Callback with result
     */
    fun createAlbum(
        userId: String,
        name: String,
        description: String = "",
        privacy: String = "friends",
        onResult: (Result<Album>) -> Unit
    ) {
        val albumId = UUID.randomUUID().toString()
        val album = Album(
            id = albumId,
            userId = userId,
            name = name,
            description = description,
            privacy = privacy
        )

        db.collection("albums").document(albumId).set(album)
            .addOnSuccessListener { onResult(Result.Success(album)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to create album")) }
    }

    /**
     * Get all albums for a user
     * @param userId The user whose albums to get
     * @param onResult Callback with list of albums
     */
    fun getUserAlbums(userId: String, onResult: (Result<List<Album>>) -> Unit) {
        db.collection("albums")
            .whereEqualTo("userId", userId)
            .orderBy("sortOrder", Query.Direction.ASCENDING)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val albums = snapshot.documents.mapNotNull { it.toObject(Album::class.java) }
                onResult(Result.Success(albums))
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch albums")) }
    }

    /**
     * Add a photo to an album
     * @param albumId The album to add to
     * @param photoId The photo to add
     * @param photoUrl The photo URL for cover image
     * @param onResult Callback with result
     */
    fun addPhotoToAlbum(
        albumId: String,
        photoId: String,
        photoUrl: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val albumRef = db.collection("albums").document(albumId)

        albumRef.get()
            .addOnSuccessListener { doc ->
                val album = doc.toObject(Album::class.java)
                if (album == null) {
                    onResult(Result.Error(Exception("Album not found"), "Album not found"))
                    return@addOnSuccessListener
                }

                // Add photo ID to list
                val updatedPhotoIds = album.photoIds + photoId
                // Update cover if this is the first photo
                val updatedCover = if (album.coverPhotoUrl.isEmpty()) photoUrl else album.coverPhotoUrl

                albumRef.update(
                    "photoIds", updatedPhotoIds,
                    "photoCount", updatedPhotoIds.size,
                    "coverPhotoUrl", updatedCover,
                    "updatedAt", FieldValue.serverTimestamp()
                )
                    .addOnSuccessListener { onResult(Result.Success(Unit)) }
                    .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to add photo to album")) }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch album")) }
    }

    /**
     * Remove a photo from an album
     * @param albumId The album to remove from
     * @param photoId The photo to remove
     * @param onResult Callback with result
     */
    fun removePhotoFromAlbum(albumId: String, photoId: String, onResult: (Result<Unit>) -> Unit) {
        val albumRef = db.collection("albums").document(albumId)

        albumRef.get()
            .addOnSuccessListener { doc ->
                val album = doc.toObject(Album::class.java)
                if (album == null) {
                    onResult(Result.Error(Exception("Album not found"), "Album not found"))
                    return@addOnSuccessListener
                }

                val updatedPhotoIds = album.photoIds - photoId

                albumRef.update(
                    "photoIds", updatedPhotoIds,
                    "photoCount", updatedPhotoIds.size,
                    "updatedAt", FieldValue.serverTimestamp()
                )
                    .addOnSuccessListener { onResult(Result.Success(Unit)) }
                    .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to remove photo from album")) }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to fetch album")) }
    }

    /**
     * Delete an album
     * @param albumId The album to delete
     * @param onResult Callback with result
     */
    fun deleteAlbum(albumId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("albums").document(albumId).delete()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to delete album")) }
    }
}
