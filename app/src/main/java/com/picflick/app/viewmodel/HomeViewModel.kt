package com.picflick.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picflick.app.Constants
import com.picflick.app.data.FeedFilter
import com.picflick.app.data.Flick
import com.picflick.app.data.FriendGroup
import com.picflick.app.data.ReactionType
import com.picflick.app.data.Result
import com.picflick.app.repository.FlickRepository
import com.picflick.app.utils.Analytics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * ViewModel for the home screen feed displaying photo flicks
 *
 * Manages:
 * - Photo feed from friends (with pagination)
 * - Explore feed (public photos)
 * - Friend groups/albums filtering
 * - Like and reaction actions
 * - Today's upload count for daily limits
 */
class HomeViewModel : ViewModel() {

    private val repository = FlickRepository.getInstance()

    /** List of flicks for the main feed */
    var flicks = mutableStateListOf<Flick>()
        private set

    /** List of public flicks for explore tab */
    var exploreFlicks = mutableStateListOf<Flick>()
        private set

    /** Loading state for initial load */
    var isLoading by mutableStateOf(false)
        private set

    /** Loading state for pagination */
    var isLoadingMore by mutableStateOf(false)
        private set

    /** Whether more pages can be loaded */
    var canLoadMore by mutableStateOf(true)
        private set

    /** Current error message to display */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** One-shot signal for load-more failures (used for toast in fullscreen) */
    var loadMoreFailureMessage by mutableStateOf<String?>(null)
        private set

    var loadMoreFailureVersion by mutableIntStateOf(0)
        private set

    /** Number of photos uploaded today (for daily limits) */
    var todayUploadCount by mutableIntStateOf(0)
        private set

    /** Current user ID for filtering */
    var currentUserId by mutableStateOf<String?>(null)
        private set

    // Deterministic pagination cursor for AllFriends feed
    private var paginationCursorTimestamp: Long? = null
    private var paginationCursorId: String? = null
    private var consecutiveEmptyPages = 0
    private var lastLoadMoreRequestKey: String? = null

    /** Available friend groups for filtering */
    var friendGroups = mutableStateListOf<FriendGroup>()
        private set

    /** Local optimistic items that should stay visible until real data catches up. */
    private val optimisticFlicks = mutableStateListOf<Flick>()
    private var debouncedFeedRefreshJob: Job? = null

    /** Currently selected feed filter */
    var selectedFilter by mutableStateOf<FeedFilter>(FeedFilter.AllFriends)
        private set

    /**
     * Load flicks for the feed based on selected filter
     * Clears existing flicks and loads first page
     *
     * @see selectedFilter Determines which flicks to load
     */
    fun loadFlicks() {
        val userId = currentUserId
        if (userId == null) {
            errorMessage = "User not logged in"
            return
        }

        isLoading = flicks.isEmpty()
        errorMessage = null
        isLoadingMore = false
        canLoadMore = true
        paginationCursorTimestamp = null
        paginationCursorId = null
        consecutiveEmptyPages = 0
        lastLoadMoreRequestKey = null

        when (val filter = selectedFilter) {
            is FeedFilter.AllFriends -> {
                // Load all friends' flicks (first page)
                viewModelScope.launch {
                    when (val result = repository.getFlicksForUserPaginated(
                        userId = userId,
                        lastTimestamp = null,
                        lastFlickId = null,
                        pageSize = Constants.Pagination.FLICKS_PER_PAGE,
                        excludeIds = emptySet()
                    )) {
                        is Result.Success -> {
                            val merged = mergeWithOptimistic(result.data)
                            flicks.clear()
                            flicks.addAll(merged)
                            paginationCursorTimestamp = result.data.lastOrNull()?.timestamp
                            paginationCursorId = result.data.lastOrNull()?.id
                            consecutiveEmptyPages = 0
                            isLoading = false
                            canLoadMore = result.data.isNotEmpty()
                        }
                        is Result.Error -> {
                            errorMessage = result.message
                            isLoading = false
                        }
                        is Result.Loading -> { }
                    }
                }
            }
            is FeedFilter.ByGroup -> {
                // Load flicks from friends in specific group
                viewModelScope.launch {
                    repository.getFlicksForFriendGroup(userId, filter.group.id) { result ->
                        when (result) {
                            is Result.Success -> {
                                val merged = mergeWithOptimistic(result.data)
                                flicks.clear()
                                flicks.addAll(merged)
                                isLoading = false
                            }
                            is Result.Error -> {
                                errorMessage = result.message
                                isLoading = false
                            }
                            is Result.Loading -> { }
                        }
                    }
                }
            }
        }
    }

