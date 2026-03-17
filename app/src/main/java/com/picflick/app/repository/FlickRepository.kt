package com.picflick.app.repository

import com.picflick.app.data.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import com.picflick.app.Constants

/**
 * Repository class for handling all Firebase Firestore and Storage operations
 */
class FlickRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    // Coroutine scope for repository operations (replaces GlobalScope)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cache for user profile
    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)

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
     * Upload flick image to Firebase Storage
     */
    suspend fun uploadFlickImage(userId: String, imageBytes: ByteArray): Result<String> {
        return try {
            val filename = "photos/${userId}/${UUID.randomUUID()}.jpg"
            val storageRef = storage.reference.child(filename)

            storageRef.putBytes(imageBytes).await()
            val downloadUrl = storageRef.downloadUrl.await()

            Result.Success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.Error(e, "Failed to upload image")
        }
    }

    /**
     * Create a new flick and notify friends
     */
    suspend fun createFlick(flick: Flick, userPhotoUrl: String): Result<Unit> {
        return try {
            // Add to Firestore
            val docRef = db.collection("flicks").add(flick).await()
            val flickId = docRef.id

            // Update with ID
            docRef.update("id", flickId).await()

            // Create notifications for friends
            val updatedFlick = flick.copy(id = flickId)
            createPhotoNotifications(updatedFlick, userPhotoUrl)
            
            // Create notifications for tagged friends
            if (flick.taggedFriends.isNotEmpty()) {
                flick.taggedFriends.forEach { taggedUserId ->
                    createTagNotification(
                        flickId = flickId,
                        photoOwnerId = flick.userId,
                        photoOwnerName = flick.userName,
                        photoOwnerPhotoUrl = userPhotoUrl,
                        taggedUserId = taggedUserId
                    )
                }
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create flick")
        }
    }

    /**
     * Get all flicks from friends + user's own photos with PAGINATION support for infinite scroll
     * @param lastTimestamp - timestamp of last loaded photo for pagination (null for first load)
     * @param pageSize - number of photos to load per page
     */
    suspend fun getFlicksForUserPaginated(
        userId: String,
        lastTimestamp: Long? = null,
        pageSize: Int = 50  // Increased from 20 to load more photos at once
    ): Result<List<Flick>> {
        return try {
            android.util.Log.d("FlickRepository", "Loading photos for user: $userId, lastTimestamp: $lastTimestamp, pageSize: $pageSize")
            
            // Get friends list
            val userDoc = db.collection("users").document(userId).get().await()
            val userProfile = userDoc.toObject(UserProfile::class.java)
            val friends = userProfile?.following ?: emptyList()
            
            android.util.Log.d("FlickRepository", "User has ${friends.size} friends: $friends")

            // Query user's own photos (NO orderBy to avoid composite index requirement)
            val ownFlicksSnapshot = db.collection("flicks")
                .whereEqualTo("userId", userId)
                .limit(pageSize.toLong() * 2)  // Load more to account for merging
                .get()
                .await()
            
            val ownFlicks = ownFlicksSnapshot.toObjects(Flick::class.java)
            android.util.Log.d("FlickRepository", "Loaded ${ownFlicks.size} own photos")

            // Query friends' photos (NO orderBy to avoid composite index requirement)
            // MUTUAL FRIENDS: Show ALL photos regardless of privacy setting
            val friendsFlicks = if (friends.isNotEmpty()) {
                friends.chunked(Constants.Pagination.MAX_FRIENDS_BATCH).flatMap { friendBatch ->
                    android.util.Log.d("FlickRepository", "Querying friend batch: $friendBatch")
                    
                    // Query ALL photos from friends (friends, public, AND private)
                    // Mutual friends should see everything
                    val batchFlicks = mutableListOf<Flick>()
                    
                    // Query "friends" privacy photos
                    try {
                        val friendsPrivacySnapshot = db.collection("flicks")
                            .whereIn("userId", friendBatch)
                            .whereEqualTo("privacy", "friends")
                            .limit(pageSize.toLong() * 2)
                            .get()
                            .await()
                        
                        val friendsPrivacyFlicks = friendsPrivacySnapshot.toObjects(Flick::class.java)
                        batchFlicks.addAll(friendsPrivacyFlicks)
                        android.util.Log.d("FlickRepository", "Loaded ${friendsPrivacyFlicks.size} friends-privacy photos")
                    } catch (e: Exception) {
                        android.util.Log.e("FlickRepository", "Error loading friends-privacy: ${e.message}")
                    }
                    
                    // Query "public" privacy photos
                    try {
                        val publicPrivacySnapshot = db.collection("flicks")
                            .whereIn("userId", friendBatch)
                            .whereEqualTo("privacy", "public")
                            .limit(pageSize.toLong() * 2)
                            .get()
                            .await()
                        
                        val publicPrivacyFlicks = publicPrivacySnapshot.toObjects(Flick::class.java)
                        batchFlicks.addAll(publicPrivacyFlicks)
                        android.util.Log.d("FlickRepository", "Loaded ${publicPrivacyFlicks.size} public-privacy photos")
                    } catch (e: Exception) {
                        android.util.Log.e("FlickRepository", "Error loading public-privacy: ${e.message}")
                    }
                    
                    // Query "private" privacy photos (MUTUAL FRIENDS SEE ALL!)
                    try {
                        val privatePrivacySnapshot = db.collection("flicks")
                            .whereIn("userId", friendBatch)
                            .whereEqualTo("privacy", "private")
                            .limit(pageSize.toLong() * 2)
                            .get()
                            .await()
                        
                        val privatePrivacyFlicks = privatePrivacySnapshot.toObjects(Flick::class.java)
                        batchFlicks.addAll(privatePrivacyFlicks)
                        android.util.Log.d("FlickRepository", "Loaded ${privatePrivacyFlicks.size} private-privacy photos (mutual friends)")
                    } catch (e: Exception) {
                        android.util.Log.e("FlickRepository", "Error loading private-privacy: ${e.message}")
                    }
                    
                    // Query photos WITHOUT privacy field (treat as "friends" - default behavior)
                    // This handles legacy photos uploaded before privacy feature was added
                    try {
                        val noPrivacySnapshot = db.collection("flicks")
                            .whereIn("userId", friendBatch)
                            // Firestore doesn't have "field doesn't exist" query
                            // So we get all and filter client-side for missing privacy
                            .limit(pageSize.toLong() * 3)
                            .get()
                            .await()
                        
                        val noPrivacyFlicks = noPrivacySnapshot.documents
                            .filter { doc -> 
                                // Only include docs where privacy is missing, null, or empty
                                val privacy = doc.getString("privacy")
                                privacy == null || privacy.isEmpty()
                            }
                            .mapNotNull { it.toObject(Flick::class.java) }
                        
                        batchFlicks.addAll(noPrivacyFlicks)
                        android.util.Log.d("FlickRepository", "Loaded ${noPrivacyFlicks.size} photos without privacy field (treated as friends)")
                    } catch (e: Exception) {
                        android.util.Log.e("FlickRepository", "Error loading no-privacy photos: ${e.message}")
                    }
                    
                    batchFlicks
                }
            } else {
                android.util.Log.d("FlickRepository", "No friends to query")
                emptyList()
            }

            // Merge, remove duplicates, and sort by timestamp DESC (client-side sorting)
            var allFlicks = (ownFlicks + friendsFlicks)
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp }
            
            android.util.Log.d("FlickRepository", "After deduplication: ${allFlicks.size} photos")
            
            // Manual pagination - filter out photos older than lastTimestamp
            if (lastTimestamp != null) {
                allFlicks = allFlicks.filter { it.timestamp < lastTimestamp }
                android.util.Log.d("FlickRepository", "After pagination filter: ${allFlicks.size} photos")
            }
            
            // Take only the requested page size
            allFlicks = allFlicks.take(pageSize)

            android.util.Log.d("FlickRepository", "Returning ${allFlicks.size} photos for page (requested: $pageSize)")
            Result.Success(allFlicks)
        } catch (e: Exception) {
            android.util.Log.e("FlickRepository", "Failed to load paginated photos", e)
            Result.Error(e, "Failed to load photos: ${e.message}")
        }
    }

    /**
     * Get all flicks from friends + user's own photos (legacy - loads first page only)
     */
    fun getFlicksForUser(userId: String, onResult: (Result<List<Flick>>) -> Unit) {
        repositoryScope.launch {
            val result = getFlicksForUserPaginated(userId, null, Constants.Pagination.FLICKS_PER_PAGE)
            onResult(result)
        }
    }

    /**
     * Get explore flicks (trending/popular for discovery)
     * Gets photos with most reactions from last 7 days
     */
    fun getExploreFlicks(onResult: (Result<List<Flick>>) -> Unit) {
        repositoryScope.launch {
            try {
                // Get photos from last 7 days (query without orderBy to avoid composite index)
                val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis

                val flicksSnapshot = db.collection("flicks")
                    .whereGreaterThan("timestamp", weekAgo)
                    .limit(Constants.Pagination.EXPLORE_FLICKS_LIMIT.toLong())
                    .get()
                    .await()

                val flicks = flicksSnapshot.toObjects(Flick::class.java)

                // Sort by timestamp first, then by reaction count (trending algorithm)
                val trendingFlicks = flicks
                    .sortedByDescending { it.timestamp } // Sort in memory
                    .sortedByDescending { it.getTotalReactions() + (it.commentCount * 2) }
                    .take(Constants.Pagination.FLICKS_PER_PAGE)

                onResult(Result.Success(trendingFlicks))
            } catch (e: Exception) {
                android.util.Log.e("FlickRepository", "Failed to load explore photos", e)
                onResult(Result.Error(e, "Failed to load explore photos. Check your connection and try again."))
            }
        }
    }

    /**
     * Get flicks - DEPRECATED: Use getFlicksForUser() for privacy
     */
    fun getFlicks(onResult: (Result<List<Flick>>) -> Unit) {
        db.collection(Constants.FirebaseCollections.FLICKS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(Constants.Pagination.FLICKS_PER_PAGE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(Exception(error.message), "Failed to load photos"))
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
        db.collection(Constants.FirebaseCollections.FLICKS)
            .whereEqualTo("userId", userId)
            .limit(Constants.Pagination.FLICKS_PER_PAGE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(Exception(error.message), "Failed to load user photos"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val flicks = snapshot.toObjects(Flick::class.java)
                        .sortedByDescending { it.timestamp } // Sort in memory
                    onResult(Result.Success(flicks))
                }
            }
    }

    /**
     * Toggle like on a flick
     */
    fun toggleLike(flickId: String, userId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { doc ->
                val flick = doc.toObject(Flick::class.java)
                if (flick == null) {
                    onResult(Result.Error(Exception("Photo not found"), "Photo not found"))
                    return@addOnSuccessListener
                }

                val currentLikes = flick.reactions.filter { it.value == ReactionType.LIKE.name }.keys.toMutableSet()
                val hasLiked = currentLikes.contains(userId)

                if (hasLiked) {
                    // Remove like
                    db.collection("flicks").document(flickId)
                        .update("reactions.${userId}", FieldValue.delete())
                        .addOnSuccessListener { onResult(Result.Success(Unit)) }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to unlike")) }
                } else {
                    // Add like
                    db.collection("flicks").document(flickId)
                        .update("reactions.${userId}", ReactionType.LIKE.name)
                        .addOnSuccessListener {
                            // Create notification for photo owner
                            if (flick.userId != userId) {
                                createReactionNotification(
                                    flickId = flickId,
                                    ownerId = flick.userId,
                                    reactorId = userId,
                                    reactorName = "Someone", // Get actual name from user profile
                                    reactorPhotoUrl = "",
                                    reactionType = ReactionType.LIKE
                                )
                            }
                            onResult(Result.Success(Unit))
                        }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to like")) }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get photo")) }
    }

    /**
     * Toggle reaction on a flick
     */
    fun toggleReaction(
        flickId: String,
        userId: String,
        userName: String,
        userPhotoUrl: String,
        reactionType: ReactionType?,
        onResult: (Result<Unit>) -> Unit
    ) {
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { doc ->
                val flick = doc.toObject(Flick::class.java)
                if (flick == null) {
                    onResult(Result.Error(Exception("Photo not found"), "Photo not found"))
                    return@addOnSuccessListener
                }

                val currentReaction = flick.reactions[userId]

                if (currentReaction == reactionType?.name) {
                    // Remove reaction (same reaction clicked)
                    db.collection("flicks").document(flickId)
                        .update("reactions.${userId}", FieldValue.delete())
                        .addOnSuccessListener { onResult(Result.Success(Unit)) }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to remove reaction")) }
                } else {
                    // Add/update reaction
                    val updateValue = reactionType?.name ?: FieldValue.delete()
                    db.collection("flicks").document(flickId)
                        .update("reactions.${userId}", updateValue)
                        .addOnSuccessListener {
                            // Create notification for photo owner
                            if (reactionType != null && flick.userId != userId) {
                                createReactionNotification(
                                    flickId = flickId,
                                    ownerId = flick.userId,
                                    reactorId = userId,
                                    reactorName = userName,
                                    reactorPhotoUrl = userPhotoUrl,
                                    reactionType = reactionType
                                )
                            }
                            onResult(Result.Success(Unit))
                        }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to add reaction")) }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get photo")) }
    }

    /**
     * Delete a flick
     */
    fun deleteFlick(flickId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("flicks").document(flickId).delete()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to delete photo")) }
    }

    /**
     * Get a single flick by ID
     * Used for opening specific photos from push notifications
     */
    fun getFlickById(flickId: String, onResult: (Result<Flick>) -> Unit) {
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { doc ->
                val flick = doc.toObject(Flick::class.java)
                if (flick != null) {
                    // Ensure the ID is set from the document reference
                    val flickWithId = flick.copy(id = doc.id)
                    android.util.Log.d("FlickRepository", "Loaded flick: id=${flickWithId.id}, imageUrl=${flickWithId.imageUrl.take(50)}...")
                    onResult(Result.Success(flickWithId))
                } else {
                    android.util.Log.e("FlickRepository", "Photo not found: $flickId")
                    onResult(Result.Error(Exception("Photo not found"), "Photo not found"))
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FlickRepository", "Failed to load photo: ${e.message}")
                onResult(Result.Error(e, "Failed to load photo"))
            }
    }

    /**
     * Resolve a flick by image URL (used by chat photo viewer to recover real flick ID for comments).
     */
    fun getFlickByImageUrl(
        imageUrl: String,
        ownerUserId: String? = null,
        onResult: (Result<Flick>) -> Unit
    ) {
        fun resolveByUrl(url: String, fallbackUrl: String? = null) {
            var query: com.google.firebase.firestore.Query = db.collection("flicks")
                .whereEqualTo("imageUrl", url)

            if (!ownerUserId.isNullOrBlank()) {
                query = query.whereEqualTo("userId", ownerUserId)
            }

            query
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    val doc = snapshot.documents.firstOrNull()
                    val flick = doc?.toObject(Flick::class.java)
                    if (doc != null && flick != null) {
                        onResult(Result.Success(flick.copy(id = doc.id)))
                    } else if (!fallbackUrl.isNullOrBlank() && fallbackUrl != url) {
                        resolveByUrl(fallbackUrl)
                    } else {
                        onResult(Result.Error(Exception("Photo not found"), "Photo not found"))
                    }
                }
                .addOnFailureListener { e ->
                    onResult(Result.Error(e, "Failed to load photo"))
                }
        }

        val baseUrl = imageUrl.substringBefore("?")
        resolveByUrl(imageUrl, baseUrl)
    }

    /**
     * Get user profile
     */
    fun getUserProfile(userId: String, onResult: (Result<UserProfile>) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val profile = doc.toObject(UserProfile::class.java)
                if (profile != null) {
                    _currentUserProfile.value = profile
                    onResult(Result.Success(profile))
                } else {
                    onResult(Result.Error(Exception("Profile not found"), "Profile not found"))
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to load profile")) }
    }

    /**
     * Listen to user profile realtime updates
     */
    fun listenToUserProfile(userId: String, onResult: (Result<UserProfile>) -> Unit): ListenerRegistration {
        onResult(Result.Loading)
        return db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> {
                        onResult(Result.Error(error, "Failed to listen to profile updates"))
                    }
                    snapshot == null || !snapshot.exists() -> {
                        onResult(Result.Error(Exception("Profile not found"), "Profile not found"))
                    }
                    else -> {
                        val profile = snapshot.toObject(UserProfile::class.java)
                        if (profile != null) {
                            _currentUserProfile.value = profile
                            onResult(Result.Success(profile))
                        } else {
                            onResult(Result.Error(Exception("Profile parsing failed"), "Failed to parse profile"))
                        }
                    }
                }
            }
    }

    /**
     * Save user profile
     */
    fun saveUserProfile(userId: String, profile: UserProfile, onResult: (Result<Unit>) -> Unit) {
        val normalizedProfile = profile.copy(
            displayNameLower = profile.displayName.trim().lowercase(Locale.getDefault())
        )
        db.collection("users").document(userId).set(normalizedProfile)
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to save profile")) }
    }

    /**
     * Check if two users are friends (mutual followers)
     */
    suspend fun areFriends(userId1: String, userId2: String): Boolean {
        return try {
            val user1Doc = db.collection("users").document(userId1).get().await()
            val user2Doc = db.collection("users").document(userId2).get().await()
            
            val user1Profile = user1Doc.toObject(UserProfile::class.java)
            val user2Profile = user2Doc.toObject(UserProfile::class.java)
            
            val user1Following = user1Profile?.following ?: emptyList()
            val user2Following = user2Profile?.following ?: emptyList()
            
            // Mutual follow = friends
            userId2 in user1Following && userId1 in user2Following
        } catch (e: Exception) {
            false // Return false on error to be safe
        }
    }
    fun getAllUsers(onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection(Constants.FirebaseCollections.USERS)
            .limit(Constants.Pagination.USERS_PER_PAGE.toLong())
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.toObjects(UserProfile::class.java)
                onResult(Result.Success(users))
            }
            .addOnFailureListener { e ->
                onResult(Result.Error(e, "Failed to get users"))
            }
    }

    /**
     * Find users by phone numbers (for contact sync)
     * Queries both full number and last 10 digits for better matching
     */
    fun findUsersByPhoneNumbers(phoneNumbers: List<String>, onResult: (Result<List<UserProfile>>) -> Unit) {
        if (phoneNumbers.isEmpty()) {
            onResult(Result.Success(emptyList()))
            return
        }

        // Remove duplicates and get last 10 digits for each
        val uniqueNumbers = phoneNumbers.distinct()
        val last10Digits = uniqueNumbers.map { it.takeLast(10) }.distinct()
        
        val foundUsers = mutableSetOf<UserProfile>() // Use set to avoid duplicates
        var completedQueries = 0
        val totalQueries = 2 // We'll do 2 queries: full numbers and last 10 digits

        // Query 1: Match full phone numbers
        val fullNumberBatches = uniqueNumbers.chunked(Constants.Pagination.MAX_FRIENDS_BATCH)
        var fullNumberCompleted = 0
        
        if (fullNumberBatches.isEmpty()) {
            completedQueries++
        } else {
            fullNumberBatches.forEach { batch ->
                db.collection("users")
                    .whereIn("phoneNumber", batch)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        foundUsers.addAll(snapshot.toObjects(UserProfile::class.java))
                        fullNumberCompleted++
                        if (fullNumberCompleted == fullNumberBatches.size) {
                            completedQueries++
                            if (completedQueries == totalQueries) {
                                onResult(Result.Success(foundUsers.toList()))
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        fullNumberCompleted++
                        if (fullNumberCompleted == fullNumberBatches.size) {
                            completedQueries++
                            if (completedQueries == totalQueries) {
                                onResult(Result.Success(foundUsers.toList()))
                            }
                        }
                    }
            }
        }

        // Query 2: Match last 10 digits (handles country code differences)
        val last10Batches = last10Digits.chunked(Constants.Pagination.MAX_FRIENDS_BATCH)
        var last10Completed = 0
        
        if (last10Batches.isEmpty()) {
            completedQueries++
            if (completedQueries == totalQueries) {
                onResult(Result.Success(foundUsers.toList()))
            }
        } else {
            last10Batches.forEach { batch ->
                db.collection("users")
                    .whereIn("phoneNumber", batch)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        foundUsers.addAll(snapshot.toObjects(UserProfile::class.java))
                        last10Completed++
                        if (last10Completed == last10Batches.size) {
                            completedQueries++
                            if (completedQueries == totalQueries) {
                                onResult(Result.Success(foundUsers.toList()))
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        last10Completed++
                        if (last10Completed == last10Batches.size) {
                            completedQueries++
                            if (completedQueries == totalQueries) {
                                onResult(Result.Success(foundUsers.toList()))
                            }
                        }
                    }
            }
        }
    }

    /**
     * Search users by name or email
     */
    fun searchUsers(query: String, currentUserId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        val searchRaw = query.trim()
        val searchLower = searchRaw.lowercase(Locale.getDefault())
        if (searchLower.isBlank()) {
            onResult(Result.Success(emptyList()))
            return
        }

        val resultMap = linkedMapOf<String, UserProfile>()

        fun addFiltered(users: List<UserProfile>) {
            users.forEach { user ->
                if (
                    user.uid != currentUserId &&
                    user.displayName.lowercase(Locale.getDefault()).startsWith(searchLower)
                ) {
                    resultMap[user.uid] = user
                }
            }
        }

        val fallbackPrefixes = listOf(
            searchRaw,
            searchRaw.lowercase(Locale.getDefault()),
            searchRaw.uppercase(Locale.getDefault()),
            searchRaw.replaceFirstChar { ch -> ch.titlecase(Locale.getDefault()) }
        ).distinct()

        var pendingQueries = 1 + fallbackPrefixes.size

        fun finishIfDone() {
            pendingQueries--
            if (pendingQueries == 0) {
                onResult(
                    Result.Success(
                        resultMap.values
                            .take(Constants.Pagination.SUGGESTED_USERS_LIMIT)
                    )
                )
            }
        }

        db.collection(Constants.FirebaseCollections.USERS)
            .orderBy("displayNameLower")
            .startAt(searchLower)
            .endAt(searchLower + "\uf8ff")
            .limit(Constants.Pagination.SUGGESTED_USERS_LIMIT.toLong())
            .get()
            .addOnSuccessListener { snapshot ->
                addFiltered(snapshot.toObjects(UserProfile::class.java))
                finishIfDone()
            }
            .addOnFailureListener {
                finishIfDone()
            }

        fallbackPrefixes.forEach { prefix ->
            db.collection(Constants.FirebaseCollections.USERS)
                .orderBy("displayName")
                .startAt(prefix)
                .endAt(prefix + "\uf8ff")
                .limit(Constants.Pagination.SUGGESTED_USERS_LIMIT.toLong())
                .get()
                .addOnSuccessListener { snapshot ->
                    addFiltered(snapshot.toObjects(UserProfile::class.java))
                    finishIfDone()
                }
                .addOnFailureListener {
                    finishIfDone()
                }
        }
    }

    /**
     * Follow a user - suspend version for ViewModel with achievement check
     */
    suspend fun followUser(currentUserId: String, targetUserId: String, userName: String? = null): Result<Unit> {
        return try {
            val batch = db.batch()

            // Make friendship mutual on both profiles
            val currentUserRef = db.collection("users").document(currentUserId)
            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(currentUserRef, "following", FieldValue.arrayUnion(targetUserId))
            batch.update(currentUserRef, "followers", FieldValue.arrayUnion(targetUserId))
            batch.update(targetUserRef, "following", FieldValue.arrayUnion(currentUserId))
            batch.update(targetUserRef, "followers", FieldValue.arrayUnion(currentUserId))

            batch.commit().await()

            // Create follow notification for target user
            if (userName != null) {
                createFollowNotification(
                    followerId = currentUserId,
                    followerName = userName,
                    targetUserId = targetUserId
                )
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to follow user")
        }
    }
    
    /**
     * Unfollow a user - suspend version for ViewModel
     */
    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            val batch = db.batch()

            // Remove friendship mutually on both profiles
            val currentUserRef = db.collection("users").document(currentUserId)
            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))
            batch.update(currentUserRef, "followers", FieldValue.arrayRemove(targetUserId))
            batch.update(targetUserRef, "following", FieldValue.arrayRemove(currentUserId))
            batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))

            batch.commit().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to unfollow user")
        }
    }

    /**
     * Get suggested users (not followed yet)
     */
    fun getSuggestedUsers(userId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        // First get current user's following list
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val userProfile = userDoc.toObject(UserProfile::class.java)
                val following = userProfile?.following ?: emptyList()

                // Get users not in following list
                db.collection(Constants.FirebaseCollections.USERS)
                    .limit(Constants.Pagination.SUGGESTED_USERS_LIMIT.toLong())
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val allUsers = snapshot.toObjects(UserProfile::class.java)
                        val suggestions = allUsers
                            .filter { it.uid != userId && it.uid !in following }
                            .take(Constants.Pagination.SUGGESTED_USERS_LIMIT / 2)
                        onResult(Result.Success(suggestions))
                    }
                    .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get suggestions")) }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get user profile")) }
    }

    /**
     * Get ALL users (not filtered - for Discover tab)
     */
    fun getAllUsers(currentUserId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection(Constants.FirebaseCollections.USERS)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val allUsers = snapshot.toObjects(UserProfile::class.java)
                // Only filter out the current user, show everyone else including followed
                val users = allUsers.filter { it.uid != currentUserId }
                onResult(Result.Success(users))
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get users")) }
    }

    /**
     * Update user's default privacy setting
     */
    fun updateDefaultPrivacy(userId: String, privacy: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("users").document(userId)
            .update("defaultPrivacy", privacy)
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to update privacy setting")) }
    }

    /**
     * Follow a user
     */
    fun followUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        val batch = db.batch()

        // Make friendship mutual on both profiles
        val currentUserRef = db.collection("users").document(currentUserId)
        val targetUserRef = db.collection("users").document(targetUserId)
        batch.update(currentUserRef, "following", FieldValue.arrayUnion(targetUserId))
        batch.update(currentUserRef, "followers", FieldValue.arrayUnion(targetUserId))
        batch.update(targetUserRef, "following", FieldValue.arrayUnion(currentUserId))
        batch.update(targetUserRef, "followers", FieldValue.arrayUnion(currentUserId))

        batch.commit()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to follow user")) }
    }

    /**
     * Unfollow a user
     */
    fun unfollowUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        val batch = db.batch()

        // Remove friendship mutually on both profiles
        val currentUserRef = db.collection("users").document(currentUserId)
        val targetUserRef = db.collection("users").document(targetUserId)
        batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))
        batch.update(currentUserRef, "followers", FieldValue.arrayRemove(targetUserId))
        batch.update(targetUserRef, "following", FieldValue.arrayRemove(currentUserId))
        batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))

        batch.commit()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to unfollow user")) }
    }

    /**
     * Block a user - they can't see your content or interact with you
     */
    fun blockUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        val batch = db.batch()

        val currentUserRef = db.collection("users").document(currentUserId)
        val targetUserRef = db.collection("users").document(targetUserId)

        // Add to blocked users list
        batch.update(currentUserRef, "blockedUsers", FieldValue.arrayUnion(targetUserId))

        // Unfollow each other (can't follow a blocked user)
        batch.update(currentUserRef, "following", FieldValue.arrayRemove(targetUserId))
        batch.update(currentUserRef, "followers", FieldValue.arrayRemove(targetUserId))
        batch.update(targetUserRef, "following", FieldValue.arrayRemove(currentUserId))
        batch.update(targetUserRef, "followers", FieldValue.arrayRemove(currentUserId))

        batch.commit()
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to block user")) }
    }

    /**
     * Unblock a user
     */
    fun unblockUser(currentUserId: String, targetUserId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("users").document(currentUserId)
            .update("blockedUsers", FieldValue.arrayRemove(targetUserId))
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to unblock user")) }
    }

    // ==================== FRIEND REQUEST SYSTEM ====================

    /**
     * Send a follow request to a user (requires approval)
     */
    suspend fun sendFollowRequest(
        currentUserId: String,
        targetUserId: String,
        currentUserName: String,
        currentUserPhotoUrl: String
    ): Result<Unit> {
        android.util.Log.d("FriendRequest", "Starting sendFollowRequest: $currentUserId -> $targetUserId")
        return try {
            val batch = db.batch()

            // Add to current user's sent requests
            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "sentFollowRequests", FieldValue.arrayUnion(targetUserId))
            android.util.Log.d("FriendRequest", "Added to batch: update sentFollowRequests for $currentUserId")

            // Add to target user's pending requests
            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(targetUserRef, "pendingFollowRequests", FieldValue.arrayUnion(currentUserId))
            android.util.Log.d("FriendRequest", "Added to batch: update pendingFollowRequests for $targetUserId")

            android.util.Log.d("FriendRequest", "Committing batch...")
            batch.commit().await()
            android.util.Log.d("FriendRequest", "Batch committed successfully!")

            // Create friend request notification
            android.util.Log.d("FriendRequest", "Creating notification...")
            createFriendRequestNotification(
                requesterId = currentUserId,
                requesterName = currentUserName,
                requesterPhotoUrl = currentUserPhotoUrl,
                targetUserId = targetUserId
            )
            android.util.Log.d("FriendRequest", "Notification created!")

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FriendRequest", "Failed to send follow request: ${e.message}", e)
            Result.Error(e, "Failed to send follow request")
        }
    }

    /**
     * Accept a follow request
     */
    suspend fun acceptFollowRequest(
        currentUserId: String,
        requesterId: String,
        requesterName: String
    ): Result<Unit> {
        return try {
            val batch = db.batch()

            // Remove request residue both ways
            val currentUserRef = db.collection("users").document(currentUserId)
            val requesterRef = db.collection("users").document(requesterId)
            batch.update(currentUserRef, "pendingFollowRequests", FieldValue.arrayRemove(requesterId))
            batch.update(currentUserRef, "sentFollowRequests", FieldValue.arrayRemove(requesterId))
            batch.update(requesterRef, "pendingFollowRequests", FieldValue.arrayRemove(currentUserId))
            batch.update(requesterRef, "sentFollowRequests", FieldValue.arrayRemove(currentUserId))

            // Make friendship mutual on both profiles
            batch.update(currentUserRef, "followers", FieldValue.arrayUnion(requesterId))
            batch.update(currentUserRef, "following", FieldValue.arrayUnion(requesterId))
            batch.update(requesterRef, "followers", FieldValue.arrayUnion(currentUserId))
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
     * Reject/Cancel a follow request
     */
    suspend fun rejectFollowRequest(
        currentUserId: String,
        requesterId: String
    ): Result<Unit> {
        return try {
            val batch = db.batch()

            // Remove from pending requests
            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "pendingFollowRequests", FieldValue.arrayRemove(requesterId))

            // Remove from requester's sent requests
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
    suspend fun declineFollowRequest(
        currentUserId: String,
        requesterId: String
    ): Result<Unit> = rejectFollowRequest(currentUserId, requesterId)

    /**
     * Cancel a sent follow request
     */
    suspend fun cancelFollowRequest(
        currentUserId: String,
        targetUserId: String
    ): Result<Unit> {
        return try {
            android.util.Log.d("FriendRequest", "CANCEL: $currentUserId -> $targetUserId")
            
            val batch = db.batch()

            // Remove from sent requests
            val currentUserRef = db.collection("users").document(currentUserId)
            batch.update(currentUserRef, "sentFollowRequests", FieldValue.arrayRemove(targetUserId))
            android.util.Log.d("FriendRequest", "Removing $targetUserId from $currentUserId.sentFollowRequests")

            // Remove from target's pending requests
            val targetUserRef = db.collection("users").document(targetUserId)
            batch.update(targetUserRef, "pendingFollowRequests", FieldValue.arrayRemove(currentUserId))
            android.util.Log.d("FriendRequest", "Removing $currentUserId from $targetUserId.pendingFollowRequests")

            batch.commit().await()
            android.util.Log.d("FriendRequest", "CANCEL SUCCESS: $currentUserId -> $targetUserId")
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FriendRequest", "CANCEL FAILED: ${e.message}")
            Result.Error(e, "Failed to cancel follow request")
        }
    }

    /**
     * Get pending follow requests for a user
     */
    fun getPendingFollowRequests(
        userId: String,
        onResult: (Result<List<UserProfile>>) -> Unit
    ) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val userProfile = doc.toObject(UserProfile::class.java)
                val pendingIds = userProfile?.pendingFollowRequests ?: emptyList()

                if (pendingIds.isEmpty()) {
                    onResult(Result.Success(emptyList()))
                    return@addOnSuccessListener
                }

                // Get profiles of users who sent requests
                val profiles = mutableListOf<UserProfile>()
                var completed = 0

                pendingIds.forEach { id ->
                    db.collection("users").document(id).get()
                        .addOnSuccessListener { userDoc ->
                            val profile = userDoc.toObject(UserProfile::class.java)
                            if (profile != null) {
                                profiles.add(profile)
                            }
                            completed++
                            if (completed == pendingIds.size) {
                                onResult(Result.Success(profiles))
                            }
                        }
                        .addOnFailureListener { _ ->
                            completed++
                            if (completed == pendingIds.size) {
                                onResult(Result.Success(profiles))
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                onResult(Result.Error(e, "Failed to load pending requests"))
            }
    }

    /**
     * Check if there's a pending follow request between two users
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
                        onResult(Result.Error(e, "Failed to check follow status"))
                    }
            }
            .addOnFailureListener { e ->
                onResult(Result.Error(e, "Failed to check follow status"))
            }
    }

    /**
     * Create friend request notification
     */
    private fun createFriendRequestNotification(
        requesterId: String,
        requesterName: String,
        requesterPhotoUrl: String,
        targetUserId: String
    ) {
        android.util.Log.d("NotificationDebug", "Creating FRIEND_REQUEST notification: $requesterId -> $targetUserId")
        
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
            .addOnSuccessListener { docRef ->
                android.util.Log.d("NotificationDebug", "FRIEND_REQUEST notification created successfully: ${docRef.id}")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationDebug", "FAILED to create FRIEND_REQUEST notification: ${e.message}")
            }
    }

    /**
     * Create follow accepted notification
     */
    private fun createFollowAcceptedNotification(
        accepterId: String,
        accepterName: String,
        requesterId: String
    ) {
        android.util.Log.d("NotificationDebug", "Creating FOLLOW_ACCEPTED notification: $accepterName accepted $requesterId")
        
        val notification = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to requesterId,
            "senderId" to accepterId,
            "senderName" to accepterName,
            "type" to "FOLLOW_ACCEPTED",
            "title" to "$accepterName accepted your request",
            "message" to "You're now connected",
            "isRead" to false,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification)
            .addOnSuccessListener { docRef ->
                android.util.Log.d("NotificationDebug", "FOLLOW_ACCEPTED notification created: ${docRef.id}")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationDebug", "FAILED to create FOLLOW_ACCEPTED notification: ${e.message}")
            }
    }

    // ==================== END FRIEND REQUEST SYSTEM ====================

    /**
     * Get following users
     */
    fun getFollowingUsers(userId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val userProfile = doc.toObject(UserProfile::class.java)
                val followingIds = userProfile?.following ?: emptyList()

                if (followingIds.isEmpty()) {
                    onResult(Result.Success(emptyList()))
                    return@addOnSuccessListener
                }

                // Get following user profiles
                val profiles = mutableListOf<UserProfile>()
                var completed = 0

                followingIds.forEach { id ->
                    db.collection("users").document(id).get()
                        .addOnSuccessListener { userDoc ->
                            val profile = userDoc.toObject(UserProfile::class.java)
                            if (profile != null) {
                                profiles.add(profile)
                            }
                            completed++
                            if (completed == followingIds.size) {
                                onResult(Result.Success(profiles))
                            }
                        }
                        .addOnFailureListener { e ->
                            completed++
                            if (completed == followingIds.size) {
                                onResult(Result.Success(profiles))
                            }
                        }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get following")) }
    }

    /**
     * Get followers
     */
    fun getFollowers(userId: String, onResult: (Result<List<UserProfile>>) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val userProfile = doc.toObject(UserProfile::class.java)
                val followerIds = userProfile?.followers ?: emptyList()

                if (followerIds.isEmpty()) {
                    onResult(Result.Success(emptyList()))
                    return@addOnSuccessListener
                }

                // Get follower profiles
                val profiles = mutableListOf<UserProfile>()
                var completed = 0

                followerIds.forEach { id ->
                    db.collection("users").document(id).get()
                        .addOnSuccessListener { userDoc ->
                            val profile = userDoc.toObject(UserProfile::class.java)
                            if (profile != null) {
                                profiles.add(profile)
                            }
                            completed++
                            if (completed == followerIds.size) {
                                onResult(Result.Success(profiles))
                            }
                        }
                        .addOnFailureListener { e ->
                            completed++
                            if (completed == followerIds.size) {
                                onResult(Result.Success(profiles))
                            }
                        }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get followers")) }
    }

    /**
     * Get daily upload count for a user
     */
    fun getDailyUploadCount(userId: String, onResult: (Result<Int>) -> Unit) {
        repositoryScope.launch {
            try {
                // Get start of today
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = calendar.timeInMillis

                // Count flicks uploaded today
                val snapshot = db.collection("flicks")
                    .whereEqualTo("userId", userId)
                    .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                    .get()
                    .await()

                onResult(Result.Success(snapshot.size()))
            } catch (e: Exception) {
                onResult(Result.Error(e, "Failed to get upload count"))
            }
        }
    }

    /**
     * Create notification for new photo
     */
    private fun createPhotoNotifications(flick: Flick, userPhotoUrl: String) {
        repositoryScope.launch {
            try {
                android.util.Log.d("NotificationDebug", "Creating PHOTO_ADDED notifications for flick: ${flick.id}")
                
                // Get user's followers
                val userDoc = db.collection("users").document(flick.userId).get().await()
                val userProfile = userDoc.toObject(UserProfile::class.java)
                val followers = userProfile?.followers ?: emptyList()
                
                android.util.Log.d("NotificationDebug", "User ${flick.userId} has ${followers.size} followers")

                // Create notification for each follower
                followers.forEach { followerId ->
                    android.util.Log.d("NotificationDebug", "Creating notification for follower: $followerId")
                    
                    val notification = hashMapOf(
                        "id" to UUID.randomUUID().toString(),
                        "userId" to followerId,
                        "senderId" to flick.userId,
                        "senderName" to flick.userName,
                        "senderPhotoUrl" to userPhotoUrl,
                        "type" to "PHOTO_ADDED",
                        "title" to "${flick.userName} added a new photo",
                        "message" to "Check it out!",
                        "flickId" to flick.id,
                        "flickImageUrl" to flick.imageUrl,
                        "isRead" to false,
                        "timestamp" to System.currentTimeMillis()
                    )

                    try {
                        db.collection("notifications").add(notification).await()
                        android.util.Log.d("NotificationDebug", "PHOTO_ADDED notification created for $followerId")
                    } catch (e: Exception) {
                        android.util.Log.e("NotificationDebug", "Failed to create notification for $followerId: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                // Silently fail - don't block photo upload, but log for debugging
                android.util.Log.e("NotificationDebug", "Failed to create photo notifications: ${e.message}", e)
            }
        }
    }

    /**
     * Listen to notifications in real-time with safe type parsing
     * REMOVED: orderBy and limit to avoid Firestore composite index requirement
     * Now using client-side sorting instead
     */
    fun listenToNotifications(
        userId: String,
        onUpdate: (List<Notification>) -> Unit,
        onError: (String) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        android.util.Log.d("FlickRepository", "Setting up notification listener for user: $userId")
        return db.collection(Constants.FirebaseCollections.NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            // REMOVED: .orderBy("timestamp") - requires composite index!
            // REMOVED: .limit() - let client handle pagination
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FlickRepository", "Notification listener error: ${error.message}")
                    onError(error.message ?: "Failed to load notifications")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    android.util.Log.d("FlickRepository", "Notification snapshot received: ${snapshot.documents.size} documents")
                    val notifications = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            android.util.Log.d("FlickRepository", "Processing notification doc: ${doc.id}, type: ${data["type"]}, userId: ${data["userId"]}")
                            Notification(
                                id = doc.id,
                                userId = data["userId"] as? String ?: "",
                                senderId = data["senderId"] as? String ?: "",
                                senderName = data["senderName"] as? String ?: "",
                                senderPhotoUrl = data["senderPhotoUrl"] as? String ?: "",
                                type = parseNotificationType(data["type"] as? String),
                                title = data["title"] as? String ?: "",
                                message = data["message"] as? String ?: "",
                                flickId = data["flickId"] as? String,
                                flickImageUrl = data["flickImageUrl"] as? String,
                                chatId = data["chatId"] as? String,
                                isRead = data["isRead"] as? Boolean ?: false,
                                timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("FlickRepository", "Error parsing notification: ${e.message}")
                            null
                        }
                    }.sortedByDescending { it.timestamp }  // Client-side sorting
                    android.util.Log.d("FlickRepository", "Parsed ${notifications.size} valid notifications")
                    onUpdate(notifications)
                } else {
                    android.util.Log.d("FlickRepository", "Notification snapshot is null")
                }
            }
    }

    /**
     * Mark notification as read
     */
    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        return try {
            db.collection("notifications").document(notificationId)
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
     * Delete all notifications for a user
     */
    suspend fun deleteAllNotifications(userId: String): Result<Unit> {
        return try {
            val batch = db.batch()

            val snapshot = db.collection("notifications")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            batch.commit().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete all notifications")
        }
    }

    /**
     * Delete a notification
     */
    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            db.collection("notifications").document(notificationId)
                .delete()
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete notification")
        }
    }

    /**
     * Delete all MESSAGE notifications for the same conversation.
     * Matches by chatId when available, otherwise falls back to senderId.
     */
    suspend fun deleteMessageNotificationsForConversation(
        userId: String,
        chatId: String?,
        senderId: String
    ): Result<Unit> {
        return try {
            val snapshot = db.collection("notifications")
                .whereEqualTo("userId", userId)
                .whereEqualTo("type", "MESSAGE")
                .get()
                .await()

            val batch = db.batch()
            val hasChatId = !chatId.isNullOrBlank()

            snapshot.documents.forEach { doc ->
                val docChatId = doc.getString("chatId").orEmpty()
                val docSenderId = doc.getString("senderId").orEmpty()

                val isSameConversation = if (hasChatId) {
                    docChatId == chatId
                } else {
                    docSenderId == senderId
                }

                if (isSameConversation) {
                    batch.delete(doc.reference)
                }
            }

            batch.commit().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete conversation notifications")
        }
    }

    /**
     * Create notification for reaction
     */
    private fun createReactionNotification(
        flickId: String,
        ownerId: String,
        reactorId: String,
        reactorName: String,
        reactorPhotoUrl: String,
        reactionType: ReactionType
    ) {
        android.util.Log.d("NotificationDebug", "Creating REACTION notification: $reactorId -> $ownerId for flick $flickId")
        
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { flickDoc ->
                val flickImageUrl = flickDoc.getString("imageUrl")
                val emoji = reactionType.toEmoji()
                val displayName = reactionType.toDisplayName()

                val notification = hashMapOf(
                    "id" to UUID.randomUUID().toString(),
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

                db.collection("notifications").add(notification)
                    .addOnSuccessListener { docRef ->
                        android.util.Log.d("NotificationDebug", "REACTION notification created: ${docRef.id}")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("NotificationDebug", "FAILED to create REACTION notification: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationDebug", "Failed to get flick for reaction notification: ${e.message}")
            }
    }

    /**
     * Toggle reaction on a comment
     */
    fun toggleCommentReaction(
        commentId: String,
        userId: String,
        userName: String,
        userPhotoUrl: String,
        emoji: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        db.collection("comments").document(commentId).get()
            .addOnSuccessListener { doc ->
                val comment = doc.toObject(Comment::class.java)
                if (comment == null) {
                    onResult(Result.Error(Exception("Comment not found"), "Comment not found"))
                    return@addOnSuccessListener
                }

                val currentReaction = comment.reactions[userId]

                if (currentReaction == emoji) {
                    // Remove reaction (same emoji clicked)
                    doc.reference.update("reactions.${userId}", FieldValue.delete())
                        .addOnSuccessListener { onResult(Result.Success(Unit)) }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to remove reaction")) }
                } else {
                    // Add/update reaction
                    doc.reference.update("reactions.${userId}", emoji)
                        .addOnSuccessListener {
                            // Create notification for comment owner
                            if (comment.userId != userId) {
                                createCommentReactionNotification(
                                    commentId = commentId,
                                    flickId = comment.flickId,
                                    ownerId = comment.userId,
                                    reactorId = userId,
                                    reactorName = userName,
                                    reactorPhotoUrl = userPhotoUrl,
                                    emoji = emoji,
                                    commentText = comment.text
                                )
                            }
                            onResult(Result.Success(Unit))
                        }
                        .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to add reaction")) }
                }
            }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to get comment")) }
    }

    /**
     * Create notification for comment reaction
     */
    private fun createCommentReactionNotification(
        commentId: String,
        flickId: String,
        ownerId: String,
        reactorId: String,
        reactorName: String,
        reactorPhotoUrl: String,
        emoji: String,
        commentText: String
    ) {
        android.util.Log.d("NotificationDebug", "Creating COMMENT REACTION notification: $reactorId -> $ownerId")

        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { flickDoc ->
                val flickImageUrl = flickDoc.getString("imageUrl")
                val truncatedComment = if (commentText.length > 30) commentText.take(30) + "..." else commentText

                val notification = hashMapOf(
                    "id" to UUID.randomUUID().toString(),
                    "userId" to ownerId,
                    "senderId" to reactorId,
                    "senderName" to reactorName,
                    "senderPhotoUrl" to reactorPhotoUrl,
                    "type" to "REACTION",
                    "title" to "$reactorName reacted $emoji to your comment",
                    "message" to "$emoji \"$truncatedComment\"",
                    "flickId" to flickId,
                    "flickImageUrl" to flickImageUrl,
                    "commentId" to commentId,
                    "reactionEmoji" to emoji,
                    "isRead" to false,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("notifications").add(notification)
                    .addOnSuccessListener { docRef ->
                        android.util.Log.d("NotificationDebug", "COMMENT REACTION notification created: ${docRef.id}")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("NotificationDebug", "FAILED to create comment reaction notification: ${e.message}")
                    }
            }
    }

    /**
     * Create notification for comment on a photo
     */
    private fun createCommentNotification(
        flickId: String,
        commenterId: String,
        commenterName: String,
        commenterPhotoUrl: String,
        commentText: String
    ) {
        android.util.Log.d("NotificationDebug", "Creating COMMENT notification: $commenterId for flick $flickId")
        
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { flickDoc ->
                val ownerId = flickDoc.getString("userId")
                val flickImageUrl = flickDoc.getString("imageUrl")

                // Don't notify if user comments on their own photo
                if (ownerId != null && ownerId != commenterId) {
                    val truncatedComment = if (commentText.length > 50)
                        commentText.take(50) + "..."
                    else
                        commentText

                    val notification = hashMapOf(
                        "id" to UUID.randomUUID().toString(),
                        "userId" to ownerId,
                        "senderId" to commenterId,
                        "senderName" to commenterName,
                        "senderPhotoUrl" to commenterPhotoUrl,
                        "type" to "COMMENT",
                        "title" to "$commenterName commented on your photo",
                        "message" to truncatedComment,
                        "flickId" to flickId,
                        "flickImageUrl" to flickImageUrl,
                        "isRead" to false,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("notifications").add(notification)
                        .addOnSuccessListener { docRef ->
                            android.util.Log.d("NotificationDebug", "COMMENT notification created: ${docRef.id}")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("NotificationDebug", "FAILED to create COMMENT notification: ${e.message}")
                        }
                } else {
                    android.util.Log.d("NotificationDebug", "Skipping comment notification - user commented on own photo")
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationDebug", "Failed to get flick for comment notification: ${e.message}")
            }
    }

    /**
     * Create streak reminder notification
     */
    suspend fun createStreakReminderNotification(userId: String, userName: String, currentStreak: Int): Result<Unit> {
        return try {
            val motivationalMessages = listOf(
                "Don't break your $currentStreak-day streak! Share a photo today ����",
                "Your $currentStreak-day streak is at risk! Post now to keep it alive ���",
                "Keep the flame burning! $currentStreak days and counting ����",
                "One photo away from day ${currentStreak + 1}! Don't stop now ����"
            )
            
            val randomMessage = motivationalMessages.random()
            
            val notification = hashMapOf(
                "id" to UUID.randomUUID().toString(),
                "userId" to userId,
                "senderId" to "system",
                "senderName" to "PicFlick",
                "senderPhotoUrl" to "",
                "type" to "STREAK_REMINDER",
                "title" to "���� Streak Alert!",
                "message" to randomMessage,
                "isRead" to false,
                "timestamp" to System.currentTimeMillis(),
                "streakCount" to currentStreak
            )

            db.collection("notifications").add(notification).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to create streak reminder")
        }
    }

    /**
     * Create notification when user is tagged in a photo
     */
    private fun createTagNotification(
        flickId: String,
        photoOwnerId: String,
        photoOwnerName: String,
        photoOwnerPhotoUrl: String,
        taggedUserId: String
    ) {
        android.util.Log.d("NotificationDebug", "Creating TAG notification: $photoOwnerId tagged $taggedUserId in photo $flickId")
        
        db.collection("flicks").document(flickId).get()
            .addOnSuccessListener { flickDoc ->
                val flickImageUrl = flickDoc.getString("imageUrl")

                val notification = hashMapOf(
                    "id" to UUID.randomUUID().toString(),
                    "userId" to taggedUserId,
                    "senderId" to photoOwnerId,
                    "senderName" to photoOwnerName,
                    "senderPhotoUrl" to photoOwnerPhotoUrl,
                    "type" to "MENTION",
                    "title" to "$photoOwnerName tagged you",
                    "message" to "You're in a photo!",
                    "flickId" to flickId,
                    "flickImageUrl" to flickImageUrl,
                    "isRead" to false,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("notifications").add(notification)
                    .addOnSuccessListener { docRef ->
                        android.util.Log.d("NotificationDebug", "TAG notification created: ${docRef.id}")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("NotificationDebug", "FAILED to create TAG notification: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("NotificationDebug", "Failed to get flick for TAG notification: ${e.message}")
            }
    }

    /**
     * Create notification when someone follows you
     */
    private fun createFollowNotification(
        followerId: String,
        followerName: String,
        targetUserId: String
    ) {
        val notification = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to targetUserId,
            "senderId" to followerId,
            "senderName" to followerName,
            "senderPhotoUrl" to "",
            "type" to "FOLLOW",
            "title" to "$followerName started following you",
            "message" to "You have a new follower!",
            "isRead" to false,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("notifications").add(notification)
    }

    /**
     * Get notifications for a user
     */
    fun getNotifications(userId: String, onResult: (Result<List<Map<String, Any>>>) -> Unit) {
        db.collection(Constants.FirebaseCollections.NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(Constants.Pagination.NOTIFICATIONS_PER_PAGE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(error, "Failed to load notifications"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val notifications = snapshot.documents.map { it.data ?: emptyMap() }
                    onResult(Result.Success(notifications))
                }
            }
    }

    /**
     * Mark notification as read
     */
    fun markNotificationRead(notificationId: String, onResult: (Result<Unit>) -> Unit) {
        db.collection("notifications").document(notificationId)
            .update("isRead", true)
            .addOnSuccessListener { onResult(Result.Success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.Error(e, "Failed to mark as read")) }
    }

    /**
     * Add a comment to a flick and notify the owner
     */
    suspend fun addComment(
        flickId: String,
        userId: String,
        userName: String,
        userPhotoUrl: String,
        text: String
    ): Result<Unit> {
        return try {
            android.util.Log.d("CommentAdd", "Creating comment: flickId=$flickId, userId=$userId, text=$text")
            
            val comment = Comment(
                id = UUID.randomUUID().toString(),
                flickId = flickId,
                userId = userId,
                userName = userName,
                userPhotoUrl = userPhotoUrl,
                text = text
                // timestamp is set automatically by @ServerTimestamp
            )

            // Add comment
            val docRef = db.collection("comments").add(comment).await()
            android.util.Log.d("CommentAdd", "Comment added with ID: ${docRef.id}")

            // Update flick comment count
            db.collection("flicks").document(flickId)
                .update("commentCount", FieldValue.increment(1))
                .await()

            // Create notification for photo owner (if not the commenter)
            createCommentNotification(flickId, userId, userName, userPhotoUrl, text)

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("CommentAdd", "Failed to add comment: ${e.message}", e)
            Result.Error(e, "Failed to add comment: ${e.message}")
        }
    }

    /**
     * Get comments for a flick - returns a ListenerRegistration that must be removed when done
     */
    fun getComments(flickId: String, onResult: (Result<List<Comment>>) -> Unit): ListenerRegistration {
        return db.collection("comments")
            .whereEqualTo("flickId", flickId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(error, "Failed to load comments"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val comments = snapshot.documents.mapNotNull { doc ->
                        val comment = doc.toObject(Comment::class.java)?.copy(id = doc.id)
                        if (comment != null && comment.id.isBlank()) {
                            android.util.Log.w("CommentDebug", "Comment has blank ID after mapping! Doc ID: ${doc.id}")
                        }
                        comment
                    }
                    android.util.Log.d("CommentDebug", "Loaded ${comments.size} comments, first ID: ${comments.firstOrNull()?.id}")
                    onResult(Result.Success(comments))
                }
            }
    }

    /**
     * Add a reply to a comment and notify the comment owner
     */
    suspend fun addReply(
        flickId: String,
        parentCommentId: String,
        userId: String,
        userName: String,
        userPhotoUrl: String,
        text: String
    ): Result<Unit> {
        return try {
            val reply = Comment(
                id = UUID.randomUUID().toString(),
                flickId = flickId,
                userId = userId,
                userName = userName,
                userPhotoUrl = userPhotoUrl,
                text = text,
                parentCommentId = parentCommentId
                // timestamp is set automatically by @ServerTimestamp
            )

            // Add reply
            db.collection("comments").add(reply).await()

            // Update parent comment reply count
            db.collection("comments").document(parentCommentId)
                .update("replyCount", FieldValue.increment(1))
                .await()

            // Update flick comment count
            db.collection("flicks").document(flickId)
                .update("commentCount", FieldValue.increment(1))
                .await()

            // Create notification for comment owner (if not the replier)
            createCommentReplyNotification(parentCommentId, flickId, userId, userName, userPhotoUrl, text)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to add reply")
        }
    }

    /**
     * Create notification for reply to a comment
     */
    private fun createCommentReplyNotification(
        parentCommentId: String,
        flickId: String,
        replierId: String,
        replierName: String,
        replierPhotoUrl: String,
        replyText: String
    ) {
        // Get the parent comment to find its owner
        db.collection("comments").document(parentCommentId).get()
            .addOnSuccessListener { commentDoc ->
                val commentOwnerId = commentDoc.getString("userId")
                
                // Don't notify if user replies to their own comment
                if (commentOwnerId != null && commentOwnerId != replierId) {
                    val truncatedReply = if (replyText.length > 50)
                        replyText.take(50) + "..."
                    else
                        replyText

                    val notification = hashMapOf(
                        "id" to UUID.randomUUID().toString(),
                        "userId" to commentOwnerId,
                        "type" to "COMMENT_REPLY",
                        "title" to "$replierName replied to your comment",
                        "message" to truncatedReply,
                        "senderId" to replierId,
                        "senderName" to replierName,
                        "senderPhotoUrl" to replierPhotoUrl,
                        "flickId" to flickId,
                        "commentId" to parentCommentId,
                        "isRead" to false,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("notifications").add(notification)
                        .addOnSuccessListener { docRef ->
                            android.util.Log.d("NotificationDebug", "COMMENT_REPLY notification created: ${docRef.id}")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("NotificationDebug", "FAILED to create comment reply notification: ${e.message}")
                        }
                }
            }
    }

    /**
     * Like a comment and notify the comment owner
     */
    suspend fun likeComment(
        commentId: String, 
        flickId: String,
        userId: String,
        userName: String,
        userPhotoUrl: String
    ): Result<Unit> {
        return try {
            db.collection("comments").document(commentId)
                .update("likes", FieldValue.arrayUnion(userId))
                .await()
            
            // Create notification for comment owner
            createCommentLikeNotification(commentId, flickId, userId, userName, userPhotoUrl)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to like comment")
        }
    }

    /**
     * Unlike a comment
     */
    suspend fun unlikeComment(commentId: String, userId: String): Result<Unit> {
        return try {
            db.collection("comments").document(commentId)
                .update("likes", FieldValue.arrayRemove(userId))
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to unlike comment")
        }
    }

    /**
     * Create notification for comment like (react)
     */
    private fun createCommentLikeNotification(
        commentId: String,
        flickId: String,
        likerId: String,
        likerName: String,
        likerPhotoUrl: String
    ) {
        // Get the comment to find its owner
        db.collection("comments").document(commentId).get()
            .addOnSuccessListener { commentDoc ->
                val commentOwnerId = commentDoc.getString("userId")
                val commentText = commentDoc.getString("text") ?: ""
                
                // Don't notify if user likes their own comment
                if (commentOwnerId != null && commentOwnerId != likerId) {
                    val truncatedComment = if (commentText.length > 50)
                        commentText.take(50) + "..."
                    else
                        commentText

                    val notification = hashMapOf(
                        "id" to UUID.randomUUID().toString(),
                        "userId" to commentOwnerId,
                        "type" to "COMMENT_LIKE",
                        "title" to "$likerName reacted to your comment",
                        "message" to truncatedComment,
                        "senderId" to likerId,
                        "senderName" to likerName,
                        "senderPhotoUrl" to likerPhotoUrl,
                        "flickId" to flickId,
                        "commentId" to commentId,
                        "isRead" to false,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("notifications").add(notification)
                        .addOnSuccessListener { docRef ->
                            android.util.Log.d("NotificationDebug", "COMMENT_LIKE notification created: ${docRef.id}")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("NotificationDebug", "FAILED to create comment like notification: ${e.message}")
                        }
                }
            }
    }

    /**
     * Get replies for a specific comment
     */
    fun getReplies(parentCommentId: String, onResult: (Result<List<Comment>>) -> Unit): ListenerRegistration {
        return db.collection("comments")
            .whereEqualTo("parentCommentId", parentCommentId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onResult(Result.Error(error, "Failed to load replies"))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val replies = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Comment::class.java)?.copy(id = doc.id)
                    }
                    onResult(Result.Success(replies))
                }
            }
    }

    /**
     * Delete a comment and all its replies
     */
    suspend fun deleteComment(commentId: String, flickId: String): Result<Unit> {
        return try {
            // Validate commentId
            if (commentId.isBlank()) {
                android.util.Log.e("CommentDelete", "Comment ID is blank!")
                return Result.Error(IllegalArgumentException("Comment ID is blank"), "Cannot delete: Comment ID is invalid")
            }
            
            android.util.Log.d("CommentDelete", "Attempting to delete comment: $commentId")
            
            // Delete the comment
            db.collection("comments").document(commentId).delete().await()
            android.util.Log.d("CommentDelete", "Comment deleted successfully")

            // Decrement flick comment count by 1
            db.collection("flicks").document(flickId)
                .update("commentCount", FieldValue.increment(-1))
                .await()
            android.util.Log.d("CommentDelete", "Flick comment count decremented")

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("CommentDelete", "Failed to delete comment: ${e.message}", e)
            Result.Error(e, "Failed to delete comment: ${e.message}")
        }
    }

    /**
     * Update flick description
     */
    suspend fun updateFlickDescription(flickId: String, description: String): Result<Unit> {
        return try {
            db.collection("flicks").document(flickId)
                .update("description", description)
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update description")
        }
    }

    /**
     * Update flick with new filter and description
     * Re-uploads the image with the applied filter
     */
    suspend fun updateFlickWithFilter(
        flickId: String,
        description: String,
        filterType: String,
        newImageUrl: String,
        taggedFriends: List<String>
    ): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "description" to description,
                "filter" to filterType,
                "imageUrl" to newImageUrl,
                "taggedFriends" to taggedFriends,
                "editedAt" to FieldValue.serverTimestamp()
            )
            
            db.collection("flicks").document(flickId)
                .update(updates)
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update photo")
        }
    }

    /**
     * Update flick filter and description only (no image re-upload)
     * Used by EditPhotoScreen for changing filters/captions on existing photos
     */
    suspend fun updateFlickFilterAndDescription(
        flickId: String,
        description: String,
        filterType: String
    ): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "description" to description,
                "filter" to filterType,
                "editedAt" to FieldValue.serverTimestamp()
            )
            
            db.collection("flicks").document(flickId)
                .update(updates)
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update photo")
        }
    }

    /**
     * Accept a tag (user accepts being tagged in a photo)
     */
    suspend fun acceptTag(flickId: String, userId: String): Result<Unit> {
        return try {
            android.util.Log.d("FlickRepository", "User $userId accepting tag in flick $flickId")
            
            // Get current flick
            val flickDoc = db.collection("flicks").document(flickId).get().await()
            val currentTagged = flickDoc.get("taggedFriends") as? List<String> ?: emptyList()
            
            // Add user to taggedFriends if not already there
            if (userId !in currentTagged) {
                val updatedTagged = currentTagged + userId
                db.collection("flicks").document(flickId)
                    .update("taggedFriends", updatedTagged)
                    .await()
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FlickRepository", "Failed to accept tag: ${e.message}")
            Result.Error(e, "Failed to accept tag")
        }
    }

    /**
     * Decline a tag (user removes themselves from being tagged)
     */
    suspend fun declineTag(flickId: String, userId: String): Result<Unit> {
        return try {
            android.util.Log.d("FlickRepository", "User $userId declining tag in flick $flickId")
            
            // Get current flick
            val flickDoc = db.collection("flicks").document(flickId).get().await()
            val currentTagged = flickDoc.get("taggedFriends") as? List<String> ?: emptyList()
            
            // Remove user from taggedFriends
            val updatedTagged = currentTagged.filter { it != userId }
            db.collection("flicks").document(flickId)
                .update("taggedFriends", updatedTagged)
                .await()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FlickRepository", "Failed to decline tag: ${e.message}")
            Result.Error(e, "Failed to decline tag")
        }
    }

    /**
     * Update tagged friends for a flick
     */
    suspend fun updateTaggedFriends(
        flickId: String,
        taggedFriends: List<String>,
        photoOwnerId: String,
        photoOwnerName: String,
        photoOwnerPhotoUrl: String
    ): Result<Unit> {
        return try {
            // Get current flick to check existing tags
            val flickDoc = db.collection("flicks").document(flickId).get().await()
            val currentTagged = flickDoc.get("taggedFriends") as? List<String> ?: emptyList()
            
            // Find newly added friends (to create notifications for them)
            val newlyTagged = taggedFriends.filter { it !in currentTagged }
            
            // Update the flick with new tagged friends list
            db.collection("flicks").document(flickId)
                .update("taggedFriends", taggedFriends)
                .await()
            
            // Create notifications for newly tagged friends
            newlyTagged.forEach { taggedUserId ->
                createTagNotification(
                    flickId = flickId,
                    photoOwnerId = photoOwnerId,
                    photoOwnerName = photoOwnerName,
                    photoOwnerPhotoUrl = photoOwnerPhotoUrl,
                    taggedUserId = taggedUserId
                )
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update tagged friends")
        }
    }

    /**
     * Get user streak info
     */
    suspend fun getUserStreak(userId: String): Result<Pair<Int, Boolean>> {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            val streakData = userDoc.get("streak") as? Map<String, Any>

            val currentStreak = (streakData?.get("current") as? Long)?.toInt() ?: 0
            val lastUpload = streakData?.get("lastUpload") as? Long ?: 0

            // Check if streak is still active (uploaded today or yesterday)
            val calendar = Calendar.getInstance()
            val today = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = calendar.timeInMillis

            val isActive = lastUpload >= yesterday
            val canContinue = lastUpload in yesterday..today

            val effectiveStreak = if (isActive) currentStreak else 0

            Result.Success(Pair(effectiveStreak, canContinue))
        } catch (e: Exception) {
            Result.Error(e, "Failed to get streak")
        }
    }

    /**
     * Update user streak after upload
     */
    suspend fun updateStreak(userId: String): Result<Unit> {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            val streakData = userDoc.get("streak") as? Map<String, Any>

            val currentStreak = (streakData?.get("current") as? Long)?.toInt() ?: 0
            val lastUpload = streakData?.get("lastUpload") as? Long ?: 0

            // Check if already uploaded today
            val calendar = Calendar.getInstance()
            val today = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            if (lastUpload >= today) {
                // Already uploaded today, don't increment
                return Result.Success(Unit)
            }

            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = calendar.timeInMillis

            val newStreak = if (lastUpload >= yesterday) {
                // Continuing streak
                currentStreak + 1
            } else {
                // New streak
                1
            }

            val newStreakData = hashMapOf(
                "current" to newStreak,
                "lastUpload" to System.currentTimeMillis(),
                "longest" to maxOf(newStreak, (streakData?.get("longest") as? Long)?.toInt() ?: 0)
            )

            db.collection("users").document(userId)
                .update("streak", newStreakData)
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update streak")
        }
    }

    /**
     * Get leaderboard (top users by likes/reactions)
     */
    suspend fun getLeaderboard(): Result<List<Pair<UserProfile, Int>>> {
        return try {
            // Get all users
            val usersSnapshot = db.collection(Constants.FirebaseCollections.USERS).limit(Constants.Pagination.USERS_PER_PAGE.toLong()).get().await()
            val users = usersSnapshot.toObjects(UserProfile::class.java)

            // Calculate scores for each user
            val userScores = users.map { user ->
                val flicksSnapshot = db.collection("flicks")
                    .whereEqualTo("userId", user.uid)
                    .get()
                    .await()

                val flicks = flicksSnapshot.toObjects(Flick::class.java)
                val totalReactions = flicks.sumOf { it.getTotalReactions() }

                Pair(user, totalReactions)
            }

            // Sort by score
            val sorted = userScores.sortedByDescending { it.second }

            Result.Success(sorted)
        } catch (e: Exception) {
            Result.Error(e, "Failed to get leaderboard")
        }
    }

    /**
     * Report a photo for inappropriate content
     * Reports are stored for admin review
     */
    suspend fun reportPhoto(
        flickId: String,
        reporterId: String,
        reason: String,
        details: String = ""
    ): Result<Unit> {
        return try {
            val report = hashMapOf(
                "flickId" to flickId,
                "reporterId" to reporterId,
                "reason" to reason,
                "details" to details,
                "timestamp" to System.currentTimeMillis(),
                "status" to "pending" // pending, reviewed, action_taken
            )

            db.collection("reports").add(report).await()

            // Also increment report count on the flick for auto-moderation
            db.collection("flicks").document(flickId)
                .update("reportCount", FieldValue.increment(1))
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to submit report")
        }
    }

    /**
     * Submit user feedback to Firestore
     * Creates a feedback document in the "feedback" collection
     */
    suspend fun submitFeedback(
        userId: String,
        userName: String,
        userEmail: String,
        subject: String,
        message: String,
        category: String = "GENERAL",
        appVersion: String = "",
        deviceInfo: String = ""
    ): Result<String> {
        return try {
            val docRef = db.collection("feedback").document()
            val feedback = hashMapOf(
                "id" to docRef.id,
                "userId" to userId,
                "userName" to userName,
                "userEmail" to userEmail,
                "subject" to subject,
                "message" to message,
                "category" to category,
                "timestamp" to System.currentTimeMillis(),
                "status" to "NEW",
                "appVersion" to appVersion,
                "deviceInfo" to deviceInfo
            )

            docRef.set(feedback).await()

            Result.Success(docRef.id)
        } catch (e: Exception) {
android.util.Log.e("FlickRepository", "Failed to submit feedback", e)
            Result.Error(e, "Failed to submit feedback. Please try again.")
        }
    }

    /**
     * Get reported photos (for admin use)
     */
    suspend fun getReportedPhotos(): Result<List<Pair<Flick, Int>>> {
        return try {
            // Get flicks with reportCount > 0
            val snapshot = db.collection("flicks")
                .whereGreaterThan("reportCount", 0)
                .orderBy("reportCount", Query.Direction.DESCENDING)
                .get()
                .await()

            val flicks = snapshot.toObjects(Flick::class.java)
            val reportCounts = flicks.map { it.reportCount }

            Result.Success(flicks.zip(reportCounts))
        } catch (e: Exception) {
            Result.Error(e, "Failed to get reported photos")
        }
    }

    /**
     * Save user notification preferences to Firestore
     */
    suspend fun saveNotificationPreferences(
        userId: String,
        preferences: com.picflick.app.data.NotificationPreferences
    ): Result<Unit> {
        return try {
            // Use set with merge to create or update the field
            db.collection("users").document(userId)
                .set(mapOf("notificationPreferences" to preferences), com.google.firebase.firestore.SetOptions.merge())
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to save notification preferences")
        }
    }

    /**
     * Delete all user data - photos, profile, friends, chats, notifications
     * Used for account deletion
     */
    suspend fun deleteUserData(userId: String): Result<Unit> {
        return try {
            // 1. Delete all user's photos (flicks)
            val flicksSnapshot = db.collection(Constants.FirebaseCollections.FLICKS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            flicksSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }

            // 2. Delete user's chats and messages
            val chatSessions = db.collection(Constants.FirebaseCollections.CHAT_SESSIONS)
                .whereArrayContains("participants", userId)
                .get()
                .await()
            
            chatSessions.documents.forEach { doc ->
                // Delete all messages in the chat
                val messages = doc.reference.collection("messages").get().await()
                messages.documents.forEach { msg ->
                    msg.reference.delete().await()
                }
                // Delete the chat session
                doc.reference.delete().await()
            }

            // 3. Delete user's notifications
            val notifications = db.collection(Constants.FirebaseCollections.NOTIFICATIONS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            notifications.documents.forEach { doc ->
                doc.reference.delete().await()
            }

            // 4. Remove user from others' followers/following lists
            val allUsers = db.collection(Constants.FirebaseCollections.USERS).get().await()
            allUsers.documents.forEach { userDoc ->
                val userData = userDoc.toObject(UserProfile::class.java)
                if (userData != null && (userData.followers.contains(userId) || userData.following.contains(userId))) {
                    val updatedFollowers = userData.followers.filter { it != userId }
                    val updatedFollowing = userData.following.filter { it != userId }
                    userDoc.reference.update(
                        mapOf(
                            "followers" to updatedFollowers,
                            "following" to updatedFollowing
                        )
                    ).await()
                }
            }

            // 5. Delete user profile
            db.collection(Constants.FirebaseCollections.USERS).document(userId)
                .delete()
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete user data: ${e.message}")
        }
    }

    // ==================== FRIEND GROUPS (ALBUMS) ====================

    /**
     * Create a new friend group/album
     */
    suspend fun createFriendGroup(
        userId: String,
        name: String,
        friendIds: List<String>,
        icon: String = "👥",
        color: String = "#4FC3F7"
    ): Result<FriendGroup> {
        return try {
            val groupId = UUID.randomUUID().toString()
            val group = hashMapOf(
                "id" to groupId,
                "userId" to userId,
                "name" to name,
                "friendIds" to friendIds,
                "icon" to icon,
                "color" to color,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection("users").document(userId)
                .collection("friendGroups")
                .document(groupId)
                .set(group)
                .await()

            Result.Success(FriendGroup(
                id = groupId,
                userId = userId,
                name = name,
                friendIds = friendIds,
                icon = icon,
                color = color
            ))
        } catch (e: Exception) {
            Result.Error(e, "Failed to create friend group")
        }
    }

    /**
     * Get all friend groups for a user
     */
    suspend fun getFriendGroups(userId: String): Result<List<FriendGroup>> {
        return try {
            val snapshot = db.collection("users").document(userId)
                .collection("friendGroups")
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            val groups = snapshot.toObjects(FriendGroup::class.java)
            Result.Success(groups)
        } catch (e: Exception) {
            Result.Error(e, "Failed to get friend groups")
        }
    }

    /**
     * Update a friend group
     */
    suspend fun updateFriendGroup(
        userId: String,
        groupId: String,
        name: String? = null,
        friendIds: List<String>? = null,
        icon: String? = null,
        color: String? = null
    ): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "updatedAt" to System.currentTimeMillis()
            )

            name?.let { updates["name"] = it }
            friendIds?.let { updates["friendIds"] = it }
            icon?.let { updates["icon"] = it }
            color?.let { updates["color"] = it }

            db.collection("users").document(userId)
                .collection("friendGroups")
                .document(groupId)
                .update(updates)
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to update friend group")
        }
    }

    /**
     * Delete a friend group
     */
    suspend fun deleteFriendGroup(userId: String, groupId: String): Result<Unit> {
        return try {
            db.collection("users").document(userId)
                .collection("friendGroups")
                .document(groupId)
                .delete()
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to delete friend group")
        }
    }

    /**
     * Add a friend to a group
     */
    suspend fun addFriendToGroup(
        userId: String,
        groupId: String,
        friendId: String
    ): Result<Unit> {
        return try {
            db.collection("users").document(userId)
                .collection("friendGroups")
                .document(groupId)
                .update(
                    "friendIds", FieldValue.arrayUnion(friendId),
                    "updatedAt", System.currentTimeMillis()
                )
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to add friend to group")
        }
    }

    /**
     * Remove a friend from a group
     */
    suspend fun removeFriendFromGroup(
        userId: String,
        groupId: String,
        friendId: String
    ): Result<Unit> {
        return try {
            db.collection("users").document(userId)
                .collection("friendGroups")
                .document(groupId)
                .update(
                    "friendIds", FieldValue.arrayRemove(friendId),
                    "updatedAt", System.currentTimeMillis()
                )
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e, "Failed to remove friend from group")
        }
    }

    /**
     * Get flicks from friends in a specific group
     */
    suspend fun getFlicksForFriendGroup(
        userId: String,
        groupId: String,
        onResult: (Result<List<Flick>>) -> Unit
    ) {
        try {
            // Get the group
            val groupDoc = db.collection("users").document(userId)
                .collection("friendGroups")
                .document(groupId)
                .get()
                .await()

            val friendIds = groupDoc.get("friendIds") as? List<String> ?: emptyList()

            if (friendIds.isEmpty()) {
                onResult(Result.Success(emptyList()))
                return
            }

            // Get flicks from friends in the group (photos visible to user)
            db.collection(Constants.FirebaseCollections.FLICKS)
                .whereIn("userId", friendIds)
                .whereIn("privacy", listOf("public", "friends"))
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(Constants.Pagination.FLICKS_PER_PAGE.toLong())
                .get()
                .addOnSuccessListener { snapshot ->
                    val flicks = snapshot.toObjects(Flick::class.java)
                    onResult(Result.Success(flicks))
                }
                .addOnFailureListener { e ->
                    onResult(Result.Error(e, "Failed to get group flicks"))
                }
        } catch (e: Exception) {
            onResult(Result.Error(e, "Failed to get group flicks"))
        }
    }

    // ==================== END FRIEND GROUPS ====================
}
