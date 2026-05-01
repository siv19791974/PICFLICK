package com.picflick.app.repository

import com.picflick.app.Constants
import com.picflick.app.data.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.picflick.app.util.CostControlManager
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Repository for notification operations
 * Handles creating, reading, and managing user notifications
 */
class NotificationRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        @Volatile
        private var instance: NotificationRepository? = null

        fun getInstance(): NotificationRepository {
            return instance ?: synchronized(this) {
                instance ?: NotificationRepository().also { instance = it }
            }
        }
    }

    /**
     * Listen to notifications for a user in real-time
     * @param userId The user to listen for
     * @param onUpdate Callback with updated notification list
     * @param onError Callback with error message
     * @return ListenerRegistration to stop listening
     */
    fun listenToNotifications(
        userId: String,
        onUpdate: (List<Notification>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        if (CostControlManager.isEnabled(Constants.FeatureFlags.KILL_NOTIFICATION_LISTENERS)) {
            android.util.Log.w("NotificationRepository", "listenToNotifications blocked by cost kill-switch")
            onUpdate(emptyList())
            return ListenerRegistration { }
        }
        return db.collection("notifications")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError("Failed to load notifications: ${error.message}")
                    return@addSnapshotListener
                }

                val notifications = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Notification::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                onUpdate(notifications)
            }
    }

    /**
     * Mark a notification as read
     * @param notificationId The notification to mark
     * @return Result success or error
     */
    suspend fun markAsRead(notificationId: String): Result<Unit> {
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
     * @param userId The user whose notifications to mark
     * @return Result success or error
     */
    suspend fun markAllAsRead(userId: String): Result<Unit> {
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
     * @param notificationId The notification to delete
     * @return Result success or error
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

    // ==================== NOTIFICATION CREATION ====================

    /**
     * Create a like notification
     * @param flickId The photo that was liked
     * @param likerId The user who liked it
     * @param likerName The name of the liker
     * @param ownerId The photo owner
     */
    fun createLikeNotification(
        flickId: String,
        likerId: String,
        likerName: String,
        ownerId: String
    ) {
        if (likerId == ownerId) return // Don't notify self

        val notification = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to ownerId,
            "senderId" to likerId,
            "senderName" to likerName,
            "type" to "LIKE",
            "title" to "$likerName liked your photo",
            "message" to "",
            "flickId" to flickId,
            "isRead" to false,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification)
    }

    /**
     * Create a reaction notification
     * @param flickId The photo that was reacted to
     * @param reactorId The user who reacted
     * @param reactorName The name of the reactor
     * @param reactionType The reaction type
     * @param ownerId The photo owner
     */
    fun createReactionNotification(
        flickId: String,
        reactorId: String,
        reactorName: String,
        reactionType: ReactionType,
        ownerId: String
    ) {
        if (reactorId == ownerId) return // Don't notify self

        val notification = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to ownerId,
            "senderId" to reactorId,
            "senderName" to reactorName,
            "type" to "REACTION",
            "title" to "$reactorName reacted to your photo",
            "message" to reactionType.name,
            "flickId" to flickId,
            "isRead" to false,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification)
    }

    /**
     * Create a follow notification
     * @param followerId The user who followed
     * @param followerName The name of the follower
     * @param targetUserId The user being followed
     */
    fun createFollowNotification(
        followerId: String,
        followerName: String,
        targetUserId: String
    ) {
        val notification = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to targetUserId,
            "senderId" to followerId,
            "senderName" to followerName,
            "type" to "FOLLOW",
            "title" to "$followerName started following you",
            "message" to "",
            "isRead" to false,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification)
    }

    /**
     * Create a friend request notification
     * @param requesterId The user sending the request
     * @param requesterName The name of the requester
     * @param requesterPhotoUrl The photo URL of the requester
     * @param targetUserId The user receiving the request
     */
    fun createFriendRequestNotification(
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

    /**
     * Create a follow accepted notification
     * @param accepterId The user who accepted
     * @param accepterName The name of the accepter
     * @param requesterId The user whose request was accepted
     */
    fun createFollowAcceptedNotification(
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

    /**
     * Create a tag notification
     * @param flickId The photo the user was tagged in
     * @param photoOwnerId The owner of the photo
     * @param photoOwnerName The name of the photo owner
     * @param photoOwnerPhotoUrl The photo URL of the owner
     * @param taggedUserId The user being tagged
     */
    fun createTagNotification(
        flickId: String,
        photoOwnerId: String,
        photoOwnerName: String,
        photoOwnerPhotoUrl: String,
        taggedUserId: String
    ) {
        val notification = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to taggedUserId,
            "senderId" to photoOwnerId,
            "senderName" to photoOwnerName,
            "senderPhotoUrl" to photoOwnerPhotoUrl,
            "type" to "TAG",
            "title" to "$photoOwnerName tagged you",
            "message" to "You're in a photo!",
            "flickId" to flickId,
            "isRead" to false,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification)
    }

    /**
     * Create a photo added notification for friends
     * @param flick The new photo
     * @param ownerPhotoUrl The owner's photo URL
     */
    fun createPhotoNotifications(flick: Flick, ownerPhotoUrl: String) {
        // This would notify all friends - simplified version
        // In production, you'd get the friend list and create batch notifications
    }

    /**
     * Create a message notification
     * @param chatId The chat session ID
     * @param senderId The message sender
     * @param senderName The sender's name
     * @param messageText The message content (or "📷 Photo" for images)
     * @param recipientId The message recipient
     */
    fun createMessageNotification(
        chatId: String,
        senderId: String,
        senderName: String,
        messageText: String,
        recipientId: String
    ) {
        if (senderId == recipientId) return // Don't notify self

        val notification = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to recipientId,
            "senderId" to senderId,
            "senderName" to senderName,
            "type" to "MESSAGE",
            "title" to "New Message from $senderName",
            "message" to messageText,
            "chatId" to chatId,
            "isRead" to false,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification)
    }

    // ==================== AUTO CLEANUP ====================

    /**
     * Auto-delete read notifications older than 7 days
     * Call this when app starts or periodically to keep inbox clean
     * @param userId The user whose notifications to clean up
     * @param onComplete Callback with count of deleted notifications
     */
    suspend fun cleanupOldReadNotifications(
        userId: String,
        onComplete: (Int) -> Unit = {}
    ) {
        try {
            // Calculate 7 days ago
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)

            // Find read notifications older than 7 days
            val oldNotifications = db.collection("notifications")
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", true)
                .whereLessThan("timestamp", sevenDaysAgo)
                .get()
                .await()

            var deletedCount = 0
            val batch = db.batch()

            for (doc in oldNotifications.documents) {
                batch.delete(doc.reference)
                deletedCount++
            }

            if (deletedCount > 0) {
                batch.commit().await()
            }

            onComplete(deletedCount)
        } catch (e: Exception) {
            // Silently fail - cleanup is not critical
            onComplete(0)
        }
    }
}