    /**
     * Load MORE flicks for infinite scroll (pagination)
     */
    fun loadMoreFlicks() {
        val userId = currentUserId
        if (userId == null || isLoadingMore) return

        // Use deterministic cursor state instead of deriving from current list tail
        val cursorTimestamp = paginationCursorTimestamp ?: flicks.lastOrNull()?.timestamp
        val cursorId = paginationCursorId ?: flicks.lastOrNull()?.id
        if (cursorTimestamp == null) return

        val existingIds = flicks.map { it.id }.filter { it.isNotBlank() }.toSet()
        val requestKey = "$cursorTimestamp|${cursorId.orEmpty()}|${flicks.size}"
        if (lastLoadMoreRequestKey == requestKey) return

        lastLoadMoreRequestKey = requestKey
        isLoadingMore = true

        viewModelScope.launch {
            when (val result = repository.getFlicksForUserPaginated(
                userId = userId,
                lastTimestamp = cursorTimestamp,
                lastFlickId = cursorId,
                pageSize = Constants.Pagination.FLICKS_PER_PAGE,
                excludeIds = existingIds
            )) {
                is Result.Success -> {
                    val newFlicks = result.data
                    if (newFlicks.isNotEmpty()) {
                        lastLoadMoreRequestKey = null
                        flicks.addAll(newFlicks)
                        paginationCursorTimestamp = newFlicks.last().timestamp
                        paginationCursorId = newFlicks.last().id
                        consecutiveEmptyPages = 0
                        canLoadMore = true
                    } else {
                        consecutiveEmptyPages += 1
                        // Allow a few sparse windows before declaring true end of feed
                        canLoadMore = consecutiveEmptyPages < 3
                        paginationCursorTimestamp = (paginationCursorTimestamp ?: cursorTimestamp) - 1L
                    }
                    isLoadingMore = false
                }
                is Result.Error -> {
                    // Keep UI responsive and retry-friendly after transient failures.
                    isLoadingMore = false
                    canLoadMore = true
                    lastLoadMoreRequestKey = null
                    loadMoreFailureMessage = result.message.ifBlank { "Failed to load more photos" }
                    loadMoreFailureVersion += 1
                }
                is Result.Loading -> { }
            }
        }
    }

    /**
     * Load flicks with explicit userId (called when userId might have changed)
     */
    fun loadFlicks(userId: String) {
        currentUserId = userId
        loadFlicks()
    }

    /**
     * Set selected filter and reload flicks
     */
    fun setFilter(filter: FeedFilter) {
        selectedFilter = filter
        loadFlicks()
    }

    /**
     * Load friend groups for the current user
     */
    fun loadFriendGroups(userId: String) {
        viewModelScope.launch {
            when (val result = repository.getFriendGroups(userId)) {
                is Result.Success -> {
                    friendGroups.clear()
                    friendGroups.addAll(result.data)
                }
                is Result.Error -> {
                    // Silently fail - not critical
                }
                is Result.Loading -> { }
            }
        }
    }

