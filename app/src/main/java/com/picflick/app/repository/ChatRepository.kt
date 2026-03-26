package com.picflick.app.repository

import com.picflick.app.data.ChatMessage
import com.picflick.app.data.ChatSession
import com.picflick.app.data.Result
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Repository for chat/messaging operations
 */
class ChatRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun Any?.toIntOrNullSafe(): Int? = when (this) {
        is Int -> this
        is Long -> this.toInt()
        is Double -> this.toInt()
        is Float -> this.toInt()
        is String -> this.toIntOrNull()
        else -> null
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.getIntFromAny(field: String): Int? {
        return get(field).toIntOrNullSafe()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.getStringMap(field: String): Map<String, String> {
        return (get(field) as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? String ?: return@mapNotNull null
                key to value
            }
            ?.toMap()
            ?: emptyMap()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toChatSessionSafe(userId: String): ChatSession {
        val participants = (get("participants") as? List<*>)
            ?.filterIsInstance<String>()
            ?: emptyList()

        val unreadFromDirectField = getIntFromAny("unreadCount_$userId")
        val unreadFromMap = (get("unreadCount") as? Map<*, *>)
            ?.get(userId)
            .toIntOrNullSafe()

        val resolvedUnread = unreadFromDirectField ?: unreadFromMap ?: 0

        return ChatSession(
            id = getString("id").orEmpty().ifBlank { id },
            participants = participants,
            participantNames = getStringMap("participantNames"),
            participantPhotos = getStringMap("participantPhotos"),
            lastMessage = getString("lastMessage").orEmpty(),
            lastTimestamp = getLong("lastTimestamp") ?: 0L,
            lastSenderId = getString("lastSenderId").orEmpty(),
            lastMessageRead = getBoolean("lastMessageRead") ?: false,
            unreadCount = resolvedUnread
        )
    }

    /**
     * Get all chat sessions for a user
     */
    fun getChatSessions(userId: String): Flow<List<ChatSession>> = callbackFlow {
        val subscription = db.collection("chatSessions")
            .whereArrayContains("participants", userId)
            // Removed orderBy - requires composite index. Sort client-side instead.
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ChatRepository", "Error loading chat sessions: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                launch {
                    val rawSessions = snapshot?.documents?.mapNotNull { doc ->
                        runCatching { doc.toChatSessionSafe(userId) }.getOrNull()
                    } ?: emptyList()

                    // Keep valid 1:1 chat sessions visible even if friendship changes later.
                    val filteredSessions = rawSessions.filter { session ->
                        val otherUserId = session.participants.firstOrNull { it != userId }
                        otherUserId != null && session.participants.size == 2
                    }

                    // Sort client-side by lastTimestamp descending
                    val sortedSessions = filteredSessions.sortedByDescending { it.lastTimestamp }
                    trySend(sortedSessions)
                }
}
        awaitClose { subscription.remove() }
    }

    /**
     * Get messages for a specific chat session
     */
    fun getMessages(chatId: String, currentUserId: String): Flow<List<ChatMessage>> = callbackFlow {
        android.util.Log.d("ChatRepository", "Setting up messages listener for chat: $chatId")
        val subscription = db.collection("chatSessions")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ChatRepository", "Messages listener error: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { doc ->
                    val message = doc.toObject(ChatMessage::class.java) ?: return@mapNotNull null
                    val deletedFor = (doc.get("deletedForUserIds") as? List<*>)
                        ?.filterIsInstance<String>()
                        ?: emptyList()

                    if (currentUserId.isNotBlank() && deletedFor.contains(currentUserId)) {
                        null
                    } else {
                        message
                    }
                } ?: emptyList()

                android.util.Log.d("ChatRepository", "Messages updated: ${messages.size} messages, docChanges: ${snapshot?.documentChanges?.size ?: 0}")
                messages.take(3).forEach { msg ->
                    android.util.Log.d("ChatRepository", "  Msg ${msg.id.take(8)}: read=${msg.read}, delivered=${msg.delivered}, sender=${msg.senderId.take(8)}")
                }
                trySend(messages)
            }
        awaitClose {
            android.util.Log.d("ChatRepository", "Removing messages listener for chat: $chatId")
            subscription.remove()
        }
    }

