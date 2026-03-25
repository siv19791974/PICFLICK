package com.picflick.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.ui.graphics.RectangleShape
import android.content.res.Configuration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.picflick.app.data.ChatMessage
import com.picflick.app.data.Flick
import com.picflick.app.data.ReactionType
import com.picflick.app.data.UserProfile
import com.picflick.app.data.toEmoji
import com.picflick.app.repository.ChatRepository
import com.picflick.app.ui.components.AnimatedReactionPicker
import com.picflick.app.ui.components.ErrorMessage
import com.picflick.app.ui.components.PhotoGridShimmer
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.picflick.app.util.withCacheBust
import com.picflick.app.viewmodel.HomeViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Home screen with photo grid and bottom navigation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    userProfile: UserProfile,
    viewModel: HomeViewModel,
    resetToTopVersion: Int = 0,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
    onUserProfileClick: (String) -> Unit = {},
    friends: List<UserProfile> = emptyList(), // Friends list for profile picture lookup
    onEditPhotoClick: (Flick) -> Unit = {} // Navigate to edit photo screen
) {
    val context = LocalContext.current
    var showUploadDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFlick by remember { mutableStateOf<Flick?>(null) }
    var selectedFlickIndex by remember { mutableIntStateOf(0) }
    var privacySetting by remember { mutableStateOf("friends") } // "friends" or "public"
    var taggedFriends by remember { mutableStateOf<List<String>>(emptyList()) } // ADDED: Tagged friends
    var showShareToChatDialog by remember { mutableStateOf(false) }
    var flickToShare by remember { mutableStateOf<Flick?>(null) }
    var isSharingPhoto by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository() }
    val currentUserPhotoUrl = rememberLiveUserPhotoUrl(userProfile.uid, userProfile.photoUrl)
    val currentUserWithLivePhoto = remember(userProfile, currentUserPhotoUrl) {
        userProfile.copy(photoUrl = if (currentUserPhotoUrl.isNotBlank()) currentUserPhotoUrl else userProfile.photoUrl)
    }
    val liveFriendProfiles = friends.associate { friend ->
        val livePhoto = rememberLiveUserPhotoUrl(friend.uid, friend.photoUrl)
        friend.uid to friend.copy(photoUrl = if (livePhoto.isNotBlank()) livePhoto else friend.photoUrl)
    }
    
    // Reaction picker state
    var showReactionPicker by remember { mutableStateOf(false) }
    var flickForReaction by remember { mutableStateOf<Flick?>(null) }
    
    // Flying reaction animation state
    var flyingReaction by remember { mutableStateOf<Pair<ReactionType, Int>?>(null) }

    // Load data
    LaunchedEffect(userProfile.uid) {
        viewModel.loadFlicks(userProfile.uid)
        viewModel.loadFriendGroups(userProfile.uid)
    }

    // External reset trigger (e.g., app foreground): refresh newest feed and return to top.
    LaunchedEffect(resetToTopVersion) {
        if (resetToTopVersion > 0) {
            viewModel.loadFlicks(userProfile.uid)
        }
    }

    // Diagnostic toast: when fullscreen/list near-end load-more fails, surface it immediately
    val loadMoreFailureVersion = viewModel.loadMoreFailureVersion
    LaunchedEffect(loadMoreFailureVersion) {
        val msg = viewModel.loadMoreFailureMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, "Load-more failed: $msg", Toast.LENGTH_SHORT).show()
            viewModel.clearLoadMoreFailure()
        }
    }

    LaunchedEffect(userProfile.uid) {
        viewModel.checkDailyUploads(userProfile.uid)
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            showUploadDialog = false
            viewModel.uploadFlick(
                userId = userProfile.uid,
                userDisplayName = userProfile.displayName,
                userPhotoUrl = currentUserWithLivePhoto.photoUrl,
                imageUri = tempCameraUri!!,
                context = context,
                privacy = privacySetting,
                taggedFriends = taggedFriends, // ADDED: Pass tagged friends
                onComplete = { success ->
                    if (success) {
                        viewModel.checkDailyUploads(userProfile.uid)
                    }
                }
            )
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            showUploadDialog = false
            viewModel.uploadFlick(
                userId = userProfile.uid,
                userDisplayName = userProfile.displayName,
                userPhotoUrl = currentUserWithLivePhoto.photoUrl,
                imageUri = it,
                context = context,
                privacy = privacySetting,
                onComplete = { success ->
                    if (success) {
                        viewModel.checkDailyUploads(userProfile.uid)
                    }
                }
            )
        }
    }

    val isDarkMode = ThemeManager.isDarkMode.value
    var isFeedAtTop by remember { mutableStateOf(true) }

    // Column WITHOUT verticalScroll (because LazyVerticalGrid has its own scroll)
    // NO BANNER HERE - banner is now in MainActivity's Scaffold topBar!
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(isDarkModeBackground(isDarkMode)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Modern PullRefresh content
        val pullRefreshState = rememberPullRefreshState(
            refreshing = viewModel.isLoading,
            onRefresh = { 
                viewModel.loadFlicks(userProfile.uid)
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(
                    state = pullRefreshState,
                    enabled = isFeedAtTop && !viewModel.isLoadingMore
                )
        ) {
            when {
                viewModel.isLoading && viewModel.flicks.isEmpty() -> PhotoGridShimmer()
                viewModel.errorMessage != null -> ErrorMessage(
                    message = viewModel.errorMessage ?: "Unknown error",
                    onRetry = { viewModel.loadFlicks(userProfile.uid) }
                )
                viewModel.flicks.isEmpty() -> EmptyState()
                else -> FlickGrid(
                    flicks = viewModel.flicks,
                    userProfile = userProfile,
                    resetToTopVersion = resetToTopVersion,
                    onPhotoClick = { flick ->
                        selectedFlick = flick
                        selectedFlickIndex = viewModel.flicks.indexOf(flick)
                    },
                    onLongPress = { flick ->
                        // Only allow reacting to OTHER people's photos
                        if (flick.userId != userProfile.uid) {
                            flickForReaction = flick
                            showReactionPicker = true
                        }
                    },
                    isLoadingMore = viewModel.isLoadingMore,
                    canLoadMore = viewModel.canLoadMore,
                    onLoadMore = { viewModel.loadMoreFlicks() },
                    onIsAtTopChanged = { isAtTop ->
                        isFeedAtTop = isAtTop
                    }
                )
            }

            // Modern PullRefreshIndicator
            PullRefreshIndicator(
                refreshing = viewModel.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )

            // Upload mini FABs overlay
            if (showUploadDialog) {
                UploadOverlay(
                    onDismiss = { showUploadDialog = false },
                    onCameraClick = {
                        val photoFile = File(
                            context.cacheDir,
                            "photo_${System.currentTimeMillis()}.jpg"
                        )
                        tempCameraUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        cameraLauncher.launch(tempCameraUri!!)
                        showUploadDialog = false
                    },
                    onGalleryClick = {
                        galleryLauncher.launch("image/*")
                        showUploadDialog = false
                    },
                    privacySetting = privacySetting,
                    onPrivacyChange = { privacySetting = it }
                )
            }
        }
    }

    // Full-screen photo viewer with comments
    selectedFlick?.let { flick ->
        FullScreenPhotoViewer(
            flick = flick,
            currentUser = currentUserWithLivePhoto,
            onDismiss = { selectedFlick = null },
            onReaction = { reactionType ->
                // Get the CURRENT flick based on selected index (not the stale flick reference)
                val currentFlick = viewModel.flicks.getOrNull(selectedFlickIndex) ?: flick
                // Handle reaction via ViewModel
                viewModel.toggleReaction(currentFlick, userProfile.uid, userProfile.displayName, currentUserWithLivePhoto.photoUrl, reactionType)
                // IMMEDIATELY update selectedFlick with the latest data from ViewModel
                // This ensures the UI shows the reaction immediately
                val updatedFlick = viewModel.flicks.getOrNull(selectedFlickIndex)
                if (updatedFlick != null) {
                    selectedFlick = updatedFlick
                }
            },
            onShareClick = {
                flickToShare = flick
                showShareToChatDialog = true
            },
            canDelete = flick.userId == userProfile.uid,
            onDeleteClick = {
                // Optimistic immediate removal. Avoid immediate reload to prevent deleted flick bounce-back.
                viewModel.removeFlickFromFeed(flick.id)
            },
            allPhotos = viewModel.flicks,
            currentIndex = selectedFlickIndex,
            onNavigateToPhoto = { index ->
                // Keep loading more while swiping near the end so very long sessions continue.
                val remaining = viewModel.flicks.size - 1 - index
                if (remaining <= 2 && !viewModel.isLoadingMore) {
                    viewModel.loadMoreFlicks()
                }

                // CRITICAL: never null-dismiss fullscreen when boundary index arrives before append.
                if (index in viewModel.flicks.indices) {
                    selectedFlickIndex = index
                    selectedFlick = viewModel.flicks[index]
                } else {
                    // Keep current photo visible until the new page appends.
                    selectedFlickIndex = index.coerceAtMost((viewModel.flicks.size - 1).coerceAtLeast(0))
                }
            },
            onUserProfileClick = { userId ->
                onUserProfileClick(userId)
            },
            onShareToFriend = { flickId, friendId ->
                val flickToSend = viewModel.flicks.firstOrNull { it.id == flickId } ?: flick
                if (flickToSend.imageUrl.isBlank()) {
                    Toast.makeText(context, "Photo unavailable to share", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        val friendProfile = liveFriendProfiles[friendId]
                        val friendName = friendProfile?.displayName?.ifBlank { "Friend" } ?: "Friend"
                        val friendPhoto = friendProfile?.photoUrl ?: ""
                        when (val sessionResult = chatRepository.getOrCreateChatSession(
                            userId1 = userProfile.uid,
                            userId2 = friendId,
                            user1Name = userProfile.displayName,
                            user2Name = friendName,
                            user1Photo = currentUserWithLivePhoto.photoUrl,
                            user2Photo = friendPhoto
                        )) {
                            is com.picflick.app.data.Result.Success -> {
                                val message = ChatMessage(
                                    chatId = sessionResult.data,
                                    senderId = userProfile.uid,
                                    senderName = userProfile.displayName,
                                    senderPhotoUrl = currentUserWithLivePhoto.photoUrl,
                                    text = "",
                                    imageUrl = flickToSend.imageUrl,
                                    flickId = flickToSend.id,
                                    timestamp = System.currentTimeMillis(),
                                    read = false,
                                    delivered = false
                                )
                                when (val sendResult = chatRepository.sendMessage(sessionResult.data, message, friendId)) {
                                    is com.picflick.app.data.Result.Success -> Toast.makeText(context, "Photo sent", Toast.LENGTH_SHORT).show()
                                    is com.picflick.app.data.Result.Error -> Toast.makeText(context, sendResult.message, Toast.LENGTH_SHORT).show()
                                    is com.picflick.app.data.Result.Loading -> Unit
                                }
                            }
                            is com.picflick.app.data.Result.Error -> Toast.makeText(context, sessionResult.message, Toast.LENGTH_SHORT).show()
                            is com.picflick.app.data.Result.Loading -> Unit
                        }
                    }
                }
            },
            friendProfiles = liveFriendProfiles,
            onEditPhotoClick = { flick ->
                onEditPhotoClick(flick)
            }
        )
    }

    if (showShareToChatDialog && flickToShare != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isSharingPhoto) {
                    showShareToChatDialog = false
                    flickToShare = null
                }
            },
            title = { Text("Share to chat") },
            text = {
                if (liveFriendProfiles.isEmpty()) {
                    Text("No friends available to share with yet.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        liveFriendProfiles.values.forEach { friend ->
                            OutlinedButton(
                                onClick = {
                                    val photo = flickToShare ?: return@OutlinedButton
                                    scope.launch {
                                        isSharingPhoto = true
                                        val chatRepository = com.picflick.app.repository.ChatRepository()
                                        when (val chatResult = chatRepository.getOrCreateChatSession(
                                            userId1 = userProfile.uid,
                                            userId2 = friend.uid,
                                            user1Name = userProfile.displayName,
                                            user2Name = friend.displayName,
                                            user1Photo = currentUserWithLivePhoto.photoUrl,
                                            user2Photo = friend.photoUrl
                                        )) {
                                            is com.picflick.app.data.Result.Success -> {
                                                val message = com.picflick.app.data.ChatMessage(
                                                    chatId = chatResult.data,
                                                    senderId = userProfile.uid,
                                                    senderName = userProfile.displayName,
                                                    senderPhotoUrl = currentUserWithLivePhoto.photoUrl,
                                                    text = "",
                                                    imageUrl = photo.imageUrl,
                                                    timestamp = System.currentTimeMillis(),
                                                    read = false,
                                                    delivered = false
                                                )
                                                when (val sendResult = chatRepository.sendMessage(chatResult.data, message, friend.uid)) {
                                                    is com.picflick.app.data.Result.Success -> {
                                                        Toast.makeText(context, "Photo shared to ${friend.displayName}", Toast.LENGTH_SHORT).show()
                                                        showShareToChatDialog = false
                                                        flickToShare = null
                                                    }
                                                    is com.picflick.app.data.Result.Error -> {
                                                        Toast.makeText(context, sendResult.message, Toast.LENGTH_SHORT).show()
                                                    }
                                                    else -> Unit
                                                }
                                            }
                                            is com.picflick.app.data.Result.Error -> {
                                                Toast.makeText(context, chatResult.message, Toast.LENGTH_SHORT).show()
                                            }
                                            else -> Unit
                                        }
                                        isSharingPhoto = false
                                    }
                                },
                                enabled = !isSharingPhoto,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(friend.displayName)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isSharingPhoto) {
                            showShareToChatDialog = false
                            flickToShare = null
                        }
                    }
                ) {
                    Text(if (isSharingPhoto) "Sharing..." else "Cancel")
                }
            }
        )
    }

    // Animated Reaction Picker Dialog for long press
    if (showReactionPicker && flickForReaction != null) {
        AnimatedReactionPicker(
            onDismiss = {
                showReactionPicker = false
                flickForReaction = null
            },
            onReactionSelected = { reactionType ->
                // Only show flying animation if adding a new reaction (not removing)
                if (reactionType != null) {
                    val index = viewModel.flicks.indexOfFirst { it.id == flickForReaction?.id }
                    if (index != -1) {
                        flyingReaction = reactionType to index
                    }
                }
                // Handle reaction via ViewModel (null = remove reaction)
                viewModel.toggleReaction(
                    flickForReaction!!,
                    userProfile.uid,
                    userProfile.displayName,
                    currentUserWithLivePhoto.photoUrl,
                    reactionType
                )
                showReactionPicker = false
                flickForReaction = null
            },
            currentReaction = flickForReaction?.getUserReaction(userProfile.uid)
        )
    }

    // Flying reaction animation overlay
    flyingReaction?.let { (reaction, targetIndex) ->
        FlyingReactionAnimation(
            reaction = reaction,
            targetIndex = targetIndex,
            onAnimationEnd = { flyingReaction = null }
        )
    }
}

