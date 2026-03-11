package com.picflick.app.repository

import com.picflick.app.data.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Repository for social operations
 * Handles following, followers, friend requests, blocking
 */
class SocialRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()

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
                        val currentProfile = currentDoc.toObject(UserProfile::class.java)
                        val targetProfile = targetDoc.toObject(UserProfile::class.java)

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
            batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))

            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))

            batch.commit().await()
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
        batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))

        val targetUserRef = db.collection("users").document(targetUserId)
        batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))

        batch.commit()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
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
            val batch = db.batch()

            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "sentFollowRequests", FieldValue.arrayUnion(targetUserId))

            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(targetUserRef, "pendingFollowRequests", FieldValue.arrayUnion(currentUserId))

            batch.commit().await()

            // Create friend request notification
            createFriendRequestNotification(
                requesterId = currentUserId,
                requesterName = currentUserName,
                requesterPhotoUrl = currentUserPhotoUrl,
                targetUserId = targetUserId
            )

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
     * @param requesterName The requester's name for notification
     * @return Result success or error
     */
    suspend fun acceptFollowRequest(
        currentUserId: String,
        requesterId: String,
        requesterName: String
    ): Result<Unit> {
        return try {
            val batch = db.batch()

            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "pendingFollowRequests", FieldValue.arrayRemove(requesterId))
            batch.update(currentUserRef, "followers", FieldValue.arrayUnion(requesterId))

            val requesterRef = db.collection("users").document(requesterId)
            batch.update(requesterRef, "sentFollowRequests", FieldValue.arrayRemove(currentUserId))
            batch.update(requesterRef, "following", FieldValue.arrayUnion(currentUserId))

            batch.commit().await()

            // Create acceptance notification
            createFollowAcceptedNotification(
                accepterId = currentUserId,
                accepterName = requesterName,
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

                db.collection("users")
                    .whereIn("uid", pendingIds.take(10))
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val users = snapshot.toObjects(UserProfile::class.java)
                        onResult(Result.Success(users))
                    }
                    .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to get pending requests")) }
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

                db.collection("users")
                    .whereIn("uid", followingIds.take(10))
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val users = snapshot.toObjects(UserProfile::class.java)
                        onResult(Result.Success(users))
                    }
                    .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to get following")) }
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

                db.collection("users")
                    .whereIn("uid", followerIds.take(10))
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val users = snapshot.toObjects(UserProfile::class.java)
                        onResult(Result.Success(users))
                    }
                    .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to get followers")) }
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
                val userProfile = userDoc.toObject(UserProfile::class.java)
                val following = userProfile?.following ?: emptyList()

                db.collection("users")
                    .limit(20)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val allUsers = snapshot.toObjects(UserProfile::class.java)
                        val suggestions = allUsers
                            .filter { it.uid != userId && it.uid !in following }
                            .take(10)
                        onResult(Result.Success(suggestions))
                    }
                    .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to get suggestions")) }
            }
            .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to get user profile")) }
    }

    /**
     * Get all users (for Discover tab)
     * @param currentUserId The current user (to exclude)
     * @param onResult Callback with list of profiles
     */
    fun getAllUsers(currentUserId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection("users")
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val allUsers = snapshot.toObjects(UserProfile::class.java)
                val users = allUsers.filter { it.uid != currentUserId }
                onResult(Result.Success(users))
            }
            .addOnFailureListener { e -> onResult(Result.Error(Exception(e.message), "Failed to get users")) }
    }

    // ==================== NOTIFICATION CREATION ====================

    private fun createFriendRequestNotification(
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

        db.collection("notifications").add(notification)
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