    /**
     * Observe per-user typing state for a chat session.
     */
    fun observeTypingStatus(chatId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val subscription = db.collection("chatSessions")
            .document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val typingMap = (snapshot?.get("typingStatus") as? Map<*, *>)
                    ?.mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = v as? Boolean ?: false
                        key to value
                    }
                    ?.toMap()
                    ?: emptyMap()

                trySend(typingMap)
            }

        awaitClose { subscription.remove() }
    }

    /**
     * Update current user's typing state for this chat.
     */
    suspend fun setTypingStatus(chatId: String, userId: String, isTyping: Boolean): Result<Unit> {
        return try {
            db.collection("chatSessions")
                .document(chatId)
                .update(
                    mapOf(
                        "typingStatus.$userId" to isTyping,
                        "typingUpdatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "Failed to update typing status")
        }
    }

    /**
     * Send a message and create notification
     */
    suspend fun sendMessage(
        chatId: String,
        message: ChatMessage,
        recipientId: String
    ): Result<Unit> {
        return try {
            val resolvedMessageId = message.id.ifBlank {
                db.collection("chatSessions").document(chatId)
                    .collection("messages").document().id
            }
            val messageWithId = message.copy(id = resolvedMessageId)

            // Add message
            db.collection("chatSessions").document(chatId)
                .collection("messages").document(messageWithId.id)
                .set(messageWithId)
                .await()

            // Update session last message
            val sessionDoc = db.collection("chatSessions").document(chatId)
            val participantsSnapshot = sessionDoc.get().await()
            val participants = (participantsSnapshot.get("participants") as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()
            val senderId = message.senderId

            val updates = mutableMapOf<String, Any>(
                "lastMessage" to if (message.text.isBlank() && message.imageUrl.isNotEmpty()) "📷 Photo" else message.text,
                "lastTimestamp" to message.timestamp,
                "lastSenderId" to senderId,
                "lastMessageRead" to false  // Reset to unread when sending
            )

            participants.forEach { participantId ->
                if (participantId == senderId) {
                    updates["unreadCount_$participantId"] = 0
                    updates["unreadCount.$participantId"] = 0
                } else {
                    updates["unreadCount_$participantId"] = FieldValue.increment(1)
                    updates["unreadCount.$participantId"] = FieldValue.increment(1)
                }
            }

            sessionDoc.update(updates).await()

            // Create notification for recipient
            val notificationMessage = when {
                message.imageUrl.isNotEmpty() && message.text.isBlank() -> "📷 Photo"
                message.imageUrl.isNotEmpty() -> "📷 ${message.text.take(50)}"
                message.text.length > 50 -> message.text.take(50) + "..."
                else -> message.text
            }
            
            val notification = hashMapOf(
                "userId" to recipientId,
                "type" to "MESSAGE",
                "title" to "New Message from ${message.senderName}",
                "message" to notificationMessage,
                "senderId" to message.senderId,
                "senderName" to message.senderName,
                "senderPhotoUrl" to message.senderPhotoUrl,
                "chatId" to chatId,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )

            android.util.Log.d("ChatRepository", "Creating notification for recipient: $recipientId")
            
            db.collection("notifications").add(notification).await()
            
            android.util.Log.d("ChatRepository", "Notification created successfully")

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "Failed to send message")
        }
    }

    /**
     * Create or get existing 1:1 chat session between two users.
     * Supports reusing historical sessions even if friendship changes later.
     */
    suspend fun getOrCreateChatSession(
        userId1: String,
        userId2: String,
        user1Name: String,
        user2Name: String,
        user1Photo: String = "",
        user2Photo: String = ""
    ): Result<String> {
        return try {
            // First, check if session already exists (allow reuse even if users are no longer mutuals)
            val existing = db.collection("chatSessions")
                .whereArrayContains("participants", userId1)
                .get()
                .await()
                .documents
                .find { doc ->
                    val participants = doc.get("participants") as? List<String>
                    participants?.contains(userId2) == true
                }

            if (existing != null) {
                return Result.Success(existing.id)
            }

            // Create new session
            val newSession = hashMapOf(
                "id" to "",
                "participants" to listOf(userId1, userId2),
                "participantNames" to mapOf(userId1 to user1Name, userId2 to user2Name),
                "participantPhotos" to mapOf(userId1 to user1Photo, userId2 to user2Photo),
                "unreadCount" to mapOf(userId1 to 0, userId2 to 0),
                "unreadCount_$userId1" to 0,
                "unreadCount_$userId2" to 0,
                "lastMessage" to "",
                "lastTimestamp" to System.currentTimeMillis(),
                "createdAt" to System.currentTimeMillis()
            )

            val docRef = db.collection("chatSessions").add(newSession).await()
            docRef.update("id", docRef.id).await()
            Result.Success(docRef.id)
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "Failed to create chat session")
        }
    }

    /**
     * Mark messages as delivered (recipient received but hasn't opened)
     * Called automatically when recipient loads messages
     * FIXED: Removed whereNotEqualTo to avoid composite index requirement
     */
    suspend fun markMessagesAsDelivered(chatId: String, userId: String): Result<Unit> {
        return try {
            android.util.Log.d("ChatRepository", "markAsDelivered START: chatId=$chatId, userId=$userId")
            
            // Query all undelivered messages (single field query - no composite index needed)
            val undeliveredMessages = db.collection("chatSessions")
                .document(chatId)
                .collection("messages")
                .whereEqualTo("delivered", false)
                .get()
                .await()

            android.util.Log.d("ChatRepository", "Found ${undeliveredMessages.size()} undelivered messages total")

            // Filter out user's own messages client-side
            val messagesToUpdate = undeliveredMessages.documents.filter { doc ->
                val senderId = doc.getString("senderId")
                val shouldUpdate = senderId != userId
                android.util.Log.d("ChatRepository", "Message ${doc.id}: senderId=$senderId, currentUser=$userId, shouldUpdate=$shouldUpdate")
                shouldUpdate
            }

            android.util.Log.d("ChatRepository", "Messages to mark as delivered: ${messagesToUpdate.size}")

            if (messagesToUpdate.isNotEmpty()) {
                val batch = db.batch()
                messagesToUpdate.forEach { doc ->
                    android.util.Log.d("ChatRepository", "Adding to batch: ${doc.id}")
                    batch.update(doc.reference, "delivered", true)
                }
                batch.commit().await()
                android.util.Log.d("ChatRepository", "Batch commit SUCCESS - marked ${messagesToUpdate.size} messages as delivered")
            } else {
                android.util.Log.d("ChatRepository", "No messages to update")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "markAsDelivered FAILED: ${e.message}", e)
            Result.Error(e, e.message ?: "Failed to mark messages as delivered")
        }
    }

    /**
     * Mark messages as read in a chat
     * FIXED: Removed whereNotEqualTo to avoid composite index requirement
     */
    suspend fun markMessagesAsRead(chatId: String, userId: String): Result<Unit> {
        return try {
            android.util.Log.d("ChatRepository", "markAsRead START: chatId=$chatId, userId=$userId")
            
            // Query all unread messages (single field query - no composite index needed)
            val unreadMessages = db.collection("chatSessions")
                .document(chatId)
                .collection("messages")
                .whereEqualTo("read", false)
                .get()
                .await()

            android.util.Log.d("ChatRepository", "Found ${unreadMessages.size()} unread messages total")

            // Filter out user's own messages client-side
            val messagesToUpdate = unreadMessages.documents.filter { doc ->
                val senderId = doc.getString("senderId")
                val shouldUpdate = senderId != userId
                android.util.Log.d("ChatRepository", "Message ${doc.id}: senderId=$senderId, currentUser=$userId, shouldUpdate=$shouldUpdate")
                shouldUpdate
            }

            android.util.Log.d("ChatRepository", "Messages to mark as read: ${messagesToUpdate.size}")

            if (messagesToUpdate.isNotEmpty()) {
                val batch = db.batch()
                messagesToUpdate.forEach { doc ->
                    android.util.Log.d("ChatRepository", "Adding to batch: ${doc.id}")
                    batch.update(doc.reference, "read", true)
                }
                batch.commit().await()
                android.util.Log.d("ChatRepository", "Batch commit SUCCESS - marked ${messagesToUpdate.size} messages as read")
                
                // Update chat session to mark last message as read + clear unread count for current user
                db.collection("chatSessions").document(chatId)
                    .update(
                        mapOf(
                            "lastMessageRead" to true,
                            "unreadCount_$userId" to 0,
                            "unreadCount.$userId" to 0
                        )
                    )
                    .await()
            } else {
                android.util.Log.d("ChatRepository", "No messages to update")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "markAsRead FAILED: ${e.message}", e)
            Result.Error(e, e.message ?: "Failed to mark messages as read")
        }
    }

    /**
     * Edit a message (only sender and only while unread by recipient).
     */
    suspend fun editMessage(chatId: String, messageId: String, currentUserId: String, newText: String): Result<Unit> {
        return try {
            val trimmedText = newText.trim()
            if (trimmedText.isBlank()) {
                return Result.Error(Exception("Message cannot be empty"), "Message cannot be empty")
            }

            val messageRef = db.collection("chatSessions")
                .document(chatId)
                .collection("messages")
                .document(messageId)

            val messageDoc = messageRef.get().await()
            if (!messageDoc.exists()) {
                return Result.Error(Exception("Message not found"), "Message not found")
            }

            val senderId = messageDoc.getString("senderId").orEmpty()
            val read = messageDoc.getBoolean("read") ?: false

            if (senderId != currentUserId) {
                return Result.Error(Exception("You can only edit your own messages"), "You can only edit your own messages")
            }

            if (read) {
                return Result.Error(Exception("Message already read and cannot be edited"), "Message already read and cannot be edited")
            }

            val editedAt = System.currentTimeMillis()
            messageRef.update(
                mapOf(
                    "text" to trimmedText,
                    "edited" to true,
                    "editedAt" to editedAt
                )
            ).await()

            val chatSessionRef = db.collection("chatSessions").document(chatId)
            val sessionDoc = chatSessionRef.get().await()
            val lastMessageText = sessionDoc.getString("lastMessage").orEmpty()
            val messageImageUrl = messageDoc.getString("imageUrl").orEmpty()
            val previousPreview = if (messageImageUrl.isNotBlank() && messageDoc.getString("text").orEmpty().isBlank()) "📷 Photo" else messageDoc.getString("text").orEmpty()
            if (lastMessageText == previousPreview) {
                chatSessionRef.update("lastMessage", trimmedText).await()
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "Failed to edit message")
        }
    }

    /**
     * Add reaction to a message
     */
    suspend fun addReaction(chatId: String, messageId: String, userId: String, emoji: String, senderName: String, senderPhotoUrl: String): Result<Unit> {
        return try {
            android.util.Log.d("ChatRepository", "addReaction: $emoji to message $messageId")
            
            // Get message data first to find original sender
            val messageDoc = db.collection("chatSessions")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .get()
                .await()
            
            val originalSenderId = messageDoc.getString("senderId") ?: ""
            val messageText = messageDoc.getString("text") ?: ""
            
            // Update message with reaction
            messageDoc.reference.update("reactions.$userId", emoji).await()
            
            android.util.Log.d("ChatRepository", "addReaction SUCCESS")
            
            // Create notification for original message sender (if different from reactor)
            if (originalSenderId.isNotEmpty() && originalSenderId != userId) {
                val notification = hashMapOf(
                    "userId" to originalSenderId,
                    "type" to "REACTION",
                    "title" to "$senderName reacted to your message",
                    "message" to "$emoji ${messageText.take(30)}${if (messageText.length > 30) "..." else ""}",
                    "senderId" to userId,
                    "senderName" to senderName,
                    "senderPhotoUrl" to senderPhotoUrl,
                    "chatId" to chatId,
                    "messageId" to messageId,
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )
                
                db.collection("notifications").add(notification).await()
                android.util.Log.d("ChatRepository", "Reaction notification created for $originalSenderId")
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "addReaction FAILED: ${e.message}", e)
            Result.Error(e, e.message ?: "Failed to add reaction")
        }
    }

    /**
     * Delete selected messages in a chat.
     */
    suspend fun deleteMessages(chatId: String, messageIds: List<String>, currentUserId: String): Result<Unit> {
        return try {
            if (messageIds.isEmpty()) return Result.Success(Unit)

            val batch = db.batch()
            val messagesCollection = db.collection("chatSessions")
                .document(chatId)
                .collection("messages")

            val now = System.currentTimeMillis()
            val deleteForEveryoneWindowMs = 10 * 60 * 1000L
            var hardDeleteCount = 0

            messageIds.forEach { messageId ->
                val messageRef = messagesCollection.document(messageId)
                val messageDoc = messageRef.get().await()
                val timestamp = messageDoc.getLong("timestamp") ?: 0L
                val ageMs = now - timestamp

                if (ageMs <= deleteForEveryoneWindowMs) {
                    batch.delete(messageRef)
                    hardDeleteCount++
                } else {
                    batch.update(messageRef, "deletedForUserIds", FieldValue.arrayUnion(currentUserId))
                }
            }
            batch.commit().await()

            if (hardDeleteCount > 0) {
                // Update chat summary with latest remaining message (if any)
                val latestMessageSnapshot = messagesCollection
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                val latestDoc = latestMessageSnapshot.documents.firstOrNull()
                if (latestDoc != null) {
                    val latestText = latestDoc.getString("text").orEmpty()
                    val latestImage = latestDoc.getString("imageUrl").orEmpty()
                    val lastMessagePreview = if (latestText.isBlank() && latestImage.isNotBlank()) "📷 Photo" else latestText

                    db.collection("chatSessions").document(chatId)
                        .update(
                            mapOf(
                                "lastMessage" to lastMessagePreview,
                                "lastTimestamp" to (latestDoc.getLong("timestamp") ?: System.currentTimeMillis()),
                                "lastSenderId" to (latestDoc.getString("senderId") ?: "")
                            )
                        ).await()
                } else {
                    db.collection("chatSessions").document(chatId)
                        .update(
                            mapOf(
                                "lastMessage" to "",
                                "lastTimestamp" to System.currentTimeMillis(),
                                "lastSenderId" to ""
                            )
                        ).await()
                }
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "Failed to delete selected messages")
        }
    }

    /**
     * Delete a chat session and all its messages.
     */
    suspend fun deleteChatSession(chatId: String): Result<Unit> {
        return try {
            val messages = db.collection("chatSessions")
                .document(chatId)
                .collection("messages")
                .get()
                .await()

            if (!messages.isEmpty) {
                val batch = db.batch()
                messages.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            db.collection("chatSessions").document(chatId).delete().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "Failed to delete chat")
        }
    }

    /**
     * Report and block a user instantly from chat context.
     */
    suspend fun blockAndReportUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            db.collection("reports")
                .add(
                    mapOf(
                        "reporterId" to currentUserId,
                        "targetUserId" to targetUserId,
                        "type" to "USER_BLOCK_FROM_CHAT",
                        "reason" to "Blocked from messages menu",
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                .await()

            val batch = db.batch()
            val currentUserRef = db.collection("users").document(currentUserId)
            val targetUserRef = db.collection("users").document(targetUserId)

            batch.update(currentUserRef, "blockedUsers", FieldValue.arrayUnion(targetUserId))
            batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))
            batch.update(currentUserRef, "followers", FieldValue.arrayRemove(targetUserId))
            batch.update(targetUserRef, "following", FieldValue.arrayRemove(currentUserId))
            batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))
            batch.commit().await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, e.message ?: "Failed to block and report user")
        }
    }

    /**
     * Get unread message count for a user
     */
    fun getUnreadMessageCount(userId: String): Flow<Int> = callbackFlow {
        val subscription = db.collection("chatSessions")
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                // Count unread messages from the session data
                var count = 0
                snapshot?.documents?.forEach { doc ->
                    val unreadFromDirectField = doc.getIntFromAny("unreadCount_$userId")
                    val unreadFromMap = (doc.get("unreadCount") as? Map<*, *>)
                        ?.get(userId)
                        .toIntOrNullSafe()
                    val sessionUnread = unreadFromDirectField ?: unreadFromMap ?: 0
                    count += sessionUnread
                }
                trySend(count)
            }
        awaitClose { subscription.remove() }
    }
}