@Composable
private fun UploadOverlay(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    privacySetting: String = "friends",
    onPrivacyChange: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Upload Photo",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Camera option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onCameraClick() }
                ) {
                    FloatingActionButton(
                        onClick = onCameraClick,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Camera",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(
                        text = "Camera",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                
                // Gallery option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onGalleryClick() }
                ) {
                    FloatingActionButton(
                        onClick = onGalleryClick,
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Gallery",
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                    Text(
                        text = "Gallery",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Privacy Toggle
            Text(
                text = "Who can see this?",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Friends only option
                FilterChip(
                    selected = privacySetting == "friends",
                    onClick = { onPrivacyChange("friends") },
                    label = { Text("Friends Only") },
                    leadingIcon = if (privacySetting == "friends") {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
                
                // Public option
                FilterChip(
                    selected = privacySetting == "public",
                    onClick = { onPrivacyChange("public") },
                    label = { Text("Public") },
                    leadingIcon = if (privacySetting == "public") {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (privacySetting == "friends") 
                    "Only your friends will see this photo" 
                else 
                    "Everyone can see this photo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun FlickGrid(
    flicks: List<Flick>,
    userProfile: UserProfile,
    resetToTopVersion: Int = 0,
    onPhotoClick: (Flick) -> Unit,
    onLongPress: (Flick) -> Unit,
    isLoadingMore: Boolean = false,
    canLoadMore: Boolean = true,
    onLoadMore: () -> Unit = {},
    onIsAtTopChanged: (Boolean) -> Unit = {}
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Portrait: ~4 rows visible; Landscape: ~1 row visible (3 photos total)
        val rowHeight = if (isLandscape) {
            this.maxHeight / 1.1f
        } else {
            this.maxHeight / 4.1f
        }

        // Track scroll position for infinite scroll
        val listState = rememberLazyGridState()
        val context = LocalContext.current
        val prefetchImageLoader = remember(context) { ImageLoader.Builder(context).build() }
        val prefetchedFlickIds = remember { mutableSetOf<String>() }
        var hasRequestedForCurrentSize by remember { mutableStateOf(false) }

        // Reset load-more request guard when item count changes (new page appended/refreshed)
        LaunchedEffect(flicks.size) {
            hasRequestedForCurrentSize = false
            if (prefetchedFlickIds.size > flicks.size + 60) {
                prefetchedFlickIds.clear()
            }
        }

        // Notify parent whether feed is exactly at top (for top-only pull-to-refresh)
        LaunchedEffect(listState) {
            snapshotFlow {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
                .distinctUntilChanged()
                .collect { atTop -> onIsAtTopChanged(atTop) }
        }

        // External reset: snap to first (newest) photo.
        LaunchedEffect(resetToTopVersion) {
            if (resetToTopVersion > 0) {
                listState.scrollToItem(0)
                onIsAtTopChanged(true)
            }
        }

        // Prefetch upcoming images just beyond current viewport
        LaunchedEffect(listState, flicks) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .distinctUntilChanged()
                .collect { lastVisibleIndex ->
                    if (lastVisibleIndex < 0 || flicks.isEmpty()) return@collect

                    val start = (lastVisibleIndex + 1).coerceAtMost(flicks.lastIndex)
                    val end = (lastVisibleIndex + 9).coerceAtMost(flicks.lastIndex)
                    if (start > end) return@collect

                    for (index in start..end) {
                        val flick = flicks[index]
                        if (!prefetchedFlickIds.add(flick.id)) continue

                        val request = ImageRequest.Builder(context)
                            .data(withCacheBust(flick.imageUrl, flick.timestamp))
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                        prefetchImageLoader.enqueue(request)
                    }
                }
        }

        // Detect near-bottom with debounce + distinct change to reduce trigger churn while flinging
        LaunchedEffect(listState, flicks.size, isLoadingMore, canLoadMore) {
            snapshotFlow {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val thresholdIndex = (flicks.size - 5).coerceAtLeast(0)
                lastVisibleIndex >= thresholdIndex
            }
                .distinctUntilChanged()
                .collect { isNearBottom ->
                    if (isNearBottom && !isLoadingMore && canLoadMore && !hasRequestedForCurrentSize) {
                        delay(120)
                        val latestLastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        val latestThreshold = (flicks.size - 5).coerceAtLeast(0)
                        val stillNearBottom = latestLastVisibleIndex >= latestThreshold
                        if (stillNearBottom && !hasRequestedForCurrentSize) {
                            hasRequestedForCurrentSize = true
                            onLoadMore()
                        }
                    }
                }
        }

        LazyVerticalGrid(
            state = listState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 1.dp,
                end = 1.dp,
                top = 4.dp,  // Slight top gap to match bottom
                bottom = if (isLoadingMore) 80.dp else 2.dp // Avoid large empty scroll gap when not loading
            ),
            userScrollEnabled = true // Enable scrolling for pull-to-refresh
        ) {
            // Show all items (scrollable)
            items(
                items = flicks,
                key = { it.id },
                contentType = { "flick" }
            ) { flick ->
                FlickCard(
                    flick = flick,
                    userId = userProfile.uid,
                    onPhotoClick = { onPhotoClick(flick) },
                    onLongPress = { onLongPress(flick) },
                    rowHeight = rowHeight
                )
            }
            
            // Loading more indicator at bottom
            if (isLoadingMore) {
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlickCard(
    flick: Flick,
    userId: String,
    onPhotoClick: () -> Unit,
    onLongPress: () -> Unit,
    rowHeight: androidx.compose.ui.unit.Dp
) {
    // Keep derived reaction display stable per reaction map change
    val topReactionDisplay = remember(flick.reactions) {
        val reactionCounts = flick.getReactionCounts()
        val topReaction = reactionCounts.maxByOrNull { it.value }
        (topReaction?.key?.toEmoji() ?: "❤️") to (topReaction?.value ?: 0)
    }
    val topReactionEmoji = topReactionDisplay.first
    val topReactionCount = topReactionDisplay.second

    Card(
        modifier = Modifier
            .padding(1.dp) // Smaller padding
            .height(rowHeight) // Fixed height for exact 4 rows
            .combinedClickable(
                onClick = { onPhotoClick() },
                onLongClick = { onLongPress() }
            ),
        shape = RectangleShape, // NO rounded corners
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // No elevation
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent // Transparent background
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Photo
            AsyncImage(
                model = withCacheBust(flick.imageUrl, flick.timestamp),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(), // Fill the exact height
                contentScale = ContentScale.Crop
            )
            
            // Info overlay at bottom (username + reactions)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(color = Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val overlayTextStyle = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Username
                    Text(
                        text = flick.userName,
                        color = Color.White,
                        style = overlayTextStyle.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    // Right: fixed-width slot keeps banner stable
                    Box(
                        modifier = Modifier.width(36.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (topReactionCount > 0) {
                            Text(
                                text = "$topReactionEmoji $topReactionCount",
                                style = overlayTextStyle,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No photos yet. Upload one!",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Flying reaction animation - animates emoji from center to target photo
 */
@Composable
private fun FlyingReactionAnimation(
    reaction: ReactionType,
    targetIndex: Int,
    onAnimationEnd: () -> Unit
) {
    val emoji = reaction.toEmoji()
    
    // Calculate target position (3-column grid)
    val column = targetIndex % 3
    val row = targetIndex / 3
    
    // Screen-relative positions (estimates for 3-column grid)
    val targetX = when (column) {
        0 -> -0.25f
        1 -> 0f
        2 -> 0.25f
        else -> 0f
    }
    val targetY = when (row) {
        0 -> -0.15f
        1 -> 0.05f
        2 -> 0.25f
        3 -> 0.45f
        else -> 0f
    }
    
    var animationStarted by remember { mutableStateOf(false) }
    
    val offsetX by animateFloatAsState(
        targetValue = if (animationStarted) targetX else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "fly_x"
    )
    
    val offsetY by animateFloatAsState(
        targetValue = if (animationStarted) targetY else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        finishedListener = { onAnimationEnd() },
        label = "fly_y"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (animationStarted) 0.5f else 2f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "fly_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (animationStarted) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "fly_alpha"
    )
    
    LaunchedEffect(Unit) {
        delay(50)
        animationStarted = true
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 48.sp,
            modifier = Modifier
                .graphicsLayer {
                    translationX = offsetX * size.width
                    translationY = offsetY * size.height
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
        )
    }
}

/**
 * Heart like animation that shows when double-tapping a photo
 * Shows ❤️ for like, 🤍 for unlike
 */
@Composable
private fun LikeAnimation(
    isUnlike: Boolean = false,
    onAnimationComplete: () -> Unit
) {
    var animationStarted by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (animationStarted) 1.5f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "like_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (animationStarted) 0f else 1f,
        animationSpec = tween(durationMillis = 400, delayMillis = 200),
        finishedListener = { onAnimationComplete() },
        label = "like_alpha"
    )
    
    LaunchedEffect(Unit) {
        animationStarted = true
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isUnlike) "🤍" else "❤️", // White heart for unlike, red for like
            fontSize = 80.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
        )
    }
}
