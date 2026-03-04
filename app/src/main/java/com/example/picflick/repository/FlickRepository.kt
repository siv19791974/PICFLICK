package com.example.picflick.repository

import com.example.picflick.data.Comment
import com.example.picflick.data.Flick
import com.example.picflick.data.Result
import com.example.picflick.data.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

/**
 * Repository class for handling all Firebase Firestore and Storage operations
 */
class FlickRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // Cache for user profile
    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: Flow<UserProfile?> = _currentUserProfile.asStateFlow()

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
     * Get user profile by ID with real-time updates
     */
    fun getUserProfile(uid: String, onResult: (Result<UserProfile>) -> Unit) {
        db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(error, "Failed to load profile"))
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val profile = snapshot.toObject(UserProfile::class.java)
                    _currentUserProfile.value = profile
                    onResult(Result.Success(profile!!))
                } else {
                    onResult(Result.Error(Exception("Profile not found"), "User profile not found"))
                }
            }
    }

    /**
     * Save or update user profile
     */
    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            db.collection("users").document(profile.uid)
                .set(profile, SetOptions.merge())
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to save profile")
        }
    }

    /**
     * Check if two users are friends (mutual following)
     */
    suspend fun areFriends(userId1: String, userId2: String): Boolean {
        return try {
            val user1Doc = db.collection("users").document(userId1).get().await()
            val user2Doc = db.collection("users").document(userId2).get().await()
            
            val user1Following = user1Doc.get("following") as? List<String> ?: emptyList()
            val user2Following = user2Doc.get("following") as? List<String> ?: emptyList()
            
            // Friends = both follow each other
            user1Following.contains(userId2) && user2Following.contains(userId1)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get user's friends list (mutual followers)
     */
    suspend fun getFriendsList(userId: String): List<String> {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            val following = userDoc.get("following") as? List<String> ?: emptyList()
            val followers = userDoc.get("followers") as? List<String> ?: emptyList()
            
            // Friends = intersection of following and followers
            following.intersect(followers.toSet()).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get all flicks from friends + user's own photos (private photos)
     * Queries separately and merges to avoid Firestore whereIn limitations
     */
    fun getFlicksForUser(userId: String, onResult: (Result<List<Flick>>) -> Unit) {
        GlobalScope.launch {
            try {
                // Get friends list
                val friends = getFriendsList(userId)
                
                // Use a map to collect all flicks by ID (avoids duplicates)
                val allFlicks = mutableMapOf<String, Flick>()
                
                // Query 1: User's own photos (always show these)
                db.collection("flicks")
                    .whereEqualTo("userId", userId)
                    .addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null) {
                            snapshot.toObjects(Flick::class.java).forEach { flick ->
                                allFlicks[flick.id] = flick
                            }
                        }
                        
                        // Try to return results after each update
                        returnMergedResults(allFlicks, onResult)
                    }
                
                // Query 2: Friends' photos (batch them to avoid whereIn limit)
                if (friends.isNotEmpty()) {
                    // Firestore whereIn has 10-item limit, so batch if needed
                    val friendBatches = friends.chunked(10)
                    
                    friendBatches.forEach { batch ->
                        db.collection("flicks")
                            .whereIn("userId", batch)
                            .addSnapshotListener { snapshot, error ->
                                if (error == null && snapshot != null) {
                                    snapshot.toObjects(Flick::class.java).forEach { flick ->
                                        allFlicks[flick.id] = flick
                                    }
                                }
                                
                                returnMergedResults(allFlicks, onResult)
                            }
                    }
                }
                
            } catch (e: Exception) {
                onResult(Result.Error(e, "Failed to load photos"))
            }
        }
    }
    
    private fun returnMergedResults(
        allFlicks: MutableMap<String, Flick>,
        onResult: (Result<List<Flick>>) -> Unit
    ) {
        // Sort by timestamp descending and return
        val sortedFlicks = allFlicks.values
            .sortedByDescending { it.timestamp }
            .take(50)
        
        onResult(Result.Success(sortedFlicks))
    }

    /**
     * Get flicks - DEPRECATED: Use getFlicksForUser() for privacy
     */
    fun getFlicks(onResult: (Result<List<Flick>>) -> Unit) {
        db.collection("flicks")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(error, "Failed to load photos"))
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
        if (userId.isEmpty()) {
            onResult(Result.Error(Exception("User ID is empty"), "User not logged in"))
            return
        }
        
        db.collection("flicks")
            .whereEqualTo("userId", userId)
            .get()  // Simple query without ordering (no index needed)
            .addOnSuccessListener { snapshot ->
                val flicks = snapshot.toObjects(Flick::class.java)
                    .sortedByDescending { it.timestamp }  // Sort in memory
                android.util.Log.d("FlickRepository", "Loaded ${flicks.size} photos for user $userId")
                onResult(Result.Success(flicks))
            }
            .addOnFailureListener { error ->
                android.util.Log.e("FlickRepository", "Error loading user photos: ${error.message}")
                onResult(Result.Error(error, "Failed to load user photos: ${error.message}"))
            }
    }

    /**
     * Get daily upload count for a user
     */
    fun getDailyUploadCount(userId: String, onResult: (Result<Int>) -> Unit) {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        db.collection("flicks")
            .whereEqualTo("userId", userId)
            .whereGreaterThan("timestamp", startOfDay)
            .get()
            .addOnSuccessListener { snap ->
                onResult(Result.Success(snap.size()))
            }
            .addOnFailureListener { error ->
                onResult(Result.Error(error, "Failed to check upload limit"))
            }
    }

    /**
     * Toggle like on a flick and create notification
     */
    fun toggleLike(
        flickId: String, 
        userId: String, 
        userName: String,
        userPhotoUrl: String,
        isLiked: Boolean, 
        onResult: (Result<Unit>) -> Unit
    ) {
        // DEPRECATED: Use toggleReaction instead
        // This now maps to LIKE reaction for backward compatibility
        val reactionType = if (isLiked) null else com.example.picflick.data.ReactionType.LIKE
        toggleReaction(flickId, userId, userName, userPhotoUrl, reactionType, onResult)
    }
    
    /**
     * Toggle or update a reaction on a flick
     * @param reactionType null = remove reaction
     */
    fun toggleReaction(
        flickId: String,
        userId: String,
        userName: String,
        userPhotoUrl: String,
        reactionType: com.example.picflick.data.ReactionType?,
        onResult: (Result<Unit>) -> Unit
    ) {
        // Get current flick data first
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { flickDoc ->
                val ownerId = flickDoc.getString("userId") ?: ""
                val currentReactions = flickDoc.get("reactions") as? Map<String, String> ?: emptyMap()
                
                // Check if user already has a reaction
                val userCurrentReaction = currentReactions[userId]
                
                // Prepare update
                val newReactions = currentReactions.toMutableMap()
                
                when {
                    // Remove reaction if same type clicked (toggle off)
                    reactionType != null && userCurrentReaction == reactionType.name -> {
                        newReactions.remove(userId)
                    }
                    // Add or update reaction
                    reactionType != null -> {
                        newReactions[userId] = reactionType.name
                    }
                    // Remove reaction if null passed
                    else -> {
                        newReactions.remove(userId)
                    }
                }
                
                // Update Firestore
                db.collection("flicks").document(flickId)
                    .update("reactions", newReactions)
                    .addOnSuccessListener {
                        // Create notification if adding a new reaction (not removing)
                        if (reactionType != null && userCurrentReaction != reactionType.name && ownerId != userId) {
                            createReactionNotification(
                                flickId = flickId,
                                ownerId = ownerId,
                                reactorId = userId,
                                reactorName = userName,
                                reactorPhotoUrl = userPhotoUrl,
                                reactionType = reactionType
                            )
                        }
                        onResult(Result.Success(Unit))
                    }
                    .addOnFailureListener { error ->
                        onResult(Result.Error(error, "Failed to update reaction"))
                    }
            }
            .addOnFailureListener { error ->
                onResult(Result.Error(error, "Failed to get flick data"))
            }
    }
    
    /**
     * Create notification when someone reacts to a photo
     */
    private fun createReactionNotification(
        flickId: String,
        ownerId: String,
        reactorId: String,
        reactorName: String,
        reactorPhotoUrl: String,
        reactionType: com.example.picflick.data.ReactionType
    ) {
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { flickDoc ->
                val flickImageUrl = flickDoc.getString("imageUrl")
                
                val emoji = reactionType.toEmoji()
                val displayName = reactionType.toDisplayName()
                
                val notification = hashMapOf(
                    "id" to db.collection("notifications").document().id,
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
                
                db.collection("notifications").document(notification["id"] as String)
                    .set(notification)
            }
    }
    
    /**
     * Create notification when someone likes a photo (deprecated - use createReactionNotification)
     */
    private fun createLikeNotification(
        flickId: String, 
        likerId: String,
        likerName: String,
        likerPhotoUrl: String
    ) {
        // Map to REACTION notification with LIKE type
        createReactionNotification(
            flickId = flickId,
            ownerId = "", // Will be fetched inside
            reactorId = likerId,
            reactorName = likerName,
            reactorPhotoUrl = likerPhotoUrl,
            reactionType = com.example.picflick.data.ReactionType.LIKE
        )
    }

    /**
     * Search users by display name
     */
    fun searchUsers(query: String, currentUserId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        if (query.isBlank()) {
            onResult(Result.Success(emptyList()))
            return
        }

        db.collection("users")
            .orderBy("displayName")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.toObjects(UserProfile::class.java)
                    .filter { it.uid != currentUserId }
                onResult(Result.Success(users))
            }
            .addOnFailureListener { error ->
                onResult(Result.Error(error, "Failed to search users"))
            }
    }

    /**
     * Follow a user
     */
    suspend fun followUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            db.collection("users").document(currentUserId)
                .update("following", FieldValue.arrayUnion(targetUserId))
                .await()
            
            db.collection("users").document(targetUserId)
                .update("followers", FieldValue.arrayUnion(currentUserId))
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to follow user")
        }
    }

    /**
     * Unfollow a user
     */
    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            db.collection("users").document(currentUserId)
                .update("following", FieldValue.arrayRemove(targetUserId))
                .await()
            
            db.collection("users").document(targetUserId)
                .update("followers", FieldValue.arrayRemove(currentUserId))
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to unfollow user")
        }
    }

    /**
     * Upload a flick image
     */
    suspend fun uploadFlickImage(userId: String, imageBytes: ByteArray): Result<String> {
        return try {
            val filename = "flicks/${userId}/${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child(filename)
            
            ref.putBytes(imageBytes).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            
            Result.Success(downloadUrl)
        } catch (e: Exception) {
            Result.Error(e, "Failed to upload image")
        }
    }

    /**
     * Create a new flick and notify followers (friends)
     */
    suspend fun createFlick(flick: Flick, userPhotoUrl: String = ""): Result<Unit> {
        return try {
            val flickWithId = if (flick.id.isEmpty()) {
                flick.copy(id = db.collection("flicks").document().id)
            } else {
                flick
            }
            
            db.collection("flicks").document(flickWithId.id)
                .set(flickWithId)
                .await()
            
            // Notify followers (friends) about new photo
            if (flick.privacy == "friends") {
                createPhotoAddedNotifications(flickWithId, userPhotoUrl)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create flick")
        }
    }
    
    /**
     * Create notifications for followers when a new photo is added
     */
    private suspend fun createPhotoAddedNotifications(flick: Flick, userPhotoUrl: String) {
        try {
            // Get user's followers
            val userDoc = db.collection("users").document(flick.userId).get().await()
            val followers = userDoc.get("followers") as? List<String> ?: emptyList()
            val following = userDoc.get("following") as? List<String> ?: emptyList()
            
            // Only notify friends (mutual followers)
            val friends = followers.intersect(following.toSet())
            
            friends.forEach { friendId ->
                val notification = hashMapOf(
                    "id" to db.collection("notifications").document().id,
                    "userId" to friendId,
                    "senderId" to flick.userId,
                    "senderName" to flick.userName,
                    "senderPhotoUrl" to userPhotoUrl,
                    "type" to "PHOTO_ADDED",
                    "title" to "${flick.userName} added a new photo",
                    "message" to if (flick.description.isNotBlank()) flick.description else "Check it out!",
                    "flickId" to flick.id,
                    "flickImageUrl" to flick.imageUrl,
                    "isRead" to false,
                    "timestamp" to System.currentTimeMillis()
                )
                
                db.collection("notifications").document(notification["id"] as String)
                    .set(notification)
                    .await()
            }
        } catch (e: Exception) {
            // Silently fail - don't block photo upload if notifications fail
            e.printStackTrace()
        }
    }

    /**
     * Update flick description/caption
     */
    suspend fun updateFlickDescription(flickId: String, description: String): Result<Unit> {
        return try {
            db.collection("flicks").document(flickId)
                .update("description", description)
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update caption")
        }
    }

    /**
     * Add a comment and create notification
     */
    suspend fun addComment(
        comment: Comment,
        commenterName: String,
        commenterPhotoUrl: String
    ): Result<Unit> {
        return try {
            val commentWithId = if (comment.id.isEmpty()) {
                comment.copy(id = db.collection("comments").document().id)
            } else {
                comment
            }

            db.collection("comments").document(commentWithId.id)
                .set(commentWithId)
                .await()

            // Increment comment count on the flick
            db.collection("flicks").document(comment.flickId)
                .update("commentCount", FieldValue.increment(1))
                .await()
            
            // Create notification for flick owner
            createCommentNotification(comment, commenterName, commenterPhotoUrl)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to add comment")
        }
    }
    
    /**
     * Create notification when someone comments on a photo
     */
    private suspend fun createCommentNotification(
        comment: Comment,
        commenterName: String,
        commenterPhotoUrl: String
    ) {
        try {
            // Get flick owner
            val flickDoc = db.collection("flicks").document(comment.flickId).get().await()
            val ownerId = flickDoc.getString("userId") ?: return
            val flickImageUrl = flickDoc.getString("imageUrl")
            
            // Don't notify if user comments on their own photo
            if (ownerId == comment.userId) return
            
            val notification = hashMapOf(
                "id" to db.collection("notifications").document().id,
                "userId" to ownerId,
                "senderId" to comment.userId,
                "senderName" to commenterName,
                "senderPhotoUrl" to commenterPhotoUrl,
                "type" to "COMMENT",
                "title" to "$commenterName commented on your photo",
                "message" to comment.text,
                "flickId" to comment.flickId,
                "flickImageUrl" to flickImageUrl,
                "isRead" to false,
                "timestamp" to System.currentTimeMillis()
            )
            
            db.collection("notifications").document(notification["id"] as String)
                .set(notification)
                .await()
        } catch (e: Exception) {
            // Silently fail - don't block comment if notification fails
            e.printStackTrace()
        }
    }

    /**
     * Get comments for a flick with real-time updates
     */
    fun getComments(flickId: String, onResult: (Result<List<Comment>>) -> Unit) {
        db.collection("comments")
            .whereEqualTo("flickId", flickId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(error, "Failed to load comments"))
                    return@addSnapshotListener
                }
                
                val comments = snapshot?.toObjects(Comment::class.java) ?: emptyList()
                onResult(Result.Success(comments))
            }
    }

    /**
     * Delete a flick
     */
    suspend fun deleteFlick(flickId: String): Result<Unit> {
        return try {
            db.collection("flicks").document(flickId)
                .delete()
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete photo")
        }
    }

    /**
     * Get all users (for suggestions)
     */
    fun getAllUsers(onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection("users")
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.toObjects(UserProfile::class.java)
                onResult(Result.Success(users))
            }
            .addOnFailureListener { error ->
                onResult(Result.Error(error, "Failed to load users"))
            }
    }

    /**
     * Find users by phone numbers (for contacts sync)
     * Note: This is a simplified version. In production, you'd want to 
     * store hashed phone numbers in user profiles for matching.
     */
    fun findUsersByPhoneNumbers(
        phoneNumbers: List<String>,
        onResult: (Result<List<UserProfile>>) -> Unit
    ) {
        if (phoneNumbers.isEmpty()) {
            onResult(Result.Success(emptyList()))
            return
        }

        // For now, just return some random users as "contacts on app"
        // In production, match actual phone numbers
        db.collection("users")
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.toObjects(UserProfile::class.java)
                    .shuffled()
                    .take(minOf(5, phoneNumbers.size)) // Simulate some matches
                onResult(Result.Success(users))
            }
            .addOnFailureListener { error ->
                onResult(Result.Error(error, "Failed to sync contacts"))
            }
    }

    // ==================== NOTIFICATION METHODS ====================

    /**
     * Create a notification for a user
     */
    suspend fun createNotification(notification: com.example.picflick.data.Notification): Result<Unit> {
        return try {
            val notificationId = UUID.randomUUID().toString()
            val notificationWithId = notification.copy(id = notificationId)
            
            db.collection("notifications")
                .document(notificationId)
                .set(notificationWithId)
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create notification")
        }
    }

    /**
     * Listen to notifications for a user in real-time
     */
    fun listenToNotifications(
        userId: String,
        onUpdate: (List<com.example.picflick.data.Notification>) -> Unit,
        onError: (String) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("notifications")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError("Failed to load notifications: ${error.message}")
                    return@addSnapshotListener
                }
                
                val notifications = snapshot?.toObjects(com.example.picflick.data.Notification::class.java) 
                    ?: emptyList()
                onUpdate(notifications)
            }
    }

    /**
     * Mark a notification as read
     */
    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        return try {
            db.collection("notifications")
                .document(notificationId)
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
            db.collection("notifications")
                .document(notificationId)
                .delete()
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete notification")
        }
    }
}
