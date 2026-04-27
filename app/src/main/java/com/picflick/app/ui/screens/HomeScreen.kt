package com.picflick.app.ui.screens

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import com.picflick.app.ui.components.ActionSheetOption
import com.picflick.app.ui.components.AddPhotoStyleActionSheet
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
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onEditPhotoClick: (Flick) -> Unit = {}, // Navigate to edit photo screen
    openGroupsManager: Boolean = false,
    onOpenGroupsManagerConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("picflick_home_prefs", Context.MODE_PRIVATE)
    }
    val deletedExampleGroupsKey = remember(userProfile.uid) { "deleted_example_groups_${userProfile.uid}" }
    val defaultExampleGroups = remember {
        listOf("Uncle John's wedding", "Five a side footie talk")
    }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showGroupsManager by remember { mutableStateOf(false) }
    val temporaryGroupExamples = remember(userProfile.uid) {
        val deleted = prefs.getStringSet(deletedExampleGroupsKey, emptySet()) ?: emptySet()
        mutableStateListOf(*defaultExampleGroups.filterNot { it in deleted }.toTypedArray())
    }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var viewingGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var inviteTargetGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var adminTargetGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingGroupIconSourceUri by remember { mutableStateOf<Uri?>(null) }
    var showGroupIconCropDialog by remember { mutableStateOf(false) }
    var showGroupIconMediaPicker by remember { mutableStateOf(false) }
    var selectedGroupIconMediaUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingGroupIconTarget by remember { mutableStateOf<String?>(null) }
    var groupIconCropSourceUri by remember { mutableStateOf<Uri?>(null) }
    var createDialogIconOverride by remember { mutableStateOf<String?>(null) }
    var editDialogIconOverride by remember { mutableStateOf<String?>(null) }
    var selectedFlick by remember { mutableStateOf<Flick?>(null) }
    var selectedFlickIndex by remember { mutableIntStateOf(0) }
    var lastEdgeLoadMoreAt by remember { mutableLongStateOf(0L) }
    var privacySetting by remember { mutableStateOf("friends") } // "friends" or "public"
    var selectedUploadGroupId by remember { mutableStateOf("") }
    var taggedFriends by remember { mutableStateOf<List<String>>(emptyList()) } // ADDED: Tagged friends

    LaunchedEffect(privacySetting) {
        if (privacySetting != "friends" && selectedUploadGroupId.isNotBlank()) {
            selectedUploadGroupId = ""
        }
    }
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

    LaunchedEffect(openGroupsManager) {
        if (openGroupsManager) {
            showGroupsManager = true
            onOpenGroupsManagerConsumed()
        }
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
                sharedGroupId = selectedUploadGroupId,
                taggedFriends = taggedFriends, // ADDED: Tagged friends
                onComplete = { success ->
                    if (success) {
                        selectedUploadGroupId = ""
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
                sharedGroupId = selectedUploadGroupId,
                onComplete = { success ->
                    if (success) {
                        selectedUploadGroupId = ""
                        viewModel.checkDailyUploads(userProfile.uid)
                    }
                }
            )
        }
    }


    // Handle cropped group icon upload
    LaunchedEffect(pendingGroupIconSourceUri) {
        val uri = pendingGroupIconSourceUri ?: return@LaunchedEffect
        val target = pendingGroupIconTarget
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val imageBytes = inputStream?.readBytes()
            inputStream?.close()
            if (imageBytes != null) {
                when (val uploadResult = com.picflick.app.repository.FlickRepository.getInstance().uploadFlickImage(userProfile.uid, imageBytes)) {
                    is com.picflick.app.data.Result.Success -> {
                        if (target == "create") {
                            createDialogIconOverride = uploadResult.data
                        } else {
                            editDialogIconOverride = uploadResult.data
                        }
                        Toast.makeText(context, "Album photo added", Toast.LENGTH_SHORT).show()
                    }
                    is com.picflick.app.data.Result.Error -> {
                        Toast.makeText(context, "Failed to upload album photo", Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to process album photo", Toast.LENGTH_SHORT).show()
        } finally {
            pendingGroupIconSourceUri = null
            pendingGroupIconTarget = null
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
                    onIsAtTopChanged = { atTop -> isFeedAtTop = atTop }
                )
            }

            PullRefreshIndicator(
                refreshing = viewModel.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    // Full-screen photo viewer with comments
    selectedFlick?.let { flick ->
        val viewerPhotos = viewModel.flicks.filter { it.imageUrl.isNotBlank() }
        val safeSelectedIndex = viewerPhotos.indexOfFirst { it.id == selectedFlick?.id }
            .takeIf { it >= 0 } ?: 0

        FullScreenPhotoViewer(
            flick = flick,
            currentUser = currentUserWithLivePhoto,
            onDismiss = { selectedFlick = null },
            onReaction = { activeFlick, reactionType ->
                viewModel.toggleReaction(activeFlick, userProfile.uid, userProfile.displayName, currentUserWithLivePhoto.photoUrl, reactionType)
                selectedFlick = viewModel.flicks.firstOrNull { it.id == activeFlick.id } ?: activeFlick
                selectedFlickIndex = viewModel.flicks.indexOfFirst { it.id == selectedFlick?.id }
                    .takeIf { it >= 0 } ?: selectedFlickIndex
            },
            onShareClick = {
                flickToShare = flick
                showShareToChatDialog = true
            },
            canDelete = flick.userId == userProfile.uid,
            onDeleteClick = {
                viewModel.removeFlickFromFeed(flick.id)
            },
            allPhotos = viewerPhotos,
            currentIndex = safeSelectedIndex,
            onNavigateToPhoto = { index ->
                val remaining = viewerPhotos.size - 1 - index
                val preloadThreshold = 8
                val now = System.currentTimeMillis()
                val atViewerTail = index >= viewerPhotos.size - 1
                val edgeRetryCooldownMs = 1200L
                val shouldEdgeRetry =
                    atViewerTail &&
                        !viewModel.isLoadingMore &&
                        (now - lastEdgeLoadMoreAt) >= edgeRetryCooldownMs

                if (remaining <= preloadThreshold && !viewModel.isLoadingMore && (viewModel.canLoadMore || shouldEdgeRetry)) {
                    if (shouldEdgeRetry) {
                        lastEdgeLoadMoreAt = now
                    }
                    android.util.Log.d(
                        "PhotoViewerPerf",
                        "LOAD_MORE_REQUEST index=$index remaining=$remaining size=${viewerPhotos.size} threshold=$preloadThreshold canLoadMore=${viewModel.canLoadMore} edgeRetry=$shouldEdgeRetry"
                    )
                    viewModel.loadMoreFlicks()
                }

                if (index in viewerPhotos.indices) {
                    selectedFlickIndex = index
                    selectedFlick = viewerPhotos[index]
                } else {
                    selectedFlickIndex = index.coerceAtMost((viewerPhotos.size - 1).coerceAtLeast(0))
                }
            },
            onUserProfileClick = { userId -> onUserProfileClick(userId) },
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
            onEditPhotoClick = { onEditPhotoClick(it) }
        )
    }

    // One-level back behavior for Home nested overlays/dialogs.
    BackHandler(enabled = showGroupIconCropDialog) {
        showGroupIconCropDialog = false
        groupIconCropSourceUri = null
    }

    BackHandler(enabled = showCreateGroupDialog && !showGroupIconCropDialog) {
        showCreateGroupDialog = false
        createDialogIconOverride = null
    }

    BackHandler(enabled = editingGroup != null && !showGroupIconCropDialog && !showCreateGroupDialog) {
        editingGroup = null
        editDialogIconOverride = null
    }

    BackHandler(enabled = viewingGroup != null && !showGroupIconCropDialog && !showCreateGroupDialog && editingGroup == null) {
        viewingGroup = null
    }

    BackHandler(enabled = inviteTargetGroup != null && !showGroupIconCropDialog && !showCreateGroupDialog && editingGroup == null && viewingGroup == null) {
        inviteTargetGroup = null
    }

    BackHandler(enabled = adminTargetGroup != null && !showGroupIconCropDialog && !showCreateGroupDialog && editingGroup == null && viewingGroup == null && inviteTargetGroup == null) {
        adminTargetGroup = null
    }

    BackHandler(enabled = showGroupsManager && !showGroupIconCropDialog && !showCreateGroupDialog && editingGroup == null && viewingGroup == null && inviteTargetGroup == null && adminTargetGroup == null) {
        showGroupsManager = false
    }

    BackHandler(enabled = showShareToChatDialog && flickToShare != null && !isSharingPhoto && !showGroupIconCropDialog && !showCreateGroupDialog && editingGroup == null && inviteTargetGroup == null && adminTargetGroup == null && !showGroupsManager) {
        showShareToChatDialog = false
        flickToShare = null
    }

    BackHandler(enabled = showReactionPicker && !showGroupIconCropDialog && !showCreateGroupDialog && editingGroup == null && inviteTargetGroup == null && adminTargetGroup == null && !showGroupsManager && !(showShareToChatDialog && flickToShare != null)) {
        showReactionPicker = false
        flickForReaction = null
    }

    BackHandler(enabled = showGroupIconMediaPicker && !showGroupIconCropDialog && !showCreateGroupDialog && editingGroup == null) {
        showGroupIconMediaPicker = false
        selectedGroupIconMediaUris = emptyList()
        pendingGroupIconTarget = null
    }

    BackHandler(enabled = showUploadDialog && !showGroupIconCropDialog && !showGroupIconMediaPicker && !showCreateGroupDialog && editingGroup == null && inviteTargetGroup == null && adminTargetGroup == null && !showGroupsManager && !(showShareToChatDialog && flickToShare != null) && !showReactionPicker) {
        showUploadDialog = false
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
            allFriendsCount = max(friends.size, userProfile.following.size),
            temporaryExampleGroups = temporaryGroupExamples,
            isDarkMode = isDarkMode,
            currentUserId = userProfile.uid,
            onDismiss = { showGroupsManager = false },
            onSelectGroup = { filter ->
                viewModel.setFilter(filter)
                showGroupsManager = false
            },
            onCreateGroup = { showCreateGroupDialog = true },
            onEditGroup = { group -> editingGroup = group },
            onViewGroup = { group -> viewingGroup = group },
            onInviteToGroup = { group -> inviteTargetGroup = group },
            onManageAdmins = { group -> adminTargetGroup = group },
            onDeleteGroup = { group -> viewModel.deleteFriendGroup(userProfile.uid, group.id) },
            onExitGroup = { group -> viewModel.leaveFriendGroup(userProfile.uid, group.id) },
            onDeleteTemporaryGroup = { title ->
                temporaryGroupExamples.remove(title)
                val currentDeleted = prefs.getStringSet(deletedExampleGroupsKey, emptySet())?.toMutableSet() ?: mutableSetOf()
                currentDeleted.add(title)
                prefs.edit().putStringSet(deletedExampleGroupsKey, currentDeleted).apply()
            },
            onReorderGroups = { orderedGroupIds -> viewModel.reorderFriendGroups(userProfile.uid, orderedGroupIds) }
        )
    }

    if (showCreateGroupDialog) {
        CreateOrEditGroupDialog(
            title = "Create album",
            submitLabel = "Save",
            friends = friends,
            isDarkMode = isDarkMode,
            initialName = "",
            initialIcon = createDialogIconOverride ?: "👥",
            initialColor = "#4FC3F7",
            initialSelectedFriendIds = emptyList(),
            onDismiss = {
                showCreateGroupDialog = false
                createDialogIconOverride = null
            },
            onAddPhoto = {
                pendingGroupIconTarget = "create"
                selectedGroupIconMediaUris = emptyList()
                showGroupIconMediaPicker = true
            },
            onSubmit = { _, _, _, _ -> },
            onCreateLocal = { name, icon, selectedFriendIds, color ->
                viewModel.createLocalFriendGroup(
                    userId = userProfile.uid,
                    name = name,
                    icon = icon,
                    friendIds = selectedFriendIds,
                    color = color
                ) { success, createdGroup ->
                    if (success) {
                        if (createdGroup != null) {
                            viewModel.setFilter(FeedFilter.ByGroup(createdGroup))
                        }
                        showCreateGroupDialog = false
                        showGroupsManager = false
                        createDialogIconOverride = null
                    } else {
                        Toast.makeText(
                            context,
                            viewModel.errorMessage ?: "Failed to create local album",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onCreateShared = { name, icon, selectedFriendIds, color ->
                viewModel.createFriendGroup(
                    userId = userProfile.uid,
                    name = name,
                    icon = icon,
                    friendIds = selectedFriendIds,
                    color = color
                ) { success, createdGroup ->
                    if (success) {
                        if (createdGroup != null) {
                            viewModel.setFilter(FeedFilter.ByGroup(createdGroup))
                        }
                        showCreateGroupDialog = false
                        showGroupsManager = false
                        createDialogIconOverride = null
                    } else {
                        Toast.makeText(
                            context,
                            viewModel.errorMessage ?: "Failed to create shared album",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            readOnly = false,
            onUserProfileClick = onUserProfileClick
        )
    }

    editingGroup?.let { group ->
        CreateOrEditGroupDialog(
            title = "Edit album",
            submitLabel = "Save",
            friends = friends,
            isDarkMode = isDarkMode,
            initialName = group.name,
            initialIcon = editDialogIconOverride ?: group.icon,
            initialColor = group.color,
            initialSelectedFriendIds = group.membersExcludingOwner(),
            onDismiss = {
                editingGroup = null
                editDialogIconOverride = null
            },
            onAddPhoto = {
                pendingGroupIconTarget = "edit"
                selectedGroupIconMediaUris = emptyList()
                showGroupIconMediaPicker = true
            },
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
                        editDialogIconOverride = null
                    }
                }
            },
            onCreateLocal = null,
            onCreateShared = null,
            readOnly = false,
            onUserProfileClick = onUserProfileClick
        )
    }

    viewingGroup?.let { group ->
        CreateOrEditGroupDialog(
            title = "View album",
            submitLabel = "Save",
            friends = friends,
            isDarkMode = isDarkMode,
            initialName = group.name,
            initialIcon = group.icon,
            initialColor = group.color,
            initialSelectedFriendIds = group.membersExcludingOwner(),
            onDismiss = { viewingGroup = null },
            onAddPhoto = {},
            onSubmit = { _, _, _, _ -> },
            onCreateLocal = null,
            onCreateShared = null,
            readOnly = true,
            onUserProfileClick = onUserProfileClick
        )
    }

    if (showGroupIconMediaPicker) {
        GroupIconMediaPickerScreen(
            isDarkMode = isDarkMode,
            selectedUris = selectedGroupIconMediaUris,
            onToggleSelection = { uri ->
                selectedGroupIconMediaUris = if (selectedGroupIconMediaUris.contains(uri)) {
                    selectedGroupIconMediaUris - uri
                } else {
                    listOf(uri)
                }
            },
            onBack = {
                showGroupIconMediaPicker = false
                selectedGroupIconMediaUris = emptyList()
                pendingGroupIconTarget = null
            },
            onDone = {
                val selectedUri = selectedGroupIconMediaUris.firstOrNull()
                if (selectedUri != null) {
                    groupIconCropSourceUri = selectedUri
                    showGroupIconCropDialog = true
                }
                showGroupIconMediaPicker = false
                selectedGroupIconMediaUris = emptyList()
            }
        )
    }

    if (showGroupIconCropDialog) {
        GroupPhotoCropDialog(
            imageUri = groupIconCropSourceUri,
            onDismiss = {
                showGroupIconCropDialog = false
                groupIconCropSourceUri = null
            },
            onConfirm = { croppedUri ->
                showGroupIconCropDialog = false
                groupIconCropSourceUri = null
                if (croppedUri != null) {
                    pendingGroupIconSourceUri = croppedUri
                }
            }
        )
    }

    if (adminTargetGroup != null) {
        val group = adminTargetGroup!!
        val isDarkMode = ThemeManager.isDarkMode.value
        val ownerId = group.effectiveOwnerId()
        val candidates = friends
            .filter { it.uid.isNotBlank() }
            .filter { group.effectiveMemberIds().contains(it.uid) }
            .filter { it.uid != ownerId }
            .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
        val addColor = Color(0xFF2A4A73)
        val waitingColor = Color(0xFF2A4A73)
        var selectedAdmins by remember(group.id, candidates) {
            mutableStateOf(group.adminIds.filter { it.isNotBlank() && it != ownerId && group.isMember(it) }.toSet())
        }
        var processingMemberId by remember(group.id) { mutableStateOf<String?>(null) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = isDarkModeBackground(isDarkMode)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    color = Color.Black
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Manage admins",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 56.dp)
                        )
                        IconButton(
                            onClick = { adminTargetGroup = null },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                }

                if (candidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No eligible members to promote.",
                            color = if (isDarkMode) Color.Gray else Color.DarkGray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(candidates, key = { it.uid }) { member ->
                            val isAdminNow = selectedAdmins.contains(member.uid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(56.dp)) {
                                    if (member.photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = withCacheBust(member.photoUrl, member.uid),
                                            contentDescription = member.displayName,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(if (isDarkMode) Color(0xFF3A3A3C) else Color(0xFFE0E0E0)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = if (isDarkMode) Color.Gray else Color.DarkGray
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.displayName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color.White else Color.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isAdminNow) "Album admin" else "Album member",
                                        fontSize = 14.sp,
                                        color = if (isDarkMode) Color.Gray else Color.DarkGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (processingMemberId == member.uid) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            val previous = selectedAdmins
                                            val updated = if (isAdminNow) previous - member.uid else previous + member.uid
                                            selectedAdmins = updated
                                            processingMemberId = member.uid
                                            viewModel.updateGroupAdmins(
                                                userId = userProfile.uid,
                                                groupId = group.id,
                                                adminIds = updated.toList()
                                            ) { success, message ->
                                                processingMemberId = null
                                                if (!success) selectedAdmins = previous
                                                Toast.makeText(
                                                    context,
                                                    if (success) {
                                                        if (isAdminNow) "Removed admin: ${member.displayName}" else "Made admin: ${member.displayName}"
                                                    } else {
                                                        message ?: "Failed to update admins"
                                                    },
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.wrapContentWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isAdminNow) waitingColor else Color.Transparent,
                                            contentColor = if (isAdminNow) Color.White else addColor,
                                            disabledContainerColor = if (isAdminNow) waitingColor else Color.Transparent,
                                            disabledContentColor = if (isAdminNow) Color.White else addColor
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isAdminNow) waitingColor else addColor
                                        )
                                    ) {
                                        Text(
                                            text = if (isAdminNow) "Admin" else "Make Admin",
                                            fontSize = 12.sp,
                                            color = if (isAdminNow) Color.White else addColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (inviteTargetGroup != null) {
        val group = inviteTargetGroup!!
        val isDarkMode = ThemeManager.isDarkMode.value
        val eligibleFriends = friends
            .filter { it.uid.isNotBlank() }
            .filter { !group.effectiveMemberIds().contains(it.uid) }
            .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
        val addColor = Color(0xFF2A4A73)
        val waitingColor = Color(0xFF2A4A73)
        var processingInviteUserId by remember(group.id) { mutableStateOf<String?>(null) }
        var invitedUserIds by remember(group.id) { mutableStateOf(setOf<String>()) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = isDarkModeBackground(isDarkMode)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    color = Color.Black
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Invite friends to ${group.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 56.dp)
                        )
                        IconButton(
                            onClick = { inviteTargetGroup = null },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                }

                if (eligibleFriends.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All your friends are already in this album.",
                            color = if (isDarkMode) Color.Gray else Color.DarkGray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(eligibleFriends, key = { it.uid }) { friend ->
                            val isInvited = invitedUserIds.contains(friend.uid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(56.dp)) {
                                    if (friend.photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = withCacheBust(friend.photoUrl, friend.uid),
                                            contentDescription = friend.displayName,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(if (isDarkMode) Color(0xFF3A3A3C) else Color(0xFFE0E0E0)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = if (isDarkMode) Color.Gray else Color.DarkGray
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = friend.displayName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color.White else Color.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${friend.followers.size} followers",
                                        fontSize = 14.sp,
                                        color = if (isDarkMode) Color.Gray else Color.DarkGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (processingInviteUserId == friend.uid) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            processingInviteUserId = friend.uid
                                            if (isInvited) {
                                                viewModel.cancelGroupInvite(
                                                    inviterId = userProfile.uid,
                                                    groupId = group.id,
                                                    inviteeId = friend.uid
                                                ) { success, message ->
                                                    processingInviteUserId = null
                                                    if (success) invitedUserIds = invitedUserIds - friend.uid
                                                    Toast.makeText(
                                                        context,
                                                        if (success) "Invite canceled for ${friend.displayName}" else (message ?: "Failed to cancel invite"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } else {
                                                viewModel.inviteFriendToGroup(
                                                    inviterId = userProfile.uid,
                                                    inviterName = userProfile.displayName,
                                                    groupId = group.id,
                                                    inviteeId = friend.uid
                                                ) { success, message ->
                                                    processingInviteUserId = null
                                                    if (success) invitedUserIds = invitedUserIds + friend.uid
                                                    Toast.makeText(
                                                        context,
                                                        if (success) "Invite sent to ${friend.displayName}" else (message ?: "Failed to send invite"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.wrapContentWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isInvited) waitingColor else Color.Transparent,
                                            contentColor = if (isInvited) Color.White else addColor,
                                            disabledContainerColor = if (isInvited) waitingColor else Color.Transparent,
                                            disabledContentColor = Color.White
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isInvited) waitingColor else addColor
                                        )
                                    ) {
                                        Text(
                                            text = if (isInvited) "Invited" else "Invite",
                                            fontSize = 12.sp,
                                            color = if (isInvited) Color.White else addColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
    onPrivacyChange: (String) -> Unit = {},
    groups: List<FriendGroup> = emptyList(),
    selectedGroupId: String = "",
    onGroupSelected: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onDismiss() }
                )
            }
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { /* Consume tap inside card */ }
                    )
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            UploadOptionsContent(
                onDismiss = onDismiss,
                onCameraClick = onCameraClick,
                onGalleryClick = onGalleryClick,
                privacySetting = privacySetting,
                onPrivacyChange = onPrivacyChange,
                groups = groups,
                selectedGroupId = selectedGroupId,
                onGroupSelected = onGroupSelected
            )
        }
    }
}

@Composable
private fun UploadOptionsContent(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    privacySetting: String,
    onPrivacyChange: (String) -> Unit,
    groups: List<FriendGroup>,
    selectedGroupId: String,
    onGroupSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    RoundedCornerShape(2.dp)
                )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Upload Photo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy toggle segmented control
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val optionModifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))

                Box(
                    modifier = optionModifier
                        .background(
                            if (privacySetting == "friends") MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .clickable { onPrivacyChange("friends") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Friends",
                        color = if (privacySetting == "friends") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = optionModifier
                        .background(
                            if (privacySetting == "public") MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .clickable { onPrivacyChange("public") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Public",
                        color = if (privacySetting == "public") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Camera option
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCameraClick() },
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Take Photo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Use camera to capture new photo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Gallery option
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onGalleryClick() },
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Choose from Gallery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Select existing photo from device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (privacySetting == "friends") {
            val sharedGroups = groups.filter { it.id.isNotBlank() }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Share to album (optional)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedGroupId.isBlank()) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onGroupSelected("") }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "No album target",
                            color = if (selectedGroupId.isBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    sharedGroups.forEach { group ->
                        val isSelected = selectedGroupId == group.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onGroupSelected(group.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = group.name.ifBlank { "Album" },
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (sharedGroups.isEmpty()) {
                        Text(
                            text = "Create a shared album first to target one album only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                privacySetting == "public" -> "Everyone can see this photo"
                selectedGroupId.isNotBlank() -> "Only this selected album will see this photo"
                else -> "Only your friends will see this photo"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun GroupManagerSheet(
    groups: List<FriendGroup>,
    selectedFilter: FeedFilter,
    friends: List<UserProfile>,
    allFriendsCount: Int,
    temporaryExampleGroups: List<String>,
    isDarkMode: Boolean,
    currentUserId: String,
    onDismiss: () -> Unit,
    onSelectGroup: (FeedFilter) -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (FriendGroup) -> Unit,
    onViewGroup: (FriendGroup) -> Unit,
    onInviteToGroup: (FriendGroup) -> Unit,
    onManageAdmins: (FriendGroup) -> Unit,
    onDeleteGroup: (FriendGroup) -> Unit,
    onExitGroup: (FriendGroup) -> Unit,
    onDeleteTemporaryGroup: (String) -> Unit,
    onReorderGroups: (List<String>) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val backgroundColor = if (isDarkMode) Color.Black else PicFlickLightBackground
    val textColor = if (isDarkMode) Color.White else Color.Black
    val addColor = Color(0xFF2A4A73)
    val waitingColor = Color(0xFF2A4A73)

    val sortedGroups = remember(groups) {
        groups.sortedWith(
            compareBy<FriendGroup> { it.orderIndex }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        )
    }
    val orderedGroupIdsState = remember(sortedGroups) {
        mutableStateListOf<String>().apply { addAll(sortedGroups.map { it.id }) }
    }
    var draggingGroupId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var confirmDeleteTempTitle by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredOrderedGroupIds = remember(orderedGroupIdsState, sortedGroups, searchQuery) {
        if (searchQuery.isBlank()) {
            orderedGroupIdsState.toList()
        } else {
            val query = searchQuery.trim().lowercase(Locale.getDefault())
            orderedGroupIdsState.filter { groupId ->
                val group = sortedGroups.firstOrNull { it.id == groupId }
                group != null && (
                    group.name.lowercase(Locale.getDefault()).contains(query) ||
                        group.membersExcludingOwner().size.toString().contains(query)
                    )
            }
        }
    }

    confirmDeleteGroup?.let { group ->
        val isOwner = group.isOwner(currentUserId)
        AddPhotoStyleActionSheet(
            title = if (isOwner) "Delete album?" else "Exit album?",
            options = listOf(
                ActionSheetOption(
                    icon = if (isOwner) Icons.Default.Delete else Icons.AutoMirrored.Filled.ExitToApp,
                    title = if (isOwner) "Delete \"${group.name}\"" else "Exit \"${group.name}\"",
                    subtitle = if (isOwner) {
                        "This album will be permanently deleted for everyone"
                    } else {
                        "You will be removed from this shared album"
                    },
                    accentColor = Color(0xFFD84343),
                    onClick = {
                        if (isOwner) onDeleteGroup(group) else onExitGroup(group)
                        confirmDeleteGroup = null
                    }
                )
            ),
            onDismiss = { confirmDeleteGroup = null },
            cancelTitle = "Cancel",
            cancelSubtitle = if (isOwner) "Keep this album" else "Stay in this album",
            cancelIcon = Icons.Default.Close,
            cancelAccentColor = Color(0xFF4B5563)
        )
    }

    confirmDeleteTempTitle?.let { tempTitle ->
        AddPhotoStyleActionSheet(
            title = "Delete album?",
            options = listOf(
                ActionSheetOption(
                    icon = Icons.Default.Delete,
                    title = "Delete \"$tempTitle\"",
                    subtitle = "This example album will be removed",
                    accentColor = Color(0xFFD84343),
                    onClick = {
                        onDeleteTemporaryGroup(tempTitle)
                        confirmDeleteTempTitle = null
                    }
                )
            ),
            onDismiss = { confirmDeleteTempTitle = null },
            cancelTitle = "Cancel",
            cancelSubtitle = "Keep this album",
            cancelIcon = Icons.Default.Close,
            cancelAccentColor = Color(0xFF4B5563)
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Albums",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 130.dp)
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }


                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Search albums") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(12.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (searchQuery.isBlank() || "all friends".contains(searchQuery.trim().lowercase(Locale.getDefault()))) {
                    item {
                        val isSelected = selectedFilter is FeedFilter.AllFriends
                        GroupRowCard(
                            title = "All friends",
                            subtitle = "$allFriendsCount friends",
                            icon = "🏠",
                            colour = "#4FC3F7",
                            selected = isSelected,
                            onClick = { onSelectGroup(FeedFilter.AllFriends) },
                            textColor = textColor,
                            trailingContent = {}
                        )
                    }
                }

                items(filteredOrderedGroupIds, key = { it }) { groupId ->
                    val group = sortedGroups.firstOrNull { it.id == groupId } ?: return@items
                    val isSelected = selectedFilter is FeedFilter.ByGroup && selectedFilter.group.id == group.id
                    var showGroupMenu by remember(group.id) { mutableStateOf(false) }
                    var dragDeltaY by remember(group.id) { mutableStateOf(0f) }

                    GroupRowCard(
                        title = group.name,
                        subtitle = "${group.membersExcludingOwner().size} friends",
                        icon = group.icon,
                        colour = group.color,
                        selected = isSelected,
                        onClick = { onSelectGroup(FeedFilter.ByGroup(group)) },
                        onLongPress = { draggingGroupId = group.id },
                        onDrag = { deltaY ->
                            if (draggingGroupId != group.id) {
                                draggingGroupId = group.id
                            }
                            dragDeltaY += deltaY
                            val moveThreshold = 36f
                            val currentIndex = orderedGroupIdsState.indexOf(group.id)
                            if (dragDeltaY <= -moveThreshold && currentIndex > 0) {
                                orderedGroupIdsState.removeAt(currentIndex)
                                orderedGroupIdsState.add(currentIndex - 1, group.id)
                                dragDeltaY = 0f
                            } else if (dragDeltaY >= moveThreshold && currentIndex in 0 until orderedGroupIdsState.lastIndex) {
                                orderedGroupIdsState.removeAt(currentIndex)
                                orderedGroupIdsState.add(currentIndex + 1, group.id)
                                dragDeltaY = 0f
                            }
                        },
                        onDragEnd = {
                            if (draggingGroupId == group.id) {
                                draggingGroupId = null
                                dragDeltaY = 0f
                                onReorderGroups(orderedGroupIdsState.toList())
                            }
                        },
                        textColor = textColor,
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (draggingGroupId == group.id) {
                                    Text(
                                        text = "Moving…",
                                        color = textColor.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }

                                Box {
                                    IconButton(onClick = { showGroupMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Album menu", tint = textColor)
                                    }
                                    DropdownMenu(
                                        expanded = showGroupMenu,
                                        onDismissRequest = { showGroupMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("View album") },
                                            onClick = {
                                                showGroupMenu = false
                                                onSelectGroup(FeedFilter.ByGroup(group))
                                            },
                                            leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null) }
                                        )
                                        if (group.isOwner(currentUserId)) {
                                            DropdownMenuItem(
                                                text = { Text("Invite") },
                                                onClick = {
                                                    showGroupMenu = false
                                                    onInviteToGroup(group)
                                                },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Admins") },
                                                onClick = {
                                                    showGroupMenu = false
                                                    onManageAdmins(group)
                                                },
                                                leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(if (group.isAdmin(currentUserId)) "Edit album" else "View album") },
                                            onClick = {
                                                showGroupMenu = false
                                                if (group.isAdmin(currentUserId)) {
                                                    onEditGroup(group)
                                                } else {
                                                    onViewGroup(group)
                                                }
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (group.isOwner(currentUserId)) "Delete album" else "Exit album") },
                                            onClick = {
                                                showGroupMenu = false
                                                confirmDeleteGroup = group
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (group.isOwner(currentUserId)) Icons.Default.Delete else Icons.AutoMirrored.Filled.ExitToApp,
                                                    contentDescription = null,
                                                    tint = Color(0xFFD84343)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                items(temporaryExampleGroups.filter { searchQuery.isBlank() || it.lowercase(Locale.getDefault()).contains(searchQuery.trim().lowercase(Locale.getDefault())) }, key = { "temp_$it" }) { tempTitle ->
                    val tempIcon = if (tempTitle == "Uncle John's wedding") "👰" else "⚽"
                    var showTempMenu by remember(tempTitle) { mutableStateOf(false) }
                    GroupRowCard(
                        title = tempTitle,
                        subtitle = "Album example. Delete when you want 🙂",
                        icon = tempIcon,
                        colour = "#9E9E9E",
                        selected = false,
                        onClick = {},
                        textColor = textColor,
                        enabled = true,
                        trailingContent = {
                            Box {
                                IconButton(onClick = { showTempMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Example album menu", tint = textColor)
                                }
                                DropdownMenu(
                                    expanded = showTempMenu,
                                    onDismissRequest = { showTempMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            showTempMenu = false
                                            confirmDeleteTempTitle = tempTitle
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD84343)) }
                                    )
                                }
                            }
                        }
                    )
                }

            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = backgroundColor,
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Button(
                        onClick = onCreateGroup,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = addColor,
                            contentColor = Color.White,
                            disabledContainerColor = addColor.copy(alpha = 0.6f),
                            disabledContentColor = Color.White.copy(alpha = 0.9f)
                        )
                    ) {
                        Text("Create new album", fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRowCard(
    title: String,
    subtitle: String,
    icon: String,
    colour: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    textColor: Color,
    enabled: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit
) {
    val rowBackground = if (selected) Color(0x221565C0) else Color.Transparent

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .pointerInput(enabled, onDrag, onDragEnd, onLongPress) {
                    if (enabled && onDrag != null) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onLongPress?.invoke() },
                            onDragEnd = { onDragEnd?.invoke() },
                            onDragCancel = { onDragEnd?.invoke() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            }
                        )
                    }
                }
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(2.dp, Color.Black, CircleShape)
                    .clip(CircleShape)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (icon.startsWith("http")) {
                    AsyncImage(
                        model = icon,
                        contentDescription = "Group icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = icon, fontSize = 22.sp)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = subtitle,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
            Row(content = trailingContent)
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 80.dp),
            thickness = 0.5.dp,
            color = textColor.copy(alpha = 0.15f)
        )
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
    onAddPhoto: () -> Unit,
    onSubmit: (name: String, icon: String, selectedFriendIds: List<String>, color: String) -> Unit,
    onCreateLocal: ((name: String, icon: String, selectedFriendIds: List<String>, color: String) -> Unit)? = null,
    onCreateShared: ((name: String, icon: String, selectedFriendIds: List<String>, color: String) -> Unit)? = null,
    readOnly: Boolean = false,
    onUserProfileClick: (String) -> Unit = {}
) {
    BackHandler(onBack = onDismiss)

    var groupName by remember(initialName) { mutableStateOf(initialName) }
    var selectedIcon by remember(initialIcon) { mutableStateOf(initialIcon) }
    var selectedColor by remember(initialColor) { mutableStateOf(initialColor) }
    var selectedFriends by remember(initialSelectedFriendIds) { mutableStateOf(initialSelectedFriendIds.toSet()) }

    val addColor = Color(0xFF2A4A73)
    val waitingColor = Color(0xFF2A4A73)
    val textColor = if (isDarkMode) Color.White else Color.Black
    val pageBackground = if (isDarkMode) Color.Black else PicFlickLightBackground
    val isCreateMode = onCreateLocal != null && onCreateShared != null

    val sortedFriends = remember(friends) {
        friends.sortedWith(
            compareBy<UserProfile>(
                { it.displayName.trim().substringAfterLast(" ").lowercase(Locale.getDefault()) },
                { it.displayName.trim().substringBeforeLast(" ").lowercase(Locale.getDefault()) },
                { it.displayName.lowercase(Locale.getDefault()) }
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = pageBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 56.dp)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    if (!isCreateMode && !readOnly) {
                        Button(
                            onClick = {
                                onSubmit(groupName.trim(), selectedIcon, selectedFriends.toList(), selectedColor)
                            },
                            enabled = groupName.isNotBlank(),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = waitingColor,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = submitLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = groupName,
                onValueChange = { if (!readOnly) groupName = it },
                singleLine = true,
                readOnly = readOnly,
                textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFB7D8F2),
                    unfocusedContainerColor = Color(0xFFB7D8F2),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Black.copy(alpha = 0.7f)
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.Black, CircleShape)
                        .background(if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF1F1F1))
                        .clickable(enabled = !readOnly) { onAddPhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedIcon.startsWith("http")) {
                        AsyncImage(
                            model = selectedIcon,
                            contentDescription = "Album icon",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset((-8).dp, (-8).dp)
                                .size(28.5.dp)
                                .background(waitingColor, CircleShape)
                                .border(3.dp, Color.Black, CircleShape)
                                .clickable(enabled = !readOnly) { onAddPhoto() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit album photo",
                                tint = Color.White,
                                modifier = Modifier.size(13.5.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Add photo",
                            tint = textColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = if (isDarkMode) Color(0xFF222222) else Color(0x22000000))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(sortedFriends, key = { it.uid }) { friend ->
                    val isSelected = selectedFriends.contains(friend.uid)
                    val tierRingColor = rememberLiveUserTierColor(friend.uid)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (friend.photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = withCacheBust(friend.photoUrl, friend.uid),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, tierRingColor, CircleShape)
                                    .clickable { onUserProfileClick(friend.uid) },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, tierRingColor, CircleShape)
                                    .background(if (isDarkMode) Color(0xFF3A3A3C) else Color(0xFFE0E0E0))
                                    .clickable { onUserProfileClick(friend.uid) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = if (isDarkMode) Color.Gray else Color.DarkGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friend.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Text(
                                text = "${friend.followers.size} followers",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDarkMode) Color.Gray else Color.DarkGray
                            )
                        }

                        if (isSelected) {
                            OutlinedButton(
                                onClick = {
                                    if (!readOnly) {
                                        selectedFriends = selectedFriends - friend.uid
                                    }
                                },
                                enabled = !readOnly,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = waitingColor,
                                    contentColor = Color.White,
                                    disabledContainerColor = waitingColor,
                                    disabledContentColor = Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, waitingColor)
                            ) {
                                Text("Added", fontSize = 12.sp, color = Color.White)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    if (!readOnly) {
                                        selectedFriends = selectedFriends + friend.uid
                                    }
                                },
                                enabled = !readOnly,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = addColor
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, addColor)
                            ) {
                                Text("Add", fontSize = 12.sp, color = addColor)
                            }
                        }
                    }
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF222222) else Color(0x22000000))
                }
            }

            if (isCreateMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onCreateLocal?.invoke(groupName.trim(), selectedIcon, selectedFriends.toList(), selectedColor)
                        },
                        enabled = groupName.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, addColor),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = addColor
                        )
                    ) {
                        Text("Create Local")
                    }

                    Button(
                        onClick = {
                            onCreateShared?.invoke(groupName.trim(), selectedIcon, selectedFriends.toList(), selectedColor)
                        },
                        enabled = groupName.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = waitingColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Create Shared")
                    }
                }
            }
        }
    }
}

@Composable
fun GroupPhotoCropDialog(
    imageUri: Uri?,
    onDismiss: () -> Unit,
    onConfirm: (Uri?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        if (imageUri == null) return@LaunchedEffect
        sourceBitmap = withContext(Dispatchers.IO) {
            loadBitmapFromUri(context, imageUri)
        }
        scale = 1f
        offset = Offset.Zero
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        confirmButton = {
            TextButton(
                enabled = sourceBitmap != null && !isSaving && viewportSize.width > 0 && viewportSize.height > 0,
                onClick = {
                    val bitmap = sourceBitmap ?: return@TextButton
                    isSaving = true
                    scope.launch {
                        val croppedUri = withContext(Dispatchers.IO) {
                            createCroppedGroupImageUri(
                                context = context,
                                sourceBitmap = bitmap,
                                viewportSize = viewportSize,
                                zoomScale = scale,
                                panOffset = offset,
                                imageQuality = 90
                            )
                        }
                        isSaving = false
                        onConfirm(croppedUri)
                    }
                }
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Adjust album photo") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Pinch to zoom and drag to position",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(sourceBitmap, viewportSize) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val bitmap = sourceBitmap ?: return@detectTransformGestures
                                val nextScale = (scale * zoom).coerceIn(1f, 6f)
                                val clampedOffset = clampPanOffsetForGroup(
                                    viewportSize = viewportSize,
                                    imageWidth = bitmap.width,
                                    imageHeight = bitmap.height,
                                    scale = nextScale,
                                    currentOffset = offset + pan
                                )
                                scale = nextScale
                                offset = clampedOffset
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (sourceBitmap == null) {
                        CircularProgressIndicator()
                    } else {
                        AsyncImage(
                            model = sourceBitmap,
                            contentDescription = "Crop album photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                    }
                }
            }
        }
    )
}

data class GroupIconMediaPickerItem(
    val uri: Uri,
    val id: Long,
    val dateAddedSeconds: Long
)

@Composable
private fun GroupIconMediaPickerScreen(
    isDarkMode: Boolean,
    selectedUris: List<Uri>,
    onToggleSelection: (Uri) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val visualUserSelectedPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    } else null

    fun hasAnyMediaPermission(): Boolean {
        val hasBase = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            mediaPermission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasSelected = visualUserSelectedPermission != null &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                visualUserSelectedPermission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        return hasBase || hasSelected
    }

    var hasMediaPermission by remember { mutableStateOf(hasAnyMediaPermission()) }
    val mediaPermissionsToRequest = remember(mediaPermission, visualUserSelectedPermission) {
        buildList {
            add(mediaPermission)
            if (visualUserSelectedPermission != null) add(visualUserSelectedPermission)
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantedMap ->
        hasMediaPermission = hasAnyMediaPermission() || grantedMap.values.any { it }
    }

    var mediaItems by remember { mutableStateOf<List<GroupIconMediaPickerItem>>(emptyList()) }
    var isLoadingMedia by remember { mutableStateOf(true) }

    LaunchedEffect(hasMediaPermission) {
        if (!hasMediaPermission) {
            isLoadingMedia = false
            mediaItems = emptyList()
            return@LaunchedEffect
        }

        isLoadingMedia = true
        mediaItems = loadGroupIconDeviceMedia(context)
        isLoadingMedia = false
    }

    LaunchedEffect(Unit) {
        hasMediaPermission = hasAnyMediaPermission()
        if (!hasMediaPermission) {
            mediaPermissionLauncher.launch(mediaPermissionsToRequest.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(isDarkModeBackground(isDarkMode))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.Black)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Choose from Gallery",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )

            if (selectedUris.isNotEmpty()) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E86DE),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Select",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Select",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoadingMedia -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = if (isDarkMode) Color.White else Color(0xFF1565C0)
                    )
                }

                !hasMediaPermission -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Allow photo permission to show your gallery",
                            color = if (isDarkMode) Color.White.copy(alpha = 0.9f) else Color(0xFF374151),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { mediaPermissionLauncher.launch(mediaPermissionsToRequest.toTypedArray()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E86DE),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Grant permission")
                        }
                    }
                }

                mediaItems.isEmpty() -> {
                    Text(
                        text = "No photos found on device",
                        color = if (isDarkMode) Color.White.copy(alpha = 0.8f) else Color(0xFF374151),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(mediaItems, key = { it.id }) { item ->
                            val isSelected = selectedUris.contains(item.uri)
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onToggleSelection(item.uri) }
                            ) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = "Media",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x802E86DE))
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadGroupIconDeviceMedia(context: android.content.Context): List<GroupIconMediaPickerItem> {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val result = mutableListOf<GroupIconMediaPickerItem>()
    context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val dateAdded = cursor.getLong(dateAddedColumn)
            val contentUri = ContentUris.withAppendedId(collection, id)
            result.add(GroupIconMediaPickerItem(uri = contentUri, id = id, dateAddedSeconds = dateAdded))
        }
    }

    return result
}

private fun clampPanOffsetForGroup(
    viewportSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
    scale: Float,
    currentOffset: Offset
): Offset {
    if (viewportSize.width <= 0 || viewportSize.height <= 0 || imageWidth <= 0 || imageHeight <= 0) {
        return Offset.Zero
    }

    val baseScale = max(
        viewportSize.width.toFloat() / imageWidth.toFloat(),
        viewportSize.height.toFloat() / imageHeight.toFloat()
    )
    val scaledWidth = imageWidth * baseScale * scale
    val scaledHeight = imageHeight * baseScale * scale

    val maxOffsetX = max(0f, (scaledWidth - viewportSize.width) / 2f)
    val maxOffsetY = max(0f, (scaledHeight - viewportSize.height) / 2f)

    return Offset(
        x = currentOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = currentOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

private fun createCroppedGroupImageUri(
    context: android.content.Context,
    sourceBitmap: Bitmap,
    viewportSize: IntSize,
    zoomScale: Float,
    panOffset: Offset,
    imageQuality: Int
): Uri? {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return null

    val baseScale = max(
        viewportSize.width.toFloat() / sourceBitmap.width.toFloat(),
        viewportSize.height.toFloat() / sourceBitmap.height.toFloat()
    )
    val totalScale = (baseScale * zoomScale).coerceAtLeast(0.0001f)

    val cropWidth = (viewportSize.width / totalScale).roundToInt().coerceIn(1, sourceBitmap.width)
    val cropHeight = (viewportSize.height / totalScale).roundToInt().coerceIn(1, sourceBitmap.height)

    val centerX = sourceBitmap.width / 2f - (panOffset.x / totalScale)
    val centerY = sourceBitmap.height / 2f - (panOffset.y / totalScale)

    val left = (centerX - cropWidth / 2f).roundToInt().coerceIn(0, sourceBitmap.width - cropWidth)
    val top = (centerY - cropHeight / 2f).roundToInt().coerceIn(0, sourceBitmap.height - cropHeight)

    val cropped = Bitmap.createBitmap(sourceBitmap, left, top, cropWidth, cropHeight)
    val outputSize = min(512, min(cropped.width, cropped.height)).coerceAtLeast(128)
    val normalized = Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)

    val outputFile = File(context.cacheDir, "group_crop_${System.currentTimeMillis()}.jpg")
    FileOutputStream(outputFile).use { stream ->
        normalized.compress(Bitmap.CompressFormat.JPEG, imageQuality, stream)
        stream.flush()
    }

    if (normalized != cropped) cropped.recycle()
    return Uri.fromFile(outputFile)
}

private fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
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
            this.maxHeight / 1.09f
        } else {
            this.maxHeight / 4.03f
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
                        val prefetchIdentity = flick.clientUploadId.takeIf { it.isNotBlank() }
                            ?: flick.id.takeIf { it.isNotBlank() }
                            ?: "flick_${flick.timestamp}"
                        val request = ImageRequest.Builder(context)
                            .data(withCacheBust(flick.imageUrl, prefetchIdentity))
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
            contentPadding = PaddingValues(top = 1.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(
                flicks,
                key = { flick ->
                    when {
                        flick.clientUploadId.isNotBlank() -> "cu_${flick.clientUploadId}"
                        flick.id.isNotBlank() -> flick.id
                        else -> "flick_${flick.timestamp}"
                    }
                }
            ) { flick ->
                // Stable identity to avoid tile teardown when optimistic item reconciles to server id/timestamp
                val stableIdentity = flick.clientUploadId.takeIf { it.isNotBlank() }
                    ?: flick.id.takeIf { it.isNotBlank() }
                    ?: "flick_${flick.timestamp}"

                val cacheBusted = withCacheBust(flick.imageUrl, stableIdentity)
                val imageModel = remember(stableIdentity, cacheBusted) {
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
                val bridgeKey = flick.clientUploadId.takeIf { it.isNotBlank() }
                    ?: flick.id.takeIf { it.isNotBlank() }
                    ?: ""
                val bridgedModel: Any? = when {
                    imageModel != null -> {
                        if (bridgeKey.isNotBlank()) {
                            optimisticImageBridge[bridgeKey] = cacheBusted
                            optimisticBridgeLastSeenAt[bridgeKey] = System.currentTimeMillis()
                        }
                        imageModel
                    }
                    bridgeKey.isNotBlank() && !optimisticImageBridge[bridgeKey].isNullOrBlank() -> {
                        optimisticBridgeLastSeenAt[bridgeKey] = System.currentTimeMillis()
                        ImageRequest.Builder(context)
                            .data(optimisticImageBridge[bridgeKey]!!)
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
                    if (bridgedModel == null) {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF111111)))
                    } else {
                        AsyncImage(
                            model = bridgedModel,
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    val reactionCounts = flick.getReactionCounts()
                    val topReaction = reactionCounts
                        .maxByOrNull { it.value }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = flick.userName,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .offset(y = (-0.5).dp),
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )

                        topReaction?.takeIf { it.value > 0 }?.let { (reactionType, count) ->
                            Text(
                                text = "${reactionType.toEmoji()} $count",
                                color = Color.White,
                                fontSize = 10.sp,
                                maxLines = 1,
                                modifier = Modifier.offset(y = (-0.5).dp),
                                style = LocalTextStyle.current.copy(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }
                }
            }

            if (isLoadingMore && flicks.isNotEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading more...", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            if (!canLoadMore && flicks.isNotEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No photos yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
        Text(
            text = "Share your first photo!",
            fontSize = 14.sp,
            color = Color.Gray.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun FlyingReactionAnimation(
    reaction: ReactionType,
    targetIndex: Int,
    onAnimationEnd: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 2f else 0.5f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        finishedListener = {
            onAnimationEnd()
        }, label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 1f,
        animationSpec = tween(400), label = "alpha"
    )

    val yOffset by animateFloatAsState(
        targetValue = if (startAnimation) -200f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing), label = "yOffset"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = reaction.toEmoji(),
            fontSize = 48.sp,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    translationY = yOffset
                }
        )
    }
}
