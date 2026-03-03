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
     * Get all flicks ordered by timestamp
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
     * Toggle like on a flick
     */
    fun toggleLike(flickId: String, userId: String, isLiked: Boolean, onResult: (Result<Unit>) -> Unit) {
        val update = if (isLiked) {
            FieldValue.arrayRemove(userId)
        } else {
            FieldValue.arrayUnion(userId)
        }

        db.collection("flicks").document(flickId)
            .update("likes", update)
            .addOnSuccessListener {
                onResult(Result.Success(Unit))
            }
            .addOnFailureListener { error ->
                onResult(Result.Error(error, "Failed to update like"))
            }
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
     * Create a new flick
     */
    suspend fun createFlick(flick: Flick): Result<Unit> {
        return try {
            val flickWithId = if (flick.id.isEmpty()) {
                flick.copy(id = db.collection("flicks").document().id)
            } else {
                flick
            }
            
            db.collection("flicks").document(flickWithId.id)
                .set(flickWithId)
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create flick")
        }
    }

    /**
     * Add a comment to a flick
     */
    suspend fun addComment(comment: Comment): Result<Unit> {
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
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to add comment")
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
     * Delete a comment
     */
    suspend fun deleteComment(comment: Comment): Result<Unit> {
        return try {
            db.collection("comments").document(comment.id)
                .delete()
                .await()
            
            // Decrement comment count on the flick
            db.collection("flicks").document(comment.flickId)
                .update("commentCount", FieldValue.increment(-1))
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete comment")
        }
    }
}