    /**
     * Create a new shared album/group
     */
    fun createFriendGroup(
        userId: String,
        name: String,
        icon: String,
        friendIds: List<String>,
        color: String = "#4FC3F7",
        eventAt: Long? = null,
        onComplete: (Boolean, FriendGroup?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.createFriendGroup(userId, name, friendIds, icon, color, eventAt)) {
                is Result.Success -> {
                    friendGroups.add(result.data)
                    isLoading = false
                    onComplete(true, result.data)
                }
                is Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                    onComplete(false, null)
                }
                is Result.Loading -> { }
            }
        }
    }

    fun createLocalFriendGroup(
        userId: String,
        name: String,
        icon: String,
        friendIds: List<String>,
        color: String = "#4FC3F7",
        eventAt: Long? = null,
        onComplete: (Boolean, FriendGroup?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.createLocalFriendGroup(userId, name, friendIds, icon, color, eventAt)) {
                is Result.Success -> {
                    friendGroups.add(result.data)
                    isLoading = false
                    onComplete(true, result.data)
                }
                is Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                    onComplete(false, null)
                }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Update an existing friend group
     */
    fun updateFriendGroup(
        userId: String,
        groupId: String,
        name: String,
        icon: String,
        friendIds: List<String>,
        color: String,
        eventAt: Long? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.updateFriendGroup(
                userId = userId,
                groupId = groupId,
                name = name,
                friendIds = friendIds,
                icon = icon,
                color = color,
                eventAt = eventAt
            )) {
                is Result.Success -> {
                    val index = friendGroups.indexOfFirst { it.id == groupId }
                    if (index != -1) {
                        val updated = friendGroups[index].copy(
                            name = name,
                            icon = icon,
                            friendIds = friendIds,
                            memberIds = (friendIds + friendGroups[index].effectiveOwnerId()).distinct(),
                            color = color,
                            eventAt = eventAt,
                            updatedAt = System.currentTimeMillis()
                        )
                        friendGroups[index] = updated

                        if (selectedFilter is FeedFilter.ByGroup && (selectedFilter as FeedFilter.ByGroup).group.id == groupId) {
                            selectedFilter = FeedFilter.ByGroup(updated)
                            loadFlicks()
                        }
                    }
                    isLoading = false
                    onComplete(true)
                }
                is Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                    onComplete(false)
                }
                is Result.Loading -> { }
            }
        }
    }

    fun inviteFriendToGroup(
        inviterId: String,
        inviterName: String,
        groupId: String,
        inviteeId: String,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            when (val result = repository.inviteFriendToGroup(inviterId, inviterName, groupId, inviteeId)) {
                is Result.Success -> onComplete(true, null)
                is Result.Error -> onComplete(false, result.message)
                is Result.Loading -> Unit
            }
        }
    }

    fun cancelGroupInvite(
        inviterId: String,
        groupId: String,
        inviteeId: String,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            when (val result = repository.cancelGroupInvite(inviterId, groupId, inviteeId)) {
                is Result.Success -> onComplete(true, null)
                is Result.Error -> onComplete(false, result.message)
                is Result.Loading -> Unit
            }
        }
    }

    fun updateGroupAdmins(
        userId: String,
        groupId: String,
        adminIds: List<String>,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            when (val result = repository.updateGroupAdmins(userId, groupId, adminIds)) {
                is Result.Success -> {
                    val idx = friendGroups.indexOfFirst { it.id == groupId }
                    if (idx != -1) {
                        val existing = friendGroups[idx]
                        friendGroups[idx] = existing.copy(
                            adminIds = adminIds.filter { it.isNotBlank() && it != existing.effectiveOwnerId() && existing.isMember(it) }.distinct(),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    onComplete(true, null)
                }
                is Result.Error -> onComplete(false, result.message)
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Delete a friend group
     */
    fun reorderFriendGroups(userId: String, orderedGroupIds: List<String>) {
        if (orderedGroupIds.isEmpty()) return
        viewModelScope.launch {
            when (val result = repository.reorderFriendGroups(userId, orderedGroupIds)) {
                is Result.Success -> {
                    val orderedMap = friendGroups.associateBy { it.id }
                    val reordered = orderedGroupIds.mapNotNull { orderedMap[it] }
                    if (reordered.size == friendGroups.size) {
                        friendGroups.clear()
                        friendGroups.addAll(
                            reordered.mapIndexed { index, group ->
                                group.copy(orderIndex = index.toLong(), updatedAt = System.currentTimeMillis())
                            }
                        )
                    }
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun deleteFriendGroup(userId: String, groupId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteFriendGroup(userId, groupId)) {
                is Result.Success -> {
                    friendGroups.removeAll { it.id == groupId }
                    // Reset to All Friends if the deleted group was selected
                    if (selectedFilter is FeedFilter.ByGroup &&
                        (selectedFilter as FeedFilter.ByGroup).group.id == groupId) {
                        selectedFilter = FeedFilter.AllFriends
                        loadFlicks()
                    }
                }
                is Result.Error -> {
                    errorMessage = result.message
                }
                is Result.Loading -> { }
            }
        }
    }

    /**
     * Load explore flicks (all public/friends photos for discovery)
     */
    fun loadExploreFlicks() {
        isLoading = true
        errorMessage = null
        
        repository.getExploreFlicks { result ->
            when (result) {
                is Result.Success -> {
                    exploreFlicks.clear()
                    exploreFlicks.addAll(result.data)
                    isLoading = false
                }
                is Result.Error -> {
                    errorMessage = result.message
                    isLoading = false
                }
                is Result.Loading -> { }
            }
        }
    }

    /**
     * Check daily upload count for a user
     */
    fun checkDailyUploads(userId: String) {
        currentUserId = userId
        repository.getDailyUploadCount(userId) { result ->
            when (result) {
                is Result.Success -> {
                    todayUploadCount = result.data
                }
                is Result.Error -> {
                    // Silently fail - not critical
                }
                is Result.Loading -> { /* Do nothing */ }
            }
        }
    }

    /**
     * Toggle like on a flick (deprecated - use toggleReaction)
     */
    fun toggleLike(flick: Flick, userId: String, userName: String, userPhotoUrl: String) {
        android.util.Log.d("HomeViewModel", "toggleLike called: user=$userId, flick=${flick.id}, owner=${flick.userId}")
        // Map to LIKE reaction
        val userReaction = flick.getUserReaction(userId)
        val newReaction = if (userReaction == ReactionType.LIKE) null else ReactionType.LIKE
        android.util.Log.d("HomeViewModel", "Toggling like: current=$userReaction, new=$newReaction")
        toggleReaction(flick, userId, userName, userPhotoUrl, newReaction)
    }
    
    /**
     * Toggle or update reaction on a flick
     * @param reactionType null = remove reaction
     */
    fun toggleReaction(
        flick: Flick, 
        userId: String, 
        userName: String, 
        userPhotoUrl: String,
        reactionType: ReactionType?
    ) {
        android.util.Log.d("HomeViewModel", "Calling repository.toggleReaction for flick=${flick.id}")
        repository.toggleReaction(flick.id, userId, userName, userPhotoUrl, reactionType) { result ->
            when (result) {
                is Result.Success -> {
                    android.util.Log.d("HomeViewModel", "toggleReaction SUCCESS")
                    // Track reaction analytics
                    reactionType?.let { Analytics.trackReactionSent(it.name) }
                    // Update local state optimistically in BOTH lists
                    val newReactions = flick.reactions.toMutableMap().apply {
                        if (reactionType == null) {
                            remove(userId)
                        } else {
                            put(userId, reactionType.name)
                        }
                    }
                    val updatedFlick = flick.copy(reactions = newReactions.toMap())
                    
                    // Update in flicks list (main feed)
                    val flicksIndex = flicks.indexOfFirst { it.id == flick.id }
                    if (flicksIndex != -1) {
                        flicks[flicksIndex] = updatedFlick
                    }
                    
                    // Update in exploreFlicks list (explore tab)
                    val exploreIndex = exploreFlicks.indexOfFirst { it.id == flick.id }
                    if (exploreIndex != -1) {
                        exploreFlicks[exploreIndex] = updatedFlick
                    }
                }
                is Result.Error -> {
                    android.util.Log.e("HomeViewModel", "toggleReaction FAILED: ${result.message}")
                    errorMessage = result.message
                }
                is Result.Loading -> { /* Do nothing */ }
            }
        }
    }

    /**
     * Search flicks by user name or description
     */
    fun searchFlicks(query: String) {
        if (query.isBlank()) {
            loadFlicks()
            return
        }

        // Track search analytics
        Analytics.trackSearch(query)

        isLoading = true
        val searchQuery = query.lowercase()

        // Filter local flicks
        val filtered = flicks.filter { flick ->
            flick.userName.lowercase().contains(searchQuery) ||
            flick.description.lowercase().contains(searchQuery)
        }

        flicks.clear()
        flicks.addAll(filtered)
        isLoading = false
    }

    /**
     * Search explore flicks by user name, description, or tags
     */
    fun searchExploreFlicks(query: String) {
        if (query.isBlank()) {
            loadExploreFlicks()
            return
        }

        isLoading = true
        val searchQuery = query.lowercase()

        // Filter explore flicks locally
        val filtered = exploreFlicks.filter { flick ->
            flick.userName.lowercase().contains(searchQuery) ||
            flick.description.lowercase().contains(searchQuery) ||
            flick.taggedFriends.any { it.lowercase().contains(searchQuery) }
        }

        exploreFlicks.clear()
        exploreFlicks.addAll(filtered)
        isLoading = false
    }

    /**
     * Clear search and reload all explore flicks
     */
    fun clearExploreSearch() {
        loadExploreFlicks()
    }

    /**
     * Clear search and reload all flicks
     */
    fun clearSearch() {
        loadFlicks()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        errorMessage = null
    }

    fun clearLoadMoreFailure() {
        loadMoreFailureMessage = null
    }

    /**
     * Optimistically add a newly uploading photo to the top of Home feed.
     */
    fun addOptimisticFlick(flick: Flick) {
        val existingOptimisticIndex = optimisticFlicks.indexOfFirst {
            it.id == flick.id || (it.clientUploadId.isNotBlank() && it.clientUploadId == flick.clientUploadId)
        }
        if (existingOptimisticIndex >= 0) {
            optimisticFlicks.removeAt(existingOptimisticIndex)
        }
        optimisticFlicks.add(0, flick)

        val existingIndex = flicks.indexOfFirst {
            it.id == flick.id || (it.clientUploadId.isNotBlank() && it.clientUploadId == flick.clientUploadId)
        }
        if (existingIndex >= 0) {
            flicks.removeAt(existingIndex)
        }
        flicks.add(0, flick)
    }

    /**
     * Remove optimistic photo once upload either succeeds or fails.
     */
    fun removeOptimisticFlick(flickId: String) {
        val optimisticIndex = optimisticFlicks.indexOfFirst { it.id == flickId }
        if (optimisticIndex >= 0) {
            optimisticFlicks.removeAt(optimisticIndex)
        }

        // Keep successful optimistic items on screen until debounced refresh reconciles them.
        // Only remove optimistic row from visible feed when we know it's a failed upload.
        val index = flicks.indexOfFirst { it.id == flickId }
        if (index >= 0) {
            flicks.removeAt(index)
        }
    }

    /**
     * Remove a flick from feed immediately (used for optimistic delete UX).
     */
    fun removeFlickFromFeed(flickId: String) {
        val optimisticIndex = optimisticFlicks.indexOfFirst { it.id == flickId }
        if (optimisticIndex >= 0) {
            optimisticFlicks.removeAt(optimisticIndex)
        }

        val index = flicks.indexOfFirst { it.id == flickId }
        if (index >= 0) {
            flicks.removeAt(index)
        }
    }

    fun requestDebouncedFeedRefresh(userId: String, delayMs: Long = 1400L) {
        currentUserId = userId
        debouncedFeedRefreshJob?.cancel()
        debouncedFeedRefreshJob = viewModelScope.launch {
            delay(delayMs)
            loadFlicks(userId)
        }
    }

    private fun mergeWithOptimistic(serverFlicks: List<Flick>): List<Flick> {
        if (optimisticFlicks.isEmpty()) return serverFlicks

        // Keep optimistic rows as visual owner even after server row exists,
        // and sync them in-place with server data to prevent tile handoff blink.
        val serverByClientUploadId = serverFlicks
            .filter { it.clientUploadId.isNotBlank() }
            .associateBy { it.clientUploadId }

        val syncedOptimistic = optimisticFlicks.map { optimistic ->
            val clientId = optimistic.clientUploadId.takeIf { it.isNotBlank() }
            val serverMatch = if (clientId != null) serverByClientUploadId[clientId] else null

            if (serverMatch != null) {
                val keepLocalDisplayUrl = optimistic.imageUrl.startsWith("content://") || optimistic.imageUrl.startsWith("file://")
                optimistic.copy(
                    id = if (serverMatch.id.isNotBlank()) serverMatch.id else optimistic.id,
                    imageUrl = if (keepLocalDisplayUrl) optimistic.imageUrl else if (serverMatch.imageUrl.isNotBlank()) serverMatch.imageUrl else optimistic.imageUrl,
                    description = serverMatch.description,
                    reactions = serverMatch.reactions,
                    commentCount = serverMatch.commentCount,
                    timestamp = maxOf(optimistic.timestamp, serverMatch.timestamp),
                    userPhotoUrl = serverMatch.userPhotoUrl,
                    taggedFriends = serverMatch.taggedFriends,
                    imageSizeBytes = serverMatch.imageSizeBytes,
                    privacy = serverMatch.privacy
                )
            } else {
                optimistic
            }
        }

        optimisticFlicks.clear()
        optimisticFlicks.addAll(syncedOptimistic)

        val serverIdsCoveredByOptimistic = syncedOptimistic
            .mapNotNull { it.clientUploadId.takeIf { id -> id.isNotBlank() } }
            .toSet()

        val serverRemainder = serverFlicks.filter { server ->
            val clientId = server.clientUploadId.takeIf { it.isNotBlank() }
            clientId == null || !serverIdsCoveredByOptimistic.contains(clientId)
        }

        val merged = (syncedOptimistic + serverRemainder)
        val seenIds = HashSet<String>()
        val seenClientUploadIds = HashSet<String>()

        return merged.filter { flick ->
            val idKey = flick.id
            val clientKey = flick.clientUploadId.takeIf { it.isNotBlank() }

            if (seenIds.contains(idKey)) return@filter false
            if (clientKey != null && seenClientUploadIds.contains(clientKey)) return@filter false

            seenIds.add(idKey)
            if (clientKey != null) seenClientUploadIds.add(clientKey)
            true
        }
    }

    internal suspend fun runUploadWithRetry(
        maxRetries: Int = 3,
        baseDelayMs: Long = 500L,
        uploadCall: suspend () -> Result<String>
    ): Result<String> {
        var result: Result<String> = Result.Error(IllegalStateException("Upload failed"), "Upload failed")

        repeat(maxRetries.coerceAtLeast(1)) { attempt ->
            result = uploadCall()
            val error = result as? Result.Error ?: return result

            val message = error.message.lowercase()
            val isTransient = message.contains("timeout") ||
                message.contains("network") ||
                message.contains("unavailable") ||
                message.contains("tempor")

            if (!isTransient || attempt == maxRetries - 1) {
                return result
            }

            delay(baseDelayMs * (attempt + 1))
        }

        return result
    }

    /**
     * Upload a new flick
     */
    fun uploadFlick(
        userId: String,
        userDisplayName: String,
        userPhotoUrl: String,
        imageUri: android.net.Uri,
        context: android.content.Context,
        caption: String = "",
        privacy: String = "friends",
        taggedFriends: List<String> = emptyList(), // ADDED: Tagged friends
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                // Read image bytes safely
                val imageBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }

                if (imageBytes == null) {
                    errorMessage = "Failed to read image"
                    isLoading = false
                    onComplete(false)
                    return@launch
                }
                
                // Upload image to Firebase Storage with safe retries for transient failures
                val uploadResult = runUploadWithRetry(
                    maxRetries = 3,
                    baseDelayMs = 500L
                ) {
                    repository.uploadFlickImage(userId, imageBytes)
                }
                
                when (uploadResult) {
                    is Result.Success -> {
                        val imageUrl = uploadResult.data
                        
                        // Create flick document with privacy setting and tagged friends
                        val flick = Flick(
                            id = "",
                            userId = userId,
                            userName = userDisplayName,
                            userPhotoUrl = userPhotoUrl,
                            imageUrl = imageUrl,
                            description = caption,
                            timestamp = System.currentTimeMillis(),
                            reactions = emptyMap(),
                            commentCount = 0,
                            privacy = privacy,
                            taggedFriends = taggedFriends // ADDED: Include tagged friends
                        )
                        
                        // Save to Firestore and notify friends
                        val createResult = repository.createFlick(flick, userPhotoUrl)
                        
                        when (createResult) {
                            is Result.Success -> {
                            todayUploadCount++
                            Analytics.trackPhotoUploaded("gallery", privacy)
                            loadFlicks() // Refresh feed
                            isLoading = false
                            onComplete(true)
                        }
                            is Result.Error -> {
                                errorMessage = createResult.message
                                isLoading = false
                                onComplete(false)
                            }
                            is Result.Loading -> { }
                        }
                    }
                    is Result.Error -> {
                        errorMessage = uploadResult.message
                        isLoading = false
                        onComplete(false)
                    }
                    is Result.Loading -> { }
                }
            } catch (_: IOException) {
                errorMessage = "Upload failed: network error. Please retry."
                isLoading = false
                onComplete(false)
            } catch (e: Exception) {
                errorMessage = "Upload failed: ${e.message}"
                isLoading = false
                onComplete(false)
            }
        }
    }
}
