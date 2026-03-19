package com.picflick.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.picflick.app.R
import com.picflick.app.data.ChatSession
import com.picflick.app.data.UserProfile
import com.picflick.app.ui.components.BottomNavBar
import com.picflick.app.ui.components.LogoImage
import com.picflick.app.ui.theme.PicFlickBannerBackground
import com.picflick.app.ui.theme.PicFlickLightBackground
import com.picflick.app.util.rememberLiveUserPhotoUrl
import com.picflick.app.ui.theme.ThemeManager
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
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = onBack,
    onChatClick: (ChatSession, String) -> Unit,
    onStartNewChat: ((String, String, String) -> Unit)? = null,
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
    val selectedChatIds = remember { mutableStateListOf<String>() }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val isSelectionMode = selectedChatIds.isNotEmpty()
    val isDarkMode = ThemeManager.isDarkMode.value

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

    val filteredChatSessions = remember(viewModel.chatSessions, searchQuery, userProfile.uid) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        if (q.isBlank()) {
            viewModel.chatSessions
        } else {
            viewModel.chatSessions.filter { session ->
                val otherId = session.participants.firstOrNull { it != userProfile.uid } ?: ""
                val otherName = session.participantNames[otherId].orEmpty()
                otherName.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    val existingChatUserIds = remember(viewModel.chatSessions, userProfile.uid) {
        viewModel.chatSessions
            .mapNotNull { session -> session.participants.firstOrNull { it != userProfile.uid } }
            .toSet()
    }

    val availableFriendsForNewChat = remember(friendsViewModel.followingUsers, existingChatUserIds) {
        friendsViewModel.followingUsers.filter { it.uid !in existingChatUserIds }
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
                                    text = "Tap + to start chatting with friends!",
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
                                val otherUserId = session.participants.find { it != userProfile.uid } ?: ""
                                var otherUserName by remember(session.id, otherUserId) {
                                    mutableStateOf(
                                        session.participantNames[otherUserId]
                                            ?.takeIf { it.isNotBlank() }
                                            ?: "Unknown"
                                    )
                                }
                                val otherUserPhoto = rememberLiveUserPhotoUrl(
                                    userId = otherUserId,
                                    fallbackPhotoUrl = session.participantPhotos[otherUserId]
                                )

                                // Backfill name from users collection for older or incomplete chat sessions
                                LaunchedEffect(otherUserId) {
                                    if (otherUserId.isNotBlank() && otherUserName == "Unknown") {
                                        try {
                                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                .collection("users")
                                                .document(otherUserId)
                                                .get()
                                                .addOnSuccessListener { userDoc ->
                                                    otherUserName = userDoc.getString("displayName")
                                                        ?.takeIf { it.isNotBlank() }
                                                        ?: userDoc.getString("username")
                                                            ?.takeIf { it.isNotBlank() }
                                                        ?: userDoc.getString("name")
                                                            ?.takeIf { it.isNotBlank() }
                                                        ?: otherUserName
                                                }
                                        } catch (_: Exception) {
                                            // Keep current fallback values if fetch fails
                                        }
                                    }
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
                                            onChatClick(session, otherUserId)
                                        }
                                    },
                                    onLongClick = {
                                        if (selectedChatIds.contains(session.id)) {
                                            selectedChatIds.remove(session.id)
                                        } else {
                                            selectedChatIds.add(session.id)
                                        }
                                    },
                                    onProfilePhotoClick = { onUserProfileClick(otherUserId) }
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
            isLoading = friendsViewModel.isLoading,
            isDarkMode = isDarkMode,
            onDismiss = { showNewChatDialog = false },
            onFriendSelected = { friendId, friendName, friendPhoto ->
                showNewChatDialog = false
                onStartNewChat?.invoke(friendId, friendName, friendPhoto)
            },
            onUserProfileClick = onUserProfileClick
        )
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
    } else if (!isLastMessageFromMe && !session.lastMessageRead) {
        1
    } else {
        0
    }
    val hasUnread = unreadDisplayCount > 0

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
        // Profile photo - clickable to view profile
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable { onProfilePhotoClick() }
        ) {
            if (otherUserPhoto.isNotEmpty()) {
                AsyncImage(
                    model = otherUserPhoto,
                    contentDescription = otherUserName,
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
                            .clip(CircleShape)
                            .background(Color(0xFF1565C0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadDisplayCount > 99) "99+" else unreadDisplayCount.toString(),
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
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
    isLoading: Boolean,
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onFriendSelected: (String, String, String) -> Unit,
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
                        text = "Compose Message",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = if (isDarkMode) Color.White else Color.Black)
                }
            }
            friends.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No available users",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All your friends already have an active chat.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
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
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
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
