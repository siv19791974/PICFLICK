package com.picflick.app.repository

import com.app.picflick.data.ChatMessage
import com.app.picflick.data.ChatSession
import com.app.picflick.data.Result
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
            .orderBy("lastTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val sessions = snapshot?.toObjects(ChatSession::class.java) ?: emptyList()
                trySend(sessions)
            }
        awaitClose { subscription.remove() }
    }

    /**
     * Get messages for a specific chat session
     */
    fun getMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val subscription = db.collection("chatSessions")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.toObjects(ChatMessage::class.java) ?: emptyList()
                trySend(messages)
            }
        awaitClose { subscription.remove() }
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
                        "lastMessage" to message.text,
                        "lastTimestamp" to message.timestamp
                    )
                )
                .await()

            // Create notification for recipient
            val notification = hashMapOf(
                "userId" to recipientId,
                "type" to "MESSAGE",
                "title" to "New Message from ${message.senderName}",
                "message" to if (message.text.length > 50) message.text.take(50) + "..." else message.text,
                "senderId" to message.senderId,
                "senderName" to message.senderName,
                "senderPhotoUrl" to message.senderPhotoUrl,
                "chatId" to chatId,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )

            db.collection("notifications").add(notification).await()

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
        user2Name: String
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
     * Mark messages as read in a chat
     */
    suspend fun markMessagesAsRead(chatId: String, userId: String): Result<Unit> {
        return try {
            val unreadMessages = db.collection("chatSessions")
                .document(chatId)
                .collection("messages")
                .whereNotEqualTo("senderId", userId)
                .whereEqualTo("read", false)
                .get()
                .await()

            val batch = db.batch()
            unreadMessages.documents.forEach { doc ->
                batch.update(doc.reference, "read", true)
            }
            batch.commit().await()

            Result.Success(Unit)
        } catch (e: Exception) {
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
