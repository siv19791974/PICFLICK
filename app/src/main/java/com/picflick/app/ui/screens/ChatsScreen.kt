package com.picflick.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.picflick.app.R
import com.picflick.app.data.ChatSession
import com.picflick.app.data.Result
import com.picflick.app.data.FriendGroup
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.components.BottomNavBar
import com.picflick.app.ui.components.LogoImage
import com.picflick.app.ui.theme.PicFlickBannerBackground
import com.picflick.app.repository.FlickRepository
import com.picflick.app.ui.theme.PicFlickLightBackground
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.picflick.app.util.rememberLiveUserTierColor
import com.picflick.app.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import com.picflick.app.ui.theme.isDarkModeBackground
import com.picflick.app.viewmodel.ChatViewModel
import com.picflick.app.viewmodel.FriendsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * WhatsApp-style Chats List Screen
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChatsScreen(
    userProfile: UserProfile,
    viewModel: ChatViewModel,
    friendsViewModel: FriendsViewModel,
    friendGroups: List<FriendGroup> = emptyList(),
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = onBack,
    onChatClick: (ChatSession, String) -> Unit,
    onStartNewChat: ((String, String, String) -> Unit)? = null,
    onCreateSharedGroup: (String, String, List<String>, (Boolean, FriendGroup?) -> Unit) -> Unit = { _, _, _, _ -> },
    onOpenGroupChat: ((FriendGroup) -> Unit)? = null,
    onUserProfileClick: (String) -> Unit = {},
    onBottomNavNavigate: (String) -> Unit = {}
) {
    LaunchedEffect(userProfile.uid) {
        viewModel.loadChatSessions(userProfile.uid)
        viewModel.observeUnreadCount(userProfile.uid)
        // Load friends for new chat dialog
        friendsViewModel.loadFollowingUsers(userProfile.following)
    }

    var showNewChatDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var pendingGroupIconSourceUri by remember { mutableStateOf<Uri?>(null) }
    var showGroupIconCropDialog by remember { mutableStateOf(false) }
    var groupIconCropSourceUri by remember { mutableStateOf<Uri?>(null) }
    var createDialogIconOverride by remember { mutableStateOf<String?>(null) }
    val selectedChatIds = remember { mutableStateListOf<String>() }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val isSelectionMode = selectedChatIds.isNotEmpty()
    val isDarkMode = ThemeManager.isDarkMode.value
    val context = androidx.compose.ui.platform.LocalContext.current

    // Modern PullRefresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isLoading,
        onRefresh = { viewModel.loadChatSessions(userProfile.uid) }
    )

    val selectedChatId = selectedChatIds.firstOrNull()
    val selectedSession = remember(selectedChatId, viewModel.chatSessions) {
        viewModel.chatSessions.firstOrNull { it.id == selectedChatId }
    }
    val selectedOtherUserId = remember(selectedSession, userProfile.uid) {
        selectedSession?.participants?.firstOrNull { it != userProfile.uid }
    }

    val inboxChatSessions = remember(viewModel.chatSessions, friendGroups, userProfile.uid) {
        fun ChatSession.isGroupLike(): Boolean = isGroup || groupId.isNotBlank() || id.startsWith("group_")

        val normalizedSessions = viewModel.chatSessions.map { session ->
            if (session.isGroupLike()) {
                session.copy(
                    isGroup = true,
                    groupId = session.groupId.ifBlank { session.id.removePrefix("group_") },
                    groupName = session.groupName.ifBlank { "Group" },
                    groupIcon = session.groupIcon.ifBlank { "👥" }
                )
            } else {
                session
            }
        }

        val existingGroupSessionsByGroupId = normalizedSessions
            .filter { it.isGroup }
            .associateBy { session ->
                session.groupId.ifBlank { session.id.removePrefix("group_") }
            }

        val seededGroupSessions = friendGroups.map { group ->
            existingGroupSessionsByGroupId[group.id] ?: ChatSession(
                id = "group_${group.id}",
                participants = group.effectiveMemberIds().ifEmpty { listOf(userProfile.uid) },
                participantNames = mapOf(userProfile.uid to userProfile.displayName),
                participantPhotos = mapOf(userProfile.uid to userProfile.photoUrl),
                lastMessage = "",
                lastTimestamp = 0L,
                unreadCount = 0,
                isGroup = true,
                groupId = group.id,
                groupName = group.name,
                groupIcon = group.icon
            )
        }

        (normalizedSessions + seededGroupSessions)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<ChatSession> { it.lastTimestamp }
                    .thenBy { it.id }
            )
    }

    val filteredChatSessions = remember(inboxChatSessions, searchQuery, userProfile.uid) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        if (q.isBlank()) {
            inboxChatSessions
        } else {
            inboxChatSessions.filter { session ->
                val searchableName = if (session.isGroup) {
                    session.groupName
                } else {
                    val otherId = session.participants.firstOrNull { it != userProfile.uid } ?: ""
                    session.participantNames[otherId].orEmpty()
                }
                searchableName.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    val existingChatUserIds = remember(inboxChatSessions, userProfile.uid) {
        inboxChatSessions
            .filter { !it.isGroup }
            .mapNotNull { session -> session.participants.firstOrNull { it != userProfile.uid } }
            .toSet()
    }

    val availableFriendsForNewChat = remember(friendsViewModel.followingUsers, existingChatUserIds) {
        friendsViewModel.followingUsers.filter { it.uid !in existingChatUserIds }
    }

    val groupIconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            groupIconCropSourceUri = uri
            showGroupIconCropDialog = true
        }
    }

    // One-level phone back behavior inside Messages stack:
    // Create Group -> Compose -> Messages -> Home
    BackHandler(enabled = showGroupIconCropDialog) {
        showGroupIconCropDialog = false
        groupIconCropSourceUri = null
    }

    BackHandler(enabled = showCreateGroupDialog && !showGroupIconCropDialog) {
        showCreateGroupDialog = false
        showNewChatDialog = true
        createDialogIconOverride = null
    }

    BackHandler(enabled = showNewChatDialog && !showCreateGroupDialog && !showGroupIconCropDialog) {
        showNewChatDialog = false
    }

    LaunchedEffect(pendingGroupIconSourceUri) {
        val uri = pendingGroupIconSourceUri ?: return@LaunchedEffect
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val imageBytes = inputStream?.readBytes()
            inputStream?.close()
            if (imageBytes != null) {
                when (val uploadResult = FlickRepository.getInstance().uploadFlickImage(userProfile.uid, imageBytes)) {
                    is Result.Success -> {
                        createDialogIconOverride = uploadResult.data
                        Toast.makeText(context, "Group photo added", Toast.LENGTH_SHORT).show()
                    }
                    is Result.Error -> {
                        Toast.makeText(context, "Failed to upload group photo", Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to process group photo", Toast.LENGTH_SHORT).show()
        } finally {
            pendingGroupIconSourceUri = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(isDarkModeBackground(isDarkMode))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = if (isSelectionMode) {
                            "Selected (${selectedChatIds.size})"
                        } else {
                            "My Messages (${filteredChatSessions.size})"
                        },
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Box {
                        IconButton(
                            onClick = { showHeaderMenu = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text(
                                text = "⋮",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(
                            expanded = showHeaderMenu,
                            onDismissRequest = { showHeaderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mark selected as read") },
                                onClick = {
                                    selectedChatIds.toList().forEach { chatId ->
                                        viewModel.markAsRead(chatId, userProfile.uid)
                                    }
                                    selectedChatIds.clear()
                                    showHeaderMenu = false
                                },
                                enabled = selectedChatIds.isNotEmpty()
                            )
                            DropdownMenuItem(
                                text = { Text("Clear selection") },
                                onClick = {
                                    selectedChatIds.clear()
                                    showHeaderMenu = false
                                },
                                enabled = selectedChatIds.isNotEmpty()
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (selectedChatIds.size <= 1) "Delete selected" else "Delete selected (${selectedChatIds.size})",
                                        color = Color.Red
                                    )
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    showDeleteConfirm = true
                                },
                                enabled = selectedChatIds.isNotEmpty()
                            )
                            DropdownMenuItem(
                                text = { Text("Block user", color = Color.Red) },
                                onClick = {
                                    showHeaderMenu = false
                                    showBlockConfirm = true
                                },
                                enabled = selectedChatIds.size == 1 && !selectedOtherUserId.isNullOrBlank()
                            )
                        }
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
                placeholder = { Text("Search conversations") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                shape = RoundedCornerShape(12.dp)
            )

            // Error message display - compact snackbar style, only when NOT loading
            if (viewModel.errorMessage != null && !viewModel.isLoading) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { 
                                viewModel.clearError()
                                viewModel.loadChatSessions(userProfile.uid) 
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Retry", 
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Modern PullRefresh content - uses weight to fill remaining space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pullRefresh(pullRefreshState)
            ) {
                when {
                    viewModel.isLoading && filteredChatSessions.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    filteredChatSessions.isEmpty() -> {
                        // Empty state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = stringResource(R.string.content_desc_person),
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No messages yet",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap + to start a new chat.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                    else -> {
                        // Chat list - WhatsApp style
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = filteredChatSessions,
                                key = { it.id },
                                contentType = { "chat_session" }
                            ) { session ->
                                val isGroupSession = session.isGroup
                                val otherUserId = if (isGroupSession) {
                                    "group:${session.groupId.ifBlank { session.id }}"
                                } else {
                                    session.participants.find { it != userProfile.uid } ?: ""
                                }
                                var otherUserName by remember(session.id, otherUserId) {
                                    mutableStateOf(
                                        if (isGroupSession) {
                                            session.groupName.ifBlank { "Group chat" }
                                        } else {
                                            session.participantNames[otherUserId]
                                                ?.takeIf { it.isNotBlank() }
                                                ?: "Unknown"
                                        }
                                    )
                                }
                                val otherUserPhoto = if (isGroupSession) {
                                    ""
                                } else {
                                    rememberLiveUserPhotoUrl(
                                        userId = otherUserId,
                                        fallbackPhotoUrl = session.participantPhotos[otherUserId]
                                    )
                                }


                                ChatListItem(
                                    session = session,
                                    otherUserId = otherUserId,
                                    otherUserName = otherUserName,
                                    otherUserPhoto = otherUserPhoto,
                                    currentUserId = userProfile.uid,
                                    isSelected = selectedChatIds.contains(session.id),
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (selectedChatIds.contains(session.id)) {
                                                selectedChatIds.remove(session.id)
                                            } else {
                                                selectedChatIds.add(session.id)
                                            }
                                        } else {
                                            if (isGroupSession) {
                                                val targetGroupId = session.groupId.ifBlank { session.id.removePrefix("group_") }
                                                val targetGroup = friendGroups.firstOrNull { it.id == targetGroupId }
                                                if (targetGroup != null && onOpenGroupChat != null) {
                                                    onOpenGroupChat(targetGroup)
                                                } else {
                                                    onChatClick(session, otherUserId)
                                                }
                                            } else {
                                                onChatClick(session, otherUserId)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (selectedChatIds.contains(session.id)) {
                                            selectedChatIds.remove(session.id)
                                        } else {
                                            selectedChatIds.add(session.id)
                                        }
                                    },
                                    onProfilePhotoClick = {
                                        if (!isGroupSession && otherUserId.isNotBlank()) {
                                            onUserProfileClick(otherUserId)
                                        }
                                    }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 80.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }

                // Modern PullRefreshIndicator
                PullRefreshIndicator(
                    refreshing = viewModel.isLoading,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        // NEW CHAT FAB - Bottom right
        FloatingActionButton(
            onClick = { showNewChatDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color.Black,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Message",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete conversation(s)?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedChatIds.toList().forEach { chatId ->
                            viewModel.deleteChat(chatId)
                        }
                        selectedChatIds.clear()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Block user?") },
            text = { Text("This will instantly report and block this user, and remove this conversation.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val otherUserId = selectedOtherUserId
                        if (!otherUserId.isNullOrBlank()) {
                            viewModel.blockAndReportUser(
                                currentUserId = userProfile.uid,
                                targetUserId = otherUserId,
                                chatId = selectedChatId
                            )
                        }
                        selectedChatIds.clear()
                        showBlockConfirm = false
                    }
                ) {
                    Text("Block", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New Chat Dialog
    if (showNewChatDialog) {
        NewChatDialog(
            friends = availableFriendsForNewChat,
            groups = friendGroups,
            isLoading = friendsViewModel.isLoading,
            isDarkMode = isDarkMode,
            onDismiss = { showNewChatDialog = false },
            onFriendSelected = { friendId, friendName, friendPhoto ->
                showNewChatDialog = false
                onStartNewChat?.invoke(friendId, friendName, friendPhoto)
            },
            onOpenGroupFlow = {
                showNewChatDialog = false
                showCreateGroupDialog = true
            },
            onOpenGroupChat = { group ->
                showNewChatDialog = false
                onOpenGroupChat?.invoke(group)
            },

            onUserProfileClick = onUserProfileClick
        )
    }

    if (showCreateGroupDialog) {
        NewGroupFromComposeDialog(
            friends = friendsViewModel.followingUsers,
            isLoading = friendsViewModel.isLoading,
            isDarkMode = isDarkMode,
            initialIcon = createDialogIconOverride ?: "👥",
            onDismiss = {
                showCreateGroupDialog = false
                createDialogIconOverride = null
            },
            onAddPhoto = {
                groupIconPickerLauncher.launch("image/*")
            },
            onCreateSharedGroup = { name, icon, selectedFriendIds, onDone ->
                onCreateSharedGroup(name, icon, selectedFriendIds) { success, createdGroup ->
                    if (success && createdGroup != null) {
                        showCreateGroupDialog = false
                        createDialogIconOverride = null
                        onOpenGroupChat?.invoke(createdGroup)
                    }
                    onDone(success, createdGroup)
                }
            },
            onUserProfileClick = onUserProfileClick
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewGroupFromComposeDialog(
    friends: List<com.picflick.app.data.UserProfile>,
    isLoading: Boolean,
    isDarkMode: Boolean,
    initialIcon: String,
    onDismiss: () -> Unit,
    onAddPhoto: () -> Unit,
    onCreateSharedGroup: (String, String, List<String>, (Boolean, FriendGroup?) -> Unit) -> Unit,
    onUserProfileClick: (String) -> Unit
) {
    BackHandler(onBack = onDismiss)

    var groupName by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }
    var selectedIcon by remember(initialIcon) { mutableStateOf(initialIcon) }
    var isSubmitting by remember { mutableStateOf(false) }

    val pageBackground = if (isDarkMode) Color.Black else PicFlickLightBackground
    val textColor = if (isDarkMode) Color.White else Color.Black
    val addColor = Color(0xFF1E88E5)
    val waitingColor = Color(0xFF2A4A73)

    val icons = listOf(
        "👥", "👨‍👩‍👧‍👦", "💼", "🎓", "⭐", "✈️", "⚽", "🎨", "🏠", "🎵", "📚", "🎮",
        "🍔", "🍷", "🛫", "🏋️", "🏖️", "🎬", "🐶", "🚗", "🛍️", "🧠", "🧑‍💻", "📷"
    )
    val canCreate = groupName.trim().isNotEmpty() && selectedIds.isNotEmpty() && !isSubmitting
    val sortedFriends = remember(friends) {
        friends.sortedWith(
            compareBy<com.picflick.app.data.UserProfile>(
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
                        text = "Create Chat Group",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
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
                }
            }

            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                label = { Text("Group name") },
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clickable { onAddPhoto() },
                    shape = CircleShape,
                    color = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF1F1F1),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkMode) Color(0xFF4A4A4A) else Color(0xFFB0BEC5))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selectedIcon.startsWith("http")) {
                            AsyncImage(
                                model = selectedIcon,
                                contentDescription = "Group icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Add photo",
                                tint = textColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(icons, key = { it }) { icon ->
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { selectedIcon = icon },
                            shape = CircleShape,
                            color = if (selectedIcon == icon) waitingColor else if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF1F1F1),
                            border = if (selectedIcon == icon) null else androidx.compose.foundation.BorderStroke(1.dp, if (isDarkMode) Color(0xFF4A4A4A) else Color(0xFFB0BEC5))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = icon, fontSize = 20.sp, color = if (selectedIcon == icon) Color.White else textColor)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = if (isDarkMode) Color(0xFF222222) else Color(0x22000000))

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(sortedFriends, key = { it.uid }) { friend ->
                        val isSelected = selectedIds.contains(friend.uid)
                        val livePhoto = rememberLiveUserPhotoUrl(friend.uid, friend.photoUrl)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (livePhoto.isNotEmpty()) {
                                AsyncImage(
                                    model = livePhoto,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .clickable { onUserProfileClick(friend.uid) },
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = android.R.drawable.ic_menu_myplaces)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
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
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${friend.followers.size} followers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDarkMode) Color.Gray else Color.DarkGray
                                )
                            }

                            if (isSelected) {
                                OutlinedButton(
                                    onClick = { selectedIds.remove(friend.uid) },
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
                                    onClick = { selectedIds.add(friend.uid) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = addColor,
                                        disabledContainerColor = Color.Transparent,
                                        disabledContentColor = addColor
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
            }

            Button(
                onClick = {
                    if (!canCreate) return@Button
                    isSubmitting = true
                    onCreateSharedGroup(groupName.trim(), selectedIcon, selectedIds.toList()) { success, _ ->
                        isSubmitting = false
                        if (success) onDismiss()
                    }
                },
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = waitingColor,
                    contentColor = Color.White
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Create shared")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    session: ChatSession,
    otherUserId: String,
    otherUserName: String,
    otherUserPhoto: String,
    currentUserId: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onProfilePhotoClick: () -> Unit = {}
) {
    val isLastMessageFromMe = session.lastSenderId == currentUserId
    val unreadDisplayCount = if (session.unreadCount > 0) {
        session.unreadCount
    } else {
        0
    }
    val hasUnread = unreadDisplayCount > 0
    val tierRingColor = rememberLiveUserTierColor(otherUserId)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0x221565C0) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar / group badge
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable { onProfilePhotoClick() }
        ) {
            if (session.isGroup) {
                val groupIcon = session.groupIcon.ifBlank { "👥" }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, Color.Black, CircleShape)
                        .background(Color(0xFF1565C0).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (groupIcon.startsWith("http")) {
                        AsyncImage(
                            model = groupIcon,
                            contentDescription = otherUserName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = groupIcon,
                            fontSize = 22.sp
                        )
                    }
                }
            } else if (otherUserPhoto.isNotEmpty()) {
                AsyncImage(
                    model = otherUserPhoto,
                    contentDescription = otherUserName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Chat info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Name and time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = otherUserName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Time and read/unread status row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // If you SENT the last message, show red/green dot
                    if (isLastMessageFromMe) {
                        val isRead = session.lastMessageRead
                        val dotColor = if (isRead) Color(0xFF25D366) else Color.Red
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .align(Alignment.CenterVertically)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    Text(
                        text = formatChatTime(session.lastTimestamp),
                        fontSize = 12.sp,
                        color = if (hasUnread) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Last message preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        if (isLastMessageFromMe) append("You: ")
                        append(session.lastMessage)
                    },
                    fontSize = 14.sp,
                    color = if (hasUnread) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    },
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Unread messages counter (small blue circle)
                if (hasUnread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .offset(y = (-1).dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1565C0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadDisplayCount > 99) "99+" else unreadDisplayCount.toString(),
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatChatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""

    val date = Date(timestamp)
    val now = Date()
    val diff = now.time - date.time

    val oneDay = 24 * 60 * 60 * 1000

    return when {
        diff < oneDay -> {
            // Today - show time
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
        diff < oneDay * 7 -> {
            // This week - show day name
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        }
        else -> {
            // Older - show date
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
        }
    }
}

/**
 * Full-screen dialog to select a friend to start a new chat with
 * WhatsApp-style contact selector - follows Lists pattern WITH MainActivity header/footer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatDialog(
    friends: List<com.picflick.app.data.UserProfile>,
    groups: List<FriendGroup>,
    isLoading: Boolean,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onFriendSelected: (String, String, String) -> Unit,
    onOpenGroupFlow: () -> Unit,
    onOpenGroupChat: (FriendGroup) -> Unit,
    onUserProfileClick: (String) -> Unit
) {
    val backgroundColor = if (isDarkMode) Color.Black else PicFlickLightBackground

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "New message",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                    item("groups_label") {
                        Text(
                            text = "Groups",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    item("new_group_action") {
                        NewContactActionItem(
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            title = "Create group",
                            isDarkMode = isDarkMode,
                            onClick = onOpenGroupFlow
                        )
                    }


                    item("friends_label") {
                        Text(
                            text = "Friends",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    when {
                        isLoading -> {
                            item("friends_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = if (isDarkMode) Color.White else Color.Black)
                                }
                            }
                        }
                        friends.isEmpty() -> {
                            item("friends_empty") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No available friends",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "All your friends already have an active chat.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        }
                        else -> {
                            items(
                                items = friends,
                                key = { it.uid },
                                contentType = { "friend" }
                            ) { friend ->
                                FullScreenFriendItem(
                                    friend = friend,
                                    isDarkMode = isDarkMode,
                                    onClick = { onFriendSelected(friend.uid, friend.displayName, friend.photoUrl) },
                                    onProfilePhotoClick = { onUserProfileClick(friend.uid) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

/**
 * Action item for new group/contact - WhatsApp style
 */
@Composable
private fun NewContactActionItem(
    icon: @Composable () -> Unit,
    title: String,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDarkMode) Color.White else Color.Black
        )
    }
}

/**
 * Friend item for full-screen contact selector - WhatsApp style
 */
@Composable
private fun FullScreenFriendItem(
    friend: com.picflick.app.data.UserProfile,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    onProfilePhotoClick: () -> Unit
) {
    val liveFriendPhoto = rememberLiveUserPhotoUrl(friend.uid, friend.photoUrl)
    val tierRingColor = rememberLiveUserTierColor(friend.uid)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile photo - 56dp like ChatListItem
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable { onProfilePhotoClick() }
        ) {
            if (liveFriendPhoto.isNotEmpty()) {
                AsyncImage(
                    model = liveFriendPhoto,
                    contentDescription = friend.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, tierRingColor, CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Friend info - same structure as ChatListItem
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Name row
            Text(
                text = friend.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Bio/subtitle row - like last message
            val subtitle = friend.bio.takeIf { it.isNotBlank() } ?: "Available"
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Start chat with ${friend.displayName}",
                tint = if (isDarkMode) Color.White else Color.Black
            )
        }
    }
}

/**
 * Individual friend item in the new chat dialog
 */
@Composable
private fun FriendListItem(
    friend: com.picflick.app.data.UserProfile,
    isDarkMode: Boolean,
    onClick: () -> Unit,
    onProfilePhotoClick: () -> Unit
) {
    val liveFriendPhoto = rememberLiveUserPhotoUrl(friend.uid, friend.photoUrl)
    val tierRingColor = rememberLiveUserTierColor(friend.uid)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile photo
        Box(
            modifier = Modifier.clickable { onProfilePhotoClick() }
        ) {
            AsyncImage(
                model = liveFriendPhoto,
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .border(2.dp, tierRingColor, CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Friend name
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = friend.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkMode) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "Tap to start chatting",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        // Chat icon
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = "Start chat",
            modifier = Modifier.size(24.dp),
            tint = Color.Black
        )
    }
}
