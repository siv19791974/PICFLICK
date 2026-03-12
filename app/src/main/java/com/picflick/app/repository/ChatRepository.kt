package com.picflick.app.repository

import com.picflick.app.data.ChatMessage
import com.picflick.app.data.ChatSession
import com.picflick.app.data.Result
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for chat/messaging operations
 */
class ChatRepository {
    private val db = FirebaseFirestore.getInstance()
    private val flickRepository = FlickRepository.getInstance()

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
                val sessions = snapshot?.toObjects(ChatSession::class.java) ?: emptyList()
                // Sort client-side by lastTimestamp descending
                val sortedSessions = sessions.sortedByDescending { it.lastTimestamp }
                trySend(sortedSessions)
            }
        awaitClose { subscription.remove() }
    }

    /**
     * Get messages for a specific chat session
     */
    fun getMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
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
                val messages = snapshot?.toObjects(ChatMessage::class.java) ?: emptyList()
                android.util.Log.d("ChatRepository", "Messages updated: ${messages.size} messages, docChanges: ${snapshot?.documentChanges?.size ?: 0}")
                // Log status of first few messages
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
     * Send a message and create notification
     */
    suspend fun sendMessage(
        chatId: String,
        message: ChatMessage,
        recipientId: String
    ): Result<Unit> {
        return try {
            val messageWithId = message.copy(
                id = db.collection("chatSessions").document(chatId)
                    .collection("messages").document().id
            )

            // Add message
            db.collection("chatSessions").document(chatId)
                .collection("messages").document(messageWithId.id)
                .set(messageWithId)
                .await()

            // Update session last message
            db.collection("chatSessions").document(chatId)
                .update(
                    mapOf(
                        "lastMessage" to if (message.text.isBlank() && message.imageUrl.isNotEmpty()) "📷 Photo" else message.text,
                        "lastTimestamp" to message.timestamp
                    )
                )
                .await()

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
     * Create or get existing chat session between two users (FRIENDS ONLY)
     * Only allows chat between friends (mutual followers)
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
            // SECURITY CHECK: Verify users are friends (mutual followers)
            val areFriends = flickRepository.areFriends(userId1, userId2)
            if (!areFriends) {
                return Result.Error(
                    Exception("Not friends"), 
                    "You can only message friends. Follow each other to start chatting!"
                )
            }
            
            // Check if session already exists
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
                Result.Success(existing.id)
            } else {
                // Create new session
                val newSession = hashMapOf(
                    "id" to "",
                    "participants" to listOf(userId1, userId2),
                    "participantNames" to mapOf(userId1 to user1Name, userId2 to user2Name),
                    "participantPhotos" to mapOf(userId1 to user1Photo, userId2 to user2Photo),
                    "lastMessage" to "",
                    "lastTimestamp" to System.currentTimeMillis(),
                    "createdAt" to System.currentTimeMillis()
                )

                val docRef = db.collection("chatSessions").add(newSession).await()
                docRef.update("id", docRef.id).await()
                Result.Success(docRef.id)
            }
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
                    // Get unread count from session metadata if available
                    val sessionUnread = doc.getLong("unreadCount_$userId")?.toInt() ?: 0
                    count += sessionUnread
                }
                trySend(count)
            }
        awaitClose { subscription.remove() }
    }
}
