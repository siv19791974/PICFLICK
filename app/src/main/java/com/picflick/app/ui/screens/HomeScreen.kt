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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.picflick.app.Constants
import com.picflick.app.data.ChatMessage
import com.picflick.app.data.FeedFilter
import com.picflick.app.data.Flick
import com.picflick.app.data.FriendGroup
import com.picflick.app.data.ReactionType
import com.picflick.app.data.UserProfile
import com.picflick.app.data.toEmoji
import com.picflick.app.repository.ChatRepository
import com.picflick.app.ui.components.AnimatedReactionPicker
import com.picflick.app.ui.components.ErrorMessage
import com.picflick.app.ui.components.PhotoGridShimmer
import com.picflick.app.ui.theme.PicFlickLightBackground
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.ui.theme.ThemeManager
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.picflick.app.util.rememberLiveUserTierColor
import com.picflick.app.util.withCacheBust
import java.util.Locale
import com.picflick.app.utils.Analytics
import com.picflick.app.viewmodel.HomeViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Home screen with photo grid and bottom navigation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
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
    var showGroupsManager by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<FriendGroup?>(null) }
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
                taggedFriends = taggedFriends, // ADDED: Tagged friends
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
            IconButton(
                onClick = { showGroupsManager = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp)
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoAlbum,
                    contentDescription = "Groups",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

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
                                    is com.picflick.app.data.Result.Success -> {
                                        Analytics.trackPhotoShared()
                                        Toast.makeText(context, "Photo sent", Toast.LENGTH_SHORT).show()
                                    }
                                    is com.picflick.app.data.Result.Error -> {
                                        Analytics.trackError("share_to_friend_failed")
                                        Toast.makeText(context, sendResult.message, Toast.LENGTH_SHORT).show()
                                    }
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
                                                        Analytics.trackPhotoShared()
                                                        Toast.makeText(context, "Photo shared to ${friend.displayName}", Toast.LENGTH_SHORT).show()
                                                        showShareToChatDialog = false
                                                        flickToShare = null
                                                    }
                                                    is com.picflick.app.data.Result.Error -> {
                                                        Analytics.trackError("share_to_chat_failed")
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

    if (showGroupsManager) {
        GroupManagerSheet(
            groups = viewModel.friendGroups,
            selectedFilter = viewModel.selectedFilter,
            friends = friends,
            isDarkMode = isDarkMode,
            onDismiss = { showGroupsManager = false },
            onSelectGroup = { filter ->
                viewModel.setFilter(filter)
                showGroupsManager = false
            },
            onCreateGroup = { showCreateGroupDialog = true },
            onEditGroup = { group -> editingGroup = group },
            onDeleteGroup = { group -> viewModel.deleteFriendGroup(userProfile.uid, group.id) }
        )
    }

    if (showCreateGroupDialog) {
        CreateOrEditGroupDialog(
            title = "Create group",
            submitLabel = "Create group",
            friends = friends,
            isDarkMode = isDarkMode,
            initialName = "",
            initialIcon = "👥",
            initialColor = "#4FC3F7",
            initialSelectedFriendIds = emptyList(),
            onDismiss = { showCreateGroupDialog = false },
            onSubmit = { name, icon, selectedFriendIds, color ->
                viewModel.createFriendGroup(
                    userId = userProfile.uid,
                    name = name,
                    icon = icon,
                    friendIds = selectedFriendIds,
                    color = color
                ) { success ->
                    if (success) {
                        showCreateGroupDialog = false
                    }
                }
            }
        )
    }

    editingGroup?.let { group ->
        CreateOrEditGroupDialog(
            title = "Edit group",
            submitLabel = "Save",
            friends = friends,
            isDarkMode = isDarkMode,
            initialName = group.name,
            initialIcon = group.icon,
            initialColor = group.color,
            initialSelectedFriendIds = group.friendIds,
            onDismiss = { editingGroup = null },
            onSubmit = { name, icon, selectedFriendIds, color ->
                viewModel.updateFriendGroup(
                    userId = userProfile.uid,
                    groupId = group.id,
                    name = name,
                    icon = icon,
                    friendIds = selectedFriendIds,
                    color = color
                ) { success ->
                    if (success) {
                        editingGroup = null
                    }
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
private fun GroupManagerSheet(
    groups: List<FriendGroup>,
    selectedFilter: FeedFilter,
    friends: List<UserProfile>,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onSelectGroup: (FeedFilter) -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (FriendGroup) -> Unit,
    onDeleteGroup: (FriendGroup) -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF121212) else PicFlickLightBackground
    val rowColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color(0xFF111111)
    val sortedGroups = remember(groups) { groups.sortedBy { it.name.lowercase(Locale.getDefault()) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .align(Alignment.BottomCenter)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = backgroundColor,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Groups",
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onCreateGroup) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add group")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                        }
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        val isSelected = selectedFilter is FeedFilter.AllFriends
                        GroupRowCard(
                            title = "All friends",
                            subtitle = "${friends.size} friends",
                            icon = "🏠",
                            selected = isSelected,
                            rowColor = rowColor,
                            onClick = { onSelectGroup(FeedFilter.AllFriends) },
                            textColor = textColor,
                            trailingContent = {}
                        )
                    }

                    items(sortedGroups, key = { it.id }) { group ->
                        val isSelected = selectedFilter is FeedFilter.ByGroup && selectedFilter.group.id == group.id
                        GroupRowCard(
                            title = group.name,
                            subtitle = "${group.friendIds.size} friends",
                            icon = group.icon,
                            selected = isSelected,
                            rowColor = rowColor,
                            onClick = { onSelectGroup(FeedFilter.ByGroup(group)) },
                            textColor = textColor,
                            trailingContent = {
                                IconButton(onClick = { onEditGroup(group) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit group", tint = textColor)
                                }
                                IconButton(onClick = { onDeleteGroup(group) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete group", tint = Color(0xFFD84343))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRowCard(
    title: String,
    subtitle: String,
    icon: String,
    selected: Boolean,
    rowColor: Color,
    onClick: () -> Unit,
    textColor: Color,
    trailingContent: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = rowColor,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = textColor, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Row(content = trailingContent)
        }
    }
}

@Composable
private fun CreateOrEditGroupDialog(
    title: String,
    submitLabel: String,
    friends: List<UserProfile>,
    isDarkMode: Boolean,
    initialName: String,
    initialIcon: String,
    initialColor: String,
    initialSelectedFriendIds: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (name: String, icon: String, selectedFriendIds: List<String>, color: String) -> Unit
) {
    var groupName by remember(initialName) { mutableStateOf(initialName) }
    var selectedIcon by remember(initialIcon) { mutableStateOf(initialIcon) }
    var selectedColor by remember(initialColor) { mutableStateOf(initialColor) }
    var selectedFriends by remember(initialSelectedFriendIds) { mutableStateOf(initialSelectedFriendIds.toSet()) }

    val icons = listOf("👥", "👨‍👩‍👧‍👦", "💼", "🎓", "⭐", "✈️", "⚽", "🎨", "🏠", "🎵", "📚", "🎮")
    val colors = listOf("#4FC3F7", "#FF6B6B", "#4CAF50", "#FFD93D", "#FF9F43", "#A55EEA", "#FF6B9D", "#26D0CE")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Group name") }
                )

                Text("Icon", fontWeight = FontWeight.SemiBold)
                LazyColumn(modifier = Modifier.heightIn(max = 64.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            icons.forEach { icon ->
                                Surface(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clickable { selectedIcon = icon },
                                    shape = CircleShape,
                                    color = if (selectedIcon == icon) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF1F1F1),
                                    border = if (selectedIcon == icon) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Box(contentAlignment = Alignment.Center) { Text(icon) }
                                }
                            }
                        }
                    }
                }

                Text("Color", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { colorHex ->
                        val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (_: Exception) { Color.Gray }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (selectedColor == colorHex) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }

                Text("Members", fontWeight = FontWeight.SemiBold)
                LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                    items(friends, key = { it.uid }) { friend ->
                        val isSelected = selectedFriends.contains(friend.uid)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFriends = if (isSelected) selectedFriends - friend.uid else selectedFriends + friend.uid
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    selectedFriends = if (it) selectedFriends + friend.uid else selectedFriends - friend.uid
                                }
                            )
                            Text(friend.displayName)
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                enabled = groupName.isNotBlank(),
                onClick = {
                    onSubmit(
                        groupName.trim(),
                        selectedIcon,
                        selectedFriends.toList(),
                        selectedColor
                    )
                }
            ) {
                Text(submitLabel)
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
            this.maxHeight / 4.08f
        }

        // Track scroll position for infinite scroll
        val listState = rememberLazyGridState()
        val context = LocalContext.current
        val prefetchImageLoader = remember(context) { ImageLoader.Builder(context).build() }
        val prefetchedFlickIds = remember { mutableSetOf<String>() }
        var hasRequestedForCurrentSize by remember { mutableStateOf(false) }
        val optimisticImageBridge: SnapshotStateMap<String, String> = remember { mutableStateMapOf() }
        val optimisticBridgeLastSeenAt: SnapshotStateMap<String, Long> = remember { mutableStateMapOf() }

        // Reset load-more request guard when item count changes (new page appended/refreshed)
        LaunchedEffect(flicks.size) {
            hasRequestedForCurrentSize = false
        }

        // Observe scroll to know if at top (for pull-to-refresh enable/disable)
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
                .distinctUntilChanged()
                .collect { atTop -> onIsAtTopChanged(atTop) }
        }

        // Infinite-scroll trigger when nearing bottom
        LaunchedEffect(listState, flicks.size, isLoadingMore, canLoadMore) {
            if (!canLoadMore || flicks.isEmpty()) return@LaunchedEffect
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .distinctUntilChanged()
                .collect { lastVisibleIndex ->
                    val threshold = 6
                    val triggerIndex = (flicks.size - threshold).coerceAtLeast(0)
                    if (!isLoadingMore && !hasRequestedForCurrentSize && lastVisibleIndex >= triggerIndex) {
                        hasRequestedForCurrentSize = true
                        onLoadMore()
                    }
                }
        }

        // Prefetch next images ahead of viewport
        LaunchedEffect(listState, flicks.size) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .distinctUntilChanged()
                .collect { lastVisibleIndex ->
                    if (lastVisibleIndex < 0 || flicks.isEmpty()) return@collect
                    val start = (lastVisibleIndex + 1).coerceAtMost(flicks.lastIndex)
                    val end = (lastVisibleIndex + 8).coerceAtMost(flicks.lastIndex)
                    if (start > end) return@collect
                    for (i in start..end) {
                        val flick = flicks[i]
                        if (flick.id.isBlank() || flick.imageUrl.isBlank() || prefetchedFlickIds.contains(flick.id)) continue
                        prefetchedFlickIds.add(flick.id)
                        val request = ImageRequest.Builder(context)
                            .data(withCacheBust(flick.imageUrl, flick.timestamp))
                            .crossfade(false)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                        prefetchImageLoader.enqueue(request)
                    }
                }
        }

        // Prevent stale optimistic bridge entries from lingering forever
        LaunchedEffect(Unit) {
            while (true) {
                delay(20_000)
                val now = System.currentTimeMillis()
                val ttlMs = 10 * 60 * 1000L
                val toRemove = optimisticBridgeLastSeenAt.filterValues { now - it > ttlMs }.keys
                toRemove.forEach {
                    optimisticBridgeLastSeenAt.remove(it)
                    optimisticImageBridge.remove(it)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(flicks, key = { flick -> if (flick.id.isNotBlank()) flick.id else "flick_${flick.timestamp}" }) { flick ->
                // Robust image model to avoid loading-spinner stuck for fresh uploads
                val cacheBusted = withCacheBust(flick.imageUrl, flick.timestamp)
                val imageModel = remember(flick.id, cacheBusted) {
                    if (cacheBusted.isNotBlank()) {
                        ImageRequest.Builder(context)
                            .data(cacheBusted)
                            .crossfade(false)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                    } else null
                }

                // Optimistic bridge: if imageUrl temporarily blank while Firestore catches up, keep last known URL
                val bridgedModel: Any? = when {
                    imageModel != null -> {
                        optimisticImageBridge[flick.id] = cacheBusted
                        optimisticBridgeLastSeenAt[flick.id] = System.currentTimeMillis()
                        imageModel
                    }
                    flick.id.isNotBlank() && !optimisticImageBridge[flick.id].isNullOrBlank() -> {
                        optimisticBridgeLastSeenAt[flick.id] = System.currentTimeMillis()
                        ImageRequest.Builder(context)
                            .data(optimisticImageBridge[flick.id]!!)
                            .crossfade(false)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    else -> null
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .combinedClickable(
                            onClick = { onPhotoClick(flick) },
                            onLongClick = { onLongPress(flick) }
                        )
                ) {
                    // Determine if this image URL likely points to a local/temp source
                    val isLocalSource = flick.imageUrl.startsWith("content://") ||
                            flick.imageUrl.startsWith("file://") ||
                            (!flick.imageUrl.startsWith("http://") && !flick.imageUrl.startsWith("https://"))

                    // Track painter state to detect stuck loading and switch to robust fallback
                    var loadingStartMs by remember(flick.id, flick.imageUrl) { mutableLongStateOf(0L) }
                    var forceDirectUrl by remember(flick.id, flick.imageUrl) { mutableStateOf(false) }

                    val currentModel = if (forceDirectUrl) cacheBusted else bridgedModel
                    val painter = rememberAsyncImagePainter(model = currentModel)
                    val painterState = painter.state

                    // Reset per-image fallback controls when model identity changes
                    LaunchedEffect(flick.id, flick.imageUrl) {
                        loadingStartMs = 0L
                        forceDirectUrl = false
                    }

                    // If spinner seems stuck for remote images, force a direct URL model once
                    LaunchedEffect(painterState, flick.id, flick.imageUrl) {
                        if (isLocalSource) return@LaunchedEffect
                        when (painterState) {
                            is AsyncImagePainter.State.Loading -> {
                                if (loadingStartMs == 0L) loadingStartMs = System.currentTimeMillis()
                                delay(2500)
                                val stillLoading = painter.state is AsyncImagePainter.State.Loading
                                if (stillLoading && !forceDirectUrl) {
                                    forceDirectUrl = true
                                }
                            }
                            is AsyncImagePainter.State.Success,
                            is AsyncImagePainter.State.Error -> {
                                loadingStartMs = 0L
                            }
                            else -> Unit
                        }
                    }

                    val hasImage = currentModel != null && cacheBusted.isNotBlank()

                    when {
                        !hasImage -> {
                            // Cleaner placeholder for missing URL (common just-after-upload race)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🖼️",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 20.sp
                                )
                            }
                        }

                        else -> {
                            AsyncImage(
                                model = currentModel,
                                contentDescription = "Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Subtle loading overlay only while image is actively loading
                            if (painterState is AsyncImagePainter.State.Loading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }

                            // If direct-url fallback also errors, show clear failure hint
                            if (painterState is AsyncImagePainter.State.Error) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Image unavailable",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    val reactionCounts = flick.getReactionCounts()
                    val orderedReactions = reactionCounts
                        .filterValues { it > 0 }
                        .toList()
                        .sortedByDescending { it.second }
                        .take(5)

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(Color.Black.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = flick.userName,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 6.dp, end = 6.dp)
                                .offset(y = (-0.5).dp),
                            style = androidx.compose.ui.text.TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            orderedReactions.forEach { (reactionType, count) ->
                                val emoji = reactionType.toEmoji()
                                Text(
                                    text = "$emoji$count",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.offset(y = (-0.5).dp),
                                    style = androidx.compose.ui.text.TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Loading indicator as grid item
            if (isLoadingMore) {
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }

            // End spacer to avoid abrupt stop under nav overlay
            item(span = { GridItemSpan(3) }) {
                Spacer(modifier = Modifier.height(24.dp))
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
