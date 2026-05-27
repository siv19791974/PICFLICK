package com.picflick.app.repository

import com.picflick.app.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

/**
 * Repository for social operations
 * Handles following, followers, friend requests, blocking
 */
class SocialRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun com.google.firebase.firestore.DocumentSnapshot.toNormalizedUserProfile(): UserProfile? {
        val parsed = toObject(UserProfile::class.java) ?: return null
        val resolvedUid = parsed.uid.ifBlank { id }
        val resolvedDisplayName = parsed.displayName
            .trim()
            .ifBlank { parsed.email.substringBefore("@").takeIf { it.isNotBlank() } ?: "PicFlick User" }
        return parsed.copy(
            uid = resolvedUid,
            displayName = resolvedDisplayName,
            displayNameLower = parsed.displayNameLower.ifBlank { resolvedDisplayName.lowercase(Locale.getDefault()) }
        )
    }

    private fun com.google.firebase.firestore.QuerySnapshot.toNormalizedUserProfiles(): List<UserProfile> =
        documents.mapNotNull { it.toNormalizedUserProfile() }

    private fun isPlaceholderDisplayName(name: String): Boolean {
        val normalized = name.trim().lowercase(Locale.getDefault())
        return normalized.isBlank() ||
            normalized == "picflick user" ||
            normalized == "unknown user" ||
            normalized == "user"
    }

    private fun loadProfilesByDocumentIds(
        userIds: List<String>,
        onResult: (Result<List<UserProfile>>) -> Unit
    ) {
        val ids = userIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) {
            onResult(Result.Success(emptyList()))
            return
        }

        val profiles = mutableListOf<UserProfile>()
        var completed = 0
        ids.forEach { id ->
            db.collection("users").document(id).get()
                .addOnSuccessListener { doc ->
                    doc.toNormalizedUserProfile()?.let { profiles.add(it) }
                    completed++
                    if (completed == ids.size) onResult(Result.Success(profiles))
                }
                .addOnFailureListener {
                    completed++
                    if (completed == ids.size) onResult(Result.Success(profiles))
                }
        }
    }

    private suspend fun ensureCurrentUserProfileForSocialActions(
        currentUserId: String,
        currentUserName: String,
        currentUserPhotoUrl: String
    ) {
        val firebaseUser = auth.currentUser
        if (firebaseUser?.uid != currentUserId) return

        val docRef = db.collection("users").document(currentUserId)
        val snapshot = docRef.get().await()
        val fallbackName = currentUserName
            .trim()
            .ifBlank { firebaseUser.displayName?.trim().orEmpty() }
            .ifBlank { firebaseUser.email?.substringBefore("@").orEmpty() }
            .ifBlank { "PicFlick User" }
        val existingDisplayName = snapshot.getString("displayName").orEmpty()
        val resolvedDisplayName = if (isPlaceholderDisplayName(existingDisplayName)) {
            fallbackName
        } else {
            existingDisplayName
        }
        val existingPhotoUrl = snapshot.getString("photoUrl").orEmpty()
        val resolvedPhotoUrl = existingPhotoUrl.ifBlank { currentUserPhotoUrl.ifBlank { firebaseUser.photoUrl?.toString().orEmpty() } }

        val profileSeed = mutableMapOf<String, Any?>(
            "uid" to currentUserId,
            "email" to (snapshot.getString("email") ?: firebaseUser.email.orEmpty()),
            "displayName" to resolvedDisplayName,
            "displayNameLower" to resolvedDisplayName.lowercase(Locale.getDefault()),
            "pendingFollowRequests" to (snapshot.get("pendingFollowRequests") ?: emptyList<String>()),
            "sentFollowRequests" to (snapshot.get("sentFollowRequests") ?: emptyList<String>()),
            "followers" to (snapshot.get("followers") ?: emptyList<String>()),
            "following" to (snapshot.get("following") ?: emptyList<String>()),
            "joinedAt" to (snapshot.getLong("joinedAt") ?: System.currentTimeMillis()),
            "schemaVersion" to (snapshot.getLong("schemaVersion") ?: 2L)
        )
        if (resolvedPhotoUrl.isNotBlank()) {
            profileSeed["photoUrl"] = resolvedPhotoUrl
        }

        docRef.set(profileSeed, SetOptions.merge()).await()
    }

    companion object {
        @Volatile
        private var instance: SocialRepository? = null

        fun getInstance(): SocialRepository {
            return instance ?: synchronized(this) {
                instance ?: SocialRepository().also { instance = it }
            }
        }
    }

    /**
     * Check if two users are friends (follow each other)
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return True if they are friends
     */
    suspend fun areFriends(userId1: String, userId2: String): Boolean {
        return try {
            val user1Doc = db.collection("users").document(userId1).get().await()
            val user2Doc = db.collection("users").document(userId2).get().await()

            @Suppress("UNCHECKED_CAST")
            val user1Following = user1Doc.get("following") as? List<String> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val user2Following = user2Doc.get("following") as? List<String> ?: emptyList()

            userId2 in user1Following && userId1 in user2Following
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check follow request status between two users
     * @param currentUserId The current user
     * @param targetUserId The target user
     * @param onResult Callback with status map containing:
     *                 - hasSentRequest: Boolean
     *                 - hasReceivedRequest: Boolean
     *                 - isFollowing: Boolean
     *                 - isFollowedBy: Boolean
     */
    fun checkFollowRequestStatus(
        currentUserId: String,
        targetUserId: String,
        onResult: (Result<Map<String, Boolean>>) -> Unit
    ) {
        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener { currentDoc ->
                db.collection("users").document(targetUserId).get()
                    .addOnSuccessListener { targetDoc ->
                        val currentProfile = currentDoc.toNormalizedUserProfile()
                        val targetProfile = targetDoc.toNormalizedUserProfile()

                        val status = mapOf(
                            "hasSentRequest" to (currentProfile?.sentFollowRequests?.contains(targetUserId) ?: false),
                            "hasReceivedRequest" to (currentProfile?.pendingFollowRequests?.contains(targetUserId) ?: false),
                            "isFollowing" to (currentProfile?.following?.contains(targetUserId) ?: false),
                            "isFollowedBy" to (currentProfile?.followers?.contains(targetUserId) ?: false),
                            "targetHasSentRequest" to (targetProfile?.sentFollowRequests?.contains(currentUserId) ?: false)
                        )

                        onResult(Result.Success(status))
                    }
                    .addOnFailureListener { e ->
                        onResult(Result.Error(Exception(e.message), "Failed to check follow status"))
                    }
            }
            .addOnFailureListener { e ->
                onResult(Result.Error(Exception(e.message), "Failed to check follow status"))
            }
    }

    /**
     * Follow a user (suspend version)
     * @param currentUserId The current user
     * @param targetUserId The user to follow
     * @return Result success or error
     */
    suspend fun followUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            val batch = db.batch()

            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "following", FieldValue.arrayUnion(targetUserId))

            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(targetUserRef, "followers", FieldValue.arrayUnion(currentUserId))

            batch.commit().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to follow user")
        }
    }

    /**
     * Unfollow a user (suspend version)
     * @param currentUserId The current user
     * @param targetUserId The user to unfollow
     * @return Result success or error
     */
    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            val batch = db.batch()
            val currentUserRef = db.collection("users").document(currentUserId)
            val targetUserRef = db.collection("users").document(targetUserId)

            // Mutual unfriend to keep state consistent on both profiles
            batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))
            batch.update(currentUserRef, "followers", FieldValue.arrayRemove(targetUserId))
            batch.update(currentUserRef, "sentFollowRequests", FieldValue.arrayRemove(targetUserId))
            batch.update(currentUserRef, "pendingFollowRequests", FieldValue.arrayRemove(targetUserId))

            batch.update(targetUserRef, "following", FieldValue.arrayRemove(currentUserId))
            batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))
            batch.update(targetUserRef, "sentFollowRequests", FieldValue.arrayRemove(currentUserId))
            batch.update(targetUserRef, "pendingFollowRequests", FieldValue.arrayRemove(currentUserId))

            batch.commit().await()

            // Remove shared activities immediately (chat sessions + notifications)
            deleteSharedChatSessions(currentUserId, targetUserId)
            deleteRelationshipNotifications(currentUserId, targetUserId)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to unfollow user")
        }
    }

    /**
     * Follow a user (callback version)
     */
    fun followUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        val batch = db.batch()

        val currentUserRef = db.collection("users").document(currentUserId)
        batch.update(currentUserRef, "following", FieldValue.arrayUnion(targetUserId))

        val targetUserRef = db.collection("users").document(targetUserId)
        batch.update(targetUserRef, "followers", FieldValue.arrayUnion(currentUserId))

        batch.commit()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to follow user")) }
    }

    /**
     * Unfollow a user (callback version)
     */
    fun unfollowUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        val batch = db.batch()
        val currentUserRef = db.collection("users").document(currentUserId)
        val targetUserRef = db.collection("users").document(targetUserId)

        // Mutual unfriend to keep state consistent on both profiles
        batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))
        batch.update(currentUserRef, "followers", FieldValue.arrayRemove(targetUserId))
        batch.update(currentUserRef, "sentFollowRequests", FieldValue.arrayRemove(targetUserId))
        batch.update(currentUserRef, "pendingFollowRequests", FieldValue.arrayRemove(targetUserId))

        batch.update(targetUserRef, "following", FieldValue.arrayRemove(currentUserId))
        batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))
        batch.update(targetUserRef, "sentFollowRequests", FieldValue.arrayRemove(currentUserId))
        batch.update(targetUserRef, "pendingFollowRequests", FieldValue.arrayRemove(currentUserId))

        batch.commit()
            .addOnSuccessListener {
                // Remove shared activities immediately (chat sessions + notifications)
                deleteSharedChatSessionsAsync(currentUserId, targetUserId)
                deleteRelationshipNotificationsAsync(currentUserId, targetUserId)
                onResult(Result.Success(Unit))
            }
            .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to unfollow user")) }
    }

    /**
     * Block a user - they can't see your content or interact with you
     * @param currentUserId The current user
     * @param targetUserId The user to block
     * @param onResult Callback with result
     */
    fun blockUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        val batch = db.batch()

        val currentUserRef = db.collection("users").document(currentUserId)
        val targetUserRef = db.collection("users").document(targetUserId)

        batch.update(currentUserRef, "blockedUsers", FieldValue.arrayUnion(targetUserId))
        batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))
        batch.update(currentUserRef, "followers", FieldValue.arrayRemove(targetUserId))
        batch.update(targetUserRef, "following", FieldValue.arrayRemove(currentUserId))
        batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))

        batch.commit()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to block user")) }
    }

    /**
     * Unblock a user
     * @param currentUserId The current user
     * @param targetUserId The user to unblock
     * @param onResult Callback with result
     */
    fun unblockUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("users").document(currentUserId)
            .update("blockedUsers", FieldValue.arrayRemove(targetUserId))
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to unblock user")) }
    }

    // ==================== FRIEND REQUEST SYSTEM ====================

    /**
     * Send a follow request to a user (requires approval)
     * Creates a friend request notification for the target user
     * @param currentUserId The current user
     * @param targetUserId The user to send request to
     * @param currentUserName The current user's name for notification
     * @param currentUserPhotoUrl The current user's photo URL for notification
     * @return Result success or error
     */
    suspend fun sendFollowRequest(
        currentUserId: String,
        targetUserId: String,
        currentUserName: String,
        currentUserPhotoUrl: String
    ): Result<Unit> {
        return try {
            if (currentUserId == targetUserId) {
                return Result.Error(Exception("Invalid request"), "You cannot send a friend request to yourself")
            }

            ensureCurrentUserProfileForSocialActions(
                currentUserId = currentUserId,
                currentUserName = currentUserName,
                currentUserPhotoUrl = currentUserPhotoUrl
            )

            val batch = db.batch()

            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "sentFollowRequests", FieldValue.arrayUnion(targetUserId))

            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(targetUserRef, "pendingFollowRequests", FieldValue.arrayUnion(currentUserId))

            batch.commit().await()

            // Create friend request notification (non-blocking)
            try {
                createFriendRequestNotification(
                    requesterId = currentUserId,
                    requesterName = currentUserName,
                    requesterPhotoUrl = currentUserPhotoUrl,
                    targetUserId = targetUserId
                )
            } catch (notificationError: Exception) {
                android.util.Log.e("SocialRepository", "Friend request saved, but notification failed: ${notificationError.message}")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to send follow request")
        }
    }

    /**
     * Accept a follow request
     * Creates a follow accepted notification for the requester
     * @param currentUserId The current user (accepting)
     * @param requesterId The user who sent the request
     * @param currentUserName The current user's display name for acceptance notification
     * @return Result success or error
     */
    suspend fun acceptFollowRequest(
        currentUserId: String,
        currentUserName: String,
        requesterId: String
    ): Result<Unit> {
        return try {
            val batch = db.batch()

            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "pendingFollowRequests", FieldValue.arrayRemove(requesterId))
            batch.update(currentUserRef, "sentFollowRequests", FieldValue.arrayRemove(requesterId))
            batch.update(currentUserRef, "followers", FieldValue.arrayUnion(requesterId))
            batch.update(currentUserRef, "following", FieldValue.arrayUnion(requesterId))

            val requesterRef = db.collection("users").document(requesterId)
            batch.update(requesterRef, "sentFollowRequests", FieldValue.arrayRemove(currentUserId))
            batch.update(requesterRef, "pendingFollowRequests", FieldValue.arrayRemove(currentUserId))
            batch.update(requesterRef, "following", FieldValue.arrayUnion(currentUserId))
            batch.update(requesterRef, "followers", FieldValue.arrayUnion(currentUserId))

            batch.commit().await()

            // Always resolve accepter name from backend profile to avoid reversed/wrong names.
            val resolvedAccepterName = try {
                val accepterDoc = currentUserRef.get().await()
                accepterDoc.getString("displayName")?.takeIf { it.isNotBlank() }
                    ?: currentUserName.ifBlank { "Someone" }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                currentUserName.ifBlank { "Someone" }
            }

            // Create acceptance notification
            createFollowAcceptedNotification(
                accepterId = currentUserId,
                accepterName = resolvedAccepterName,
                requesterId = requesterId
            )

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to accept follow request")
        }
    }

    /**
     * Reject a follow request
     * @param currentUserId The current user (rejecting)
     * @param requesterId The user who sent the request
     * @return Result success or error
     */
    suspend fun rejectFollowRequest(currentUserId: String, requesterId: String): Result<Unit> {
        return try {
            val batch = db.batch()

            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "pendingFollowRequests", FieldValue.arrayRemove(requesterId))

            val requesterRef = db.collection("users").document(requesterId)
            batch.update(requesterRef, "sentFollowRequests", FieldValue.arrayRemove(currentUserId))

            batch.commit().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to reject follow request")
        }
    }

    /**
     * Decline a friend request (alias for rejectFollowRequest)
     */
    suspend fun declineFollowRequest(currentUserId: String, requesterId: String): Result<Unit> =
        rejectFollowRequest(currentUserId, requesterId)

    /**
     * Cancel a sent follow request
     * @param currentUserId The current user (cancelling)
     * @param targetUserId The user who received the request
     * @return Result success or error
     */
    suspend fun cancelFollowRequest(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            val batch = db.batch()

            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "sentFollowRequests", FieldValue.arrayRemove(targetUserId))

            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(targetUserRef, "pendingFollowRequests", FieldValue.arrayRemove(currentUserId))

            batch.commit().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to cancel follow request")
        }
    }

    /**
     * Get pending follow requests for a user
     * @param userId The user to get requests for
     * @param onResult Callback with list of requester profiles
     */
    fun getPendingFollowRequests(userId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val pendingIds = doc.get("pendingFollowRequests") as? List<String> ?: emptyList()

                if (pendingIds.isEmpty()) {
                    onResult(Result.Success(emptyList()))
                    return@addOnSuccessListener
                }

                loadProfilesByDocumentIds(pendingIds, onResult = onResult)
            }
            .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to get user data")) }
    }

    // ==================== USER LISTS ====================

    /**
     * Get users the current user is following
     * @param userId The user ID
     * @param onResult Callback with list of profiles
     */
    fun getFollowingUsers(userId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val followingIds = doc.get("following") as? List<String> ?: emptyList()

                if (followingIds.isEmpty()) {
                    onResult(Result.Success(emptyList()))
                    return@addOnSuccessListener
                }

                loadProfilesByDocumentIds(followingIds, onResult = onResult)
            }
            .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to get user data")) }
    }

    /**
     * Get users following the current user
     * @param userId The user ID
     * @param onResult Callback with list of profiles
     */
    fun getFollowers(userId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val followerIds = doc.get("followers") as? List<String> ?: emptyList()

                if (followerIds.isEmpty()) {
                    onResult(Result.Success(emptyList()))
                    return@addOnSuccessListener
                }

                loadProfilesByDocumentIds(followerIds, onResult = onResult)
            }
            .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to get user data")) }
    }

    /**
     * Get suggested users (not followed yet)
     * @param userId The current user
     * @param onResult Callback with list of suggested profiles
     */
    fun getSuggestedUsers(userId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val userProfile = userDoc.toNormalizedUserProfile()
                val following = userProfile?.following ?: emptyList()

                val mergedUsers = linkedMapOf<String, UserProfile>()
                var completedQueries = 0
                var lastError: Exception? = null

                fun finishSuggestions() {
                    completedQueries++
                    if (completedQueries < 2) return

                    val suggestions = mergedUsers.values
                        .filter { it.uid != userId && it.uid !in following }
                        .sortedByDescending { it.joinedAt }
                        .take(10)
                    if (suggestions.isNotEmpty() || lastError == null) {
                        onResult(Result.Success(suggestions))
                    } else {
                        onResult(Result.Error(Exception(lastError?.message), "Failed to get suggestions"))
                    }
                }

                db.collection("users")
                    .orderBy("joinedAt", Query.Direction.DESCENDING)
                    .limit(100)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        snapshot.toNormalizedUserProfiles().forEach { mergedUsers[it.uid] = it }
                        finishSuggestions()
                    }
                    .addOnFailureListener { e ->
                        lastError = e
                        finishSuggestions()
                    }

                db.collection("users")
                    .limit(250)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        snapshot.toNormalizedUserProfiles().forEach { mergedUsers[it.uid] = it }
                        finishSuggestions()
                    }
                    .addOnFailureListener { e ->
                        lastError = e
                        finishSuggestions()
                    }
            }
            .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to get user profile")) }
    }

    /**
     * Get all users (for Discover tab)
     * @param currentUserId The current user (to exclude)
     * @param onResult Callback with list of profiles
     */
    fun getAllUsers(currentUserId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        val mergedUsers = linkedMapOf<String, UserProfile>()
        var completedQueries = 0
        var lastError: Exception? = null

        fun finishAllUsers() {
            completedQueries++
            if (completedQueries < 2) return

            val users = mergedUsers.values
                .filter { it.uid != currentUserId }
                .sortedByDescending { it.joinedAt }
            if (users.isNotEmpty() || lastError == null) {
                onResult(Result.Success(users))
            } else {
                onResult(Result.Error(Exception(lastError?.message), "Failed to get users"))
            }
        }

        db.collection("users")
            .orderBy("joinedAt", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.toNormalizedUserProfiles().forEach { mergedUsers[it.uid] = it }
                finishAllUsers()
            }
            .addOnFailureListener { e ->
                lastError = e
                finishAllUsers()
            }

        db.collection("users")
            .limit(250)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.toNormalizedUserProfiles().forEach { mergedUsers[it.uid] = it }
                finishAllUsers()
            }
            .addOnFailureListener { e ->
                lastError = e
                finishAllUsers()
            }
    }

    private suspend fun deleteSharedChatSessions(userId1: String, userId2: String) {
        val sessionsSnapshot = db.collection("chatSessions")
            .whereArrayContains("participants", userId1)
            .get()
            .await()

        val sharedSessionRefs = sessionsSnapshot.documents
            .filter { doc ->
                val participants = doc.get("participants") as? List<*>
                participants?.contains(userId2) == true
            }
            .map { it.reference }

        // Delete chat sessions first so they disappear instantly from both users' chat lists.
        sharedSessionRefs.forEach { sessionRef ->
            sessionRef.delete().await()
        }
    }

    private fun deleteSharedChatSessionsAsync(userId1: String, userId2: String) {
        db.collection("chatSessions")
            .whereArrayContains("participants", userId1)
            .get()
            .addOnSuccessListener { sessionsSnapshot ->
                val sharedSessionRefs = sessionsSnapshot.documents
                    .filter { doc ->
                        val participants = doc.get("participants") as? List<*>
                        participants?.contains(userId2) == true
                    }
                    .map { it.reference }

                // Delete chat sessions first so they disappear instantly from both users' chat lists.
                sharedSessionRefs.forEach { sessionRef ->
                    sessionRef.delete()
                }
            }
    }

    private suspend fun deleteRelationshipNotifications(userId1: String, userId2: String) {
        // Notifications shown to user1 triggered by user2
        val user1FromUser2 = db.collection("notifications")
            .whereEqualTo("userId", userId1)
            .whereEqualTo("senderId", userId2)
            .get()
            .await()

        // Notifications shown to user2 triggered by user1
        val user2FromUser1 = db.collection("notifications")
            .whereEqualTo("userId", userId2)
            .whereEqualTo("senderId", userId1)
            .get()
            .await()

        val refsToDelete = (user1FromUser2.documents + user2FromUser1.documents)
            .map { it.reference }

        refsToDelete.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
    }

    private fun deleteRelationshipNotificationsAsync(userId1: String, userId2: String) {
        db.collection("notifications")
            .whereEqualTo("userId", userId1)
            .whereEqualTo("senderId", userId2)
            .get()
            .addOnSuccessListener { user1FromUser2 ->
                db.collection("notifications")
                    .whereEqualTo("userId", userId2)
                    .whereEqualTo("senderId", userId1)
                    .get()
                    .addOnSuccessListener { user2FromUser1 ->
                        val refsToDelete = (user1FromUser2.documents + user2FromUser1.documents)
                            .map { it.reference }

                        refsToDelete.chunked(400).forEach { chunk ->
                            val batch = db.batch()
                            chunk.forEach { batch.delete(it) }
                            batch.commit()
                        }
                    }
            }
    }

    // ==================== NOTIFICATION CREATION ====================

    private suspend fun createFriendRequestNotification(
        requesterId: String,
        requesterName: String,
        requesterPhotoUrl: String,
        targetUserId: String
    ) {
        val notification = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to targetUserId,
            "senderId" to requesterId,
            "senderName" to requesterName,
            "senderPhotoUrl" to requesterPhotoUrl,
            "type" to "FRIEND_REQUEST",
            "title" to "Friend request from $requesterName",
            "message" to "Accept or decline",
            "isRead" to false,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification).await()
    }

    private fun createFollowAcceptedNotification(
        accepterId: String,
        accepterName: String,
        requesterId: String
    ) {
        val notification = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to requesterId,
            "senderId" to accepterId,
            "senderName" to accepterName,
            "type" to "FOLLOW_ACCEPTED",
            "title" to "$accepterName accepted your request",
            "message" to "You are now connected",
            "isRead" to false,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification)
    }
}
