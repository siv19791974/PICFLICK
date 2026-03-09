package com.picflick.app.repository

import com.picflick.app.data.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID
import com.picflick.app.Constants

/**
 * Repository class for handling all Firebase Firestore and Storage operations
 */
class FlickRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    // Coroutine scope for repository operations (replaces GlobalScope)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cache for user profile
    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)

    companion object {
        @Volatile
        private var instance: FlickRepository? = null

        fun getInstance(): FlickRepository {
            return instance ?: synchronized(this) {
                instance ?: FlickRepository().also { instance = it }
            }
        }
    }

    /**
     * Upload flick image to Firebase Storage
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
     */
    suspend fun createFlick(flick: Flick, userPhotoUrl: String): Result<Unit> {
        return try {
            // Add to Firestore
            val docRef = db.collection("flicks").add(flick).await()
            val flickId = docRef.id

            // Update with ID
            docRef.update("id", flickId).await()

            // Create notifications for friends
            val updatedFlick = flick.copy(id = flickId)
            createPhotoNotifications(updatedFlick, userPhotoUrl)
            
            // Create notifications for tagged friends
            if (flick.taggedFriends.isNotEmpty()) {
                flick.taggedFriends.forEach { taggedUserId ->
                    createTagNotification(
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
     * Get all flicks from friends + user's own photos (private photos)
     * Queries separately and merges to avoid Firestore whereIn limitations
     */
    fun getFlicksForUser(userId: String, onResult: (Result<List<Flick>>) -> Unit) {
        repositoryScope.launch {
            try {
                // Get friends list
                val userDoc = db.collection("users").document(userId).get().await()
                val userProfile = userDoc.toObject(UserProfile::class.java)
                val friends = userProfile?.following ?: emptyList()

                // Query user's own photos (simple query - no composite index needed)
                val ownFlicksSnapshot = db.collection("flicks")
                    .whereEqualTo("userId", userId)
                    .limit(Constants.Pagination.FLICKS_PER_PAGE.toLong())
                    .get()
                    .await()

                val ownFlicks = ownFlicksSnapshot.toObjects(Flick::class.java)

                // Query friends' photos (simplified to avoid composite index)
                val friendsFlicks = if (friends.isNotEmpty()) {
                    friends.chunked(Constants.Pagination.MAX_FRIENDS_BATCH).flatMap { friendBatch ->
                        // Query without orderBy to avoid composite index requirement
                        val batchSnapshot = db.collection("flicks")
                            .whereIn("userId", friendBatch)
                            .whereEqualTo("privacy", "friends")
                            .limit(Constants.Pagination.FLICKS_PER_PAGE.toLong())
                            .get()
                            .await()
                        batchSnapshot.toObjects(Flick::class.java)
                    }
                } else {
                    emptyList()
                }

                // Merge and sort in memory (client-side sorting)
                val allFlicks = (ownFlicks + friendsFlicks)
                    .distinctBy { it.id }
                    .sortedByDescending { it.timestamp }
                    .take(Constants.Pagination.FLICKS_PER_PAGE)

                onResult(Result.Success(allFlicks))
            } catch (e: Exception) {
                e.printStackTrace() // Log the error for debugging
                onResult(Result.Error(e, "Failed to load photos: ${e.message}"))
            }
        }
    }

    /**
     * Get explore flicks (trending/popular for discovery)
     * Gets photos with most reactions from last 7 days
     */
    fun getExploreFlicks(onResult: (Result<List<Flick>>) -> Unit) {
        repositoryScope.launch {
            try {
                // Get photos from last 7 days (query without orderBy to avoid composite index)
                val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis

                val flicksSnapshot = db.collection("flicks")
                    .whereGreaterThan("timestamp", weekAgo)
                    .limit(Constants.Pagination.EXPLORE_FLICKS_LIMIT.toLong())
                    .get()
                    .await()

                val flicks = flicksSnapshot.toObjects(Flick::class.java)

                // Sort by timestamp first, then by reaction count (trending algorithm)
                val trendingFlicks = flicks
                    .sortedByDescending { it.timestamp } // Sort in memory
                    .sortedByDescending { it.getTotalReactions() + (it.commentCount * 2) }
                    .take(Constants.Pagination.FLICKS_PER_PAGE)

                onResult(Result.Success(trendingFlicks))
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(Result.Error(e, "Failed to load explore photos: ${e.message}"))
            }
        }
    }

    /**
     * Get flicks - DEPRECATED: Use getFlicksForUser() for privacy
     */
    fun getFlicks(onResult: (Result<List<Flick>>) -> Unit) {
        db.collection(Constants.FirebaseCollections.FLICKS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(Constants.Pagination.FLICKS_PER_PAGE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(Exception(error.message), "Failed to load photos"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val flicks = snapshot.toObjects(Flick::class.java)
                    onResult(Result.Success(flicks))
                }
            }
    }

    /**
     * Get flicks for a specific user
     */
    fun getUserFlicks(userId: String, onResult: (Result<List<Flick>>) -> Unit) {
        db.collection(Constants.FirebaseCollections.FLICKS)
            .whereEqualTo("userId", userId)
            .limit(Constants.Pagination.FLICKS_PER_PAGE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(Exception(error.message), "Failed to load user photos"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val flicks = snapshot.toObjects(Flick::class.java)
                        .sortedByDescending { it.timestamp } // Sort in memory
                    onResult(Result.Success(flicks))
                }
            }
    }

    /**
     * Toggle like on a flick
     */
    fun toggleLike(flickId: String, userId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { doc ->
                val flick = doc.toObject(Flick::class.java)
                if (flick == null) {
                    onResult(Result.Error(Exception("Photo not found"), "Photo not found"))
                    return@addOnSuccessListener
                }

                val currentLikes = flick.reactions.filter { it.value == ReactionType.LIKE.name }.keys.toMutableSet()
                val hasLiked = currentLikes.contains(userId)

                if (hasLiked) {
                    // Remove like
                    db.collection("flicks").document(flickId)
                        .update("reactions.${userId}", FieldValue.delete())
                        .addOnSuccessListener { onResult(Result.Success(Unit)) }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to unlike")) }
                } else {
                    // Add like
                    db.collection("flicks").document(flickId)
                        .update("reactions.${userId}", ReactionType.LIKE.name)
                        .addOnSuccessListener {
                            // Create notification for photo owner
                            if (flick.userId != userId) {
                                createReactionNotification(
                                    flickId = flickId,
                                    ownerId = flick.userId,
                                    reactorId = userId,
                                    reactorName = "Someone", // Get actual name from user profile
                                    reactorPhotoUrl = "",
                                    reactionType = ReactionType.LIKE
                                )
                            }
                            onResult(Result.Success(Unit))
                        }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to like")) }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get photo")) }
    }

    /**
     * Toggle reaction on a flick
     */
    fun toggleReaction(
        flickId: String,
        userId: String,
        userName: String,
        userPhotoUrl: String,
        reactionType: ReactionType?,
        onResult: (Result<Unit>) -> Unit
    ) {
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { doc ->
                val flick = doc.toObject(Flick::class.java)
                if (flick == null) {
                    onResult(Result.Error(Exception("Photo not found"), "Photo not found"))
                    return@addOnSuccessListener
                }

                val currentReaction = flick.reactions[userId]

                if (currentReaction == reactionType?.name) {
                    // Remove reaction (same reaction clicked)
                    db.collection("flicks").document(flickId)
                        .update("reactions.${userId}", FieldValue.delete())
                        .addOnSuccessListener { onResult(Result.Success(Unit)) }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to remove reaction")) }
                } else {
                    // Add/update reaction
                    val updateValue = reactionType?.name ?: FieldValue.delete()
                    db.collection("flicks").document(flickId)
                        .update("reactions.${userId}", updateValue)
                        .addOnSuccessListener {
                            // Create notification for photo owner
                            if (reactionType != null && flick.userId != userId) {
                                createReactionNotification(
                                    flickId = flickId,
                                    ownerId = flick.userId,
                                    reactorId = userId,
                                    reactorName = userName,
                                    reactorPhotoUrl = userPhotoUrl,
                                    reactionType = reactionType
                                )
                            }
                            onResult(Result.Success(Unit))
                        }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to add reaction")) }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get photo")) }
    }

    /**
     * Delete a flick
     */
    fun deleteFlick(flickId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("flicks").document(flickId).delete()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to delete photo")) }
    }

    /**
     * Get user profile
     */
    fun getUserProfile(userId: String, onResult: (Result<UserProfile>) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val profile = doc.toObject(UserProfile::class.java)
                if (profile != null) {
                    _currentUserProfile.value = profile
                    onResult(Result.Success(profile))
                } else {
                    onResult(Result.Error(Exception("Profile not found"), "Profile not found"))
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to load profile")) }
    }

    /**
     * Save user profile
     */
    fun saveUserProfile(userId: String, profile: UserProfile, onResult: (Result<Unit>) -> Unit) {
        db.collection("users").document(userId).set(profile)
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to save profile")) }
    }

    /**
     * Check if two users are friends (mutual followers)
     */
    suspend fun areFriends(userId1: String, userId2: String): Boolean {
        return try {
            val user1Doc = db.collection("users").document(userId1).get().await()
            val user2Doc = db.collection("users").document(userId2).get().await()
            
            val user1Profile = user1Doc.toObject(UserProfile::class.java)
            val user2Profile = user2Doc.toObject(UserProfile::class.java)
            
            val user1Following = user1Profile?.following ?: emptyList()
            val user2Following = user2Profile?.following ?: emptyList()
            
            // Mutual follow = friends
            userId2 in user1Following && userId1 in user2Following
        } catch (e: Exception) {
            false // Return false on error to be safe
        }
    }
    fun getAllUsers(onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection(Constants.FirebaseCollections.USERS)
            .limit(Constants.Pagination.USERS_PER_PAGE.toLong())
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.toObjects(UserProfile::class.java)
                onResult(Result.Success(users))
            }
            .addOnFailureListener { e ->
                onResult(Result.Error(e, "Failed to get users"))
            }
    }

    /**
     * Find users by phone numbers (for contact sync)
     */
    fun findUsersByPhoneNumbers(phoneNumbers: List<String>, onResult: (Result<List<UserProfile>>) -> Unit) {
        if (phoneNumbers.isEmpty()) {
            onResult(Result.Success(emptyList()))
            return
        }

        // Query users with matching phone numbers
        // Since Firestore doesn't support direct array contains for multiple values efficiently,
        // we'll query in batches
        val batches = phoneNumbers.chunked(Constants.Pagination.MAX_FRIENDS_BATCH)
        val foundUsers = mutableListOf<UserProfile>()
        var completedBatches = 0

        batches.forEach { batch ->
            db.collection("users")
                .whereIn("phoneNumber", batch)
                .get()
                .addOnSuccessListener { snapshot ->
                    foundUsers.addAll(snapshot.toObjects(UserProfile::class.java))
                    completedBatches++
                    if (completedBatches == batches.size) {
                        onResult(Result.Success(foundUsers))
                    }
                }
                .addOnFailureListener { e ->
                    completedBatches++
                    if (completedBatches == batches.size) {
                        onResult(Result.Success(foundUsers)) // Return what we found
                    }
                }
        }
    }

    /**
     * Search users by name or email
     */
    fun searchUsers(query: String, currentUserId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        val searchLower = query.lowercase()

        db.collection(Constants.FirebaseCollections.USERS)
            .orderBy("displayName")
            .startAt(searchLower)
            .endAt(searchLower + "\uf8ff")
            .limit(Constants.Pagination.SUGGESTED_USERS_LIMIT.toLong())
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.toObjects(UserProfile::class.java)
                // Filter out current user
                val filteredUsers = users.filter { it.uid != currentUserId }
                onResult(Result.Success(filteredUsers))
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to search users")) }
    }

    /**
     * Follow a user - suspend version for ViewModel with achievement check
     */
    suspend fun followUser(currentUserId: String, targetUserId: String, userName: String? = null): Result<Unit> {
        return try {
            val batch = db.batch()

            // Add to current user's following
            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "following", FieldValue.arrayUnion(targetUserId))

            // Add to target user's followers
            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(targetUserRef, "followers", FieldValue.arrayUnion(currentUserId))

            batch.commit().await()

            // Create follow notification for target user
            if (userName != null) {
                createFollowNotification(
                    followerId = currentUserId,
                    followerName = userName,
                    targetUserId = targetUserId
                )
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to follow user")
        }
    }
    
    /**
     * Unfollow a user - suspend version for ViewModel
     */
    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            val batch = db.batch()

            // Remove from current user's following
            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))

            // Remove from target user's followers
            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))

            batch.commit().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to unfollow user")
        }
    }

    /**
     * Get suggested users (not followed yet)
     */
    fun getSuggestedUsers(userId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        // First get current user's following list
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val userProfile = userDoc.toObject(UserProfile::class.java)
                val following = userProfile?.following ?: emptyList()

                // Get users not in following list
                db.collection(Constants.FirebaseCollections.USERS)
                    .limit(Constants.Pagination.SUGGESTED_USERS_LIMIT.toLong())
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val allUsers = snapshot.toObjects(UserProfile::class.java)
                        val suggestions = allUsers
                            .filter { it.uid != userId && it.uid !in following }
                            .take(Constants.Pagination.SUGGESTED_USERS_LIMIT / 2)
                        onResult(Result.Success(suggestions))
                    }
                    .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get suggestions")) }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get user profile")) }
    }

    /**
     * Follow a user
     */
    fun followUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        val batch = db.batch()

        // Add to current user's following
        val currentUserRef = db.collection("users").document(currentUserId)
        batch.update(currentUserRef, "following", FieldValue.arrayUnion(targetUserId))

        // Add to target user's followers
        val targetUserRef = db.collection("users").document(targetUserId)
        batch.update(targetUserRef, "followers", FieldValue.arrayUnion(currentUserId))

        batch.commit()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to follow user")) }
    }

    /**
     * Unfollow a user
     */
    fun unfollowUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        val batch = db.batch()

        // Remove from current user's following
        val currentUserRef = db.collection("users").document(currentUserId)
        batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))

        // Remove from target user's followers
        val targetUserRef = db.collection("users").document(targetUserId)
        batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))

        batch.commit()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to unfollow user")) }
    }

    /**
     * Block a user - they can't see your content or interact with you
     */
    fun blockUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        val batch = db.batch()

        val currentUserRef = db.collection("users").document(currentUserId)
        val targetUserRef = db.collection("users").document(targetUserId)

        // Add to blocked users list
        batch.update(currentUserRef, "blockedUsers", FieldValue.arrayUnion(targetUserId))

        // Unfollow each other (can't follow a blocked user)
        batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))
        batch.update(currentUserRef, "followers", FieldValue.arrayRemove(targetUserId))
        batch.update(targetUserRef, "following", FieldValue.arrayRemove(currentUserId))
        batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))

        batch.commit()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to block user")) }
    }

    /**
     * Unblock a user
     */
    fun unblockUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("users").document(currentUserId)
            .update("blockedUsers", FieldValue.arrayRemove(targetUserId))
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to unblock user")) }
    }

    /**
     * Get following users
     */
    fun getFollowingUsers(userId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val userProfile = doc.toObject(UserProfile::class.java)
                val followingIds = userProfile?.following ?: emptyList()

                if (followingIds.isEmpty()) {
                    onResult(Result.Success(emptyList()))
                    return@addOnSuccessListener
                }

                // Get following user profiles
                val profiles = mutableListOf<UserProfile>()
                var completed = 0

                followingIds.forEach { id ->
                    db.collection("users").document(id).get()
                        .addOnSuccessListener { userDoc ->
                            val profile = userDoc.toObject(UserProfile::class.java)
                            if (profile != null) {
                                profiles.add(profile)
                            }
                            completed++
                            if (completed == followingIds.size) {
                                onResult(Result.Success(profiles))
                            }
                        }
                        .addOnFailureListener { e ->
                            completed++
                            if (completed == followingIds.size) {
                                onResult(Result.Success(profiles))
                            }
                        }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get following")) }
    }

    /**
     * Get followers
     */
    fun getFollowers(userId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val userProfile = doc.toObject(UserProfile::class.java)
                val followerIds = userProfile?.followers ?: emptyList()

                if (followerIds.isEmpty()) {
                    onResult(Result.Success(emptyList()))
                    return@addOnSuccessListener
                }

                // Get follower profiles
                val profiles = mutableListOf<UserProfile>()
                var completed = 0

                followerIds.forEach { id ->
                    db.collection("users").document(id).get()
                        .addOnSuccessListener { userDoc ->
                            val profile = userDoc.toObject(UserProfile::class.java)
                            if (profile != null) {
                                profiles.add(profile)
                            }
                            completed++
                            if (completed == followerIds.size) {
                                onResult(Result.Success(profiles))
                            }
                        }
                        .addOnFailureListener { e ->
                            completed++
                            if (completed == followerIds.size) {
                                onResult(Result.Success(profiles))
                            }
                        }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get followers")) }
    }

    /**
     * Get daily upload count for a user
     */
    fun getDailyUploadCount(userId: String, onResult: (Result<Int>) -> Unit) {
        repositoryScope.launch {
            try {
                // Get start of today
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = calendar.timeInMillis

                // Count flicks uploaded today
                val snapshot = db.collection("flicks")
                    .whereEqualTo("userId", userId)
                    .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                    .get()
                    .await()

                onResult(Result.Success(snapshot.size()))
            } catch (e: Exception) {
                onResult(Result.Error(e, "Failed to get upload count"))
            }
        }
    }

    /**
     * Create notification for new photo
     */
    private fun createPhotoNotifications(flick: Flick, userPhotoUrl: String) {
        repositoryScope.launch {
            try {
                // Get user's followers
                val userDoc = db.collection("users").document(flick.userId).get().await()
                val userProfile = userDoc.toObject(UserProfile::class.java)
                val followers = userProfile?.followers ?: emptyList()

                // Create notification for each follower
                followers.forEach { followerId ->
                    val notification = hashMapOf(
                        "id" to UUID.randomUUID().toString(),
                        "userId" to followerId,
                        "senderId" to flick.userId,
                        "senderName" to flick.userName,
                        "senderPhotoUrl" to userPhotoUrl,
                        "type" to "PHOTO_ADDED",
                        "title" to "${flick.userName} added a new photo",
                        "message" to "Check it out!",
                        "flickId" to flick.id,
                        "flickImageUrl" to flick.imageUrl,
                        "isRead" to false,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("notifications").add(notification).await()
                }
            } catch (e: Exception) {
                // Silently fail - don't block photo upload
                e.printStackTrace()
            }
        }
    }

    /**
     * Listen to notifications in real-time
     */
    fun listenToNotifications(
        userId: String,
        onUpdate: (List<Notification>) -> Unit,
        onError: (String) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        return db.collection(Constants.FirebaseCollections.NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(Constants.Pagination.NOTIFICATIONS_PER_PAGE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load notifications")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val notifications = snapshot.toObjects(Notification::class.java)
                    onUpdate(notifications)
                }
            }
    }

    /**
     * Mark notification as read
     */
    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        return try {
            db.collection("notifications").document(notificationId)
                .update("isRead", true)
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to mark notification as read")
        }
    }

    /**
     * Mark all notifications as read for a user
     */
    suspend fun markAllNotificationsAsRead(userId: String): Result<Unit> {
        return try {
            val batch = db.batch()
            
            val unreadNotifications = db.collection("notifications")
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
            
            unreadNotifications.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            
            batch.commit().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to mark all notifications as read")
        }
    }

    /**
     * Delete a notification
     */
    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            db.collection("notifications").document(notificationId)
                .delete()
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete notification")
        }
    }

    /**
     * Create notification for reaction
     */
    private fun createReactionNotification(
        flickId: String,
        ownerId: String,
        reactorId: String,
        reactorName: String,
        reactorPhotoUrl: String,
        reactionType: ReactionType
    ) {
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { flickDoc ->
                val flickImageUrl = flickDoc.getString("imageUrl")
                val emoji = reactionType.toEmoji()
                val displayName = reactionType.toDisplayName()

                val notification = hashMapOf(
                    "id" to UUID.randomUUID().toString(),
                    "userId" to ownerId,
                    "senderId" to reactorId,
                    "senderName" to reactorName,
                    "senderPhotoUrl" to reactorPhotoUrl,
                    "type" to "REACTION",
                    "title" to "$reactorName reacted $emoji to your photo",
                    "message" to "$displayName reaction",
                    "flickId" to flickId,
                    "flickImageUrl" to flickImageUrl,
                    "reactionType" to reactionType.name,
                    "isRead" to false,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("notifications").add(notification)
            }
    }

    /**
     * Create notification for comment on a photo
     */
    private fun createCommentNotification(
        flickId: String,
        commenterId: String,
        commenterName: String,
        commenterPhotoUrl: String,
        commentText: String
    ) {
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { flickDoc ->
                val ownerId = flickDoc.getString("userId")
                val flickImageUrl = flickDoc.getString("imageUrl")
                
                // Don't notify if user comments on their own photo
                if (ownerId != null && ownerId != commenterId) {
                    val truncatedComment = if (commentText.length > 50) 
                        commentText.take(50) + "..." 
                    else 
                        commentText
                    
                    val notification = hashMapOf(
                        "id" to UUID.randomUUID().toString(),
                        "userId" to ownerId,
                        "senderId" to commenterId,
                        "senderName" to commenterName,
                        "senderPhotoUrl" to commenterPhotoUrl,
                        "type" to "COMMENT",
                        "title" to "$commenterName commented on your photo",
                        "message" to truncatedComment,
                        "flickId" to flickId,
                        "flickImageUrl" to flickImageUrl,
                        "isRead" to false,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("notifications").add(notification)
                }
            }
    }

    /**
     * Create streak reminder notification
     */
    suspend fun createStreakReminderNotification(userId: String, userName: String, currentStreak: Int): Result<Unit> {
        return try {
            val motivationalMessages = listOf(
                "Don't break your $currentStreak-day streak! Share a photo today ����",
                "Your $currentStreak-day streak is at risk! Post now to keep it alive ���",
                "Keep the flame burning! $currentStreak days and counting ����",
                "One photo away from day ${currentStreak + 1}! Don't stop now ����"
            )
            
            val randomMessage = motivationalMessages.random()
            
            val notification = hashMapOf(
                "id" to UUID.randomUUID().toString(),
                "userId" to userId,
                "senderId" to "system",
                "senderName" to "PicFlick",
                "senderPhotoUrl" to "",
                "type" to "STREAK_REMINDER",
                "title" to "���� Streak Alert!",
                "message" to randomMessage,
                "isRead" to false,
                "timestamp" to System.currentTimeMillis(),
                "streakCount" to currentStreak
            )

            db.collection("notifications").add(notification).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create streak reminder")
        }
    }

    /**
     * Create notification when user is tagged in a photo
     */
    private fun createTagNotification(
        flickId: String,
        photoOwnerId: String,
        photoOwnerName: String,
        photoOwnerPhotoUrl: String,
        taggedUserId: String
    ) {
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { flickDoc ->
                val flickImageUrl = flickDoc.getString("imageUrl")
                
                val notification = hashMapOf(
                    "id" to UUID.randomUUID().toString(),
                    "userId" to taggedUserId,
                    "senderId" to photoOwnerId,
                    "senderName" to photoOwnerName,
                    "senderPhotoUrl" to photoOwnerPhotoUrl,
                    "type" to "MENTION",
                    "title" to "$photoOwnerName tagged you in a photo",
                    "message" to "Check out the photo you're in!",
                    "flickId" to flickId,
                    "flickImageUrl" to flickImageUrl,
                    "isRead" to false,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("notifications").add(notification)
            }
    }

    /**
     * Create notification when someone follows you
     */
    private fun createFollowNotification(
        followerId: String,
        followerName: String,
        targetUserId: String
    ) {
        val notification = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to targetUserId,
            "senderId" to followerId,
            "senderName" to followerName,
            "senderPhotoUrl" to "",
            "type" to "FOLLOW",
            "title" to "$followerName started following you",
            "message" to "You have a new follower!",
            "isRead" to false,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification)
    }

    /**
     * Create achievement unlocked notification for photographer (1st photo)
     */
    suspend fun createFirstPhotoAchievement(userId: String, userName: String): Result<Unit> {
        return try {
            val notification = hashMapOf(
                "id" to UUID.randomUUID().toString(),
                "userId" to userId,
                "senderId" to "system",
                "senderName" to "PicFlick",
                "senderPhotoUrl" to "",
                "type" to "ACHIEVEMENT",
                "title" to "���� Achievement Unlocked!",
                "message" to "Congratulations $userName! You earned the ���� Photographer achievement for uploading your first photo!",
                "isRead" to false,
                "timestamp" to System.currentTimeMillis(),
                "achievementType" to "PHOTOGRAPHER",
                "emoji" to "����"
            )

            db.collection("notifications").add(notification).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create achievement notification")
        }
    }

    /**
     * Create achievement unlocked notification for active user (5+ photos)
     */
    suspend fun createActiveUserAchievement(userId: String, userName: String, photoCount: Int): Result<Unit> {
        return try {
            val notification = hashMapOf(
                "id" to UUID.randomUUID().toString(),
                "userId" to userId,
                "senderId" to "system",
                "senderName" to "PicFlick",
                "senderPhotoUrl" to "",
                "type" to "ACHIEVEMENT",
                "title" to "���� Achievement Unlocked!",
                "message" to "Keep it up $userName! You earned the ���� Active achievement for uploading $photoCount photos!",
                "isRead" to false,
                "timestamp" to System.currentTimeMillis(),
                "achievementType" to "ACTIVE",
                "emoji" to "����",
                "photoCount" to photoCount
            )

            db.collection("notifications").add(notification).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create achievement notification")
        }
    }

    /**
     * Get notifications for a user
     */
    fun getNotifications(userId: String, onResult: (Result<List<Map<String, Any>>>) -> Unit) {
        db.collection(Constants.FirebaseCollections.NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(Constants.Pagination.NOTIFICATIONS_PER_PAGE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(error, "Failed to load notifications"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val notifications = snapshot.documents.map { it.data ?: emptyMap() }
                    onResult(Result.Success(notifications))
                }
            }
    }

    /**
     * Mark notification as read
     */
    fun markNotificationRead(notificationId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("notifications").document(notificationId)
            .update("isRead", true)
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to mark as read")) }
    }

    /**
     * Add a comment to a flick and notify the owner
     */
    suspend fun addComment(
        flickId: String,
        userId: String,
        userName: String,
        userPhotoUrl: String,
        text: String
    ): Result<Unit> {
        return try {
            val comment = Comment(
                id = UUID.randomUUID().toString(),
                flickId = flickId,
                userId = userId,
                userName = userName,
                userPhotoUrl = userPhotoUrl,
                text = text,
                timestamp = System.currentTimeMillis()
            )

            // Add comment
            db.collection("comments").add(comment).await()

            // Update flick comment count
            db.collection("flicks").document(flickId)
                .update("commentCount", FieldValue.increment(1))
                .await()

            // Create notification for photo owner (if not the commenter)
            createCommentNotification(flickId, userId, userName, userPhotoUrl, text)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to add comment")
        }
    }

    /**
     * Get comments for a flick - returns a ListenerRegistration that must be removed when done
     */
    fun getComments(flickId: String, onResult: (Result<List<Comment>>) -> Unit): ListenerRegistration {
        return db.collection("comments")
            .whereEqualTo("flickId", flickId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(error, "Failed to load comments"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val comments = snapshot.toObjects(Comment::class.java)
                    onResult(Result.Success(comments))
                }
            }
    }

    /**
     * Delete a comment
     */
    suspend fun deleteComment(commentId: String, flickId: String): Result<Unit> {
        return try {
            db.collection("comments").document(commentId).delete().await()

            // Decrement flick comment count
            db.collection("flicks").document(flickId)
                .update("commentCount", FieldValue.increment(-1))
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete comment")
        }
    }

    /**
     * Update flick description
     */
    suspend fun updateFlickDescription(flickId: String, description: String): Result<Unit> {
        return try {
            db.collection("flicks").document(flickId)
                .update("description", description)
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update description")
        }
    }

    /**
     * Update tagged friends for a flick
     */
    suspend fun updateTaggedFriends(
        flickId: String,
        taggedFriends: List<String>,
        photoOwnerId: String,
        photoOwnerName: String,
        photoOwnerPhotoUrl: String
    ): Result<Unit> {
        return try {
            // Get current flick to check existing tags
            val flickDoc = db.collection("flicks").document(flickId).get().await()
            val currentTagged = flickDoc.get("taggedFriends") as? List<String> ?: emptyList()
            
            // Find newly added friends (to create notifications for them)
            val newlyTagged = taggedFriends.filter { it !in currentTagged }
            
            // Update the flick with new tagged friends list
            db.collection("flicks").document(flickId)
                .update("taggedFriends", taggedFriends)
                .await()
            
            // Create notifications for newly tagged friends
            newlyTagged.forEach { taggedUserId ->
                createTagNotification(
                    flickId = flickId,
                    photoOwnerId = photoOwnerId,
                    photoOwnerName = photoOwnerName,
                    photoOwnerPhotoUrl = photoOwnerPhotoUrl,
                    taggedUserId = taggedUserId
                )
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update tagged friends")
        }
    }

    /**
     * Get user streak info
     */
    suspend fun getUserStreak(userId: String): Result<Pair<Int, Boolean>> {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            val streakData = userDoc.get("streak") as? Map<String, Any>

            val currentStreak = (streakData?.get("current") as? Long)?.toInt() ?: 0
            val lastUpload = streakData?.get("lastUpload") as? Long ?: 0

            // Check if streak is still active (uploaded today or yesterday)
            val calendar = Calendar.getInstance()
            val today = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = calendar.timeInMillis

            val isActive = lastUpload >= yesterday
            val canContinue = lastUpload in yesterday..today

            val effectiveStreak = if (isActive) currentStreak else 0

            Result.Success(Pair(effectiveStreak, canContinue))
        } catch (e: Exception) {
            Result.Error(e, "Failed to get streak")
        }
    }

    /**
     * Update user streak after upload
     */
    suspend fun updateStreak(userId: String): Result<Unit> {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            val streakData = userDoc.get("streak") as? Map<String, Any>

            val currentStreak = (streakData?.get("current") as? Long)?.toInt() ?: 0
            val lastUpload = streakData?.get("lastUpload") as? Long ?: 0

            // Check if already uploaded today
            val calendar = Calendar.getInstance()
            val today = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            if (lastUpload >= today) {
                // Already uploaded today, don't increment
                return Result.Success(Unit)
            }

            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = calendar.timeInMillis

            val newStreak = if (lastUpload >= yesterday) {
                // Continuing streak
                currentStreak + 1
            } else {
                // New streak
                1
            }

            val newStreakData = hashMapOf(
                "current" to newStreak,
                "lastUpload" to System.currentTimeMillis(),
                "longest" to maxOf(newStreak, (streakData?.get("longest") as? Long)?.toInt() ?: 0)
            )

            db.collection("users").document(userId)
                .update("streak", newStreakData)
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update streak")
        }
    }

    /**
     * Get leaderboard (top users by likes/reactions)
     */
    suspend fun getLeaderboard(): Result<List<Pair<UserProfile, Int>>> {
        return try {
            // Get all users
            val usersSnapshot = db.collection(Constants.FirebaseCollections.USERS).limit(Constants.Pagination.USERS_PER_PAGE.toLong()).get().await()
            val users = usersSnapshot.toObjects(UserProfile::class.java)

            // Calculate scores for each user
            val userScores = users.map { user ->
                val flicksSnapshot = db.collection("flicks")
                    .whereEqualTo("userId", user.uid)
                    .get()
                    .await()

                val flicks = flicksSnapshot.toObjects(Flick::class.java)
                val totalReactions = flicks.sumOf { it.getTotalReactions() }

                Pair(user, totalReactions)
            }

            // Sort by score
            val sorted = userScores.sortedByDescending { it.second }

            Result.Success(sorted)
        } catch (e: Exception) {
            Result.Error(e, "Failed to get leaderboard")
        }
    }

    /**
     * Report a photo for inappropriate content
     * Reports are stored for admin review
     */
    suspend fun reportPhoto(
        flickId: String,
        reporterId: String,
        reason: String,
        details: String = ""
    ): Result<Unit> {
        return try {
            val report = hashMapOf(
                "flickId" to flickId,
                "reporterId" to reporterId,
                "reason" to reason,
                "details" to details,
                "timestamp" to System.currentTimeMillis(),
                "status" to "pending" // pending, reviewed, action_taken
            )

            db.collection("reports").add(report).await()

            // Also increment report count on the flick for auto-moderation
            db.collection("flicks").document(flickId)
                .update("reportCount", FieldValue.increment(1))
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to submit report")
        }
    }

    /**
     * Get reported photos (for admin use)
     */
    suspend fun getReportedPhotos(): Result<List<Pair<Flick, Int>>> {
        return try {
            // Get flicks with reportCount > 0
            val snapshot = db.collection("flicks")
                .whereGreaterThan("reportCount", 0)
                .orderBy("reportCount", Query.Direction.DESCENDING)
                .get()
                .await()

            val flicks = snapshot.toObjects(Flick::class.java)
            val reportCounts = flicks.map { it.reportCount }

            Result.Success(flicks.zip(reportCounts))
        } catch (e: Exception) {
            Result.Error(e, "Failed to get reported photos")
        }
    }

    /**
     * Save user notification preferences to Firestore
     */
    suspend fun saveNotificationPreferences(
        userId: String,
        preferences: com.picflick.app.data.NotificationPreferences
    ): Result<Unit> {
        return try {
            // Use set with merge to create or update the field
            db.collection("users").document(userId)
                .set(mapOf("notificationPreferences" to preferences), com.google.firebase.firestore.SetOptions.merge())
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to save notification preferences")
        }
    }

    /**
     * Delete all user data - photos, profile, friends, chats, notifications
     * Used for account deletion
     */
    suspend fun deleteUserData(userId: String): Result<Unit> {
        return try {
            // 1. Delete all user's photos (flicks)
            val flicksSnapshot = db.collection(Constants.FirebaseCollections.FLICKS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            flicksSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }

            // 2. Delete user's chats and messages
            val chatSessions = db.collection(Constants.FirebaseCollections.CHAT_SESSIONS)
                .whereArrayContains("participants", userId)
                .get()
                .await()
            
            chatSessions.documents.forEach { doc ->
                // Delete all messages in the chat
                val messages = doc.reference.collection("messages").get().await()
                messages.documents.forEach { msg ->
                    msg.reference.delete().await()
                }
                // Delete the chat session
                doc.reference.delete().await()
            }

            // 3. Delete user's notifications
            val notifications = db.collection(Constants.FirebaseCollections.NOTIFICATIONS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            notifications.documents.forEach { doc ->
                doc.reference.delete().await()
            }

            // 4. Remove user from others' followers/following lists
            val allUsers = db.collection(Constants.FirebaseCollections.USERS).get().await()
            allUsers.documents.forEach { userDoc ->
                val userData = userDoc.toObject(UserProfile::class.java)
                if (userData != null && (userData.followers.contains(userId) || userData.following.contains(userId))) {
                    val updatedFollowers = userData.followers.filter { it != userId }
                    val updatedFollowing = userData.following.filter { it != userId }
                    userDoc.reference.update(
                        mapOf(
                            "followers" to updatedFollowers,
                            "following" to updatedFollowing
                        )
                    ).await()
                }
            }

            // 5. Delete user profile
            db.collection(Constants.FirebaseCollections.USERS).document(userId)
                .delete()
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete user data: ${e.message}")
        }
    }
}
