package com.picflick.app.utils

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
    
    /**
     * Create FIREBENDER user and send friend request to current user
     */
    suspend fun createFirebenderFriend(currentUserId: String) {
        val db = FirebaseFirestore.getInstance()
        
        // Create FIREBENDER profile
        val firebender = UserProfile(
            uid = FIREBENDER_ID,
            displayName = "FIREBENDER",
            email = "firebender@picflick.ai",
            photoUrl = "https://firebasestorage.googleapis.com/v0/b/picflick.firebasestorage.app/o/firebender_avatar.png?alt=1", // Will use default
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
        
        println("✅ FIREBENDER created and added as friend!")
    }
    
    /**
     * Create a sample photo from FIREBENDER
     */
    suspend fun createFirebenderPhoto() {
        val db = FirebaseFirestore.getInstance()
        
        val flick = hashMapOf(
            "id" to "flick_firebender_001",
            "userId" to FIREBENDER_ID,
            "imageUrl" to "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800", // AI/cyber themed
            "caption" to "Hello from your AI friend! I'm FIREBENDER - your coding assistant! 🤖🔥",
            "privacy" to "public",
            "taggedFriends" to emptyList<String>(),
            "reactions" to hashMapOf<String, String>(),
            "timestamp" to System.currentTimeMillis()
        )
        
        db.collection("flicks")
            .document("flick_firebender_001")
            .set(flick)
            .await()
        
        println("✅ FIREBENDER's photo added!")
    }
}