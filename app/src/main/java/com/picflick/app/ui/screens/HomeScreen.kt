package com.picflick.app.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
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
    var showUploadDialog by remember { mutableStateOf(false) }
    var showGroupsManager by remember { mutableStateOf(false) }
    val temporaryGroupExamples = remember {
        mutableStateListOf(
            "Uncle John's wedding",
            "Five a side footie talk"
        )
    }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var inviteTargetGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var adminTargetGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingGroupIconSourceUri by remember { mutableStateOf<Uri?>(null) }
    var showGroupIconCropDialog by remember { mutableStateOf(false) }
    var pendingGroupIconTarget by remember { mutableStateOf<String?>(null) }
    var groupIconCropSourceUri by remember { mutableStateOf<Uri?>(null) }
    var createDialogIconOverride by remember { mutableStateOf<String?>(null) }
    var editDialogIconOverride by remember { mutableStateOf<String?>(null) }
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

    // Group icon image picker
    val groupIconPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            groupIconCropSourceUri = uri
            showGroupIconCropDialog = true
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
        FullScreenPhotoViewer(
            flick = flick,
            currentUser = currentUserWithLivePhoto,
            onDismiss = { selectedFlick = null },
            onReaction = { reactionType ->
                val currentFlick = viewModel.flicks.getOrNull(selectedFlickIndex) ?: flick
                viewModel.toggleReaction(currentFlick, userProfile.uid, userProfile.displayName, currentUserWithLivePhoto.photoUrl, reactionType)
                val updatedFlick = viewModel.flicks.getOrNull(selectedFlickIndex)
                if (updatedFlick != null) selectedFlick = updatedFlick
            },
            onShareClick = {
                flickToShare = flick
                showShareToChatDialog = true
            },
            canDelete = flick.userId == userProfile.uid,
            onDeleteClick = {
                viewModel.removeFlickFromFeed(flick.id)
            },
            allPhotos = viewModel.flicks,
            currentIndex = selectedFlickIndex,
            onNavigateToPhoto = { index ->
                val remaining = viewModel.flicks.size - 1 - index
                if (remaining <= 2 && !viewModel.isLoadingMore) {
                    viewModel.loadMoreFlicks()
                }

                if (index in viewModel.flicks.indices) {
                    selectedFlickIndex = index
                    selectedFlick = viewModel.flicks[index]
                } else {
                    selectedFlickIndex = index.coerceAtMost((viewModel.flicks.size - 1).coerceAtLeast(0))
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
            onInviteToGroup = { group -> inviteTargetGroup = group },
            onManageAdmins = { group -> adminTargetGroup = group },
            onDeleteGroup = { group -> viewModel.deleteFriendGroup(userProfile.uid, group.id) },
            onDeleteTemporaryGroup = { title -> temporaryGroupExamples.remove(title) },
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
                groupIconPickerLauncher.launch("image/*")
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
            }
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
                groupIconPickerLauncher.launch("image/*")
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
            onCreateShared = null
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

    adminTargetGroup?.let { group ->
        val ownerId = group.effectiveOwnerId()
        val candidates = friends
            .filter { it.uid.isNotBlank() }
            .filter { group.effectiveMemberIds().contains(it.uid) }
            .filter { it.uid != ownerId }
            .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
        var selectedAdmins by remember(group.id, candidates) {
            mutableStateOf(group.adminIds.filter { it.isNotBlank() && it != ownerId && group.isMember(it) }.toSet())
        }

        AlertDialog(
            onDismissRequest = { adminTargetGroup = null },
            title = { Text("Manage admins") },
            text = {
                if (candidates.isEmpty()) {
                    Text("No eligible members to promote.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(candidates, key = { it.uid }) { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable {
                                        selectedAdmins = if (selectedAdmins.contains(member.uid)) {
                                            selectedAdmins - member.uid
                                        } else {
                                            selectedAdmins + member.uid
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF1F1F1),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    if (member.photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = withCacheBust(member.photoUrl, System.currentTimeMillis()),
                                            contentDescription = member.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = member.displayName,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Checkbox(
                                    checked = selectedAdmins.contains(member.uid),
                                    onCheckedChange = {
                                        selectedAdmins = if (it) selectedAdmins + member.uid else selectedAdmins - member.uid
                                    }
                                )
                            }
                            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFE5E5E5))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateGroupAdmins(
                        userId = userProfile.uid,
                        groupId = group.id,
                        adminIds = selectedAdmins.toList()
                    ) { success, message ->
                        Toast.makeText(
                            context,
                            if (success) "Admins updated" else (message ?: "Failed to update admins"),
                            Toast.LENGTH_SHORT
                        ).show()
                        if (success) adminTargetGroup = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { adminTargetGroup = null }) { Text("Cancel") }
            }
        )
    }

    inviteTargetGroup?.let { group ->
        val eligibleFriends = friends
            .filter { it.uid.isNotBlank() }
            .filter { !group.effectiveMemberIds().contains(it.uid) }
            .sortedBy { it.displayName.lowercase(Locale.getDefault()) }

        AlertDialog(
            onDismissRequest = { inviteTargetGroup = null },
            title = { Text("Invite friends to ${group.name}") },
            text = {
                if (eligibleFriends.isEmpty()) {
                    Text("All your friends are already in this group.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(eligibleFriends, key = { it.uid }) { friend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF1F1F1),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    if (friend.photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = withCacheBust(friend.photoUrl, System.currentTimeMillis()),
                                            contentDescription = friend.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = friend.displayName,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                OutlinedButton(
                                    onClick = {
                                        viewModel.inviteFriendToGroup(
                                            inviterId = userProfile.uid,
                                            inviterName = userProfile.displayName,
                                            groupId = group.id,
                                            inviteeId = friend.uid
                                        ) { success, message ->
                                            Toast.makeText(
                                                context,
                                                if (success) "Invite sent to ${friend.displayName}" else (message ?: "Failed to send invite"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Invite")
                                }
                            }
                            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFE5E5E5))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inviteTargetGroup = null }) { Text("Close") }
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
                onPrivacyChange = onPrivacyChange
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
    onPrivacyChange: (String) -> Unit
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
    onInviteToGroup: (FriendGroup) -> Unit,
    onManageAdmins: (FriendGroup) -> Unit,
    onDeleteGroup: (FriendGroup) -> Unit,
    onDeleteTemporaryGroup: (String) -> Unit,
    onReorderGroups: (List<String>) -> Unit
) {
    val backgroundColor = if (isDarkMode) Color(0xFF121212) else PicFlickLightBackground
    val textColor = if (isDarkMode) Color.White else Color(0xFF111111)
    val sortedGroups = remember(groups) {
        groups.sortedWith(
            compareBy<FriendGroup> { it.orderIndex }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        )
    }
    val orderedGroupIdsState = remember(sortedGroups) { mutableStateListOf<String>().apply { addAll(sortedGroups.map { it.id }) } }
    var draggingGroupId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteGroup by remember { mutableStateOf<FriendGroup?>(null) }
    var confirmDeleteTempTitle by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onDismiss() }
    ) {
        confirmDeleteGroup?.let { group ->
            AlertDialog(
                onDismissRequest = { confirmDeleteGroup = null },
                title = { Text("Delete album?") },
                text = { Text("Are you sure you want to delete '${group.name}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteGroup(group)
                        confirmDeleteGroup = null
                    }) { Text("Delete", color = Color(0xFFD84343)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteGroup = null }) { Text("Cancel") }
                }
            )
        }

        confirmDeleteTempTitle?.let { tempTitle ->
            AlertDialog(
                onDismissRequest = { confirmDeleteTempTitle = null },
                title = { Text("Delete album?") },
                text = { Text("Are you sure you want to delete '$tempTitle'?") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteTemporaryGroup(tempTitle)
                        confirmDeleteTempTitle = null
                    }) { Text("Delete", color = Color(0xFFD84343)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteTempTitle = null }) { Text("Cancel") }
                }
            )
        }

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
                        text = "Albums",
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onCreateGroup,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A4A73),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New album", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                        }
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
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

                    items(orderedGroupIdsState, key = { it }) { groupId ->
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
                            onLongPress = {
                                draggingGroupId = group.id
                            },
                            onDrag = { deltaY ->
                                if (draggingGroupId == group.id) {
                                    dragDeltaY += deltaY
                                    val moveThreshold = 28f
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
                                            Icon(Icons.Default.MoreVert, contentDescription = "Group menu", tint = textColor)
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
                                                text = { Text("Edit") },
                                                onClick = {
                                                    showGroupMenu = false
                                                    onEditGroup(group)
                                                },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                onClick = {
                                                    showGroupMenu = false
                                                    confirmDeleteGroup = group
                                                },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD84343)) }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    items(temporaryExampleGroups, key = { "temp_$it" }) { tempTitle ->
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
                                        Icon(Icons.Default.MoreVert, contentDescription = "Group menu", tint = textColor)
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
    val rowBackground = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
    val accentColour = try { Color(android.graphics.Color.parseColor(colour)) } catch (_: Exception) { Color(0xFF4FC3F7) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .pointerInput(enabled, onDrag, onDragEnd) {
                    if (enabled && onDrag != null) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { onDragEnd?.invoke() },
                            onDragCancel = { onDragEnd?.invoke() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            }
                        )
                    }
                }
                .combinedClickable(
                    enabled = enabled,
                    onClick = { onClick() },
                    onLongClick = { onLongPress?.invoke() }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
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
                    Text(text = icon, fontSize = 20.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = textColor, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Row(content = trailingContent)
        }
        HorizontalDivider(color = textColor.copy(alpha = 0.12f))
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
    onCreateShared: ((name: String, icon: String, selectedFriendIds: List<String>, color: String) -> Unit)? = null
) {
    var groupName by remember(initialName) { mutableStateOf(initialName) }
    var selectedIcon by remember(initialIcon) { mutableStateOf(initialIcon) }
    var selectedColor by remember(initialColor) { mutableStateOf(initialColor) }
    var selectedFriends by remember(initialSelectedFriendIds) { mutableStateOf(initialSelectedFriendIds.toSet()) }

    val icons = listOf(
        "👥", "👨‍👩‍👧‍👦", "💼", "🎓", "⭐", "✈️", "⚽", "🎨", "🏠", "🎵", "📚", "🎮",
        "🍔", "🍷", "🛫", "🏋️", "🏖️", "🎬", "🐶", "🚗", "🛍️", "🧠", "🧑‍💻", "📷"
    )

    val isCreateMode = onCreateLocal != null && onCreateShared != null
    val pageBackground = if (isDarkMode) Color(0xFF121212) else PicFlickLightBackground
    val textColor = if (isDarkMode) Color.White else Color(0xFF111111)
    val sortedFriends = remember(friends) {
        friends.sortedWith(
            compareBy<UserProfile>(
                { it.displayName.trim().substringAfterLast(" ").lowercase(Locale.getDefault()) },
                { it.displayName.trim().substringBeforeLast(" ").lowercase(Locale.getDefault()) },
                { it.displayName.lowercase(Locale.getDefault()) }
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .align(Alignment.BottomCenter)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = pageBackground,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    if (isCreateMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = {
                                        onCreateLocal?.invoke(groupName.trim(), selectedIcon, selectedFriends.toList(), selectedColor)
                                    },
                                    enabled = groupName.isNotBlank(),
                                    modifier = Modifier.height(36.dp),
                                    border = ButtonDefaults.outlinedButtonBorder,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2A4A73))
                                ) {
                                    Text("Create Local", fontSize = 12.sp)
                                }
                                Text(
                                    text = "ONLY FOR YOU",
                                    fontSize = 10.sp,
                                    color = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = {
                                        onCreateShared?.invoke(groupName.trim(), selectedIcon, selectedFriends.toList(), selectedColor)
                                    },
                                    enabled = groupName.isNotBlank(),
                                    modifier = Modifier.height(36.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2A4A73),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Create Shared", fontSize = 12.sp)
                                }
                                Text(
                                    text = "SHARE ALBUM WITH FRIENDS",
                                    fontSize = 10.sp,
                                    color = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onDismiss) { Text("Cancel") }
                            Button(
                                onClick = {
                                    onSubmit(groupName.trim(), selectedIcon, selectedFriends.toList(), selectedColor)
                                },
                                enabled = groupName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2A4A73),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(submitLabel)
                            }
                        }
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    label = { Text("Album name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFB7D8F2),
                        unfocusedContainerColor = Color(0xFFB7D8F2),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black.copy(alpha = 0.8f),
                        focusedBorderColor = Color.Black,
                        unfocusedBorderColor = Color.Black.copy(alpha = 0.7f)
                    )
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            text = "Icon",
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(2.dp, Color.Black, CircleShape)
                                    .clip(CircleShape)
                                    .background(if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF1F1F1))
                                    .clickable { onAddPhoto() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedIcon.startsWith("http")) {
                                    AsyncImage(
                                        model = selectedIcon,
                                        contentDescription = "Album icon photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = "Add photo", tint = textColor)
                                }
                            }

                            LazyRow(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(icons, key = { it }) { icon ->
                                    Surface(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clickable { selectedIcon = icon },
                                        shape = CircleShape,
                                        color = if (selectedIcon == icon) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        else if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF1F1F1),
                                        border = if (selectedIcon == icon) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp) }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        HorizontalDivider()

                        Text(
                            text = "Members",
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    items(sortedFriends, key = { it.uid }) { friend ->
                        val isSelected = selectedFriends.contains(friend.uid)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFriends = if (isSelected) selectedFriends - friend.uid else selectedFriends + friend.uid
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF1F1F1),
                                modifier = Modifier.size(36.dp)
                            ) {
                                if (friend.photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = withCacheBust(friend.photoUrl, System.currentTimeMillis()),
                                        contentDescription = "${friend.displayName} profile photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = textColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = friend.displayName,
                                color = textColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            FilledIconButton(
                                onClick = {
                                    selectedFriends = if (isSelected) selectedFriends - friend.uid else selectedFriends + friend.uid
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isSelected) Color(0xFF2E7D32) else if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF1F1F1),
                                    contentColor = if (isSelected) Color.White else textColor
                                ),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = if (isSelected) "Added" else "Add",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = textColor.copy(alpha = 0.12f))
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
