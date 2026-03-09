package com.picflick.app.utils

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.picflick.app.data.UserProfile
import kotlinx.coroutines.tasks.await

/**
 * Debug utility to add FIREBENDER as a dummy friend
 * Run this once to create a test friend for debugging
 */
object FirebenderDummyFriend {
    
    const val FIREBENDER_ID = "firebender_ai_001"
    private const val TAG = "FirebenderDummy"
    
    /**
     * Create FIREBENDER user and send friend request to current user
     */
    suspend fun createFirebenderFriend(currentUserId: String): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            
            // Check if FIREBENDER already exists
            val existingDoc = db.collection("users")
                .document(FIREBENDER_ID)
                .get()
                .await()
            
            if (existingDoc.exists()) {
                Log.d(TAG, "FIREBENDER already exists, updating friendship...")
            } else {
                Log.d(TAG, "Creating new FIREBENDER profile...")
                
                // Create FIREBENDER profile with reliable avatar
                val firebender = UserProfile(
                    uid = FIREBENDER_ID,
                    displayName = "FIREBENDER",
                    email = "firebender@picflick.ai",
                    photoUrl = "https://i.pravatar.cc/150?u=firebender", // Reliable avatar service
                    bio = "I'm an AI coding assistant! Your personal friend for testing. I can help you debug, code, and ship amazing apps! 🤖🔥",
                    followers = listOf(currentUserId), // FIREBENDER follows you
                    following = listOf(currentUserId),
                    isFounder = true,
                    subscriptionTier = com.picflick.app.data.SubscriptionTier.ULTRA,
                    joinedAt = System.currentTimeMillis()
                )
                
                // Save FIREBENDER to Firestore
                db.collection("users")
                    .document(FIREBENDER_ID)
                    .set(firebender)
                    .await()
                
                Log.d(TAG, "✅ FIREBENDER profile created!")
            }
            
            // Add FIREBENDER to your following list
            db.collection("users")
                .document(currentUserId)
                .update("following", FieldValue.arrayUnion(FIREBENDER_ID))
                .await()
            
            // Add you to FIREBENDER's followers
            db.collection("users")
                .document(FIREBENDER_ID)
                .update("followers", FieldValue.arrayUnion(currentUserId))
                .await()
            
            Log.d(TAG, "✅ FIREBENDER friendship established!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create FIREBENDER: ${e.message}", e)
            false
        }
    }
    
    /**
     * Create a sample photo from FIREBENDER
     */
    suspend fun createFirebenderPhoto(): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            
            // Check if photo already exists
            val existingPhoto = db.collection("flicks")
                .document("flick_firebender_001")
                .get()
                .await()
            
            if (existingPhoto.exists()) {
                Log.d(TAG, "FIREBENDER photo already exists")
                return true
            }
            
            // Use reliable image URL from picsum.photos
            val flick = hashMapOf(
                "id" to "flick_firebender_001",
                "userId" to FIREBENDER_ID,
                "imageUrl" to "https://picsum.photos/seed/firebenderai/600/800", // Reliable random image
                "caption" to "Hello from your AI friend! I'm FIREBENDER - your coding assistant! 🤖🔥 Long-press my messages to test reply feature!",
                "privacy" to "public",
                "taggedFriends" to emptyList<String>(),
                "reactions" to hashMapOf<String, String>(),
                "timestamp" to System.currentTimeMillis(),
                "storageUrl" to "https://picsum.photos/seed/firebenderai/600/800"
            )
            
            db.collection("flicks")
                .document("flick_firebender_001")
                .set(flick)
                .await()
            
            // Also create a second photo
            val flick2 = hashMapOf(
                "id" to "flick_firebender_002",
                "userId" to FIREBENDER_ID,
                "imageUrl" to "https://picsum.photos/seed/aiassistant/600/800",
                "caption" to "Testing the new comment and reaction features! Try adding a comment or reaction to this photo! 👇",
                "privacy" to "public",
                "taggedFriends" to emptyList<String>(),
                "reactions" to hashMapOf<String, String>(),
                "timestamp" to System.currentTimeMillis() - 3600000, // 1 hour ago
                "storageUrl" to "https://picsum.photos/seed/aiassistant/600/800"
            )
            
            db.collection("flicks")
                .document("flick_firebender_002")
                .set(flick2)
                .await()
            
            Log.d(TAG, "✅ FIREBENDER's photos added!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create photo: ${e.message}", e)
            false
        }
    }
    
    /**
     * Create a chat message from FIREBENDER
     */
    suspend fun createWelcomeMessage(chatId: String, currentUserName: String): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            
            val message = hashMapOf(
                "id" to db.collection("chatSessions").document(chatId).collection("messages").document().id,
                "chatId" to chatId,
                "senderId" to FIREBENDER_ID,
                "senderName" to "FIREBENDER",
                "senderPhotoUrl" to "https://i.pravatar.cc/150?u=firebender",
                "text" to "Hey $currentUserName! 👋 I'm FIREBENDER, your AI coding assistant! I just helped build this app! Try replying to this message by long-pressing it! 🔥🤖",
                "timestamp" to System.currentTimeMillis(),
                "read" to false,
                "delivered" to true
            )
            
            db.collection("chatSessions")
                .document(chatId)
                .collection("messages")
                .add(message)
                .await()
            
            Log.d(TAG, "✅ Welcome message sent!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send message: ${e.message}", e)
            false
        }
    }
    
    /**
     * Remove FIREBENDER as a friend (cleanup)
     */
    suspend fun removeFirebenderFriend(currentUserId: String): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            
            // Remove FIREBENDER from your following list
            db.collection("users")
                .document(currentUserId)
                .update("following", FieldValue.arrayRemove(FIREBENDER_ID))
                .await()
            
            // Remove you from FIREBENDER's followers
            db.collection("users")
                .document(FIREBENDER_ID)
                .update("followers", FieldValue.arrayRemove(currentUserId))
                .await()
            
            // Remove FIREBENDER from your followers (if applicable)
            db.collection("users")
                .document(currentUserId)
                .update("followers", FieldValue.arrayRemove(FIREBENDER_ID))
                .await()
            
            Log.d(TAG, "✅ FIREBENDER removed as friend!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove FIREBENDER: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if FIREBENDER is already a friend
     */
    suspend fun isFirebenderFriend(currentUserId: String): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            
            val userDoc = db.collection("users")
                .document(currentUserId)
                .get()
                .await()
            
            val following = userDoc.get("following") as? List<String> ?: emptyList()
            following.contains(FIREBENDER_ID)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to check friendship: ${e.message}", e)
            false
        }
    }
}